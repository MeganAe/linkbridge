package com.linkbridge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Onglet « Se connecter / Tester » : ce PC utilise le relais d'un téléphone A
 * (ou d'un autre PC) à travers un proxy local sans mot de passe, destiné aux
 * navigateurs qui ne savent pas faire l'authentification SOCKS5.
 */
@Composable
fun ConnectScreen(state: DesktopAppState) {
    val logScroll = rememberScrollState()
    LaunchedEffect(state.proxyLog.size) { logScroll.scrollTo(logScroll.maxValue) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Coordonnées du relais ---
        Text("Relais distant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = state.relayHost,
                onValueChange = { state.relayHost = it.filter { c -> !c.isWhitespace() } },
                modifier = Modifier.weight(1.6f),
                label = { Text("IP du relais") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = state.relayPort,
                onValueChange = { state.relayPort = it.filter { c -> c.isDigit() }.take(5) },
                modifier = Modifier.weight(1f),
                label = { Text("Port") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.relayCode,
                onValueChange = { state.relayCode = it.filter { c -> c.isDigit() }.take(6) },
                modifier = Modifier.weight(1.3f),
                label = { Text("Code (6 chiffres)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { state.runTest() },
                enabled = !state.testing,
                colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeTheme.Purple)
            ) { Text("Tester") }
            state.testStatus?.let { status ->
                Text(
                    status,
                    color = if (state.testOk) Color(0xFF087F6C) else LinkBridgeTheme.Coral,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- Proxy local sans mot de passe ---
        Text("Proxy local pour le navigateur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Mint)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Chrome et Edge ne savent pas s'authentifier sur un proxy SOCKS5. " +
                        "LinkBridge ouvre donc un proxy local sans mot de passe sur " +
                        "${state.proxyEndpoint} qui relaie vers le relais distant.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { state.startProxy() },
                        enabled = !state.proxyRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeTheme.Ink)
                    ) { Text("Démarrer le proxy") }
                    OutlinedButton(onClick = { state.stopProxy() }, enabled = state.proxyRunning) { Text("Arrêter") }
                    Text(
                        if (state.proxyRunning) "En écoute sur ${state.proxyEndpoint}" else "Arrêté",
                        fontWeight = FontWeight.Bold
                    )
                }
                state.proxyError?.let { message ->
                    Text(message, color = LinkBridgeTheme.Coral, fontWeight = FontWeight.Bold)
                }
                Text("Réglage du navigateur", fontWeight = FontWeight.Bold)
                Text(
                    "• Firefox : Paramètres → Réseau → Paramètres de connexion → Proxy manuel → " +
                        "SOCKS v5, hôte ${state.proxyEndpoint}, port ${Socks5ChainProxy.DEFAULT_PORT}, " +
                        "cocher « Proxy DNS pour SOCKS v5 ».",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Chrome / Edge : Paramètres → Système → Paramètres du proxy → Options Internet → " +
                        "Connexions → Paramètres LAN → Avancé, champ Socks = 127.0.0.1 port ${Socks5ChainProxy.DEFAULT_PORT}." +
                        " Alternative : lancer avec --proxy-server=\"socks5://127.0.0.1:${Socks5ChainProxy.DEFAULT_PORT}\".",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Version sans tunnel système : UDP / QUIC n'est pas relayé ; les sites restent " +
                        "accessibles en TCP (HTTPS inclus).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // --- Journal du proxy ---
        Text("Journal du proxy local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Ink)) {
            Column(
                Modifier.fillMaxWidth().height(160.dp).verticalScroll(logScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (state.proxyLog.isEmpty()) {
                    Text("Aucune activité pour le moment.", color = Color.White.copy(alpha = 0.7f))
                }
                state.proxyLog.forEach { line ->
                    Text(line, color = LinkBridgeTheme.Mint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
