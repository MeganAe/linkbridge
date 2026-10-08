package com.linkbridge.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/** Délai au-delà duquel une demande de connexion sans réponse est annoncée à l'utilisateur. */
private const val CONNECT_TIMEOUT_MS = 30_000L

/** Code de demande pour l'autorisation d'afficher les notifications. */
private const val REQUEST_NOTIFICATIONS = 41

class MainActivity : ComponentActivity() {
    private var role by mutableStateOf<LinkRole?>(null)
    private var status by mutableStateOf("Prêt à créer un lien")
    private var peers by mutableStateOf<List<WifiP2pDevice>>(emptyList())
    private var pairingInput by mutableStateOf("")
    private var vpnPrepared = false
    private var pendingPermissionAction: (() -> Unit)? = null

    // A fresh pairing code is generated for every sharing session and is
    // never persisted on the device.
    private var pairingCode by mutableStateOf(PairingCode.generate())
    private lateinit var wifiDirect: WifiDirectController

    // Connexion manuelle (relais sur le réseau local, ex. un PC) : IP + code.
    private var manualIp by mutableStateOf("")
    private var manualCode by mutableStateOf("")

    // Groupe Wi-Fi Direct (SSID / mot de passe) affiché pour qu'un PC puisse
    // rejoindre le réseau DIRECT-xx-… depuis ses réglages Wi-Fi.
    private var groupSsid by mutableStateOf<String?>(null)
    private var groupPassphrase by mutableStateOf<String?>(null)

    // Host en attente du consentement VPN avant de démarrer le tunnel.
    private var pendingManual: Pair<String, String>? = null

    // Wi-Fi activé ou non : c'est le prérequis que les nouveaux utilisateurs ignorent, donc
    // l'écran d'accueil le vérifie et propose d'ouvrir les réglages.
    private var wifiOn by mutableStateOf(true)

    // Session en cours : permet de retrouver l'écran du partage quand on rouvre
    // l'application alors que le service tourne toujours en arrière-plan.
    private var sessionActive by mutableStateOf(false)

    // Session née d'un lien Wi‑Fi Direct : les services suivent alors l'état du lien pour
    // qu'une connexion terminée ne laisse jamais une notification qui annonce le contraire.
    private var sessionFromDirect = false

    // Une invitation est partie et n'a pas encore abouti : garde-fou contre les doublons.
    private var connecting by mutableStateOf(false)

    // Un lien a réellement été formé.
    private var linkUp = false

    // Relais dont la connexion a été obtenue avant l'autorisation VPN.
    private var pendingHost: String? = null

    // Surveillant : au-delà de trente secondes sans réponse, l'utilisateur est prévenu
    // au lieu de rester devant un écran qui ne dit rien.
    private var connectWatchdog: Runnable? = null

