package com.dojo.david

import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections


fun isPermissionGrantedFor(context: Context, permission: String): Boolean {
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

fun isPermissionGrantedFor(context: Context, permissions: List<String>): Boolean {
    for (permission in permissions) {
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
    }

    return true
}

fun <T> checkPermissionsAndThen(
    context: Context,
    permissions: Collection<String>,
    onFailure: (failedPermissions: Collection<String>) -> Unit,
    onSuccess: () -> T,
): T? {
    val failed = mutableListOf<String>()

    for (permission in permissions) {
        if (!isPermissionGrantedFor(context, permission)) {
            failed.add(permission)
        }
    }
    if (failed.size > 0) {
        onFailure(failed)
        return null
    }
    return onSuccess()
}

fun showShortToast(context: Context, message: String) {
    Toast.makeText(
        context, message, Toast.LENGTH_SHORT
    ).show()
}


interface EventEmitter<T> {
    val listeners : MutableCollection<T>

    fun addListener(l: T) {
        listeners.add(l)
    }

    fun removeListener(l: T) {
        listeners.remove(l)
    }

    fun forEachListenerDo(f: (listener: T) -> Unit) {
        listeners.forEach(f)
    }
}

fun getAvailablePort(minPort: Int, maxPort: Int): Int {
    for (port in minPort..maxPort) {
        try {
            val server = ServerSocket(port)
            server.close()
            return port // If the server was successfully opened, this port is available.
        } catch (e: Exception) {
            // If an exception was thrown, this port is probably in use.
        }
    }
    return -1 // If no port was found in the given range, return -1.
}

fun getLocalIPAddress(): String? {
    try {
        for (networkInterface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (inetAddress in Collections.list(networkInterface.inetAddresses)) {
                if (!inetAddress.isLoopbackAddress) {
                    val hostAddress = inetAddress.hostAddress ?: continue
                    // Check if the address is IPv4
                    val isIPv4 = hostAddress.indexOf(':') < 0

                    if (isIPv4) {
                        return hostAddress
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Log.e(TAG, "Error getting IP address", ex)
    }
    return null
}
