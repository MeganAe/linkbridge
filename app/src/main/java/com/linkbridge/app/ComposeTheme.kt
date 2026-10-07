package com.linkbridge.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Palette de l'application, partagée par l'écran principal et la page du guide. */
fun linkBridgeColorScheme() = lightColorScheme(
    primary = LinkBridgeBrand.Purple,
    onPrimary = Color.White,
    primaryContainer = LinkBridgeBrand.Lavender,
    onPrimaryContainer = LinkBridgeBrand.Ink,
    secondary = LinkBridgeBrand.Coral,
    onSecondary = Color.White,
    secondaryContainer = LinkBridgeBrand.Butter,
    onSecondaryContainer = LinkBridgeBrand.Ink,
    tertiary = Color(0xFF087F6C),
    background = LinkBridgeBrand.Background,
    onBackground = LinkBridgeBrand.Ink,
    surface = LinkBridgeBrand.Background,
    onSurface = LinkBridgeBrand.Ink
)

fun linkBridgeShapes() = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
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
