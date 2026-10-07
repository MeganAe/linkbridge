package com.linkbridge.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Pages de la fenêtre, atteignables par la barre latérale ou par Ctrl+1, Ctrl+2, Ctrl+3. */
enum class DesktopPage(val label: String, val subtitle: String, val shortcutHint: String) {
    SHARE("Partager", "Ce PC possède Internet et devient le relais.", "Ctrl+1"),
    CONNECT("Se connecter", "Ce PC utilise le relais d'un téléphone ou d'un autre PC.", "Ctrl+2"),
    APPS("Applications", "Choisir ce qui passe par la connexion reçue.", "Ctrl+3")
}

/**
 * État purement visuel de la fenêtre : page affichée, note temporaire de la barre d'état,
 * ouverture des boîtes de dialogue. Aucune logique réseau, aucun appel à [DesktopAppState].
 */
class DesktopUiState {
    var page by mutableStateOf(DesktopPage.SHARE)

    var notice by mutableStateOf<String?>(null)
        private set

    /** Incrémenté à chaque note : la barre d'état relance son compte à rebours. */
    var noticeId by mutableStateOf(0)
        private set

    var showAbout by mutableStateOf(false)
    var showQuickGuide by mutableStateOf(false)

    fun open(page: DesktopPage) {
        this.page = page
    }

    fun notify(message: String) {
        notice = message
        noticeId++
    }

    fun clearNotice() {
        notice = null
    }
}
