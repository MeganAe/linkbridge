package com.linkbridge.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.core.location.LocationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import java.net.ConnectException
import java.net.SocketTimeoutException

/** Délai au-delà duquel une demande de connexion sans réponse est annoncée à l'utilisateur. */
private const val CONNECT_TIMEOUT_MS = 30_000L

/** Code de demande pour l'autorisation d'afficher les notifications. */
private const val REQUEST_NOTIFICATIONS = 41

/**
 * Android n'exige la localisation activée pour Wi‑Fi Direct que jusqu'à Android 12.
 * À partir d'Android 13, l'autorisation des appareils à proximité la remplace : demander la
 * localisation en plus bloquait Partager et Recevoir pour rien.
 */
private val locationRequired = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

/** Indique à l'accueil qu'on revient d'une autre page. */
const val EXTRA_GO_HOME = "com.linkbridge.app.GO_HOME"

/** Résultat du test de connexion demandé par l'utilisateur. */
private sealed interface LinkTestState {
    data object Idle : LinkTestState
    data object Running : LinkTestState
    data class Success(val detail: String) : LinkTestState
    data class Failure(val message: String) : LinkTestState
}

/** Source de la connexion reçue. */
private object ConnectionSource {
    const val DIRECT = "direct"
    const val MANUAL = "manual"
}

class MainActivity : ComponentActivity() {
    private var role by mutableStateOf<LinkRole?>(null)
    private var status by mutableStateOf("Prêt à créer un lien")
    private var peers by mutableStateOf<List<WifiP2pDevice>>(emptyList())
    private var pairingInput by mutableStateOf("")
    private var vpnPrepared = false
    private var pendingPermissionAction: (() -> Unit)? = null

    private var pairingCode by mutableStateOf(PairingCode.generate())
    private lateinit var wifiDirect: WifiDirectController