    // Applications qui passent par la connexion reçue (téléphone B) : toutes,
    // ou seulement celles que l'utilisateur a cochées.
    private var restrictApps by mutableStateOf(false)
    private var selectedApps by mutableStateOf<Set<String>>(emptySet())
    private var installedApps by mutableStateOf<List<InstalledApp>>(emptyList())

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnPrepared = true
            val manual = pendingManual
            pendingManual = null
            if (manual != null) {
                status = "VPN autorisé. Connexion au relais ${manual.first}…"
                startReceiverVpn(manual.first, manual.second, SOURCE_MANUAL)
            } else if (pendingHost != null) {
                // Le lien Wi‑Fi Direct était déjà formé : le tunnel démarre dès que le code
                // est saisi, sans que l'utilisateur ait à recommencer la moindre étape.
                if (pairingInput.length == 6) {
                    tryStartPendingTunnel()
                } else {
                    status = "Lien établi. Entre le code à 6 chiffres affiché sur l'autre appareil."
                }
            } else {
                status = "VPN autorisé. Choisis le téléphone A."
                wifiDirect.startReceiver()
            }
        } else {
            status = "Autorisation VPN refusée"
            pendingHost = null
        }
    }

    private val nearbyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted && action != null) action() else status = "Permission Wi-Fi refusée"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // L'écran de démarrage disparaît dès la première image prête : aucun délai artificiel.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        wifiDirect = WifiDirectController(
            context = this,
            onStatus = { status = it },
            onPeers = { peers = it },
            onConnectionInfo = ::onConnectionInfo,
            onGroupInfo = { ssid, passphrase ->
                groupSsid = ssid
                groupPassphrase = passphrase
            },
            onWifiState = { enabled -> wifiOn = enabled },
            onDisconnected = {
                connecting = false
                cancelConnectWatchdog()
                if (linkUp) {
                    linkUp = false
                    sessionActive = false
                    SessionStore.clear(this)
                    groupSsid = null
                    groupPassphrase = null
                    if (sessionFromDirect) {
                        sessionFromDirect = false
                        stopBackgroundSession()
                    }
                    status = "La connexion s'est terminée"
                }
            },
            onConnectFailed = { message, retry ->
                connecting = false
                cancelConnectWatchdog()
                status = if (retry) {
                    "$message. Appuie de nouveau sur la ligne du téléphone pour réessayer."
                } else {
                    message
                }
            }
        )
        wifiDirect.register()
        wifiOn = wifiDirect.wifiEnabled()

        // Le guide s'ouvre en pleine page au premier lancement, puis reste accessible par le
        // bouton Guide de la barre du haut.
        if (!GuideStore.hasSeen(this)) openGuide()

        // Session déjà en cours : l'application a été fermée pendant que le partage tournait.
        val saved = SessionStore.load(this)
        if (saved.active && (GatewayService.running || VpnTunnelService.running)) {
            sessionActive = true
            linkUp = true
            sessionFromDirect = saved.role == LinkRole.SHARE
            role = saved.role
            if (saved.code.isNotEmpty()) pairingCode = saved.code
            status = when (saved.role) {
                LinkRole.SHARE -> "Partage en cours. Il continue même si tu fermes l'application."
                LinkRole.RECEIVE -> "Connexion reçue active. Elle continue en arrière-plan."
                null -> "Session en cours"
            }
        } else if (saved.active) {
            // Les services se sont arrêtés entre-temps : on ne laisse pas une session fantôme.
            SessionStore.clear(this)
        }

        val selection = AppSelectionStore.load(this)
        restrictApps = selection.restrict
        selectedApps = selection.packages
        Thread {
            // La liste se construit hors du fil principal, puis revient sur l'écran : écrire
            // l'état Compose depuis un autre fil faisait clignoter la liste, parfois sans rien.
            val apps = AppSelectionStore.listLaunchableApps(this)
            runOnUiThread { installedApps = apps }
        }.apply {
            name = "linkbridge-apps"
            start()
        }

        setContent {
            LinkBridgeApp(
                role = role,
                status = status,
                wifiOn = wifiOn,
                sessionActive = sessionActive,
                connecting = connecting,
                onOpenGuide = ::openGuide,
                onOpenWifiSettings = ::openWifiSettings,
                pairingCode = pairingCode,
                pairingInput = pairingInput,
                peers = peers,
                manualIp = manualIp,
                manualCode = manualCode,
                groupSsid = groupSsid,
                groupPassphrase = groupPassphrase,
                installedApps = installedApps,
                selectedApps = selectedApps,
                restrictApps = restrictApps,
                onRestrictChange = {
                    restrictApps = it
                    saveAppSelection()
                },
                onToggleApp = { pkg ->
                    selectedApps = if (pkg in selectedApps) selectedApps - pkg else selectedApps + pkg
                    saveAppSelection()
                },
                onManualIpChange = { manualIp = it.filter { character -> !character.isWhitespace() } },
                onManualCodeChange = { manualCode = it.filter { character -> character.isDigit() }.take(6) },
                onManualConnect = { host, code -> beginManualReceiving(host, code) },
                onPairingInputChange = { raw ->
                    val clean = raw.filter { character -> character.isDigit() }.take(6)
                    pairingInput = clean
                    // Le lien est parfois déjà formé quand le code arrive : on enchaîne.
                    if (clean.length == 6) tryStartPendingTunnel()
                },
                onShare = { runWithNearbyPermissions(::beginSharing) },
                onReceive = { runWithNearbyPermissions(::beginReceiving) },
                onPeerSelected = { peer ->
                    when {
                        !vpnPrepared -> status =
                            "Autorise d'abord le VPN, puis appuie de nouveau sur Recevoir"
                        pairingInput.length != 6 -> status =
                            "Entre d'abord le code affiché sur l'autre téléphone"
                        !appSelectionValid() -> Unit
                        wifiDirect.connect(peer) -> {
                            connecting = true
                            startConnectWatchdog()
                        }
                    }
                },
                onStop = ::stopEverything
            )
        }
    }

    private fun onConnectionInfo(info: WifiP2pInfo) {
        val host = info.groupOwnerAddress?.hostAddress ?: GatewayService.DEFAULT_GROUP_OWNER_ADDRESS
        connecting = false
        cancelConnectWatchdog()
        // Une invitation acceptée depuis la notification d'Android arrive ici alors que
        // l'utilisateur n'a rien choisi dans l'application. C'est le sens réel du lien qui
        // décide : c'est exactement ce qui manquait, accepter l'invitation ne faisait rien.
        if (role == null) role = if (info.isGroupOwner) LinkRole.SHARE else LinkRole.RECEIVE
        val current = role
        linkUp = true
        sessionActive = true
        sessionFromDirect = true
        when {
            current == LinkRole.SHARE && info.isGroupOwner -> {
                SessionStore.save(this, LinkRole.SHARE, pairingCode)
                status = "Téléphone B connecté. Le partage continue même si tu fermes l'application."
                startGateway(host)
            }

            current == LinkRole.RECEIVE && !info.isGroupOwner -> {
                SessionStore.save(this, LinkRole.RECEIVE, pairingInput)
                when {
                    pairingInput.length != 6 -> {
                        // Le lien est formé, il ne manque que le code : le tunnel partira tout
                        // seul dès la sixième chiffre, sans nouvel appui.
                        pendingHost = host
                        status =
                            "Lien établi. Entre le code à 6 chiffres affiché sur l'autre appareil."
                    }

                    !vpnPrepared -> {
                        // L'autorisation VPN manque encore : on garde le relais de côté pour
                        // démarrer le tunnel dès qu'elle arrive, au lieu de ne rien faire.
                        pendingHost = host
                        status = "Lien établi. Autorise le VPN pour terminer la connexion."
                    }

                    else -> {
                        status = "Téléphone A connecté. Démarrage du tunnel…"
                        startReceiverVpn(host, pairingInput, SOURCE_DIRECT)
                    }
                }
            }

            current == LinkRole.SHARE -> {
                status =
                    "Le lien s'est formé dans le mauvais sens. Appuie sur Arrêter, puis relance le partage."
            }

            else -> {
                status =
                    "Le lien s'est formé dans le mauvais sens. Appuie sur Annuler, puis relance la réception."
            }
        }
    }

    /**
     * Le lien Wi‑Fi Direct peut être formé avant que tout soit prêt : un code encore vide, ou
     * l'autorisation VPN. Dès que la dernière pièce arrive, le tunnel démarre tout seul.
     */
    private fun tryStartPendingTunnel() {
        val host = pendingHost ?: return
        if (!vpnPrepared || pairingInput.length != 6) return
        pendingHost = null
        status = "Téléphone A connecté. Démarrage du tunnel…"
        startReceiverVpn(host, pairingInput, SOURCE_DIRECT)
    }

    /** Ouvre le guide, désormais une page complète et non une fenêtre de dialogue. */
    private fun openGuide() {
        GuideStore.markSeen(this)
        startActivity(Intent(this, GuideActivity::class.java))
    }

    /** Sans cette autorisation, la notification du partage reste invisible sur Android 13 et plus. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun startConnectWatchdog() {
        cancelConnectWatchdog()
        val task = Runnable {
            if (connecting) {
                connecting = false
                status =
                    "La demande n'a pas abouti. Rapproche les deux téléphones, puis appuie de nouveau sur la ligne à relier."
            }
        }
        connectWatchdog = task
        Handler(Looper.getMainLooper()).postDelayed(task, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectWatchdog() {
        connectWatchdog?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        connectWatchdog = null
    }

    private fun beginSharing() {
        if (!wifiOn) {
            status = "Active le Wi‑Fi, puis appuie de nouveau sur Partager"
            if (!GuideStore.hasSeen(this)) openGuide()
            return
        }
        requestNotificationPermissionIfNeeded()
        role = LinkRole.SHARE
        connecting = false
        linkUp = false
        sessionFromDirect = true
        pendingHost = null
        pairingCode = PairingCode.generate()
        sessionActive = true
        SessionStore.save(this, LinkRole.SHARE, pairingCode)
        peers = emptyList()
        status = "Création du lien privé…"
        startGateway(GatewayService.DEFAULT_GROUP_OWNER_ADDRESS)
        wifiDirect.startHost()
    }

    private fun beginReceiving() {
        requestNotificationPermissionIfNeeded()
        role = LinkRole.RECEIVE
        linkUp = false
        connecting = false
        sessionFromDirect = true
        val intent = VpnService.prepare(this)
        if (intent != null) {
            status = "Autorise LinkBridge à créer la connexion VPN"
            vpnLauncher.launch(intent)
        } else {
            vpnPrepared = true
            status = "VPN autorisé. Choisis le téléphone A."
            wifiDirect.startReceiver()
        }
    }

    private fun startGateway(host: String) {
        val intent = Intent(this, GatewayService::class.java)
            .setAction(GatewayService.ACTION_START)
            .putExtra(GatewayService.EXTRA_CODE, pairingCode)
            .putExtra(GatewayService.EXTRA_ADVERTISED_HOST, host)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this, intent)
        else startService(intent)
    }

    private fun saveAppSelection() {
        AppSelectionStore.save(this, restrictApps, selectedApps)
    }

    /**
     * Vérifie que le mode « seulement certaines applications » a bien au moins une
     * application utilisable. Pendant le chargement de la liste, on laisse passer :
     * le service ignore de lui-même les paquets qui n'existent plus.
     */
    private fun appSelectionValid(): Boolean {
        if (!restrictApps) return true
        if (installedApps.isEmpty()) return true
        val installed = installedApps.map { it.packageName }.toSet()
        if (selectedApps.any { it in installed }) return true
        status = "Choisis au moins une application installée, ou repasse sur « Toutes les applications »"
        return false
    }

    private fun startReceiverVpn(host: String, code: String, source: String) {
        if (code.length != 6) {
            status = "Entre le code affiché sur le téléphone A"
            return
        }
        if (!appSelectionValid()) return
        // Les applications désinstallées depuis le dernier choix sont retirées ici : elles
        // faisaient échouer la préparation du tunnel, et la connexion ne démarrait jamais.
        val installed = installedApps.map { it.packageName }.toSet()
        val allowed = when {
            !restrictApps -> emptySet()
            installed.isEmpty() -> selectedApps
            else -> selectedApps.filter { it in installed }.toSet()
        }
        if (restrictApps && allowed.isEmpty()) {
            status = "Aucune des applications choisies n'est installée. Repasse sur « Toutes les applications »"
            return
        }
        val intent = Intent(this, VpnTunnelService::class.java)
            .setAction(VpnTunnelService.ACTION_START)
            .putExtra(VpnTunnelService.EXTRA_GATEWAY_HOST, host)
            .putExtra(VpnTunnelService.EXTRA_CODE, code)
            .putExtra(VpnTunnelService.EXTRA_RESTRICT_APPS, restrictApps)
            .putStringArrayListExtra(VpnTunnelService.EXTRA_ALLOWED_APPS, ArrayList(allowed))
            .putExtra(VpnTunnelService.EXTRA_SOURCE, source)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this, intent)
        else startService(intent)
    }

    /**
     * Connexion manuelle à un relais sur le réseau local (par exemple le PC
     * avec LinkBridge), sans Wi-Fi Direct : IP + code suffisent.
     */
    private fun beginManualReceiving(host: String, code: String) {
        role = LinkRole.RECEIVE
        sessionFromDirect = false
        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || code.length != 6) {
            status = "Entre l'adresse du relais et le code à 6 chiffres"
            return
        }
        if (!appSelectionValid()) return
        val intent = VpnService.prepare(this)
        if (intent != null) {
            status = "Autorise LinkBridge à créer la connexion VPN"
            pendingManual = cleanHost to code
            vpnLauncher.launch(intent)
        } else {
            vpnPrepared = true
            status = "Connexion au relais $cleanHost…"
            startReceiverVpn(cleanHost, code, SOURCE_MANUAL)
        }
    }

    /**
     * Le lien a disparu : les services s'arrêtent au lieu de laisser une notification qui
     * annonce une connexion qui n'existe plus.
     */
    private fun stopBackgroundSession() {
        stopService(Intent(this, GatewayService::class.java))
        stopService(
            Intent(this, VpnTunnelService::class.java).setAction(VpnTunnelService.ACTION_STOP)
        )
    }

    private fun stopEverything() {
        cancelConnectWatchdog()
        wifiDirect.cancelConnect()
        wifiDirect.stop()
        stopBackgroundSession()
        sessionFromDirect = false
        pendingManual = null
        pendingHost = null
        connecting = false
        linkUp = false
        sessionActive = false
        SessionStore.clear(this)
        groupSsid = null
        groupPassphrase = null
        role = null
        peers = emptyList()
        status = "Prêt à créer un lien"
    }

    private fun runWithNearbyPermissions(action: () -> Unit) {
        // Android 12 et avant réclame la position précise, et refuse net une demande qui
        // n'inclut pas aussi la position approximative : les deux sont demandées ensemble.
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) action()
        else {
            pendingPermissionAction = action
            nearbyPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        // L'utilisateur a pu activer le Wi-Fi depuis les réglages Android.
        wifiOn = wifiDirect.wifiEnabled()
    }

    /** Ouvre la page Wi-Fi des réglages Android, pour que le prérequis soit à un appui. */
    private fun openWifiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {
            status = "Ouvre les réglages Android, puis active le Wi-Fi"
        }
    }

    override fun onDestroy() {
        wifiDirect.unregister()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkBridgeApp(
    role: LinkRole?,
    status: String,
    wifiOn: Boolean,
    sessionActive: Boolean,
    connecting: Boolean,
    onOpenGuide: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    pairingCode: String,
    pairingInput: String,
    peers: List<WifiP2pDevice>,
    manualIp: String,
    manualCode: String,
    groupSsid: String?,
    groupPassphrase: String?,
    installedApps: List<InstalledApp>,
    selectedApps: Set<String>,
    restrictApps: Boolean,
    onRestrictChange: (Boolean) -> Unit,
    onToggleApp: (String) -> Unit,
    onManualIpChange: (String) -> Unit,
    onManualCodeChange: (String) -> Unit,
    onManualConnect: (host: String, code: String) -> Unit,
    onPairingInputChange: (String) -> Unit,
    onShare: () -> Unit,
    onReceive: () -> Unit,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    onStop: () -> Unit
) {
    var showAbout by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    LinkBridgeTheme {
        if (showAbout) AboutDialog(onDismiss = { showAbout = false })
        if (showAppPicker) {
            AppPickerDialog(
                apps = installedApps,
                selected = selectedApps,
                onToggle = onToggleApp,
                onDismiss = { showAppPicker = false }
            )
        }
        Scaffold(
            containerColor = LinkBridgeBrand.Background,
            topBar = {
                // Barre normale, compacte, qui ne se déplie pas : juste la marque à gauche et
                // les deux accès à droite. Aucun nom qui flotte au milieu de l'écran.
                TopAppBar(
                    title = { LinkBridgeMark(Modifier.size(32.dp)) },
                    actions = {
                        TextButton(onClick = onOpenGuide) { Text("Guide") }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "À propos")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LinkBridgeBrand.Background,
                        titleContentColor = LinkBridgeBrand.Ink,
                        navigationIconContentColor = LinkBridgeBrand.Ink,
                        actionIconContentColor = LinkBridgeBrand.Ink
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .animateContentSize(tween(220))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(role = role, status = status)

                AnimatedVisibility(
                    visible = sessionActive,
                    enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
                ) {
                    SessionNotice(onStop = onStop)
                }

                if (role == null) {
                    PreparationCard(
                        wifiOn = wifiOn,
                        onOpenWifiSettings = onOpenWifiSettings,
                        onOpenGuide = onOpenGuide
                    )
                    Text(
                        "Que veux-tu faire ?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    RoleCard(
                        icon = Icons.Outlined.Wifi,
                        title = "Partager Internet",
                        description = "Ce téléphone possède Internet et devient le pont.",
                        container = LinkBridgeBrand.Mint,
                        buttonText = "Partager",
                        onClick = onShare
                    )
                    RoleCard(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Recevoir Internet",
                        description = "Ce téléphone se connecte au pont sans hotspot classique.",
                        container = LinkBridgeBrand.Lavender,
                        buttonText = "Recevoir",
                        onClick = onReceive
                    )
                }

                AnimatedVisibility(
                    visible = role == LinkRole.SHARE,
                    enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
                ) {
                    SharePanel(
                        pairingCode = pairingCode,
                        groupSsid = groupSsid,
                        groupPassphrase = groupPassphrase,
                        onStop = onStop
                    )
                }

                AnimatedVisibility(
                    visible = role == LinkRole.RECEIVE,
                    enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                    exit = fadeOut(tween(140)) + shrinkVertically(tween(200))
                ) {
                    ReceivePanel(
                        connecting = connecting,
                        pairingInput = pairingInput,
                        onPairingInputChange = onPairingInputChange,
                        peers = peers,
                        onPeerSelected = onPeerSelected,
                        manualIp = manualIp,
                        manualCode = manualCode,
                        onManualIpChange = onManualIpChange,
                        onManualCodeChange = onManualCodeChange,
                        onManualConnect = onManualConnect,
                        restrictApps = restrictApps,
                        selectedCount = selectedApps.size,
                        onRestrictChange = onRestrictChange,
                        onChooseApps = { showAppPicker = true },
                        onStop = onStop
                    )
                }

                TrustPanel()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Rappel discret pendant une session : le travail continue hors de l'écran, et il ne
 * s'arrête que sur une demande explicite.
 */
@Composable
private fun SessionNotice(onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LinkBridgeBrand.Mint)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Security, contentDescription = null, tint = LinkBridgeBrand.Ink)
        Spacer(Modifier.width(12.dp))
        Text(
            "Le partage continue même si tu fermes l'application. La notification permet de le suivre et de l'arrêter.",
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeBrand.Ink,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onStop) { Text("Arrêter") }
    }
}

@Composable
private fun HeroCard(role: LinkRole?, status: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Ink),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkBridgeMark(Modifier.size(58.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Un pont, pas un hotspot", color = LinkBridgeBrand.Mint, fontSize = 14.sp)
                    Text(
                        when (role) {
                            LinkRole.SHARE -> "Tu partages"
                            LinkRole.RECEIVE -> "Tu reçois"
                            null -> "Internet qui circule autrement"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (role == null) LinkBridgeBrand.Coral else LinkBridgeBrand.Mint)
                )
                Spacer(Modifier.width(8.dp))
                // Le statut change souvent : il glisse au lieu de sauter d'un coup.
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                    },
                    label = "statut"
                ) { current ->
                    Text(current, color = Color.White.copy(alpha = 0.88f))
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    container: Color,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center
                ) { Icon(icon, contentDescription = null, tint = LinkBridgeBrand.Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeBrand.Ink)
            ) { Text(buttonText) }
        }
    }
}

