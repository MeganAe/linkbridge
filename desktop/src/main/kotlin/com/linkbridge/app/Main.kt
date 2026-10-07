package com.linkbridge.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val state = remember { DesktopAppState() }
    Window(
        onCloseRequest = {
            // Stoppe proprement le relais et le proxy avant de quitter.
            state.shutdown()
            exitApplication()
        },
        title = "LinkBridge",
        state = rememberWindowState(width = 1024.dp, height = 820.dp)
    ) {
        App(state)
    }
}