    private var manualIp by mutableStateOf("")
    private var manualCode by mutableStateOf("")
    private var groupSsid by mutableStateOf<String?>(null)
    private var groupPassphrase by mutableStateOf<String?>(null)
    private var pendingManual: Pair<String, String>? = null
    private var wifiOn by mutableStateOf(true)
    private var locationOn by mutableStateOf(true)
    private var radioPrompt by mutableStateOf(false)
    private var pendingRadioAction: (() -> Unit)? = null
    private var sessionActive by mutableStateOf(false)
    private var sessionFromDirect = false
    private var connecting by mutableStateOf(false)
    private var receiverHost by mutableStateOf("")
    private var internetOk by mutableStateOf(true)
    private var linkTest by mutableStateOf<LinkTestState>(LinkTestState.Idle)
    private var batteryRestricted by mutableStateOf(false)
    private var linkUp = false
    private var pendingHost: String? = null
    private var connectWatchdog: Runnable? = null
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
                startReceiverVpn(manual.first, manual.second, ConnectionSource.MANUAL)
            } else if (pendingHost != null) {
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
                    Notifier.post(
                        this, Notifier.ID_LINK,
                        "Connexion terminée", "Le lien avec l'autre téléphone s'est arrêté."
                    )
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
                Notifier.post(
                    this, Notifier.ID_ERROR, "Connexion impossible", message, important = true
                )
            }
        )
        wifiDirect.register()
        wifiOn = wifiDirect.wifiEnabled()
        internetOk = internetAvailable()
        batteryRestricted = !batteryExempt()
        locationOn = locationEnabled()
        requestNotificationPermissionIfNeeded()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    radioPrompt -> radioPrompt = false
                    role == null -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    sessionActive || linkUp -> {
                        moveTaskToBack(true)
                    }
                    else -> stopEverything()
                }
            }
        })

        val saved = SessionStore.load(this)
        if (saved.active && (GatewayService.running || VpnTunnelService.running)) {
            sessionActive = true
            linkUp = true
            sessionFromDirect = saved.role == LinkRole.SHARE
            role = saved.role
            if (saved.code.isNotEmpty()) pairingCode = saved.code
            if (saved.role == LinkRole.RECEIVE) {
                pairingInput = saved.code
                receiverHost = saved.host
            }
            status = when (saved.role) {
                LinkRole.SHARE -> "Partage en cours. Il continue même si tu fermes l'application."
                LinkRole.RECEIVE -> "Connexion reçue active. Elle continue en arrière-plan."
                null -> "Session en cours"
            }
        } else if (saved.active) {
            SessionStore.clear(this)
        }

        val selection = AppSelectionStore.load(this)
        restrictApps = selection.restrict
        selectedApps = selection.packages
        Thread {
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
                locationOn = locationOn,
                radioPrompt = radioPrompt,
                onEnableRadios = ::enableRadios,
                onDismissRadioPrompt = {
                    radioPrompt = false
                    pendingRadioAction = null
                    Notifier.cancel(this, Notifier.ID_SETUP)
                },
                sessionActive = sessionActive,
                connecting = connecting,
                internetOk = internetOk,
                onRecheckInternet = {
                    internetOk = internetAvailable()
                    status = if (internetOk) {
                        "Internet disponible sur ce téléphone"
                    } else {
                        "Toujours pas d'Internet : active les données mobiles"
                    }
                },
                batteryRestricted = batteryRestricted,
                onOpenBatterySettings = ::openBatterySettings,
                onOpenAppPicker = {
                    startActivity(Intent(this, AppPickerActivity::class.java))
                },
                receiverHost = receiverHost,
                linkTest = linkTest,
                onTestLink = ::runConnectionTest,
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
                    if (clean.length == 6) tryStartPendingTunnel()
                },
                onShare = { ensureRadios { runWithNearbyPermissions(::beginSharing) } },
                onReceive = { ensureRadios { runWithNearbyPermissions(::beginReceiving) } },
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
        if (role == null) role = if (info.isGroupOwner) LinkRole.SHARE else LinkRole.RECEIVE
        val current = role
        linkUp = true
        sessionActive = true
        sessionFromDirect = true
        when {
            current == LinkRole.SHARE && info.isGroupOwner -> {
                SessionStore.save(this, LinkRole.SHARE, pairingCode)
                status = "Téléphone B connecté. Le partage continue même si tu fermes l'application."
                Notifier.post(
                    this, Notifier.ID_LINK,
                    "Téléphone B connecté", "Le partage est actif. Il continue même si tu fermes l'application."
                )
                startGateway(host)
            }

            current == LinkRole.RECEIVE && !info.isGroupOwner -> {
                receiverHost = host
                linkTest = LinkTestState.Idle
                SessionStore.save(this, LinkRole.RECEIVE, pairingInput, host)
                when {
                    pairingInput.length != 6 -> {
                        pendingHost = host
                        status =
                            "Lien établi. Entre le code à 6 chiffres affiché sur l'autre appareil."
                    }

                    !vpnPrepared -> {
                        pendingHost = host
                        status = "Lien établi. Autorise le VPN pour terminer la connexion."
                    }

                    else -> {
                        status = "Téléphone A connecté. Démarrage du tunnel…"
                        Notifier.post(
                            this, Notifier.ID_LINK,
                            "Téléphone A connecté", "Démarrage de la connexion Internet reçue."
                        )
                        startReceiverVpn(host, pairingInput, ConnectionSource.DIRECT)
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

    private fun tryStartPendingTunnel() {
        val host = pendingHost ?: return
        if (!vpnPrepared || pairingInput.length != 6) return
        pendingHost = null
        status = "Téléphone A connecté. Démarrage du tunnel…"
        startReceiverVpn(host, pairingInput, ConnectionSource.DIRECT)
    }

    private fun openGuide() {
        GuideStore.markSeen(this)
        startActivity(Intent(this, GuideActivity::class.java))
    }

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
                wifiDirect.cancelConnect()
                wifiDirect.resumeDiscovery()
                status =
                    "La demande n'a pas abouti. Rapproche les deux téléphones, puis appuie de nouveau sur la ligne à relier."
                Notifier.post(
                    this@MainActivity, Notifier.ID_ERROR,
                    "Connexion non aboutie", "Rapproche les deux téléphones, puis réessaie.",
                    important = true
                )
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
        requestNotificationPermissionIfNeeded()
        internetOk = internetAvailable()
        if (!internetOk) {
            Notifier.post(
                this,
                Notifier.ID_SETUP,
                "Ce téléphone n'a pas Internet",
                "Le partage est lancé, mais rien ne passera tant que les données mobiles " +
                    "ne sont pas activées.",
                important = true
            )
        }
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

    private fun beginManualReceiving(host: String, code: String) {
        role = LinkRole.RECEIVE
        sessionFromDirect = false
        val cleanHost = host.trim()
        if (cleanHost.isEmpty() || code.length != 6) {
            status = "Entre l'adresse du relais et le code à 6 chiffres"
            return
        }
        if (!appSelectionValid()) return
        receiverHost = cleanHost
        linkTest = LinkTestState.Idle
        val intent = VpnService.prepare(this)
        if (intent != null) {
            status = "Autorise LinkBridge à créer la connexion VPN"
            pendingManual = cleanHost to code
            vpnLauncher.launch(intent)
        } else {
            vpnPrepared = true
            status = "Connexion au relais $cleanHost…"
            startReceiverVpn(cleanHost, code, ConnectionSource.MANUAL)
        }
    }

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
        receiverHost = ""
        linkTest = LinkTestState.Idle
        pendingRadioAction = null
        status = "Prêt à créer un lien"
    }

    private fun runWithNearbyPermissions(action: () -> Unit) {
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
        wifiOn = wifiDirect.wifiEnabled()
        locationOn = locationEnabled()
        if (wifiOn && (!locationRequired || locationOn)) {
            Notifier.cancel(this, Notifier.ID_SETUP)
            val waiting = pendingRadioAction
            if (waiting != null) {
                pendingRadioAction = null
                radioPrompt = false
                waiting()
            }
        }
        internetOk = internetAvailable()
        batteryRestricted = !batteryExempt()
        val selection = AppSelectionStore.load(this)
        restrictApps = selection.restrict
        selectedApps = selection.packages
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_GO_HOME, false) && !sessionActive && !linkUp) {
            if (role != null) stopEverything()
        }
    }

    private fun locationEnabled(): Boolean = try {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        LocationManagerCompat.isLocationEnabled(manager)
    } catch (_: Throwable) {
        true
    }

    private fun internetAvailable(): Boolean = try {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.allNetworks.any { network ->
            val caps = manager.getNetworkCapabilities(network) ?: return@any false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    } catch (_: Throwable) {
        true
    }

    private fun batteryExempt(): Boolean = try {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        power.isIgnoringBatteryOptimizations(packageName)
    } catch (_: Throwable) {
        true
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            status = "Ouvre les réglages Android, puis cherche l'économie de batterie"
        }
    }

    private fun runConnectionTest() {
        val host = receiverHost
        val code = pairingInput
        if (host.isBlank() || code.length != 6) {
            linkTest = LinkTestState.Failure("Le lien n'est pas encore établi")
            return
        }
        linkTest = LinkTestState.Running
        Thread {
            val result = try {
                val statusLine = Socks5Client.probeHttp(
                    relayHost = host,
                    relayPort = Socks5Gateway.DEFAULT_PORT,
                    username = Socks5Gateway.USERNAME,
                    password = code
                )
                LinkTestState.Success(statusLine)
            } catch (_: SocketTimeoutException) {
                LinkTestState.Failure(
                    "Le relais ne répond pas. Vérifie que le téléphone A affiche toujours son code."
                )
            } catch (_: ConnectException) {
                LinkTestState.Failure("Le relais est injoignable. Rapproche les deux téléphones.")
            } catch (error: Exception) {
                LinkTestState.Failure(error.message ?: "Le test a échoué")
            }
            runOnUiThread { linkTest = result }
        }.apply {
            name = "linkbridge-test"
            start()
        }
    }

    private fun ensureRadios(action: () -> Unit) {
        wifiOn = wifiDirect.wifiEnabled()
        locationOn = locationEnabled()
        if (wifiOn && (!locationRequired || locationOn)) {
            radioPrompt = false
            action()
        } else {
            pendingRadioAction = action
            radioPrompt = true
            Notifier.post(
                this, Notifier.ID_SETUP,
                if (locationRequired) {
                    "Active le Wi‑Fi et la localisation pour continuer"
                } else {
                    "Active le Wi‑Fi pour continuer"
                },
                "LinkBridge en a besoin pour relier les deux téléphones. Le partage de " +
                    "connexion et le point d'accès d'Android ne servent à rien ici.",
                important = true
            )
        }
    }

    private fun enableRadios() {
        radioPrompt = false
        try {
            if (!wifiOn) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startActivity(Intent(Settings.Panel.ACTION_WIFI))
                } else {
                    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                }
            } else if (!locationOn && locationRequired) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        } catch (_: Exception) {
            status = "Ouvre les réglages Android, puis active le Wi‑Fi et la localisation"
        }
    }

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

