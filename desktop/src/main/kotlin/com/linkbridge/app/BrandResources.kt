package com.linkbridge.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Image
import javax.imageio.ImageIO

/**
 * Ressources de marque embarquées dans `desktop/src/main/resources/brand`.
 *
 * Les images sont générées depuis `branding/linkbridge-mark.svg` : ce sont les mêmes dessins
 * que l'icône `installer/linkbridge.ico`. Elles sont lues depuis le classpath, donc l'application
 * n'a besoin d'aucun accès réseau et d'aucun fichier externe.
 */
object BrandResources {
    /** Tailles fournies : la barre des tâches et Alt+Tab choisissent la plus adaptée. */
    val iconSizes: List<Int> = listOf(16, 24, 32, 48, 64, 128, 256)

    private fun resourcePath(size: Int): String = "brand/linkbridge-$size.png"

    private fun readBytes(size: Int): ByteArray? = try {
        BrandResources::class.java.classLoader
            ?.getResourceAsStream(resourcePath(size))
            ?.use { it.readBytes() }
    } catch (_: Throwable) {
        null
    }

    private fun readImage(size: Int): Image? = try {
        BrandResources::class.java.classLoader
            ?.getResourceAsStream(resourcePath(size))
            ?.use { ImageIO.read(it) }
    } catch (_: Throwable) {
        null
    }

    /** Icônes de la fenêtre : barre de titre, barre des tâches, Alt+Tab. */
    fun windowIcons(): List<Image> = iconSizes.mapNotNull { readImage(it) }

    /** Icône de l'application dans la barre des tâches Windows. */
    fun taskbarIcon(): Image? = readImage(256)

    /** Logo affiché dans l'interface, décodé une seule fois. */
    fun markImageBitmap(size: Int = 64): ImageBitmap? = try {
        readBytes(size)?.let { bytes -> SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
    } catch (_: Throwable) {
        null
    }
}

/** Logo de la marque, avec un repli silencieux si la ressource manquait. */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Int = 64) {
    val bitmap = remember(size) { BrandResources.markImageBitmap(size) }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = "Logo LinkBridge", modifier = modifier)
    } else {
        Box(modifier)
    }
}
