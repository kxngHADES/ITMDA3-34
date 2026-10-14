package com.itmda.swiftq.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether the phone can reach the internet right now, for the offline banner. "Online" means the default network
 * has been validated by Android (not just connected to a Wi-Fi with no data), which is what a patient experiences as offline.
 */
class ConnectivityMonitor(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(caps.isUsable()) }
            override fun onLost(network: Network) { trySend(false) }
        }
        trySend(current())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()

    fun current(): Boolean = manager.getNetworkCapabilities(manager.activeNetwork)?.isUsable() == true

    private fun NetworkCapabilities.isUsable() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