/**
 * Construit le graphique de la marque avec l'icône PNG fournie par l'utilisateur.
 */
@Composable
internal fun LinkBridgeMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.linkbridge_icone),
        contentDescription = "LinkBridge",
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkBridgeApp(
    role: LinkRole?,
    status: String,
    wifiOn: Boolean,
    locationOn: Boolean,
    radioPrompt: Boolean,
    onEnableRadios: () -> Unit,
    onDismissRadioPrompt: () -> Unit,
    sessionActive: Boolean,
    connecting: Boolean,
    internetOk: Boolean,
    onRecheckInternet: () -> Unit,
    batteryRestricted: Boolean,
    onOpenBatterySettings: () -> Unit,
    onOpenAppPicker: () -> Unit,
    receiverHost: String,
    linkTest: LinkTestState,
    onTestLink: () -> Unit,
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

    LinkBridgeTheme {
        if (showAbout) AboutDialog(onDismiss = { showAbout = false })
        if (radioPrompt) {
            AlertDialog(
                onDismissRequest = onDismissRadioPrompt,
                title = {
                    Text(
                        if (locationRequired) "Active le Wi‑Fi et la localisation"
                        else "Active le Wi‑Fi"
                    )
                },
                text = {
                    Text(
                        "Sans lui, rien ne passe : LinkBridge ne peut ni trouver l'autre " +
                            "téléphone, ni créer le lien.\n\n" +
                            (if (!wifiOn) "• Wi‑Fi : désactivé" else "• Wi‑Fi : activé") +
                            if (locationRequired) {
                                if (!locationOn) "\n• Localisation : désactivée"
                                else "\n• Localisation : activée"
                            } else {
                                ""
                            }
                    )
                },
                confirmButton = { TextButton(onClick = onEnableRadios) { Text("Activer") } },
                dismissButton = { TextButton(onClick = onDismissRadioPrompt) { Text("Plus tard") } }
            )
        }
        Scaffold(
            containerColor = LinkBridgeBrand.Canvas,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinkBridgeMark(Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("LinkBridge", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                        }
                    },
                    actions = {
                        TextButton(onClick = onOpenGuide) {
                            Text("Guide", color = LinkBridgeBrand.Primary)
                        }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "À propos", tint = LinkBridgeBrand.Ink)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = LinkBridgeBrand.Canvas,
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
                        fontWeight = FontWeight.Bold,
                        color = LinkBridgeBrand.Ink
                    )
                    RoleCard(
                        icon = Icons.Outlined.Wifi,
                        title = "Partager Internet",
                        description = "Ce téléphone possède Internet et devient le pont.",
                        buttonText = "Partager",
                        accent = LinkBridgeBrand.Primary,
                        onClick = onShare
                    )
                    RoleCard(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = "Recevoir Internet",
                        description = "Ce téléphone se connecte au pont sans hotspot classique.",
                        buttonText = "Recevoir",
                        accent = LinkBridgeBrand.Teal,
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
                        sessionActive = sessionActive,
                        internetOk = internetOk,
                        onRecheckInternet = onRecheckInternet,
                        batteryRestricted = batteryRestricted,
                        onOpenBatterySettings = onOpenBatterySettings,
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
                        sessionActive = sessionActive,
                        receiverHost = receiverHost,
                        linkTest = linkTest,
                        onTestLink = onTestLink,
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
                        onChooseApps = onOpenAppPicker,
                        onStop = onStop
                    )
                }

                TrustPanel()
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SessionNotice(onStop: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LinkBridgeBrand.Primary.copy(alpha = 0.10f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape).background(LinkBridgeBrand.Accent)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "Le partage continue même si tu fermes l'application. La notification permet de le suivre et de l'arrêter.",
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeBrand.OnSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onStop) {
            Text("Arrêter", color = LinkBridgeBrand.Primary)
        }
    }
}

