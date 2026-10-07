package com.linkbridge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Onglet « Applications » : choisir ce qui utilise la connexion du téléphone.
 *
 * - « Tout le PC » : proxy système de Windows. Chrome et Edge déjà ouverts
 *   s'en servent sans redémarrer.
 * - « Lancer une application » : démarre un navigateur déjà réglé sur le
 *   proxy local, sans commande à taper.
 */
@Composable
fun AppsScreen(state: DesktopAppState) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!state.proxyRunning) {
            Text(
                "Démarre d'abord le proxy local dans l'onglet « Se connecter / Tester ».",
                color = LinkBridgeTheme.Coral,
                fontWeight = FontWeight.Bold
            )
        }

        // --- Tout le PC ---
        Text("Tout le PC", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Lavender)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Règle le proxy de Windows sur LinkBridge. Les navigateurs déjà ouverts " +
                        "(Chrome, Edge…) et les applications qui suivent le proxy Windows passent " +
                        "par le téléphone, sans redémarrage. LinkBridge remet tes réglages " +
                        "d'origine quand tu le désactives, arrêtes le proxy ou fermes l'application.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!state.systemProxySupported) {
                    Text("Disponible seulement sous Windows.", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { state.enableSystemProxy() },
                        enabled = state.systemProxySupported && state.proxyRunning && !state.systemProxyOn,
                        colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeTheme.Ink)
                    ) { Text("Activer pour tout le PC") }
                    OutlinedButton(
                        onClick = { state.disableSystemProxy() },
                        enabled = state.systemProxyOn
                    ) { Text("Désactiver") }
                    Text(
                        if (state.systemProxyOn) "Actif" else "Inactif",
                        fontWeight = FontWeight.Bold
                    )
                }
                state.systemProxyMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // --- Lancer une application ---
        Text(
            "Lancer une application via LinkBridge",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Card(colors = CardDefaults.cardColors(containerColor = LinkBridgeTheme.Mint)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.launchableApps.isEmpty()) {
                    Text(
                        "Aucun navigateur compatible détecté (Chrome, Edge, Brave, Firefox).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.launchableApps.forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.Bold)
                            Text(app.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = { state.launchApp(app) },
                            enabled = state.proxyRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = LinkBridgeTheme.Purple)
                        ) { Text("Lancer") }
                    }
                }
                state.appsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Chaque navigateur s'ouvre avec un profil LinkBridge à part : tes connexions " +
                        "aux sites y sont conservées d'un lancement à l'autre, mais tes favoris " +
                        "et comptes habituels n'y sont pas.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