@Composable
private fun SharePanel(
    pairingCode: String,
    groupSsid: String?,
    groupPassphrase: String?,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Code de liaison", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Butter)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Entre ce code sur le téléphone B", style = MaterialTheme.typography.bodyMedium)
                Text(
                    pairingCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = LinkBridgeBrand.Ink,
                    letterSpacing = 5.sp
                )
                AssistChip(onClick = {}, label = { Text("Wi‑Fi Direct privé") }, leadingIcon = { Icon(Icons.Outlined.Link, null) })
            }
        }
        if (groupSsid != null) {
            Text("Accès PC sur ce réseau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Mint)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Un ordinateur peut rejoindre ce réseau Wi‑Fi, puis utiliser le relais avec " +
                            "LinkBridge PC. Adresse " + GatewayService.DEFAULT_GROUP_OWNER_ADDRESS +
                            ", port " + Socks5Gateway.DEFAULT_PORT + ", et le code ci-dessus.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Réseau : $groupSsid", fontWeight = FontWeight.Bold)
                    if (groupPassphrase != null) {
                        Text("Mot de passe : $groupPassphrase", fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "Mot de passe non disponible sur cet appareil.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.StopCircle, null)
            Spacer(Modifier.width(8.dp))
            Text("Arrêter le partage")
        }
    }
}