@Composable
private fun HeroCard(role: LinkRole?, status: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(LinkBridgeBrand.Ink, Color(0xFF3B2FA0), LinkBridgeBrand.Primary)
                )
            )
    ) {
        // Décor : deux pastilles reprises des couleurs du logo.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 14.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(LinkBridgeBrand.TealLight.copy(alpha = 0.18f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 22.dp, end = 64.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(LinkBridgeBrand.AmberLight.copy(alpha = 0.35f))
        )
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkBridgeMark(Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Un pont, pas un hotspot",
                        color = LinkBridgeBrand.TealLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (role == null) LinkBridgeBrand.AmberLight else LinkBridgeBrand.TealLight)
                )
                Spacer(Modifier.width(8.dp))
                AnimatedContent(
                    targetState = status,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                            .togetherWith(fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                    },
                    label = "statut"
                ) { current ->
                    Text(current, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp)
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
    buttonText: String,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LinkBridgeBrand.Ink
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LinkBridgeBrand.OnSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SharePanel(
    pairingCode: String,
    groupSsid: String?,
    groupPassphrase: String?,
    sessionActive: Boolean,
    internetOk: Boolean,
    onRecheckInternet: () -> Unit,
    batteryRestricted: Boolean,
    onOpenBatterySettings: () -> Unit,
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Code de liaison", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
        Card(
            colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.5.dp, LinkBridgeBrand.Primary.copy(alpha = 0.25f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Entre ce code sur le téléphone B", style = MaterialTheme.typography.bodyMedium, color = LinkBridgeBrand.OnSurfaceVariant)
                Text(
                    pairingCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = LinkBridgeBrand.Ink,
                    letterSpacing = 6.sp
                )
                AssistChip(
                    onClick = {},
                    label = { Text("Wi‑Fi Direct privé", fontWeight = FontWeight.Medium) },
                    leadingIcon = { Icon(Icons.Outlined.Link, null, tint = LinkBridgeBrand.Primary) }
                )
            }
        }
        if (groupSsid != null) {
            Text("Accès PC sur ce réseau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
            Card(
                colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.5.dp, LinkBridgeBrand.Primary.copy(alpha = 0.25f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Un ordinateur peut rejoindre ce réseau Wi‑Fi, puis utiliser le relais avec " +
                            "LinkBridge PC. Adresse " + GatewayService.DEFAULT_GROUP_OWNER_ADDRESS +
                            ", port " + Socks5Gateway.DEFAULT_PORT + ", et le code ci-dessus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeBrand.OnSurfaceVariant
                    )
                    Text("Réseau : $groupSsid", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                    if (groupPassphrase != null) {
                        Text("Mot de passe : $groupPassphrase", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                    } else {
                        Text(
                            "Mot de passe non disponible sur cet appareil.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkBridgeBrand.OnSurfaceVariant
                        )
                    }
                }
            }
        }
        InternetNotice(internetOk = internetOk, onRecheck = onRecheckInternet)
        if (sessionActive && batteryRestricted) {
            BatteryNotice(onOpenSettings = onOpenBatterySettings)
        }
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Ink),
            border = BorderStroke(1.dp, LinkBridgeBrand.OnSurfaceVariant.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Outlined.StopCircle, null, tint = LinkBridgeBrand.Ink)
            Spacer(Modifier.width(8.dp))
            Text("Arrêter le partage")
        }
    }
}

