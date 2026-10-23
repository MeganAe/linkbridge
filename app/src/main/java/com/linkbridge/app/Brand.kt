package com.linkbridge.app

import androidx.compose.ui.graphics.Color

/**
 * Palette sobre de LinkBridge, tirée de la nouvelle icône :
 * un violet profond, un violet moyen pour les actions, un accent turquoise
 * utilisé avec parcimonie, et le blanc pour le fond et les surfaces.
 *
 * Rien de flashy, rien d'enfantin : trois couleurs actives maximum.
 */
object LinkBridgeBrand {
    /** Violet profond : fonds de carte, texte principal, surfaces hero. */
    val Ink = Color(0xFF1E1B4B)
    /** Blanc : fond de l'application, texte sur fond foncé. */
    val Canvas = Color(0xFFFFFFFF)
    /** Violet moyen de l'icône : couleur primaire, boutons, éléments interactifs. */
    val Primary = Color(0xFF6D55D9)
    /** Turquoise de l'icône : accent unique, états actifs et positifs uniquement. */
    val Accent = Color(0xFF14B8A6)
    /** Surface blanche. */
    val Surface = Color(0xFFFFFFFF)
    /** Texte sur surface blanche. */
    val OnSurface = Color(0xFF1E1B4B)
    /** Texte secondaire sur surface blanche. */
    val OnSurfaceVariant = Color(0xFF6B7280)

    // —————————————————————————————————————————————————————————————
    // Aliases de compatibilité : les écrans secondaires (Guide, AppPicker, GuideActivity)
    // utilisent encore les anciens noms. Ils seront migrés vers la palette sobre
    // dans une prochaine itération. Ces alias pointent vers les nouvelles couleurs.
    // —————————————————————————————————————————————————————————————
    val Purple = Primary
    val Coral = Accent
    val Mint = Primary.copy(alpha = 0.15f)
    val Butter = Primary.copy(alpha = 0.08f)
    val Lavender = Primary.copy(alpha = 0.10f)
    val Background = Canvas
}
