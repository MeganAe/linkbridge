package com.linkbridge.app

import java.awt.Desktop
import java.net.URI
import java.util.Properties

/**
 * Métadonnées du produit, affichées dans la boîte « À propos », dans la barre d'état et
 * dans les liens d'aide. Aucune de ces valeurs ne dépend du réseau.
 */
object LinkBridgeInfo {
    const val NAME: String = "LinkBridge"
    const val TAGLINE: String = "Un pont, pas un hotspot."
    const val AUTHOR: String = "Metoushela Walker"
    const val REPOSITORY_URL: String = "https://github.com/MeganAe/linkbridge"
    const val ISSUES_URL: String = "https://github.com/MeganAe/linkbridge/issues"
    const val LICENSE: String = "MIT"

    /**
     * Version de l'application, écrite par Gradle dans `linkbridge.properties` à la
     * compilation. Le repli évite un écran vide si la ressource est absente.
     */
    val version: String by lazy {
        try {
            val properties = Properties()
            LinkBridgeInfo::class.java.classLoader
                ?.getResourceAsStream("linkbridge.properties")
                ?.use { properties.load(it) }
            properties.getProperty("version")?.takeIf { it.isNotBlank() } ?: "0.0.0"
        } catch (_: Throwable) {
            "0.0.0"
        }
    }

    /** Vrai si le système sait ouvrir un navigateur. */
    val canOpenBrowser: Boolean
        get() = try {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
        } catch (_: Throwable) {
            false
        }

    /** Ouvre [url] dans le navigateur du système. Renvoie faux si c'est impossible. */
    fun openInBrowser(url: String): Boolean = try {
        val desktop = Desktop.getDesktop()
        if (!Desktop.isDesktopSupported() || !desktop.isSupported(Desktop.Action.BROWSE)) {
            false
        } else {
            desktop.browse(URI(url))
            true
        }
    } catch (_: Throwable) {
        false
    }
}
