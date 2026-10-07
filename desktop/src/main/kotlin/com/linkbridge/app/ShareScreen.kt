package com.linkbridge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Onglet « Partager » : ce PC possède Internet et devient le relais SOCKS5.
 */
@Composable
fun ShareScreen(state: DesktopAppState) {
    val logScroll = rememberScrollState()
    LaunchedEffect(state.shareLog.size) { logScroll.scrollTo(logScroll.maxValue) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            state.refreshClients()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Interface réseau ---
        Text("Interface réseau", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Le relais n'écoute que sur l'adresse choisie, pas sur toutes les interfaces.",
            style = MaterialTheme.typography.bodySmall
        )
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Lavender)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.interfaces.isEmpty()) {
                    Text("Aucune interface réseau détectée.")
                    OutlinedButton(onClick = { state.refreshInterfaces() }) { Text("Actualiser") }
                }
                state.interfaces.forEach { iface ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.selectedIface == iface,
                            onClick = { state.selectedIface = iface },
                            enabled = !state.sharing
                        )
                        Text(iface.toString())
                    }
                }
            }
        }

        // --- Code de liaison ---
        Text("Code de liaison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Butter)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Communique ces informations au téléphone B ou à l'autre PC :")
                Text(
                    state.pairingCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = LinkBridgeTheme.Ink,
                    letterSpacing = 5.sp
                )
                Text("Relais : ${state.relayEndpoint ?: "—"}", fontWeight = FontWeight.Bold)
                Text("Utilisateur : ${Socks5Gateway.USERNAME}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { state.regenerateCode() }, enabled = !state.sharing) {
                    Text("Régénérer le code")
                }
            }
        }

        // --- Démarrage ---
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { state.startSharing() },
                enabled = !state.sharing,
                colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeTheme.Purple)
            ) { Text("Démarrer le partage") }
            OutlinedButton(onClick = { state.stopSharing() }, enabled = state.sharing) { Text("Arrêter") }
            if (state.sharing) {
                Text(
                    "Clients connectés : ${state.clientCount}",
                    modifier = Modifier.padding(start = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        state.shareError?.let { message ->
            Text(message, color = LinkBridgeTheme.Coral, fontWeight = FontWeight.Bold)
        }

        // --- Journal ---
        Text("Journal des connexions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Ink)) {
            Column(
                Modifier.fillMaxWidth().height(220.dp).verticalScroll(logScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (state.shareLog.isEmpty()) {
                    Text("Aucune activité pour le moment.", color = Color.White.copy(alpha = 0.7f))
                }
                state.shareLog.forEach { line ->
                    Text(line, color = LinkBridgeTheme.Mint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
