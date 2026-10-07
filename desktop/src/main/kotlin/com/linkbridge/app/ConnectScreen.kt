package com.linkbridge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Page « Se connecter » : ce PC utilise le relais d'un téléphone A ou d'un autre PC,
 * à travers un proxy local sans mot de passe destiné aux navigateurs qui ne savent pas
 * s'authentifier en SOCKS5.
 */
@Composable
fun ConnectScreen(state: DesktopAppState, wide: Boolean) {
    val invalid = state.validationMessage()

    TwoColumn(
        wide = wide,
        left = {
            SectionCard(
                title = "Relais distant",
                subtitle = "Adresse affichée sur l'appareil qui partage la connexion."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopTextField(
                        value = state.relayHost,
                        onValueChange = { value ->
                            state.relayHost = value.filter { character -> !character.isWhitespace() }
                        },
                        label = "Adresse IP du relais",
                        modifier = Modifier.weight(1.6f),
                        keyboardType = KeyboardType.Uri
                    )
                    DesktopTextField(
                        value = state.relayPort,
                        onValueChange = { value ->
                            state.relayPort = value.filter { character -> character.isDigit() }.take(5)
                        },
                        label = "Port",
                        modifier = Modifier.weight(0.8f),
                        keyboardType = KeyboardType.Number
                    )
                    DesktopTextField(
                        value = state.relayCode,
                        onValueChange = { value ->
                            state.relayCode = value.filter { character -> character.isDigit() }.take(6)
                        },
                        label = "Code à 6 chiffres",
                        modifier = Modifier.weight(1.2f),
                        keyboardType = KeyboardType.Number,
                        isError = state.relayCode.isNotEmpty() && state.relayCode.length != 6
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryActionButton(
                        text = if (state.testing) "Test en cours" else "Tester la connexion",
                        onClick = { state.runTest() },
                        enabled = !state.testing
                    )
                }
                val testMessage = state.testStatus ?: invalid
                if (testMessage != null) {
                    Text(
                        testMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            state.testStatus == null -> LinkBridgeTheme.OnSurfaceMuted
                            state.testOk -> LinkBridgeTheme.Success
                            else -> LinkBridgeTheme.Error
                        },
                        fontWeight = if (state.testStatus == null) FontWeight.Normal else FontWeight.SemiBold
                    )
                }
            }

            SectionCard(
                title = "Proxy local pour le navigateur",
                subtitle = "Sans mot de passe, en écoute uniquement sur cette machine.",
                container = LinkBridgeTheme.Mint
            ) {
                Text(
                    "Chrome et Edge ne savent pas s'authentifier sur un proxy SOCKS5. LinkBridge ouvre donc " +
                        "un proxy local sur ${state.proxyEndpoint}, qui relaie vers le relais distant. Ce proxy " +
                        "comprend SOCKS5 et HTTP. La page Applications permet de l'activer pour tout le PC ou " +
                        "de lancer un navigateur déjà réglé.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryActionButton(
                        text = "Démarrer le proxy",
                        onClick = { state.startProxy() },
                        enabled = !state.proxyRunning,
                        container = LinkBridgeTheme.Ink
                    )
                    SecondaryActionButton(
                        text = "Arrêter",
                        onClick = { state.stopProxy() },
                        enabled = state.proxyRunning
                    )
                }
                StatusLine(
                    active = state.proxyRunning,
                    text = if (state.proxyRunning) "En écoute sur ${state.proxyEndpoint}" else "Arrêté"
                )
                state.proxyError?.let { message ->
                    Text(
                        message,
                        color = LinkBridgeTheme.Error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        right = {
            SectionCard(
                title = "Réglage du navigateur",
                subtitle = "Utile seulement si le proxy Windows n'est pas activé."
            ) {
                BulletLine(
                    "Firefox",
                    "Paramètres → Réseau → Paramètres de connexion → Proxy manuel → SOCKS v5. " +
                        "Hôte ${state.proxyEndpoint}, port ${Socks5ChainProxy.DEFAULT_PORT}, puis coche " +
                        "«\u202FProxy DNS pour SOCKS v5\u202F»."
                )
                BulletLine(
                    "Chrome et Edge",
                    "Paramètres → Système → Paramètres du proxy → Options Internet → Connexions → " +
                        "Paramètres LAN → Avancé. Champ Socks\u202F: 127.0.0.1, port ${Socks5ChainProxy.DEFAULT_PORT}."
                )
                BulletLine(
                    "Ligne de commande",
                    "Tu peux aussi lancer le navigateur avec l'option --proxy-server=\"socks5://127.0.0.1:${Socks5ChainProxy.DEFAULT_PORT}\"."
                )
                Text(
                    "Cette version ne crée pas de tunnel système\u202F: UDP et QUIC ne sont pas relayés. " +
                        "Les sites restent accessibles en TCP, HTTPS compris.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeTheme.OnSurfaceMuted
                )
            }

            SectionCard(
                title = "Journal du proxy local",
                subtitle = "Chaque ligne correspond à une action de cette session.",
                trailing = {
                    SecondaryActionButton(
                        text = "Effacer",
                        onClick = { state.proxyLog.clear() },
                        enabled = state.proxyLog.isNotEmpty()
                    )
                }
            ) {
                LogView(lines = state.proxyLog, emptyText = "Aucune activité pour le moment.")
            }
        }
    )
}

/** Une puce de documentation : titre court puis explication. */
@Composable
private fun BulletLine(title: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = LinkBridgeTheme.Purple)
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = LinkBridgeTheme.OnSurfaceMuted
            )
        }
    }
}
