package com.dojo.david

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import android.util.Log

interface P2pEventListener {
    fun onChannelLost() {}
    fun onNewDevices(devices: Collection<WifiP2pDevice>) {}
    fun onConnection(info: WifiP2pInfo) {}
    fun onDisconnection() {}
    fun onWifiP2pOff() {}
    fun onWifiP2pOn() {}
    fun onDiscoveryStarted() {}
    fun onDiscoveryStopped() {}
    fun onDeviceChanged() {}
    fun onLocationPermissionNeeded() {}
}

class WifiP2pBroadcastReceiver(
    private val manager: WifiP2pManager,
    private var channel: Channel,
) : BroadcastReceiver(), EventEmitter<P2pEventListener> {
    private val TAG = "WifiP2pBroadcastReceiver"

    private val permissions = if (Build.VERSION.SDK_INT >= 33) listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.NEARBY_WIFI_DEVICES
    ) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    override val listeners = mutableListOf<P2pEventListener>()

    fun setChannel(c: Channel) {
        channel = c
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Intent received: ${intent.action}")

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    forEachListenerDo { it.onWifiP2pOn() }
                } else {
                    forEachListenerDo { it.onWifiP2pOff() }
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                checkPermissionsAndThen(
                    context,
                    permissions,
                    { listeners.forEach { it.onLocationPermissionNeeded() } }
                ) {
                    @Suppress("MissingPermission")
                    manager.requestPeers(channel) { peers ->
                        forEachListenerDo { it.onNewDevices(peers.deviceList) }
                    }
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo?

                if (networkInfo?.isConnected == true) {
                    // we are connected with the other device, request connection
                    // info to find group owner IP
                    manager.requestConnectionInfo(channel) { info ->
                        forEachListenerDo { it.onConnection(info) }
                    }
                } else {
                    forEachListenerDo { it.onDisconnection() }
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
            }

            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, 10000)
                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    forEachListenerDo { it.onDiscoveryStarted() }
                } else {
                    forEachListenerDo { it.onDiscoveryStopped() }
                }
            }
        }
    }
}
