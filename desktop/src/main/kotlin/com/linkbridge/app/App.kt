package com.linkbridge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Couleurs de la marque (voir BRAND.md). */
object LinkBridgeTheme {
    val Ink = Color(0xFF251A46)
    val Purple = Color(0xFF6D55D9)
    val Coral = Color(0xFFFF7657)
    val Mint = Color(0xFFC7F7E8)
    val Butter = Color(0xFFFFE7A8)
    val Lavender = Color(0xFFEAE3FF)
    val Background = Color(0xFFFFFBFF)
}

@Composable
fun App(state: DesktopAppState) {
    val colors = lightColorScheme(
        primary = LinkBridgeTheme.Purple,
        onPrimary = Color.White,
        primaryContainer = LinkBridgeTheme.Lavender,
        onPrimaryContainer = LinkBridgeTheme.Ink,
        secondary = LinkBridgeTheme.Coral,
        onSecondary = Color.White,
        secondaryContainer = LinkBridgeTheme.Butter,
        onSecondaryContainer = LinkBridgeTheme.Ink,
        tertiary = Color(0xFF087F6C),
        background = LinkBridgeTheme.Background,
        surface = LinkBridgeTheme.Background
    )
    val shapes = androidx.compose.material3.Shapes(
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )

    MaterialTheme(colorScheme = colors, shapes = shapes) {
        Surface(color = LinkBridgeTheme.Background) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LinkBridge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Un pont, pas un hotspot.",
                        color = LinkBridgeTheme.Purple,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Partage la connexion Internet entre deux appareils, sans hotspot classique.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))

                var tab by remember { mutableStateOf(0) }
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Partager") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Se connecter / Tester") })
                }

                when (tab) {
                    0 -> ShareScreen(state)
                    1 -> ConnectScreen(state)
                }
            }
        }
    }
}