@Composable
private fun ReceivePanel(
    connecting: Boolean,
    pairingInput: String,
    onPairingInputChange: (String) -> Unit,
    peers: List<WifiP2pDevice>,
    onPeerSelected: (WifiP2pDevice) -> Unit,
    manualIp: String,
    manualCode: String,
    onManualIpChange: (String) -> Unit,
    onManualCodeChange: (String) -> Unit,
    onManualConnect: (host: String, code: String) -> Unit,
    restrictApps: Boolean,
    selectedCount: Int,
    onRestrictChange: (Boolean) -> Unit,
    onChooseApps: () -> Unit,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connexion sécurisée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Seulement certaines applications", fontWeight = FontWeight.Bold)
                        Text(
                            if (restrictApps) "Seules les applications choisies utilisent la connexion reçue."
                            else "Toutes les applications utilisent la connexion reçue.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = restrictApps, onCheckedChange = onRestrictChange)
                }
                if (restrictApps) {
                    OutlinedButton(onClick = onChooseApps, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                selectedCount == 0 -> "Choisir les applications"
                                selectedCount == 1 -> "Choisir les applications, 1 sélectionnée"
                                else -> "Choisir les applications, $selectedCount sélectionnées"
                            }
                        )
                    }
                    Text(
                        "Les autres applications gardent la connexion habituelle de ce téléphone. " +
                            "Le choix s'applique à la prochaine connexion.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        OutlinedTextField(
            value = pairingInput,
            onValueChange = onPairingInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code du téléphone A") },
            supportingText = { Text("Le code ne quitte pas les deux téléphones.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (connecting) {
            Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Butter)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LinkBridgeBrand.Ink
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Demande envoyée. Accepte la connexion qui s'affiche sur l'autre téléphone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LinkBridgeBrand.Ink
                    )
                }
            }
        }
        if (peers.isEmpty() && !connecting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Recherche des appareils LinkBridge à proximité…")
        } else if (peers.isNotEmpty()) {
            Text("Téléphones trouvés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            peers.forEach { peer ->
                OutlinedCard(onClick = { onPeerSelected(peer) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, tint = LinkBridgeBrand.Purple)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.deviceName.ifBlank { "Téléphone sans nom" }, fontWeight = FontWeight.Bold)
                            Text("Appuie pour demander la connexion", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onPeerSelected(peer) }) { Text("Lier") }
                    }
                }
            }
        }
        Text("Connexion manuelle sur le réseau local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Pour un relais sans Wi‑Fi Direct : un PC ou un téléphone A sur le même réseau.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = manualIp,
            onValueChange = onManualIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Adresse du relais") },
            supportingText = { Text("Ex. 192.168.1.10") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = manualCode,
            onValueChange = onManualCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code du relais") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(
            onClick = { onManualConnect(manualIp, manualCode) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeBrand.Ink)
        ) { Text("Se connecter au relais") }

        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
    }
}

