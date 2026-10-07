package com.linkbridge.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Mémorise si le guide de démarrage a déjà été montré sur ce téléphone.
 * Rien d'autre n'est enregistré, et rien ne quitte l'appareil.
 */
object GuideStore {
    private const val PREFS = "linkbridge_guide"
    private const val KEY_SEEN = "guide_seen"

    fun hasSeen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEN, true)
            .apply()
    }
}

/**
 * Carte de préparation affichée sur l'écran d'accueil.
 *
 * Le point qui bloque le plus souvent les nouveaux utilisateurs est le Wi‑Fi : sans lui,
 * Wi‑Fi Direct ne peut rien faire, et l'application reste silencieuse. Cette carte le dit
 * explicitement, prévient quand le Wi‑Fi est éteint, et ouvre les réglages en un appui.
 */
@Composable
internal fun PreparationCard(
    wifiOn: Boolean,
    onOpenWifiSettings: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (wifiOn) LinkBridgeBrand.Lavender else LinkBridgeBrand.Butter
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (wifiOn) Icons.Outlined.Info else Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = LinkBridgeBrand.Ink
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (wifiOn) "Avant de commencer" else "Le Wi‑Fi est désactivé",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (wifiOn) {
                PreparationStep("1", "Active le Wi‑Fi sur les deux téléphones. LinkBridge s'en sert pour les relier directement.")
            } else {
                Text(
                    "Active le Wi‑Fi pour continuer. LinkBridge a besoin du Wi‑Fi pour relier les deux " +
                        "téléphones entre eux.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            PreparationStep("2", "Le partage de connexion et le point d'accès ne servent à rien ici : le Wi‑Fi allumé suffit.")
            PreparationStep("3", "Garde les deux téléphones à quelques mètres, puis choisis Partager sur l'un et Recevoir sur l'autre.")

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!wifiOn) {
                    OutlinedButton(onClick = onOpenWifiSettings) { Text("Ouvrir les réglages Wi‑Fi") }
                }
                OutlinedButton(onClick = onOpenGuide) { Text("Voir le guide") }
            }
        }
    }
}

@Composable
private fun PreparationStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            number,
            style = MaterialTheme.typography.labelLarge,
            color = LinkBridgeBrand.Purple,
            modifier = Modifier.width(14.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Guide de démarrage complet. Il est montré au premier lancement, puis reste accessible par le
 * bouton Guide de la barre du haut et par la carte de préparation.
 */
@Composable
internal fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
        title = { Text("Comment utiliser LinkBridge", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuideSection("Ce qu'il faut savoir")
                GuideBullet("Le Wi‑Fi doit être activé sur les deux téléphones. C'est la seule chose à préparer.")
                GuideBullet(
                    "Le partage de connexion, le point d'accès et le mode avion ne servent à rien ici. " +
                        "LinkBridge relie les deux téléphones directement, sans passer par un routeur."
                )
                GuideBullet("Les deux téléphones doivent rester à quelques mètres l'un de l'autre.")

                Spacer(Modifier.padding(top = 4.dp))
                GuideSection("Téléphone A, celui qui a Internet")
                GuideStep("1", "Ouvre LinkBridge, puis appuie sur Partager.")
                GuideStep("2", "Si le Wi‑Fi est éteint, LinkBridge te le dit : active-le et reviens.")
                GuideStep("3", "Un code à 6 chiffres apparaît. Garde l'écran allumé.")
                GuideStep("4", "Ne coupe pas les données mobiles de ce téléphone : c'est lui qui fournit Internet.")

                Spacer(Modifier.padding(top = 4.dp))
                GuideSection("Téléphone B, celui qui reçoit")
                GuideStep("1", "Active le Wi‑Fi, ouvre LinkBridge, puis appuie sur Recevoir.")
                GuideStep("2", "Autorise la recherche des appareils à proximité quand Android le demande.")
                GuideStep("3", "Entre le code affiché sur le téléphone A.")
                GuideStep("4", "Choisis le téléphone A dans la liste, puis accepte la connexion qui apparaît sur A.")
                GuideStep("5", "Autorise le VPN. C'est ce qui fait passer la connexion reçue dans toutes tes applications.")
                GuideStep("6", "Le Wi‑Fi affiché peut indiquer que le réseau n'a pas Internet : c'est normal, la connexion vient de LinkBridge.")

                Spacer(Modifier.padding(top = 4.dp))
                GuideSection("Si ça ne marche pas")
                GuideBullet("La liste reste vide : vérifie le Wi‑Fi des deux téléphones et le code saisi.")
                GuideBullet("La connexion se coupe : rapproche les téléphones, puis relance Recevoir.")
                GuideBullet("Le VPN s'arrête tout seul : dans les réglages Android, retire LinkBridge de l'optimisation de la batterie.")
                GuideBullet("Autorise LinkBridge dans le pare-feu si tu utilises la version PC.")

                Spacer(Modifier.padding(top = 4.dp))
                GuideSection("Depuis un ordinateur")
                GuideBullet(
                    "Sur le téléphone A, la carte Accès PC sur ce réseau donne le nom du réseau et le " +
                        "mot de passe à saisir dans les réglages Wi‑Fi de l'ordinateur."
                )
                GuideBullet(
                    "Installe ensuite LinkBridge PC, choisis Se connecter, et entre le code à 6 chiffres " +
                        "avec l'adresse affichée sur le téléphone."
                )
            }
        }
    )
}

@Composable
private fun GuideSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = LinkBridgeBrand.Ink
    )
}

@Composable
private fun GuideBullet(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium, color = LinkBridgeBrand.Purple)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            number,
            style = MaterialTheme.typography.labelLarge,
            color = LinkBridgeBrand.Purple,
            modifier = Modifier.width(14.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
