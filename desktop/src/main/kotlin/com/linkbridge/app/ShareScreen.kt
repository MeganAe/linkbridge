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
import androidx.compose.foundation.shape.RoundedCornerShape
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
                    Text(
                        "Aucune interface réseau détectée.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                subtitle = if (state.sharing) {
                    "Le partage est actif. Le code reste valable jusqu'à l'arrêt."
                } else {
                    "Démarre le relais quand le téléphone ou l'autre PC est prêt."
                }
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                StatusLine(
                    active = state.sharing,
                    text = if (state.sharing) {
                        if (state.clientCount == 1) "Actif, 1 client connecté"
                        else "Actif, ${state.clientCount} clients connectés"
                    } else {
                        "Arrêté"
                    }
                )
                state.shareError?.let { message ->
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
                title = "Code de liaison",
                subtitle = "Communique ces informations au téléphone B ou à l'autre PC.",
                container = LinkBridgeTheme.Butter
            ) {
                Text(
                    state.pairingCode.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = LinkBridgeTheme.Ink,
                    letterSpacing = 6.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Relais\u202F: ${state.relayEndpoint ?: "aucune interface sélectionnée"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "Utilisateur\u202F: ${Socks5Gateway.USERNAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeTheme.OnSurfaceMuted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryActionButton(
                        text = "Copier le code",
                        onClick = onCopyCode,
                        container = LinkBridgeTheme.Ink
                    )
                    SecondaryActionButton(
                        text = "Régénérer le code",
                        onClick = { state.regenerateCode() },
                        enabled = !state.sharing
                    )
                }
            }

            SectionCard(
                title = "Journal des connexions",
                subtitle = "Deux cents dernières lignes, rien n'est écrit sur le disque.",
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

/** Une interface réseau sur une seule ligne propre : nom tronqué avec infobulle, adresse à droite. */
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
            .heightIn(min = 30.dp)
            .clickable(enabled = enabled, onClick = onSelect)
            .handCursor()
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        TruncatedTextWithTooltip(
            text = iface.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
        Spacer(Modifier.width(12.dp))
        Text(
            iface.address,
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
    }
}
