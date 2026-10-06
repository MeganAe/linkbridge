package com.linkbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

/** Wi-Fi Direct lifecycle and peer discovery for both phone roles. */
class WifiDirectController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onPeers: (List<WifiP2pDevice>) -> Unit,
    private val onConnectionInfo: (WifiP2pInfo) -> Unit
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, Looper.getMainLooper(), null)
    private var receiverRegistered = false
    private var receiverMode = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val enabled = intent.getIntExtra(
                        WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED
                    ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    if (!enabled) onStatus("Active le Wi-Fi pour continuer")
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                        if (info.groupFormed) onConnectionInfo(info)
                    }
                }
            }
        }
    }

    fun register() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    fun startHost() {
        receiverMode = false
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Point de liaison privé créé. En attente du téléphone B…")
            }

            override fun onFailure(reason: Int) {
                onStatus("Impossible de créer la liaison Wi-Fi Direct (code $reason)")
            }
        })
    }

    fun startReceiver() {
        receiverMode = true
        onPeers(emptyList())
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Recherche du téléphone A…")
            }

            override fun onFailure(reason: Int) {
                onStatus("Recherche Wi-Fi Direct impossible (code $reason)")
            }
        })
    }

    fun connect(peer: WifiP2pDevice) {
        if (!receiverMode) return
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            // Phone A explicitly creates the group. This makes it the gateway.
            groupOwnerIntent = 0
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Connexion à ${peer.deviceName} en cours…")
            }

            override fun onFailure(reason: Int) {
                onStatus("Connexion impossible (code $reason)")
            }
        })
    }

    private fun requestPeers() {
        manager.requestPeers(channel) { list: WifiP2pDeviceList ->
            onPeers(list.deviceList.toList())
        }
    }

    fun stop() {
        receiverMode = false
        onPeers(emptyList())
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = onStatus("Connexion arrêtée")
            override fun onFailure(reason: Int) = onStatus("Connexion arrêtée")
        })
    }

    fun unregister() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        receiverRegistered = false
    }
}
