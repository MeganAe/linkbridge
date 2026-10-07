package com.linkbridge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Le guide de LinkBridge, en pleine page.
 *
 * La version précédente s'affichait dans une fenêtre de dialogue : le texte y était serré,
 * coupé en bas, et le bouton de sortie tombait sous le contenu. Une page complète laisse
 * respirer les six étapes du téléphone B, se lit en faisant défiler, et se ferme par la
 * flèche du haut ou par le bouton du bas.
 */
class GuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkBridgeTheme {
                GuidePage(onClose = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuidePage(onClose: () -> Unit) {
    Scaffold(
        containerColor = LinkBridgeBrand.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Comment utiliser LinkBridge",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LinkBridgeBrand.Background,
                    titleContentColor = LinkBridgeBrand.Ink,
                    navigationIconContentColor = LinkBridgeBrand.Ink
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LinkBridgeBrand.Ink,
                        contentColor = Color.White
                    )
                ) {
                    Text("J'ai compris", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GuideHero()

            GuideSectionCard(
                number = null,
                title = "Ce qu'il faut savoir",
                subtitle = "Trois points, et rien d'autre à préparer."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuideBulletRow(
                        "Le Wi‑Fi doit être activé sur les deux téléphones. C'est la seule chose " +
                            "à préparer."
                    )
                    GuideBulletRow(
                        "Le partage de connexion, le point d'accès et le mode avion ne servent " +
                            "à rien ici. LinkBridge relie les deux téléphones directement, sans " +
                            "passer par un routeur."
                    )
                    GuideBulletRow(
                        "Les deux téléphones doivent rester à quelques mètres l'un de l'autre."
                    )
                }
                Spacer(Modifier.height(4.dp))
                GuideNotice(
                    "Android peut afficher sa propre demande Connexion à LinkBridge. Accepte-la : " +
                        "c'est le système qui relie les deux téléphones, pas une publicité."
                )
            }

            GuideSectionCard(
                number = "A",
                title = "Le téléphone qui a Internet",
                subtitle = "Celui qui partage",
                container = LinkBridgeBrand.Mint
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideStepRow("1", "Ouvre LinkBridge, puis appuie sur Partager.")
                    GuideStepRow(
                        "2",
                        "Si le Wi‑Fi est éteint, LinkBridge te le dit.",
                        "Active-le, puis reviens sur l'application."
                    )
                    GuideStepRow(
                        "3",
                        "Un code à six chiffres apparaît.",
                        "C'est lui que l'autre téléphone doit saisir."
                    )
                    GuideStepRow(
                        "4",
                        "Ne coupe pas les données mobiles de ce téléphone.",
                        "C'est lui qui fournit Internet aux autres."
                    )
                    GuideStepRow(
                        "5",
                        "Laisse le Wi‑Fi allumé et garde l'écran accessible.",
                        "Le partage continue même si tu quittes l'application."
                    )
                }
            }

            GuideSectionCard(
                number = "B",
                title = "Le téléphone qui reçoit",
                subtitle = "Celui qui se connecte",
                container = LinkBridgeBrand.Lavender
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideStepRow("1", "Active le Wi‑Fi, ouvre LinkBridge, puis appuie sur Recevoir.")
                    GuideStepRow(
                        "2",
                        "Autorise la recherche des appareils à proximité quand Android le demande."
                    )
                    GuideStepRow(
                        "3",
                        "Autorise le VPN.",
                        "C'est ce qui fait passer la connexion reçue dans tes applications."
                    )
                    GuideStepRow("4", "Entre le code affiché sur le téléphone qui partage.")
                    GuideStepRow(
                        "5",
                        "Choisis le téléphone qui partage dans la liste.",
                        "Android affiche alors une demande de connexion sur ce téléphone : accepte-la."
                    )
                    GuideStepRow(
                        "6",
                        "Si la demande arrive d'abord dans la barre de notifications, accepte-la.",
                        "LinkBridge reprend le lien tout seul et te demande seulement le code."
                    )
                    GuideStepRow(
                        "7",
                        "Le Wi‑Fi peut afficher que le réseau n'a pas Internet.",
                        "C'est normal : la connexion vient de LinkBridge, pas du réseau affiché."
                    )
                }
                Spacer(Modifier.height(4.dp))
                GuideNotice(
                    "Si la liste reste vide après dix secondes, rapproche les deux téléphones et " +
                        "appuie de nouveau sur Recevoir.",
                    LinkBridgeBrand.Butter
                )
            }

            GuideSectionCard(
                number = "?",
                title = "Si ça ne marche pas",
                subtitle = "Les causes réelles, dans l'ordre."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GuideBulletRow(
                        "La demande de connexion revient sans arrêt : dans les réglages Wi‑Fi " +
                            "d'Android, cherche le réseau DIRECT du téléphone, puis oublie-le. " +
                            "Une ancienne liaison enregistrée est la cause habituelle.",
                        LinkBridgeBrand.Coral
                    )
                    GuideBulletRow(
                        "La demande est acceptée mais rien ne se passe : garde les deux " +
                            "téléphones à portée, appuie de nouveau sur Recevoir, puis choisis " +
                            "le téléphone dans la liste. Une demande acceptée hors de " +
                            "l'application ne relie rien toute seule.",
                        LinkBridgeBrand.Coral
                    )
                    GuideBulletRow(
                        "Sur Android 12 et avant, active la localisation du téléphone : c'est " +
                            "la condition qu'Android impose à la recherche des appareils à " +
                            "proximité.",
                        LinkBridgeBrand.Coral
                    )
                    GuideBulletRow(
                        "La connexion se coupe toute seule : retire LinkBridge de l'optimisation " +
                            "de la batterie, dans les réglages Android.",
                        LinkBridgeBrand.Coral
                    )
                    GuideBulletRow(
                        "Rien ne passe alors que tout semble connecté : vérifie que les données " +
                            "mobiles du téléphone qui partage sont bien actives, et que le code " +
                            "saisi est bien celui affiché sur ce téléphone.",
                        LinkBridgeBrand.Coral
                    )
                }
            }

            GuideSectionCard(
                number = "PC",
                title = "Depuis un ordinateur",
                subtitle = "Le même relais, mais depuis un PC"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GuideStepRow(
                        "1",
                        "Sur le téléphone qui partage, la carte Accès PC donne le nom du réseau " +
                            "et son mot de passe."
                    )
                    GuideStepRow(
                        "2",
                        "Rejoins ce réseau depuis les réglages Wi‑Fi de l'ordinateur."
                    )
                    GuideStepRow(
                        "3",
                        "Ouvre LinkBridge PC, choisis Se connecter, puis entre le code à six " +
                            "chiffres et l'adresse affichée sur le téléphone."
                    )
                    GuideStepRow(
                        "4",
                        "Autorise LinkBridge dans le pare-feu, une seule fois."
                    )
                }
                Spacer(Modifier.height(4.dp))
                GuideNotice(
                    "Depuis un ordinateur, le partage de connexion du téléphone reste inutile : " +
                        "LinkBridge PC se connecte au relais, pas au réseau partagé.",
                    LinkBridgeBrand.Mint
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkBridgeMark(Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "LinkBridge, conçu et développé par Metoushela Walker",
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeBrand.Ink.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
