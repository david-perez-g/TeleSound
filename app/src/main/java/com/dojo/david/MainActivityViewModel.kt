package com.dojo.david

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.lang.IllegalStateException
import java.lang.ref.WeakReference


@Suppress("UNCHECKED_CAST")
class MainActivityViewModelFactory(
    private val context: Context,
    private val activity: MainActivity,
) :
    ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainActivityViewModel(context, activity) as T
    }
}

data class ClientMessage(val author: String, val content: String)

class MainActivityViewModel(
    context: Context,
    activity: MainActivity,
) : ViewModel(), P2pEventListener {
    private val contextWeakReference = WeakReference(context)
    private val activityWeakReference = WeakReference(activity)

    private fun getActivity(): MainActivity {
        return activityWeakReference.get()!!
    }

    fun show(message: String) {
        showShortToast(contextWeakReference.get()!!, message)
    }

    // State for Wi-Fi toggle
    var isP2pDiscoveryOn by mutableStateOf(false)
        private set

    // List of available users
    val p2pAvailableUsers = mutableStateListOf<WifiP2pDevice>()

    var p2pGroup: WifiP2pGroup? by mutableStateOf(null)

    val deviceName: String = if (Build.VERSION.SDK_INT >= 25) Settings.Global.getString(
        contextWeakReference.get()!!.contentResolver,
        Settings.Global.DEVICE_NAME
    ) else Build.MODEL

    var chatServer: RemoteChatServer? = null
    var serverAddress by mutableStateOf(DEFAULT_CHAT_ADDRESS)
    var serverPort by mutableStateOf(DEFAULT_CHAT_PORT)
    var chatServerHasStarted by mutableStateOf(false)
    
    var chatClient: RemoteChatClient? = null
    var targetChatAddress by mutableStateOf(DEFAULT_CHAT_ADDRESS)
    var targetChatPort by mutableStateOf(DEFAULT_CHAT_PORT)
    var chatClientHasStarted by mutableStateOf(false)

    val uiChatMessages = mutableStateListOf<ClientMessage>()

    private var audioRecord: AudioRecord? = null
    private val audioMinBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
    var isRecordingAudio = false

    companion object {
        const val DEFAULT_CHAT_ADDRESS = "127.0.0.1"
        const val DEFAULT_CHAT_PORT = "8888"
        const val SAMPLE_RATE = 44100
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val TAG = "MainActivityViewModel"
    }

    // P2p Event Listeners
    override fun onLocationPermissionNeeded() {
        Log.i(TAG, "Location permission needed")
        getActivity().requestLocationPermission()
    }

    override fun onConnection(info: WifiP2pInfo) {
        Log.i(TAG, "New connection, updating group info")
        updateP2pGroupInformation()
        if (info.groupFormed) {
            if (info.isGroupOwner) {
                serverAddress = info.groupOwnerAddress.toString().substring(1)
            } else {
                serverAddress = getLocalIPAddress() ?: DEFAULT_CHAT_ADDRESS
            }
            targetChatAddress = info.groupOwnerAddress.toString().substring(1)
        }
    }

    @Suppress("MissingPermission")
    private fun updateP2pGroupInformation(onPostExecute: () -> Unit = {}) {
        val activity = getActivity()
        val manager = activity.manager!!
        val channel = activity.channel!!

        manager.requestGroupInfo(channel) {
            p2pGroup = it
            onPostExecute()
        }
    }

    override fun onDisconnection() {
        if (p2pGroup == null) return

        val oldClientList = p2pGroup!!.clientList.toSet()
        updateP2pGroupInformation(onPostExecute = {
            if (p2pGroup == null) {
                // the group was destroyed
                stopSocketServer()
                stopSocketClient()
                show("Left group")
                return@updateP2pGroupInformation
            }

            val newClientList = p2pGroup!!.clientList.toSet()
            val missingClients = oldClientList.minus(newClientList)
            missingClients.forEach { show("${it.deviceName} left the group") } // TODO: test
        })
    }

    override fun onNewDevices(devices: Collection<WifiP2pDevice>) {
        p2pAvailableUsers.clear()
        p2pAvailableUsers.addAll(devices)
        p2pAvailableUsers.sortBy { it.deviceName }

        Log.i(TAG, "New devices")
        for (device in devices) {
            Log.i(TAG, "- ${device.deviceName}")
        }
    }

    private fun areLocationPermissionsGranted(): Boolean {
        return isPermissionGrantedFor(
            getActivity().applicationContext,
            getActivity().locationPermissions
        )
    }

    private fun areAudioRecordingPermissionsGranted(): Boolean {
        return isPermissionGrantedFor(
            getActivity().applicationContext,
            getActivity().audioRecordPermission
        )
    }

    @SuppressLint("MissingPermission")
    fun switchP2pDiscoveryState() {
        val activity = getActivity()

        if (!activity.isP2pInitialized()) {
            activity.initializeP2p()

            if (!activity.isP2pInitialized()) {
                Log.i(TAG, "P2p failed to initialize")
                show("Wi-Fi Direct failed to initialize")
                return
            }

            // p2p was initialized for the first time
            activity.p2pBroadcastReceiver!!.addListener(this)
            Log.i(TAG, "P2p was initialized correctly")
        }

        if (!areLocationPermissionsGranted()) {
            show("Grant permission for location and try again")
            onLocationPermissionNeeded()
            return
        }

        val manager = activity.manager!!
        val channel = activity.channel!!

        if (isP2pDiscoveryOn) {
            manager.stopPeerDiscovery(channel,
                object : ActionListener {
                    override fun onSuccess() {
                        isP2pDiscoveryOn = false
                        Log.i(TAG, "Discovery stopped successfully")
                        show("Discovery stopped")
                        p2pAvailableUsers.clear()
                    }

                    override fun onFailure(reason: Int) {
                        Log.i(TAG, "Discovery failed to stop $reason")
                        show("Discovery failed to stop, try again in a few seconds")
                    }
                }
            )
        } else {
            manager.discoverPeers(channel,
                object : ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Discovery started")
                        show("Discovery started")
                        isP2pDiscoveryOn = true
                    }

                    override fun onFailure(reason: Int) {
                        Log.i(TAG, "Discovery failed: $reason")
                        show("Discovery failed to start, try re-enabling location and Wi-Fi")
                    }
                }
            )
        }
    }

    fun disconnect() {
        val activity = getActivity()
        val manager = activity.manager!!
        val channel = activity.channel!!

        manager.removeGroup(channel, object : ActionListener {
            override fun onSuccess() {
                show("Group removed")
                stopSocketServer()
                stopSocketClient()
                p2pGroup = null
            }

            override fun onFailure(reason: Int) {
                Log.i(TAG, "Couldn't remove group, reason: $reason")
                show("Couldn't remove group")
            }
        })
    }

    fun switchSocketServerState() {
        if (chatServerHasStarted) {
            stopSocketServer()
        } else {
            startSocketServer()
        }
    }

    fun switchSocketClientState() {
        if (chatClientHasStarted) {
            stopSocketClient()
        } else {
            startSocketClient(targetChatAddress, targetChatPort)
        }
    }

    fun startSocketServer(): Boolean {
        var attempts = 5
        while (attempts > 0) {
            try {
                chatServer = RemoteChatServer(serverPort)
                chatServer!!.acceptConnections()
                chatServerHasStarted = true
                return true
            } catch (e: Exception) {
                // exceptions at this point
                // are likely to be a result of an already used port
                serverPort = getAvailablePort(serverPort.toInt() + 1, 50000).toString()
            }
            attempts--
        }

        return false
    }

    fun stopSocketServer() {
        chatServer?.stop()
        chatServer = null
        chatServerHasStarted = false
    }

    fun startSocketClient(host: String, port: String) {
        chatClient = RemoteChatClient(
            deviceName,
            host,
            port,
            onStartSuccess = {
                chatClient!!.setMessageListener {
                    uiChatMessages.add(it)
                }

                chatClientHasStarted = true

                Log.i(TAG, "Chat client connected at $host:$port")
            },
            onStartFailure = {
                show("Socket failed to connect")
            },
            onDisconnection = {
                chatClientHasStarted = false
                show("Socket was disconnected")
                uiChatMessages.clear()
            }
        )
    }
    
    fun stopSocketClient() {
        chatClient?.stop()
        chatClient = null
        chatClientHasStarted = false
    }

    @Suppress("MissingPermission")
    fun connectWith(user: WifiP2pDevice) {
        if (!areLocationPermissionsGranted()) {
            throw IllegalStateException()
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = user.deviceAddress
        }


        getActivity().manager!!.connect(
            getActivity().channel!!,
            config,
            object : ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Connected to ${user.deviceName}")
                    show("Connected to ${user.deviceName}")
                    // All other actions after successfully connecting will
                    // be performed in the onConnection callback
                }

                override fun onFailure(reason: Int) {
                    Log.i(TAG, "Failed to connect with ${user.deviceName}, reason: $reason")
                    show("Failed to connect with ${user.deviceName}.")
                }
            }
        )
    }

    @Suppress("MissingPermission")
    fun initializeAudioRecorder() {
        if (!areAudioRecordingPermissionsGranted()) {
            show("Grant the permission to record audio and try again")
            getActivity().requestAudioRecordingPermission()
            return
        }

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, audioMinBufferSize)

        if (audioRecord!!.state == AudioRecord.STATE_UNINITIALIZED) {
            show("Failed to initialize audio recorder")
        }
    }

    fun startAudioEmission() {
        if (audioRecord == null || !chatClientHasStarted) return

        // Check if audioRecord is already recording
        if (audioRecord!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            Log.i(TAG, "Audio is already being recorded")
            return
        }

        if (audioRecord!!.state == AudioRecord.STATE_UNINITIALIZED) {
            Log.i(TAG, "Audio record is initialized")
            return
        }

        audioRecord!!.startRecording()
        isRecordingAudio = true
        Thread {
            val buffer = ByteArray(audioMinBufferSize)
            while (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord!!.read(buffer, 0, buffer.size)

                // Send the audio data over the socket
                if (bytesRead > 0) {
                    chatClient?.send("")
                }
            }
        }.start()
    }

    fun stopAudioEmission() {
        if (isRecordingAudio) {
            audioRecord!!.stop()
            isRecordingAudio = false
        }
    }
}
