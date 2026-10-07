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
import androidx.compose.ui.unit.dp

/**
 * Page « Applications » : choisir ce qui utilise la connexion du téléphone.
 *
 * - « Tout le PC » : le proxy système de Windows, suivi par Chrome et Edge déjà ouverts.
 * - « Lancer une application » : démarre un navigateur déjà réglé sur le proxy local,
 *   sans commande à taper.
 */
@Composable
fun AppsScreen(state: DesktopAppState, wide: Boolean) {
    TwoColumn(
        wide = wide,
        left = {
            if (!state.proxyRunning) {
                SectionCard(
                    title = "Le proxy local est arrêté",
                    subtitle = "Les deux sections ci-dessous en ont besoin.",
                    container = LinkBridgeTheme.Butter
                ) {
                    Text(
                        "Démarre le proxy local depuis la page Se connecter, puis reviens ici.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionCard(
                title = "Tout le PC",
                subtitle = "Le réglage le plus simple pour un navigateur déjà ouvert.",
                container = LinkBridgeTheme.Lavender
            ) {
                Text(
                    "Règle le proxy de Windows sur LinkBridge. Les navigateurs déjà ouverts, comme Chrome ou " +
                        "Edge, et les applications qui suivent le proxy Windows passent par le téléphone, " +
                        "sans redémarrage. LinkBridge remet tes réglages d'origine quand tu désactives le " +
                        "proxy, quand tu l'arrêtes ou quand tu fermes l'application.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!state.systemProxySupported) {
                    Text(
                        "Disponible seulement sous Windows.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrimaryActionButton(
                        text = "Activer pour tout le PC",
                        onClick = { state.enableSystemProxy() },
                        enabled = state.systemProxySupported && state.proxyRunning && !state.systemProxyOn,
                        container = LinkBridgeTheme.Ink
                    )
                    SecondaryActionButton(
                        text = "Désactiver",
                        onClick = { state.disableSystemProxy() },
                        enabled = state.systemProxyOn
                    )
                }
                StatusLine(active = state.systemProxyOn, text = if (state.systemProxyOn) "Actif" else "Inactif")
                state.systemProxyMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                }
            }
        },
        right = {
            SectionCard(
                title = "Lancer une application via LinkBridge",
                subtitle = "Un écran à part, réglé sur le proxy local, sans commande à taper.",
                container = LinkBridgeTheme.Mint
            ) {
                if (state.launchableApps.isEmpty()) {
                    Text(
                        "Aucun navigateur compatible détecté. LinkBridge reconnaît Chrome, Edge, Brave et Firefox.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.launchableApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                TruncatedTextWithTooltip(
                                    text = app.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    weight = FontWeight.SemiBold
                                )
                                Text(
                                    app.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LinkBridgeTheme.OnSurfaceMuted
                                )
                            }
                            PrimaryActionButton(
                                text = "Lancer",
                                onClick = { state.launchApp(app) },
                                enabled = state.proxyRunning
                            )
                        }
                    }
                }
                state.appsMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                }
                Text(
                    "Chaque navigateur s'ouvre avec un profil LinkBridge à part. Tes connexions aux sites y " +
                        "sont conservées d'un lancement à l'autre, mais tes favoris et tes comptes habituels " +
                        "n'y sont pas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeTheme.OnSurfaceMuted
                )
            }
        }
    )
}
