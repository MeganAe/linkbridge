package com.linkbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build

/** Origine d'une session : lien Wi‑Fi Direct entre deux appareils, ou relais du réseau local. */
internal const val SOURCE_DIRECT = "direct"
internal const val SOURCE_MANUAL = "manual"

/**
 * Surveille le lien Wi‑Fi Direct depuis un service, donc même application fermée.
 *
 * Sans cela, un partage ou un tunnel continuait de tourner après la disparition du lien, et
 * la notification restait au vert pour une connexion qui n'existait plus. Le surveillant ne
 * se déclenche qu'après avoir vu un lien réellement formé : une session montée à la main
 * sur le réseau local n'est jamais interrompue par un événement Wi‑Fi Direct sans rapport.
 */
internal class LinkWatch(
    private val context: Context,
    private val onLinkLost: () -> Unit
) {
    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var sawGroup = false
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val currentManager = manager ?: return
            val currentChannel = channel ?: return
            try {
                currentManager.requestConnectionInfo(currentChannel) { info: WifiP2pInfo ->
                    if (info.groupFormed) {
                        sawGroup = true
                    } else if (sawGroup) {
                        sawGroup = false
                        stop()
                        onLinkLost()
                    }
                }
            } catch (_: SecurityException) {
                // Autorisation retirée : le lien ne peut plus être suivi, le service s'arrête
                // de lui-même plutôt que de rester au vert pour une connexion inconnue.
                stop()
                onLinkLost()
            }
        }
    }

    fun start() {
        if (registered) return
        try {
            val wifiManager = context.getSystemService(Context.WIFI_P2P_SERVICE)
                as? WifiP2pManager ?: return
            val wifiChannel = wifiManager.initialize(context, context.mainLooper, null) ?: return
            manager = wifiManager
            channel = wifiChannel
            val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            registered = true
            // Le service peut démarrer alors que le lien est déjà formé.
            try {
                wifiManager.requestConnectionInfo(wifiChannel) { info: WifiP2pInfo ->
                    if (info.groupFormed) sawGroup = true
                }
            } catch (_: SecurityException) {
                sawGroup = false
            }
        } catch (_: Throwable) {
            registered = false
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        manager = null
        channel = null
    }
}