@Composable
private fun ReceivePanel(
    connecting: Boolean,
    sessionActive: Boolean,
    receiverHost: String,
    linkTest: LinkTestState,
    onTestLink: () -> Unit,
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
        Text("Connexion sécurisée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Seulement certaines applications", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                        Text(
                            if (restrictApps) "Seules les applications choisies utilisent la connexion reçue."
                            else "Toutes les applications utilisent la connexion reçue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkBridgeBrand.OnSurfaceVariant
                        )
                    }
                    Switch(checked = restrictApps, onCheckedChange = onRestrictChange, colors = SwitchDefaults.colors(
                        checkedThumbColor = LinkBridgeBrand.Primary,
                        checkedTrackColor = LinkBridgeBrand.Primary.copy(alpha = 0.3f)
                    ))
                }
                if (restrictApps) {
                    OutlinedButton(
                        onClick = onChooseApps,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Primary),
                        border = BorderStroke(1.dp, LinkBridgeBrand.Primary.copy(alpha = 0.5f))
                    ) {
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
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeBrand.OnSurfaceVariant
                    )
                }
            }
        }
        OutlinedTextField(
            value = pairingInput,
            onValueChange = onPairingInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code du téléphone A", color = LinkBridgeBrand.OnSurfaceVariant) },
            supportingText = { Text("Le code ne quitte pas les deux téléphones.", style = MaterialTheme.typography.bodySmall, color = LinkBridgeBrand.OnSurfaceVariant) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (sessionActive) {
            LinkTestCard(state = linkTest, onTest = onTestLink)
        } else if (receiverHost.isNotBlank() && linkTest !is LinkTestState.Idle) {
            LinkTestCard(state = linkTest, onTest = onTestLink)
        }
        if (connecting) {
            Card(
                colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Primary.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = LinkBridgeBrand.Primary
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LinkBridgeBrand.Primary)
            Text("Recherche des appareils LinkBridge à proximité…", style = MaterialTheme.typography.bodyMedium, color = LinkBridgeBrand.OnSurfaceVariant)
        } else if (peers.isNotEmpty()) {
            Text("Téléphones trouvés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
            peers.forEach { peer ->
                OutlinedCard(
                    onClick = { onPeerSelected(peer) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, tint = LinkBridgeBrand.Primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(peer.deviceName.ifBlank { "Téléphone sans nom" }, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                            Text("Appuie pour demander la connexion", style = MaterialTheme.typography.bodySmall, color = LinkBridgeBrand.OnSurfaceVariant)
                        }
                        TextButton(onClick = { onPeerSelected(peer) }) {
                            Text("Lier", color = LinkBridgeBrand.Primary)
                        }
                    }
                }
            }
        }
        Text("Connexion manuelle sur le réseau local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
        Text(
            "Pour un relais sans Wi‑Fi Direct : un PC ou un téléphone A sur le même réseau.",
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeBrand.OnSurfaceVariant
        )
        OutlinedTextField(
            value = manualIp,
            onValueChange = onManualIpChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Adresse du relais", color = LinkBridgeBrand.OnSurfaceVariant) },
            supportingText = { Text("Ex. 192.168.1.10", style = MaterialTheme.typography.bodySmall, color = LinkBridgeBrand.OnSurfaceVariant) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            value = manualCode,
            onValueChange = onManualCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Code du relais", color = LinkBridgeBrand.OnSurfaceVariant) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(
            onClick = { onManualConnect(manualIp, manualCode) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeBrand.Primary)
        ) {
            Text("Se connecter au relais", color = Color.White)
        }

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Ink),
            border = BorderStroke(1.dp, LinkBridgeBrand.OnSurfaceVariant.copy(alpha = 0.5f))
        ) { Text("Annuler") }
    }
}

@Composable
private fun InternetNotice(internetOk: Boolean, onRecheck: () -> Unit) {
    if (internetOk) return
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = LinkBridgeBrand.Primary)
                Spacer(Modifier.width(8.dp))
                Text("Ce téléphone n'a pas Internet", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
            }
            Text(
                "Le lien peut se créer, mais rien ne passera : active les données mobiles ou " +
                    "reconnecte le Wi‑Fi, puis appuie sur Revérifier.",
                style = MaterialTheme.typography.bodySmall,
                color = LinkBridgeBrand.OnSurfaceVariant
            )
            OutlinedButton(
                onClick = onRecheck,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Primary),
                border = BorderStroke(1.dp, LinkBridgeBrand.Primary.copy(alpha = 0.5f))
            ) { Text("Revérifier") }
        }
    }
}

