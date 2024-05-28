package com.dojo.david

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.dojo.david.views.TabScreen

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private val vm: MainActivityViewModel by viewModels {
        MainActivityViewModelFactory(
            applicationContext, this
        )
    }

    // Wi-Fi P2p related
    val manager: WifiP2pManager? by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
    }
    var channel: WifiP2pManager.Channel? = null
    var p2pBroadcastReceiver: WifiP2pBroadcastReceiver? = null
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    val locationPermissions = if (Build.VERSION.SDK_INT >= 33) listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val audioRecordPermission = Manifest.permission.RECORD_AUDIO

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    var isBroadcastReceiverRegistered = false

    fun requestLocationPermission() {
        locationPermissions.forEach { requestPermissionLauncher.launch(it) }
    }

    fun requestAudioRecordingPermission() {
        requestPermissionLauncher.launch(audioRecordPermission)
    }

    fun initializeP2p() {
        channel = manager?.initialize(this, mainLooper, null)
        channel?.also { channel ->
            p2pBroadcastReceiver = WifiP2pBroadcastReceiver(manager!!, channel)
            isBroadcastReceiverRegistered = true
            registerReceiver(p2pBroadcastReceiver, intentFilter)
        }
    }

    fun isP2pInitialized(): Boolean {
        return channel != null && p2pBroadcastReceiver != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}

        setContent {
            MaterialTheme {
                TabScreen(viewModel = vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isBroadcastReceiverRegistered) {
            p2pBroadcastReceiver?.also { receiver ->
                Log.i(TAG, "P2p Broadcast receiver registered")
                isBroadcastReceiverRegistered = true
                registerReceiver(receiver, intentFilter)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isBroadcastReceiverRegistered) {
            p2pBroadcastReceiver?.also { receiver ->
                Log.i(TAG, "P2p Broadcast receiver unregistered")
                isBroadcastReceiverRegistered = false
                unregisterReceiver(receiver)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.stopSocketServer()
        vm.stopSocketClient()
    }
}
