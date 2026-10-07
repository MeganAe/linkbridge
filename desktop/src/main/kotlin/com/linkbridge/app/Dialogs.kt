package com.linkbridge.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
            size = DpSize(500.dp, 470.dp),
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
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BrandMark(
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
                        size = 128
                    )
                    Text(LinkBridgeInfo.NAME, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Version ${LinkBridgeInfo.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                    Text(
                        LinkBridgeInfo.TAGLINE,
                        style = MaterialTheme.typography.titleMedium,
                        color = LinkBridgeTheme.Purple
                    )

                    SoftDivider(Modifier.padding(vertical = 6.dp))

                    Text(
                        "Conçu et développé par ${LinkBridgeInfo.AUTHOR}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "© 2026 ${LinkBridgeInfo.AUTHOR}. Logiciel sous licence ${LinkBridgeInfo.LICENSE}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.OnSurfaceMuted,
                        textAlign = TextAlign.Center
                    )

                    LinkLine(
                        label = "Dépôt du projet",
                        url = LinkBridgeInfo.REPOSITORY_URL,
                        onClick = { DesktopActions.openRepository(ui) }
                    )
                    LinkLine(
                        label = "Signaler un problème",
                        url = LinkBridgeInfo.ISSUES_URL,
                        onClick = { DesktopActions.openIssues(ui) }
                    )

                    SoftDivider(Modifier.padding(vertical = 6.dp))

                    Text(
                        "LinkBridge partage la connexion Internet d'un appareil vers un autre sur le réseau " +
                            "local, sans activer de hotspot classique. Le relais n'écoute que sur l'interface " +
                            "choisie et chaque session utilise un code de six chiffres, jamais enregistré.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LinkBridgeTheme.OnSurfaceMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))
                    PrimaryActionButton(
                        text = "Fermer",
                        onClick = { ui.showAbout = false },
                        modifier = Modifier.width(140.dp)
                    )
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
            size = DpSize(560.dp, 520.dp),
            position = WindowPosition(Alignment.Center)
        ),
        title = "Guide rapide",
        resizable = true
    ) {
        LinkBridgeDesktopTheme {
            Surface(color = LinkBridgeTheme.Canvas, modifier = Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Partager la connexion de ce PC", style = MaterialTheme.typography.titleMedium)
                    GuideStep("1", "Ouvre la page Partager et choisis l'interface réseau qui possède Internet.")
                    GuideStep("2", "Appuie sur Démarrer le partage. Le code à six chiffres apparaît.")
                    GuideStep("3", "Sur le téléphone B, ouvre LinkBridge, choisis Recevoir, puis entre ce code.")

                    SoftDivider(Modifier.padding(vertical = 4.dp))

                    Text("Utiliser la connexion d'un téléphone", style = MaterialTheme.typography.titleMedium)
                    GuideStep("1", "Ouvre la page Se connecter et saisis l'adresse du relais, le port et le code.")
                    GuideStep("2", "Appuie sur Tester la connexion. Le message confirme que ça répond.")
                    GuideStep("3", "Appuie sur Démarrer le proxy, puis va sur la page Applications.")

                    SoftDivider(Modifier.padding(vertical = 4.dp))

                    Text("Applications", style = MaterialTheme.typography.titleMedium)
                    GuideStep("1", "Tout le PC règle le proxy de Windows. Chrome et Edge déjà ouverts en profitent.")
                    GuideStep("2", "Lancer une application démarre un navigateur réglé sur LinkBridge, avec son profil à part.")

                    SoftDivider(Modifier.padding(vertical = 4.dp))

                    Text("Raccourcis clavier", style = MaterialTheme.typography.titleMedium)
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

                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton(text = "Fermer", onClick = { ui.showQuickGuide = false })
                        SecondaryActionButton(
                            text = "Signaler un problème",
                            onClick = { DesktopActions.openIssues(ui) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            number,
            style = MaterialTheme.typography.labelLarge,
            color = LinkBridgeTheme.Purple,
            modifier = Modifier.width(18.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ShortcutRow(keys: String, action: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            keys,
            style = MaterialTheme.typography.labelMedium,
            color = LinkBridgeTheme.Ink,
            modifier = Modifier.width(140.dp)
        )
        Text(
            action,
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
    }
}

@Composable
private fun LinkLine(label: String, url: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
        Text(
            url,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = TextDecoration.Underline
            ),
            color = LinkBridgeTheme.Purple,
            modifier = Modifier
                .clickable(onClick = onClick)
                .handCursor()
                .padding(2.dp)
        )
    }
}
