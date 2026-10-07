package com.linkbridge.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Cycle de vie Wi‑Fi Direct pour les deux rôles du téléphone.
 *
 * Trois règles expliquent l'essentiel de ce fichier, et chacune corrige un défaut qui
 * faisait revenir l'invitation système encore et encore :
 *
 * 1. Avant de créer un groupe ou de rejoindre un pair, on nettoie tout ce qui traîne :
 *    groupe en cours, recherche de pairs, et surtout les groupes persistants. Un groupe
 *    persistant oublié par une session précédente fait redemander la connexion au système
 *    à chaque fois que Wi‑Fi Direct se réveille, sans fin.
 * 2. Une seule tentative de connexion à la fois. Un second appel à connect() pendant qu'une
 *    invitation est en cours envoie une deuxième invitation, ce qui empile les demandes.
 * 3. La recherche de pairs est arrêtée avant de se connecter : laissée active, elle fait
 *    échouer connect() avec un code d'occupation, et le pair se retrouve invité pour rien.
 */
@SuppressLint("MissingPermission")
class WifiDirectController(
    private val context: Context,
    private val onStatus: (String) -> Unit,
    private val onPeers: (List<WifiP2pDevice>) -> Unit,
    private val onConnectionInfo: (WifiP2pInfo) -> Unit,
    private val onGroupInfo: (ssid: String, passphrase: String?) -> Unit = { _, _ -> },
    private val onWifiState: (Boolean) -> Unit = {},
    /** Appelé quand un groupe se défait, ou qu'une invitation reste sans suite. */
    private val onDisconnected: () -> Unit = {},
    /** Appelé quand la négociation échoue, avec un message déjà rédigé et une clé de réessai. */
    private val onConnectFailed: (message: String, retry: Boolean) -> Unit = { _, _ -> }
) {
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = manager.initialize(context, Looper.getMainLooper(), null)
    private val handler = Handler(Looper.getMainLooper())

    private var receiverRegistered = false
    private var receiverMode = false

    /** Un groupe a réellement été formé, et la déconnexion suivante mérite d'être signalée. */
    private var formed = false

    /** Rôle de ce téléphone : true quand il rejoint un pair, false quand il crée le groupe. */
    fun isReceiver(): Boolean = receiverMode

    /** Une connexion est demandée et pas encore aboutie. */
    @Volatile
    var connectionPending: Boolean = false
        private set

    /** Le téléphone a créé un groupe et écoute. */
    @Volatile
    var hosting: Boolean = false
        private set

    private val cleanupTimeout = Runnable { /* filet de sécurité : ne rien laisser en attente à vie */ }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                handleIntent(intent)
            } catch (_: SecurityException) {
                // Autorisation retirée pendant la session : on prévient au lieu de planter,
                // un plantage couperait la connexion en cours.
                onStatus("Autorisation Wi‑Fi retirée. Relance l'application")
            }
        }
    }

    /** Traite un événement Wi‑Fi Direct envoyé par le système. */
    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val enabled = intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED
                ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                onWifiState(enabled)
                if (!enabled) {
                    // Sans Wi‑Fi, Wi‑Fi Direct est arrêté par le système : on remet tout à
                    // plat, sinon l'écran reste bloqué sur une connexion qui n'existe plus.
                    connectionPending = false
                    hosting = false
                    receiverMode = false
                    onPeers(emptyList())
                    onStatus("Wi‑Fi désactivé : le lien est coupé")
                    if (formed) {
                        formed = false
                        onDisconnected()
                    }
                }
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeers()

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Un seul appel suffit : requestConnectionInfo indique à la fois si un groupe
                // existe et quel en est le propriétaire.
                manager.requestConnectionInfo(channel) { info: WifiP2pInfo ->
                    if (info.groupFormed) {
                        connectionPending = false
                        formed = true
                        onConnectionInfo(info)
                        // Le nom du réseau et sa clé permettent à un PC de rejoindre
                        // DIRECT-xx-… depuis ses réglages Wi‑Fi.
                        manager.requestGroupInfo(channel) { group: WifiP2pGroup? ->
                            if (group != null) {
                                onGroupInfo(group.networkName, groupPassphrase(group))
                            }
                        }
                    } else {
                        // Groupe dissous, invitation refusée ou négociation abandonnée : on
                        // relâche tout pour ne pas relancer une invitation en boucle.
                        connectionPending = false
                        hosting = false
                        onPeers(emptyList())
                        if (formed) onStatus("Connexion perdue")
                        if (formed) {
                            formed = false
                            onDisconnected()
                        }
                        manager.removeGroup(channel, quiet())
                    }
                }
            }
        }
    }

    /**
     * Vrai si l'application a le droit d'utiliser Wi‑Fi Direct.
     *
     * Chaque point d'entrée l'appelle avant de parler au système : sans ce contrôle, un refus
     * de l'utilisateur faisait lever une exception de sécurité et tombait le processus.
     */
    fun permitted(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        val granted = ContextCompat.checkSelfPermission(context, needed) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) onStatus("Autorise les appareils à proximité pour utiliser Wi‑Fi Direct")
        return granted
    }

    /** État du Wi‑Fi au moment de l'appel, sans attendre un changement système. */
    @Suppress("DEPRECATION")
    fun wifiEnabled(): Boolean = try {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.isWifiEnabled
    } catch (_: Throwable) {
        true
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

    /**
     * Le téléphone A crée le groupe : il devient le point d'accès du lien.
     * L'ordre est important — ancien groupe, invitation en attente et recherche de pairs
     * sont nettoyés d'abord, sinon createGroup() échoue avec un code d'occupation et le
     * pair invité continue de demander la connexion.
     */
    fun startHost() {
        if (!permitted()) return
        receiverMode = false
        hosting = false
        connectionPending = false
        cleanupPending()
        onStatus("Préparation du lien privé…")
        removeGroupThen {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    hosting = true
                    onStatus("Lien privé prêt. En attente du téléphone B…")
                }

                override fun onFailure(reason: Int) {
                    hosting = false
                    val message = when (reason) {
                        WifiP2pManager.ERROR ->
                            "Le téléphone n'a pas pu créer le lien. Vérifie que le Wi‑Fi est bien activé"
                        WifiP2pManager.BUSY ->
                            "Wi‑Fi Direct est occupé. Attends quelques secondes, puis réessaie"
                        WifiP2pManager.P2P_UNSUPPORTED ->
                            "Ce téléphone ne prend pas en charge Wi‑Fi Direct"
                        else ->
                            "Impossible de créer le lien Wi‑Fi Direct. Code $reason"
                    }
                    onStatus(message)
                    onConnectFailed(message, true)
                }
            })
        }
    }

    /** Le téléphone B cherche le téléphone A. */
    fun startReceiver() {
        if (!permitted()) return
        receiverMode = true
        connectionPending = false
        hosting = false
        onPeers(emptyList())
        cleanupPending()
        removeGroupThen {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onStatus("Recherche du téléphone A…")
                }

                override fun onFailure(reason: Int) {
                    val message = when (reason) {
                        WifiP2pManager.BUSY ->
                            "Wi‑Fi Direct est occupé. Attends quelques secondes, puis réessaie"
                        WifiP2pManager.P2P_UNSUPPORTED ->
                            "Ce téléphone ne prend pas en charge Wi‑Fi Direct"
                        else ->
                            "Recherche Wi‑Fi Direct impossible. Code $reason"
                    }
                    onStatus(message)
                    onConnectFailed(message, true)
                }
            })
        }
    }

    /**
     * Invite le pair choisi. Renvoie false quand une invitation est déjà en cours : c'est ce
     * garde-fou qui empêche une double pression d'envoyer deux invitations, et donc de faire
     * apparaître la demande système deux fois.
     */
    fun connect(peer: WifiP2pDevice): Boolean {
        if (!permitted()) return false
        if (!receiverMode) return false
        if (connectionPending) {
            onStatus("Connexion déjà en cours avec le téléphone A…")
            return false
        }
        connectionPending = true
        stopDiscovery()
        val config = WifiP2pConfig().apply {
            deviceAddress = peer.deviceAddress
            // Le téléphone A crée le groupe : il reste la passerelle, celui qui a Internet.
            groupOwnerIntent = 0
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onStatus("Demande envoyée à ${peer.deviceName}. Accepte la connexion sur le téléphone A.")
            }

            override fun onFailure(reason: Int) {
                connectionPending = false
                val message = when (reason) {
                    WifiP2pManager.BUSY ->
                        "Wi‑Fi Direct est déjà occupé. Attends quelques secondes, puis réessaie"
                    WifiP2pManager.ERROR ->
                        "Connexion impossible. Vérifie que le téléphone A affiche bien son code"
                    WifiP2pManager.P2P_UNSUPPORTED ->
                        "Ce téléphone ne prend pas en charge Wi‑Fi Direct"
                    else ->
                        "Connexion impossible. Code $reason"
                }
                onStatus(message)
                onConnectFailed(message, true)
            }
        })
        return true
    }

    /** Renonce à l'invitation en cours sans casser le groupe du téléphone A. */
    fun cancelConnect() {
        connectionPending = false
        manager.cancelConnect(channel, quiet())
        stopDiscovery()
    }

    fun requestPeers() {
        if (!permitted()) return
        manager.requestPeers(channel) { list: WifiP2pDeviceList ->
            onPeers(list.deviceList.toList())
        }
    }

    fun stop() {
        receiverMode = false
        connectionPending = false
        hosting = false
        formed = false
        onPeers(emptyList())
        try {
            stopDiscovery()
            cleanupPending()
            manager.removeGroup(channel, quiet())
        } catch (_: SecurityException) {
            // Plus d'autorisation : il n'y a plus rien à arrêter côté système.
        }
        onStatus("Connexion arrêtée")
    }

    fun unregister() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        receiverRegistered = false
    }

    /**
     * Annule tout ce qui peut rester en attente côté système : recherche de pairs en cours,
     * invitation envoyée mais pas encore aboutie, et groupe courant.
     *
     * Les groupes persistants enregistrés par Android ne sont pas supprimables depuis une
     * application : l'API correspondante n'est pas exposée dans le SDK. C'est pour cela que
     * chaque session repart d'un groupe neuf, que la recherche est arrêtée avant toute
     * connexion, et que le guide indique comment oublier un ancien réseau DIRECT si le
     * téléphone en a gardé un.
     */
    fun cleanupPending() {
        connectionPending = false
        try {
            manager.cancelConnect(channel, quiet())
            stopDiscovery()
        } catch (_: SecurityException) {
            // Sans autorisation, aucun nettoyage n'est possible : on ne fait pas planter
            // l'application pour autant.
        }
    }

    /** Arrête la recherche de pairs : obligatoire avant connect(), sinon elle échoue occupée. */
    private fun stopDiscovery() {
        try {
            manager.stopPeerDiscovery(channel, quiet())
        } catch (_: Throwable) {
        }
    }

    /**
     * Supprime le groupe courant s'il existe, puis lance la suite. Si aucun groupe n'existe,
     * removeGroup() appelle onFailure tout de suite : dans les deux cas la suite démarre, et
     * jamais deux fois grâce au drapeau.
     */
    private fun removeGroupThen(action: () -> Unit) {
        var done = false
        val runOnce = Runnable {
            if (!done) {
                done = true
                handler.removeCallbacks(cleanupTimeout)
                action()
            }
        }
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = runOnce.run()
            override fun onFailure(reason: Int) = runOnce.run()
        })
        // Filet de sécurité : si le système ne répond pas, on enchaîne quand même.
        handler.postDelayed(runOnce, 900)
    }

    /** The passphrase exists only on API 27+ and may be hidden by the OEM. */
    private fun groupPassphrase(group: WifiP2pGroup): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                group.passphrase
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun quiet(): WifiP2pManager.ActionListener =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        }
}
