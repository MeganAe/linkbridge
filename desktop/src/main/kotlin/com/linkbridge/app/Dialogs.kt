package com.linkbridge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState

/** Boîte « À propos » : logo, nom, version, slogan, auteur, dépôt et licence. */
@Composable
fun AboutDialog(ui: DesktopUiState) {
    if (!ui.showAbout) return

    DialogWindow(
        onCloseRequest = { ui.showAbout = false },
        state = rememberDialogState(
            size = DpSize(460.dp, 400.dp),
            position = WindowPosition(Alignment.Center)
        ),
        title = "À propos de LinkBridge",
        resizable = false
    ) {
        LinkBridgeDesktopTheme {
            Surface(color = LinkBridgeTheme.Canvas, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(modifier = Modifier.size(40.dp), size = 128)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                LinkBridgeInfo.NAME,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Version ${LinkBridgeInfo.version} — ${LinkBridgeInfo.TAGLINE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LinkBridgeTheme.OnSurfaceMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SoftDivider()
                    Spacer(Modifier.height(14.dp))

                    InfoLine("Auteur", LinkBridgeInfo.AUTHOR)
                    InfoLine("Licence", "${LinkBridgeInfo.LICENSE}. Copyright 2026 ${LinkBridgeInfo.AUTHOR}.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Conçu et développé par ${LinkBridgeInfo.AUTHOR}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))

                    LinkRow("Dépôt du projet", LinkBridgeInfo.REPOSITORY_URL) {
                        DesktopActions.openRepository(ui)
                    }
                    LinkRow("Signaler un problème", LinkBridgeInfo.ISSUES_URL) {
                        DesktopActions.openIssues(ui)
                    }

                    Spacer(Modifier.height(14.dp))
                    SoftDivider()
                    Spacer(Modifier.height(14.dp))

                    HelpText(
                        "LinkBridge partage la connexion Internet d'un appareil vers un autre sur le " +
                            "réseau local, sans activer de hotspot classique. Le relais n'écoute que sur " +
                            "l'interface choisie et chaque session utilise un code de six chiffres, " +
                            "jamais enregistré."
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryActionButton(text = "Fermer", onClick = { ui.showAbout = false })
                        SecondaryActionButton(
                            text = "Guide rapide",
                            onClick = {
                                ui.showAbout = false
                                ui.showQuickGuide = true
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Boîte « Guide rapide » : les gestes essentiels et les raccourcis clavier. */
@Composable
fun QuickGuideDialog(ui: DesktopUiState) {
    if (!ui.showQuickGuide) return

    DialogWindow(
        onCloseRequest = { ui.showQuickGuide = false },
        state = rememberDialogState(
            size = DpSize(520.dp, 480.dp),
            position = WindowPosition(Alignment.Center)
        ),
        title = "Guide rapide",
        resizable = true
    ) {
        LinkBridgeDesktopTheme {
            Surface(color = LinkBridgeTheme.Canvas, modifier = Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(18.dp)
                    ) {
                        Text(
                            "Partager la connexion de ce PC",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        GuideStep("1", "Ouvre la page Partager et choisis l'interface réseau qui possède Internet.")
                        GuideStep("2", "Appuie sur Démarrer le partage. Le code à six chiffres apparaît.")
                        GuideStep("3", "Sur le téléphone B, ouvre LinkBridge, choisis Recevoir, puis entre ce code.")

                        Spacer(Modifier.height(14.dp))
                        SoftDivider()
                        Spacer(Modifier.height(14.dp))

                        Text(
                            "Utiliser la connexion d'un téléphone",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        GuideStep("1", "Ouvre la page Se connecter et saisis l'adresse du relais, le port et le code.")
                        GuideStep("2", "Appuie sur Tester la connexion. Le message confirme que ça répond.")
                        GuideStep("3", "Appuie sur Démarrer le proxy, puis va sur la page Applications.")

                        Spacer(Modifier.height(14.dp))
                        SoftDivider()
                        Spacer(Modifier.height(14.dp))

                        Text(
                            "Applications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        GuideStep("1", "Tout le PC règle le proxy de Windows. Chrome et Edge déjà ouverts en profitent.")
                        GuideStep("2", "Lancer une application démarre un navigateur réglé sur LinkBridge, avec son profil à part.")

                        Spacer(Modifier.height(14.dp))
                        SoftDivider()
                        Spacer(Modifier.height(14.dp))

                        Text(
                            "Raccourcis clavier",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        ShortcutRow("Ctrl+1, Ctrl+2, Ctrl+3", "Changer de page")
                        ShortcutRow("Ctrl+R", "Démarrer le relais")
                        ShortcutRow("Ctrl+Maj+R", "Arrêter le relais")
                        ShortcutRow("Ctrl+P", "Démarrer le proxy local")
                        ShortcutRow("Ctrl+Maj+P", "Arrêter le proxy local")
                        ShortcutRow("Ctrl+W", "Activer ou désactiver le proxy Windows")
                        ShortcutRow("Ctrl+Maj+C", "Copier le code de liaison")
                        ShortcutRow("Ctrl+L", "Effacer les journaux")
                        ShortcutRow("F1", "Ouvrir ce guide")
                        ShortcutRow("Ctrl+Q", "Quitter LinkBridge")

                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrimaryActionButton(text = "Fermer", onClick = { ui.showQuickGuide = false })
                            SecondaryActionButton(
                                text = "Signaler un problème",
                                onClick = { DesktopActions.openIssues(ui) }
                            )
                        }
                    }
                    // Marge de défilement à droite, pour ne pas coller la barre au texte.
                    Box(Modifier.width(12.dp).fillMaxHeight().background(Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelMedium,
            color = LinkBridgeTheme.OnSurfaceMuted,
            modifier = Modifier.width(12.dp)
        )
        HelpText(text, Modifier.weight(1f))
    }
}

@Composable
private fun ShortcutRow(keys: String, action: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            keys,
            style = MaterialTheme.typography.labelMedium,
            color = LinkBridgeTheme.Ink,
            modifier = Modifier.width(140.dp)
        )
        HelpText(action, Modifier.weight(1f))
    }
}

@Composable
private fun LinkRow(label: String, url: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeTheme.OnSurfaceMuted,
            modifier = Modifier.width(112.dp)
        )
        LinkText(url, onClick, Modifier.weight(1f))
    }
}
