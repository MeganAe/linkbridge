package com.linkbridge.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Police unique du produit : Inter, embarquée dans `res/font` à quatre graisses.
 *
 * Rien n'est téléchargé et rien ne dépend des Google Play Services : les fichiers sont
 * livrés dans l'APK, ce qui fonctionne hors ligne et donne le même rendu que la version
 * Windows, qui embarque exactement les mêmes fichiers.
 */
private val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private fun interStyle(
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
 * Les quinze styles Material 3 sont redéfinis : aucun texte ne peut retomber sur la
 * police du constructeur du téléphone.
 */
val LinkBridgeTypography: Typography = Typography(
    displayLarge = interStyle(40, FontWeight.SemiBold, letterSpacing = -0.5),
    displayMedium = interStyle(36, FontWeight.SemiBold, letterSpacing = -0.4),
    displaySmall = interStyle(32, FontWeight.SemiBold, letterSpacing = -0.3),
    headlineLarge = interStyle(28, FontWeight.SemiBold),
    headlineMedium = interStyle(26, FontWeight.SemiBold),
    headlineSmall = interStyle(24, FontWeight.SemiBold),
    titleLarge = interStyle(20, FontWeight.SemiBold),
    titleMedium = interStyle(16, FontWeight.SemiBold),
    titleSmall = interStyle(14, FontWeight.SemiBold),
    bodyLarge = interStyle(16, FontWeight.Normal),
    bodyMedium = interStyle(14, FontWeight.Normal),
    bodySmall = interStyle(12, FontWeight.Normal),
    labelLarge = interStyle(14, FontWeight.Medium),
    labelMedium = interStyle(12, FontWeight.Medium),
    labelSmall = interStyle(11, FontWeight.Medium)
)
