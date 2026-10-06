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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

private enum class LinkRole { SHARE, RECEIVE }

class MainActivity : ComponentActivity() {
    private var role by mutableStateOf<LinkRole?>(null)
    private var status by mutableStateOf("Prêt à créer un lien")
    private var peers by mutableStateOf<List<WifiP2pDevice>>(emptyList())
    private var pairingInput by mutableStateOf("")
    private var vpnPrepared = false
    private var pendingPermissionAction: (() -> Unit)? = null
    private lateinit var pairingCode: String
    private lateinit var wifiDirect: WifiDirectController

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnPrepared = true
            status = "VPN autorisé. Choisis le téléphone A."
            wifiDirect.startReceiver()
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
        super.onCreate(savedInstanceState)
        pairingCode = PairingCode.getOrCreate(this)
        wifiDirect = WifiDirectController(
            context = this,
            onStatus = { status = it },
            onPeers = { peers = it },
            onConnectionInfo = ::onConnectionInfo
        )
        wifiDirect.register()

        setContent {
            LinkBridgeApp(
                role = role,
                status = status,
                pairingCode = pairingCode,
                pairingInput = pairingInput,
                peers = peers,
                onPairingInputChange = { pairingInput = it.filter { character -> character.isDigit() }.take(6) },
                onShare = { runWithNearbyPermissions(::beginSharing) },
                onReceive = { runWithNearbyPermissions(::beginReceiving) },
                onPeerSelected = { peer ->
                    if (pairingInput.length == 6) wifiDirect.connect(peer)
                    else status = "Entre d'abord le code du téléphone A"
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
                startReceiverVpn(host)
            }
        }
    }

    private fun beginSharing() {
        role = LinkRole.SHARE
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

    private fun startReceiverVpn(host: String) {
        if (pairingInput.length != 6) {
            status = "Entre le code affiché sur le téléphone A"
            return
        }
        val intent = Intent(this, VpnTunnelService::class.java)
            .setAction(VpnTunnelService.ACTION_START)
            .putExtra(VpnTunnelService.EXTRA_GATEWAY_HOST, host)
            .putExtra(VpnTunnelService.EXTRA_CODE, pairingInput)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(this, intent)
        else startService(intent)
    }

    private fun stopEverything() {
        wifiDirect.stop()
        stopService(Intent(this, GatewayService::class.java))
        stopService(Intent(this, VpnTunnelService::class.java).setAction(VpnTunnelService.ACTION_STOP))
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
    pairingCode: String,
    pairingInput: String,
    peers: List<WifiP2pDevice>,
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
    // Back returns to the role menu (and stops sharing/receiving) instead of
    // closing the app. From the menu itself, back exits normally.
    BackHandler(enabled = role != null) { onStop() }

    MaterialTheme(
        colorScheme = colors,
        shapes = expressiveShapes
    ) {
        if (showAbout) AboutDialog(onDismiss = { showAbout = false })
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
                    SharePanel(pairingCode = pairingCode, onStop = onStop)
                }

                AnimatedVisibility(role == LinkRole.RECEIVE) {
                    ReceivePanel(
                        pairingInput = pairingInput,
                        onPairingInputChange = onPairingInputChange,
                        peers = peers,
                        onPeerSelected = onPeerSelected,
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
private fun SharePanel(pairingCode: String, onStop: () -> Unit) {
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
    onStop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connexion sécurisée", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                Text("Téléphone A (qui a Internet)", fontWeight = FontWeight.Bold)
                Text("Appuie sur « Partager » et communique le code à 6 chiffres affiché.")
                Text("Téléphone B (qui reçoit)", fontWeight = FontWeight.Bold)
                Text("Appuie sur « Recevoir », autorise le VPN, entre le code, puis choisis le téléphone A dans la liste.")
                Text(
                    "Android affiche une icône VPN et une notification pendant la connexion. Le bouton retour arrête le lien et revient à l'accueil.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