@Composable
private fun BatteryNotice(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.BatteryAlert,
                    contentDescription = null,
                    tint = LinkBridgeBrand.Primary
                )
                Spacer(Modifier.width(8.dp))
                Text("Économie de batterie", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
            }
            Text(
                "Android peut arrêter le partage pour économiser la batterie, surtout l'écran " +
                    "éteint. Autorise LinkBridge à tourner sans restriction.",
                style = MaterialTheme.typography.bodySmall,
                color = LinkBridgeBrand.OnSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Primary),
                border = BorderStroke(1.dp, LinkBridgeBrand.Primary.copy(alpha = 0.5f))
            ) { Text("Ouvrir les réglages") }
        }
    }
}

@Composable
private fun LinkTestCard(state: LinkTestState, onTest: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vérifier la connexion", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
            Text(
                "LinkBridge interroge le relais avec ton code, puis demande une page minuscule " +
                    "à travers lui.",
                style = MaterialTheme.typography.bodySmall,
                color = LinkBridgeBrand.OnSurfaceVariant
            )
            when (state) {
                is LinkTestState.Idle -> Unit
                is LinkTestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LinkBridgeBrand.Primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Test en cours…", style = MaterialTheme.typography.bodySmall, color = LinkBridgeBrand.OnSurfaceVariant)
                }

                is LinkTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = LinkBridgeBrand.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Internet passe. Le relais répond : ${state.detail}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = LinkBridgeBrand.Accent
                    )
                }

                is LinkTestState.Failure -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LinkBridgeBrand.Primary
                )
            }
            OutlinedButton(
                onClick = onTest,
                enabled = state !is LinkTestState.Running,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBridgeBrand.Primary),
                border = BorderStroke(1.dp, LinkBridgeBrand.Primary.copy(alpha = 0.5f))
            ) {
                Text(
                    when (state) {
                        is LinkTestState.Failure -> "Réessayer"
                        is LinkTestState.Success -> "Retester"
                        else -> "Tester la connexion"
                    }
                )
            }
        }
    }
}

