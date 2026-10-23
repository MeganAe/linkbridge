package com.linkbridge.app

import androidx.compose.ui.graphics.Color

/**
 * Palette de LinkBridge, tirée de l'icône : violet profond et violet d'action, turquoise,
 * ambre et corail pour les accents. Chaque zone de l'écran a sa couleur, sur fond blanc.
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

    /** Turquoise franc : boutons et pastilles « Recevoir ». */
    val Teal = Color(0xFF0FA697)
    /** Turquoise clair, lisible sur fond sombre. */
    val TealLight = Color(0xFF5EEAD4)
    /** Ambre : avertissements et accents chauds. */
    val Amber = Color(0xFFF59E0B)
    /** Jaune de l'icône, lisible sur fond sombre. */
    val AmberLight = Color(0xFFFFC63D)
    /** Corail : repères et points d'attention. */
    val CoralStrong = Color(0xFFF2605C)
    /** Bleu ciel : informations. */
    val Sky = Color(0xFF3B82F6)

    // —————————————————————————————————————————————————————————————
    // Aliases de compatibilité : les écrans secondaires (Guide, AppPicker, GuideActivity)
    // utilisent encore les anciens noms. Ils seront migrés vers la palette sobre
    // dans une prochaine itération. Ces alias pointent vers les nouvelles couleurs.
    // —————————————————————————————————————————————————————————————
    val Purple = Primary
    val Coral = CoralStrong
    val Mint = Teal.copy(alpha = 0.14f)
    val Butter = Amber.copy(alpha = 0.16f)
    val Lavender = Primary.copy(alpha = 0.10f)
    val Background = Canvas
}
