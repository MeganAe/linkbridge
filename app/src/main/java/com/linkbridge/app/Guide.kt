package com.linkbridge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        shape = RoundedCornerShape(24.dp),
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
                PreparationStep(
                    "1",
                    "Active le Wi‑Fi sur les deux téléphones. LinkBridge s'en sert pour les relier directement."
                )
            } else {
                // Même numérotation dans les deux cas : sans elle, la liste commençait à 2
                // et l'étape manquante donnait l'impression d'un affichage cassé.
                PreparationStep(
                    "1",
                    "Active le Wi‑Fi pour continuer. LinkBridge a besoin du Wi‑Fi pour relier les " +
                        "deux téléphones entre eux."
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

/* ---------------------------------------------------------------------------------------
 * Contenu du guide, affiché dans sa propre page par GuideActivity.
 * ------------------------------------------------------------------------------------ */

/** Bandeau d'ouverture de la page guide. */
@Composable
internal fun GuideHero() {
    Card(
        colors = CardDefaults.cardColors(containerColor = LinkBridgeBrand.Ink),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LinkBridgeMark(Modifier.size(54.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    "Un pont, pas un hotspot",
                    color = LinkBridgeBrand.Mint,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Deux téléphones suffisent",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pas de câble, pas de compte, pas de routeur.",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** Une section du guide : titre, sous-titre, puis son contenu. */
@Composable
internal fun GuideSectionCard(
    number: String?,
    title: String,
    subtitle: String? = null,
    container: Color = Color.White,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(LinkBridgeBrand.Ink),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            number,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = LinkBridgeBrand.Ink
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LinkBridgeBrand.Ink.copy(alpha = 0.72f)
                        )
                    }
                }
            }
            HorizontalDivider(color = LinkBridgeBrand.Ink.copy(alpha = 0.10f))
            content()
        }
    }
}

/** Une étape numérotée à l'intérieur d'une section. */
@Composable
internal fun GuideStepRow(number: String, title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(LinkBridgeBrand.Lavender),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = LinkBridgeBrand.Purple,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LinkBridgeBrand.Ink.copy(alpha = 0.74f)
                )
            }
        }
    }
}

/** Une puce simple, pour les listes sans ordre. */
@Composable
internal fun GuideBulletRow(text: String, accent: Color = LinkBridgeBrand.Purple) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        ) {}
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Encadré d'avertissement, pour les pièges qui font échouer une tentative. */
@Composable
internal fun GuideNotice(text: String, container: Color = LinkBridgeBrand.Butter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = LinkBridgeBrand.Ink
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LinkBridgeBrand.Ink)
    }
}
