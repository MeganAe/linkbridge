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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Couleurs de la marque (voir BRAND.md), utilisées avec parcimonie.
 *
 * Le violet est un accent, pas une décoration : il sert au lien de la page active, aux liens
 * cliquables et au focus. Les actions sont en Ink, l'habillage est neutre, et seuls deux
 * signaux de couleur restent : Butter pour la carte du code de liaison, Mint pour la carte du
 * proxy local, plus le vert de confirmation.
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
    val Success = Color(0xFF0F7B63)
    val Error = Color(0xFFB3261E)

    /** Habillage neutre : barres, cartes, séparateurs. */
    val Chrome = Color(0xFFF2F1F5)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFF7F6F9)
    val Selection = Color(0xFFECEBF1)
    val Outline = Color(0xFFD7D6DD)
    val OutlineSoft = Color(0xFFE6E5EB)
    val OnSurfaceMuted = Color(0xFF5F5A6E)
}

/** Dimensions de la fenêtre. Valeurs en dp, donc indépendantes de la mise à l'échelle Windows. */
object DesktopMetrics {
    val MinWindowWidth = 860.dp
    val MinWindowHeight = 560.dp
    val MaxWindowWidth = 1040.dp
    val MaxWindowHeight = 700.dp

    val SidebarWidth = 190.dp
    val SidebarRailWidth = 48.dp

    /** Largeur maximale de la colonne de contenu. */
    val ContentMaxWidth = 1000.dp

    /** En dessous, barre latérale en icônes et cartes sur une seule colonne. */
    val TwoColumnBreakpoint = 900.dp

    val ButtonHeight = 30.dp
    val FieldHeight = 32.dp
    val StatusBarHeight = 24.dp
    val Gap = 12.dp
    val CardPadding = 12.dp
    val PagePadding = 16.dp
    val LogHeight = 176.dp
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
    lineHeight: Int = (size * 1.3f).toInt(),
    letterSpacing: Double = 0.0
): TextStyle = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

/**
 * Typographie de bureau, volontairement serrée : titres de 12 à 15 sp, corps de 11 à 12 sp.
 * Les graisses restent dans la moitié basse de l'échelle, comme dans un utilitaire Windows.
 * Tous les styles Material 3 sont redéfinis : aucun texte ne retombe sur une police système.
 */
fun linkBridgeTypography(): Typography = Typography(
    displayLarge = linkBridgeStyle(22, FontWeight.SemiBold, letterSpacing = -0.3),
    displayMedium = linkBridgeStyle(20, FontWeight.SemiBold, letterSpacing = -0.2),
    displaySmall = linkBridgeStyle(18, FontWeight.SemiBold),
    headlineLarge = linkBridgeStyle(16, FontWeight.SemiBold),
    headlineMedium = linkBridgeStyle(15, FontWeight.SemiBold),
    headlineSmall = linkBridgeStyle(14, FontWeight.Medium),
    titleLarge = linkBridgeStyle(14, FontWeight.Medium),
    titleMedium = linkBridgeStyle(12, FontWeight.SemiBold),
    titleSmall = linkBridgeStyle(12, FontWeight.Medium),
    bodyLarge = linkBridgeStyle(12, FontWeight.Normal),
    bodyMedium = linkBridgeStyle(12, FontWeight.Normal),
    bodySmall = linkBridgeStyle(11, FontWeight.Normal),
    labelLarge = linkBridgeStyle(12, FontWeight.Medium),
    labelMedium = linkBridgeStyle(11, FontWeight.Normal),
    labelSmall = linkBridgeStyle(10, FontWeight.Normal)
)

/**
 * Aucun angle arrondi : tous les rayons sont à zéro.
 *
 * Material 3 impose des formes dérivées de `CornerBasedShape`, donc `RectangleShape` n'est pas
 * accepté ici ; un rayon nul donne exactement le même dessin.
 */
private val Sharp = RoundedCornerShape(0.dp)

fun linkBridgeShapes(): Shapes = Shapes(
    extraSmall = Sharp,
    small = Sharp,
    medium = Sharp,
    large = Sharp,
    extraLarge = Sharp
)

fun linkBridgeColorScheme() = lightColorScheme(
    primary = LinkBridgeTheme.Ink,
    onPrimary = Color.White,
    primaryContainer = LinkBridgeTheme.Selection,
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

/** Barre de défilement droite, visible en permanence, adaptée à la souris. */
private val DesktopScrollbarStyle = ScrollbarStyle(
    minimalHeight = 24.dp,
    thickness = 10.dp,
    shape = RectangleShape,
    hoverDurationMillis = 200,
    unhoverColor = LinkBridgeTheme.Ink.copy(alpha = 0.18f),
    hoverColor = LinkBridgeTheme.Ink.copy(alpha = 0.42f)
)

/**
 * Thème de la fenêtre de bureau.
 *
 * Trois réglages changent tout par rapport à une application tactile : aucune forme arrondie,
 * `LocalMinimumInteractiveComponentSize` ramené à 28 dp pour que les cases à cocher, les boutons
 * radio et les interrupteurs ne prennent plus 48 dp de haut, et une barre de défilement visible
 * en permanence.
 */
@Composable
fun LinkBridgeDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = linkBridgeColorScheme(),
        shapes = linkBridgeShapes(),
        typography = linkBridgeTypography()
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 28.dp,
            LocalScrollbarStyle provides DesktopScrollbarStyle
        ) {
            content()
        }
    }
}
