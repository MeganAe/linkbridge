package com.linkbridge.app

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Actions d'interface partagées entre la barre de menus et les pages. Elles n'ajoutent
 * aucune logique réseau : elles se contentent d'appeler [DesktopAppState] et de rendre
 * compte dans la barre d'état.
 */
object DesktopActions {

    /** Copie le code de liaison, et l'adresse du relais si elle est connue. */
    fun copyPairingCode(state: DesktopAppState, ui: DesktopUiState) {
        val parts = mutableListOf("Code de liaison\u202F: ${state.pairingCode}")
        state.relayEndpoint?.let { endpoint -> parts += "Relais\u202F: $endpoint" }
        val text = parts.joinToString("\n")
        val copied = copyToClipboard(text)
        ui.notify(
            if (copied) "Code de liaison copié dans le presse-papiers."
            else "Impossible de copier le code de liaison."
        )
    }

    fun clearLogs(state: DesktopAppState, ui: DesktopUiState) {
        state.shareLog.clear()
        state.proxyLog.clear()
        ui.notify("Journaux effacés.")
    }

    fun openIssues(ui: DesktopUiState) {
        val opened = LinkBridgeInfo.openInBrowser(LinkBridgeInfo.ISSUES_URL)
        ui.notify(
            if (opened) "Page des problèmes ouverte dans le navigateur."
            else "Impossible d'ouvrir le navigateur. Adresse\u202F: ${LinkBridgeInfo.ISSUES_URL}"
        )
    }

    fun openRepository(ui: DesktopUiState) {
        val opened = LinkBridgeInfo.openInBrowser(LinkBridgeInfo.REPOSITORY_URL)
        ui.notify(
            if (opened) "Dépôt ouvert dans le navigateur."
            else "Impossible d'ouvrir le navigateur. Adresse\u202F: ${LinkBridgeInfo.REPOSITORY_URL}"
        )
    }

    private fun copyToClipboard(text: String): Boolean = try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    } catch (_: Throwable) {
        false
    }
}
