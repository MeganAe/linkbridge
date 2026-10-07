package com.linkbridge.app

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Couleurs de la marque (voir BRAND.md). Palette inchangée : Ink, Purple, Coral,
 * Mint, Butter, Lavender, Canvas.
 */
object LinkBridgeTheme {
    val Ink = Color(0xFF251A46)
    val Purple = Color(0xFF6D55D9)
    val Coral = Color(0xFFFF7657)
    val Mint = Color(0xFFC7F7E8)
    val Butter = Color(0xFFFFE7A8)
    val Lavender = Color(0xFFEAE3FF)
    val Canvas = Color(0xFFFFFBFF)

    /** Ancien nom conservé : plusieurs écrans l'utilisent encore. */
    val Background = Canvas

    /** Vert de confirmation, déjà utilisé par Material dans l'application Android. */
    val Success = Color(0xFF087F6C)

    /** Surfaces secondaires discrètes, adaptées à une fenêtre de bureau. */
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF5F2FA)
    val Outline = Color(0xFFD9D3E6)
    val OutlineSoft = Color(0xFFE8E3F2)
    val OnSurfaceMuted = Color(0xFF5B5470)
    val Error = Color(0xFFB3261E)
}

/** Dimensions communes à toute la fenêtre. Valeurs en dp, donc indépendantes de la mise à l'échelle Windows. */
object DesktopMetrics {
    val MinWindowWidth = 860.dp
    val MinWindowHeight = 560.dp
    val MaxWindowWidth = 1040.dp
    val MaxWindowHeight = 700.dp

    val SidebarWidth = 210.dp
    val SidebarRailWidth = 56.dp

    /** Largeur maximale de la colonne de contenu. */
    val ContentMaxWidth = 1000.dp

    /** En dessous, la barre latérale devient une colonne d'icônes et les cartes passent sur une colonne. */
    val TwoColumnBreakpoint = 900.dp

    val ButtonHeight = 36.dp
    val FieldHeight = 38.dp
    val StatusBarHeight = 28.dp
    val Gap = 16.dp
    val CardPadding = 14.dp
    val PagePadding = 20.dp
    val LogHeight = 190.dp
}

/**
 * Police unique du produit : Inter, embarquée hors ligne dans les ressources du module
 * (voir THIRD_PARTY_NOTICES.md). Aucun téléchargement, aucune dépendance à Google Play Services.
 */
private val InterFamily = FontFamily(
    Font("fonts/inter-regular.ttf", FontWeight.Normal),
    Font("fonts/inter-medium.ttf", FontWeight.Medium),
    Font("fonts/inter-semibold.ttf", FontWeight.SemiBold),
    Font("fonts/inter-bold.ttf", FontWeight.Bold)
)

private fun linkBridgeStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int = (size * 1.35f).toInt(),
    letterSpacing: Double = 0.0
): TextStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

/**
 * Typographie de bureau : titres de 18 à 20 sp, corps de 13 à 14 sp, libellés de 11 à 13 sp.
 * Tous les styles Material 3 sont redéfinis pour qu'aucun texte ne retombe sur une police système.
 */
fun linkBridgeTypography(): Typography = Typography(
    displayLarge = linkBridgeStyle(30, FontWeight.SemiBold, letterSpacing = -0.4),
    displayMedium = linkBridgeStyle(26, FontWeight.SemiBold, letterSpacing = -0.3),
    displaySmall = linkBridgeStyle(22, FontWeight.SemiBold, letterSpacing = -0.2),
    headlineLarge = linkBridgeStyle(22, FontWeight.SemiBold),
    headlineMedium = linkBridgeStyle(20, FontWeight.SemiBold),
    headlineSmall = linkBridgeStyle(18, FontWeight.SemiBold),
    titleLarge = linkBridgeStyle(18, FontWeight.SemiBold),
    titleMedium = linkBridgeStyle(15, FontWeight.SemiBold),
    titleSmall = linkBridgeStyle(14, FontWeight.SemiBold),
    bodyLarge = linkBridgeStyle(14, FontWeight.Normal),
    bodyMedium = linkBridgeStyle(13, FontWeight.Normal),
    bodySmall = linkBridgeStyle(12, FontWeight.Normal),
    labelLarge = linkBridgeStyle(13, FontWeight.Medium),
    labelMedium = linkBridgeStyle(12, FontWeight.Medium),
    labelSmall = linkBridgeStyle(11, FontWeight.Medium)
)

fun linkBridgeShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp)
)

fun linkBridgeColorScheme() = lightColorScheme(
    primary = LinkBridgeTheme.Purple,
    onPrimary = Color.White,
    primaryContainer = LinkBridgeTheme.Lavender,
    onPrimaryContainer = LinkBridgeTheme.Ink,
    secondary = LinkBridgeTheme.Coral,
    onSecondary = Color.White,
    secondaryContainer = LinkBridgeTheme.Butter,
    onSecondaryContainer = LinkBridgeTheme.Ink,
    tertiary = LinkBridgeTheme.Success,
    onTertiary = Color.White,
    background = LinkBridgeTheme.Canvas,
    onBackground = LinkBridgeTheme.Ink,
    surface = LinkBridgeTheme.Canvas,
    onSurface = LinkBridgeTheme.Ink,
    surfaceVariant = LinkBridgeTheme.SurfaceAlt,
    onSurfaceVariant = LinkBridgeTheme.OnSurfaceMuted,
    outline = LinkBridgeTheme.Outline,
    outlineVariant = LinkBridgeTheme.OutlineSoft,
    error = LinkBridgeTheme.Error,
    onError = Color.White
)

/** Barre de défilement visible en permanence, adaptée à la souris. */
private val DesktopScrollbarStyle = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 200,
    unhoverColor = LinkBridgeTheme.Ink.copy(alpha = 0.22f),
    hoverColor = LinkBridgeTheme.Ink.copy(alpha = 0.45f)
)

/**
 * Thème de la fenêtre de bureau.
 *
 * Deux réglages changent tout par rapport à une application tactile :
 * - `LocalMinimumInteractiveComponentSize` ramené à 32 dp : les cases à cocher, les boutons
 *   radio et les interrupteurs Material 3 ne prennent plus 48 dp de haut.
 * - une barre de défilement sombre, toujours visible.
 */
@Composable
fun LinkBridgeDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = linkBridgeColorScheme(),
        shapes = linkBridgeShapes(),
        typography = linkBridgeTypography()
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 32.dp,
            LocalScrollbarStyle provides DesktopScrollbarStyle
        ) {
            content()
        }
    }
}