@Composable
private fun TrustPanel() {
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Canvas),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, LinkBridgeBrand.Primary.copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = LinkBridgeBrand.Primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Clair et contrôlable", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                Text(
                    "Android affiche le VPN et une notification pendant la connexion. LinkBridge ne démarre rien sans ton accord.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeBrand.OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) {
            Text("Fermer", color = LinkBridgeBrand.Primary)
        } },
        title = { Text("LinkBridge ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Partage la connexion Internet d'un téléphone vers un autre via Wi‑Fi Direct, sans hotspot classique.",
                    color = LinkBridgeBrand.OnSurfaceVariant
                )
                Text("Téléphone A : celui qui a Internet", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                Text("Appuie sur « Partager » et communique le code à 6 chiffres affiché.", color = LinkBridgeBrand.OnSurfaceVariant)
                Text("Téléphone B : celui qui reçoit", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                Text("Appuie sur « Recevoir », autorise le VPN, entre le code, puis choisis le téléphone A dans la liste.", color = LinkBridgeBrand.OnSurfaceVariant)
                Text(
                    "Android affiche une icône VPN et une notification pendant la connexion. Le partage continue en arrière-plan quand tu quittes l'application : il ne s'arrête que sur le bouton Arrêter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeBrand.OnSurfaceVariant
                )
                HorizontalDivider()
                Text("Conçu et développé par Metoushela Walker", fontWeight = FontWeight.Bold, color = LinkBridgeBrand.Ink)
                Text(
                    "Dépôt du projet : https://github.com/MeganAe/linkbridge",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeBrand.OnSurfaceVariant
                )
                Text(
                    "Logiciel sous licence MIT. © 2026 Metoushela Walker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeBrand.OnSurfaceVariant
                )
            }
        }
    )
}
