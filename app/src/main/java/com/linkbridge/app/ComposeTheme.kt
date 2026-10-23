package com.linkbridge.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Palette de l'application : sobre, trois couleurs actives maximum. */
fun linkBridgeColorScheme() = lightColorScheme(
    primary = LinkBridgeBrand.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAE3FF),
    onPrimaryContainer = LinkBridgeBrand.Ink,
    secondary = LinkBridgeBrand.Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7F4),
    onSecondaryContainer = LinkBridgeBrand.Ink,
    background = LinkBridgeBrand.Canvas,
    onBackground = LinkBridgeBrand.Ink,
    surface = LinkBridgeBrand.Surface,
    onSurface = LinkBridgeBrand.OnSurface,
    surfaceVariant = Color(0xFFF5F0FF),
    onSurfaceVariant = LinkBridgeBrand.OnSurfaceVariant,
    inverseSurface = LinkBridgeBrand.Ink,
    inverseOnSurface = Color.White
)

fun linkBridgeShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Thème complet du produit : palette, formes et typographie Inter.
 * Les deux écrans passent par ici, donc aucun texte ne peut retomber sur la police du
 * constructeur ni sur une autre palette.
 */
@Composable
fun LinkBridgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = linkBridgeColorScheme(),
        shapes = linkBridgeShapes(),
        typography = LinkBridgeTypography,
        content = content
    )
}