@Composable
private fun TrustPanel() {
    Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Lavender)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = LinkBridgeBrand.Purple)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Clair et contrôlable", fontWeight = FontWeight.Bold)
                Text(
                    "Android affiche le VPN et une notification pendant la connexion. LinkBridge ne démarre rien sans ton accord.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun LinkBridgeMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_linkbridge),
        contentDescription = "LinkBridge",
        modifier = modifier.clip(CircleShape)
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("LinkBridge ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Partage la connexion Internet d'un téléphone vers un autre via Wi‑Fi Direct, sans hotspot classique.")
                Text("Téléphone A : celui qui a Internet", fontWeight = FontWeight.Bold)
                Text("Appuie sur « Partager » et communique le code à 6 chiffres affiché.")
                Text("Téléphone B : celui qui reçoit", fontWeight = FontWeight.Bold)
                Text("Appuie sur « Recevoir », autorise le VPN, entre le code, puis choisis le téléphone A dans la liste.")
                Text(
                    "Android affiche une icône VPN et une notification pendant la connexion. Le partage continue en arrière-plan quand tu quittes l'application : il ne s'arrête que sur le bouton Arrêter.",
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider()
                Text("Conçu et développé par Metoushela Walker", fontWeight = FontWeight.Bold)
                Text(
                    "Dépôt du projet : https://github.com/MeganAe/linkbridge",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Logiciel sous licence MIT. © 2026 Metoushela Walker.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
