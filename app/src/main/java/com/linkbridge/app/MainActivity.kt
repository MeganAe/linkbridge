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
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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

private enum class LinkRole { SHARE, RECEIVE }

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

    // Guide de démarrage : montré une seule fois, puis rappelé par le bouton Guide.
    private var showGuide by mutableStateOf(false)

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
                startReceiverVpn(manual.first, manual.second)
            } else {
                status = "VPN autorisé. Choisis le téléphone A."
                wifiDirect.startReceiver()
            }
        } else {
            status = "Autorisation VPN refusée"
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
            onWifiState = { enabled -> wifiOn = enabled }
        )
        wifiDirect.register()
        wifiOn = wifiDirect.wifiEnabled()
        showGuide = !GuideStore.hasSeen(this)

        val saved = AppSelectionStore.load(this)
        restrictApps = saved.restrict
        selectedApps = saved.packages
        Thread { installedApps = AppSelectionStore.listLaunchableApps(this) }.apply {
            name = "linkbridge-apps"
            start()
        }

        setContent {
            LinkBridgeApp(
                role = role,
                status = status,
                wifiOn = wifiOn,
                showGuide = showGuide,
                onDismissGuide = ::dismissGuide,
                onOpenGuide = { showGuide = true },
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
                onPairingInputChange = { pairingInput = it.filter { character -> character.isDigit() }.take(6) },
                onShare = { runWithNearbyPermissions(::beginSharing) },
                onReceive = { runWithNearbyPermissions(::beginReceiving) },
                onPeerSelected = { peer ->
                    if (pairingInput.length != 6) status = "Entre d'abord le code du téléphone A"
                    else if (appSelectionValid()) wifiDirect.connect(peer)
                },
                onStop = ::stopEverything
            )
        }
    }

    private fun onConnectionInfo(info: WifiP2pInfo) {
        val host = info.groupOwnerAddress?.hostAddress ?: GatewayService.DEFAULT_GROUP_OWNER_ADDRESS
        when {
            role == LinkRole.SHARE && info.isGroupOwner -> {
                status = "Téléphone B connecté. Partage actif."
                startGateway(host)
            }
            role == LinkRole.RECEIVE && !info.isGroupOwner && vpnPrepared -> {
                status = "Téléphone A connecté. Démarrage du VPN…"
                startReceiverVpn(host, pairingInput)
            }
        }
    }

    private fun beginSharing() {
        if (!wifiOn) {
            status = "Active le Wi-Fi, puis appuie de nouveau sur Partager"
            showGuide = !GuideStore.hasSeen(this)
            return
        }
        role = LinkRole.SHARE
        pairingCode = PairingCode.generate()
        peers = emptyList()
        status = "Création du lien privé…"
        startGateway(GatewayService.DEFAULT_GROUP_OWNER_ADDRESS)
        wifiDirect.startHost()
    }

    private fun beginReceiving() {
        role = LinkRole.RECEIVE
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

    /** Refuse « seulement certaines applications » quand aucune n'est cochée. */
    private fun appSelectionValid(): Boolean {
        if (restrictApps && selectedApps.isEmpty()) {
            status = "Choisis au moins une application, ou repasse sur « Toutes les applications »"
            return false
        }
        return true
    }

    private fun startReceiverVpn(host: String, code: String) {
        if (code.length != 6) {
            status = "Entre le code affiché sur le téléphone A"
            return
        }
        if (!appSelectionValid()) return
        val intent = Intent(this, VpnTunnelService::class.java)
            .setAction(VpnTunnelService.ACTION_START)
            .putExtra(VpnTunnelService.EXTRA_GATEWAY_HOST, host)
            .putExtra(VpnTunnelService.EXTRA_CODE, code)
            .putExtra(VpnTunnelService.EXTRA_RESTRICT_APPS, restrictApps)
            .putStringArrayListExtra(VpnTunnelService.EXTRA_ALLOWED_APPS, ArrayList(selectedApps))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this, intent)
        else startService(intent)
    }

    /**
     * Connexion manuelle à un relais sur le réseau local (par exemple le PC
     * avec LinkBridge), sans Wi-Fi Direct : IP + code suffisent.
     */
    private fun beginManualReceiving(host: String, code: String) {
        role = LinkRole.RECEIVE
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
            startReceiverVpn(cleanHost, code)
        }
    }

    private fun stopEverything() {
        wifiDirect.stop()
        stopService(Intent(this, GatewayService::class.java))
        stopService(Intent(this, VpnTunnelService::class.java).setAction(VpnTunnelService.ACTION_STOP))
        pendingManual = null
        groupSsid = null
        groupPassphrase = null
        role = null
        peers = emptyList()
        status = "Prêt à créer un lien"
    }

    private fun runWithNearbyPermissions(action: () -> Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
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

    private fun dismissGuide() {
        showGuide = false
        GuideStore.markSeen(this)
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
    showGuide: Boolean,
    onDismissGuide: () -> Unit,
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
    val colors = androidx.compose.material3.lightColorScheme(
        primary = LinkBridgeBrand.Purple,
        onPrimary = Color.White,
        primaryContainer = LinkBridgeBrand.Lavender,
        onPrimaryContainer = LinkBridgeBrand.Ink,
        secondary = LinkBridgeBrand.Coral,
        onSecondary = Color.White,
        secondaryContainer = LinkBridgeBrand.Butter,
        onSecondaryContainer = LinkBridgeBrand.Ink,
        tertiary = Color(0xFF087F6C),
        background = LinkBridgeBrand.Background,
        surface = LinkBridgeBrand.Background
    )
    val expressiveShapes = androidx.compose.material3.Shapes(
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp)
    )

    var showAbout by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    // Back returns to the role menu (and stops sharing/receiving) instead of
    // closing the app. From the menu itself, back exits normally.
    BackHandler(enabled = role != null) { onStop() }

    MaterialTheme(
        colorScheme = colors,
        shapes = expressiveShapes,
        typography = LinkBridgeTypography
    ) {
        if (showAbout) AboutDialog(onDismiss = { showAbout = false })
        if (showGuide) GuideDialog(onDismiss = onDismissGuide)
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
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinkBridgeMark(Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("LinkBridge", fontWeight = FontWeight.Bold)
                        }
                    },
                    actions = {
                        TextButton(onClick = onOpenGuide) { Text("Guide") }
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "À propos")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(role = role, status = status)

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

                AnimatedVisibility(role == LinkRole.SHARE) {
                    SharePanel(
                        pairingCode = pairingCode,
                        groupSsid = groupSsid,
                        groupPassphrase = groupPassphrase,
                        onStop = onStop
                    )
                }

                AnimatedVisibility(role == LinkRole.RECEIVE) {
                    ReceivePanel(
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
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (role == null) LinkBridgeBrand.Coral else LinkBridgeBrand.Mint))
                Spacer(Modifier.width(8.dp))
                Text(status, color = Color.White.copy(alpha = 0.88f))
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
        if (peers.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Recherche des appareils LinkBridge à proximité…")
        } else {
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0ECF7))) {
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
private fun LinkBridgeMark(modifier: Modifier = Modifier) {
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
                    "Android affiche une icône VPN et une notification pendant la connexion. Le bouton retour arrête le lien et revient à l'accueil.",
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
