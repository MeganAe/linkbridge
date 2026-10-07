package com.linkbridge.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Page « Partager » : ce PC possède Internet et devient le relais SOCKS5. */
@Composable
fun ShareScreen(state: DesktopAppState, wide: Boolean, onCopyCode: () -> Unit) {
    // Compte les clients connectés sans toucher à la logique du relais.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            state.refreshClients()
        }
    }

    TwoColumn(
        wide = wide,
        left = {
            SectionCard(
                title = "Interface réseau",
                subtitle = "Le relais n'écoute que sur l'adresse choisie, jamais sur toutes les interfaces.",
                trailing = {
                    SecondaryActionButton(
                        text = "Actualiser",
                        onClick = { state.refreshInterfaces() },
                        enabled = !state.sharing
                    )
                }
            ) {
                if (state.interfaces.isEmpty()) {
                    HelpText("Aucune interface réseau détectée. Vérifie ta connexion, puis actualise.")
                } else {
                    Column {
                        state.interfaces.forEach { iface ->
                            NetworkIfaceRow(
                                iface = iface,
                                selected = state.selectedIface == iface,
                                enabled = !state.sharing,
                                onSelect = { state.selectedIface = iface }
                            )
                        }
                    }
                }
            }

            SectionCard(
                title = "Relais",
                subtitle = "Le relais distribue la connexion de ce PC aux appareils qui présentent le bon code."
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryActionButton(
                        text = "Démarrer le partage",
                        onClick = { state.startSharing() },
                        enabled = !state.sharing
                    )
                    SecondaryActionButton(
                        text = "Arrêter",
                        onClick = { state.stopSharing() },
                        enabled = state.sharing
                    )
                }
                InfoLine(
                    label = "État",
                    value = if (state.sharing) {
                        if (state.clientCount == 0) "En écoute, aucun appareil connecté"
                        else if (state.clientCount == 1) "En écoute, 1 appareil connecté"
                        else "En écoute, ${state.clientCount} appareils connectés"
                    } else {
                        "Arrêté"
                    },
                    valueColor = if (state.sharing) LinkBridgeTheme.Success else LinkBridgeTheme.OnSurfaceMuted
                )
                state.shareError?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.Error
                    )
                }
            }
        },
        right = {
            SectionCard(
                title = "Code de liaison",
                subtitle = "À saisir sur le téléphone B ou sur l'autre PC, avec l'adresse ci-dessous.",
                container = LinkBridgeTheme.Butter
            ) {
                Text(
                    state.pairingCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = LinkBridgeTheme.Ink,
                    letterSpacing = 5.sp
                )
                InfoLine(
                    label = "Relais",
                    value = state.relayEndpoint ?: "aucune interface sélectionnée"
                )
                InfoLine(label = "Utilisateur", value = Socks5Gateway.USERNAME)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(text = "Copier le code", onClick = onCopyCode)
                    SecondaryActionButton(
                        text = "Régénérer",
                        onClick = { state.regenerateCode() },
                        enabled = !state.sharing
                    )
                }
            }

            SectionCard(
                title = "Journal des connexions",
                subtitle = "Les deux cents dernières lignes. Rien n'est écrit sur le disque.",
                trailing = {
                    SecondaryActionButton(
                        text = "Effacer",
                        onClick = { state.shareLog.clear() },
                        enabled = state.shareLog.isNotEmpty()
                    )
                }
            ) {
                LogView(lines = state.shareLog, emptyText = "Aucune activité pour le moment.")
            }
        }
    )
}

/** Une interface réseau sur une seule ligne : nom tronqué avec infobulle, adresse à droite. */
@Composable
private fun NetworkIfaceRow(
    iface: NetworkIface,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 26.dp)
            .clickable(enabled = enabled, onClick = onSelect)
            .handCursor()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Spacer(Modifier.width(4.dp))
        TruncatedTextWithTooltip(
            text = iface.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            weight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
        Spacer(Modifier.width(12.dp))
        Text(
            iface.address,
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
    }
}
