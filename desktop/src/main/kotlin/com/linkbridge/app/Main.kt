package com.linkbridge.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Taskbar
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.roundToInt

/**
 * Point d'entrée de l'application de bureau.
 *
 * La fenêtre s'ouvre centrée, entièrement visible et au-dessus de la barre des tâches :
 * sa taille vaut 80 % de la zone utile de l'écran, plafonnée à 1040 x 700 dp, et sa position
 * est [WindowPosition.Aligned] sur [Alignment.Center], ce qui centre la fenêtre dans la zone
 * qui exclut les encoches système.
 *
 * Les valeurs de taille et de position de Compose sont exprimées dans la même unité que celle
 * rendue par AWT : les dimensions de `maximumWindowBounds` sont donc reportées telles quelles,
 * ce qui reste correct à 100 %, 125 % et 150 % de mise à l'échelle Windows.
 */
fun main() = application {
    val state = remember { DesktopAppState() }
    val ui = remember { DesktopUiState() }
    val initialSize = remember { initialWindowSize() }
    val windowState = rememberWindowState(
        size = initialSize,
        position = WindowPosition(Alignment.Center)
    )

    Window(
        onCloseRequest = {
            state.shutdown()
            exitApplication()
        },
        title = LinkBridgeInfo.NAME,
        state = windowState
    ) {
        // Barre de titre, barre des tâches et Alt+Tab reçoivent le logo de la marque.
        LaunchedEffect(Unit) { applyWindowChrome(window) }

        WindowMenuBar(
            state = state,
            ui = ui,
            onQuit = {
                state.shutdown()
                exitApplication()
            }
        )

        App(state = state, ui = ui)
        AboutDialog(ui)
        QuickGuideDialog(ui)
    }
}

/** Barre de menus native, avec ses raccourcis. */
@Composable
private fun FrameWindowScope.WindowMenuBar(
    state: DesktopAppState,
    ui: DesktopUiState,
    onQuit: () -> Unit
) {
    MenuBar {
        Menu("Fichier", mnemonic = 'F') {
            Item(
                text = "Démarrer le relais",
                shortcut = KeyShortcut(Key.R, ctrl = true),
                enabled = !state.sharing,
                onClick = {
                    ui.open(DesktopPage.SHARE)
                    state.startSharing()
                }
            )
            Item(
                text = "Arrêter le relais",
                shortcut = KeyShortcut(Key.R, ctrl = true, shift = true),
                enabled = state.sharing,
                onClick = { state.stopSharing() }
            )
            Separator()
            Item(
                text = "Démarrer le proxy local",
                shortcut = KeyShortcut(Key.P, ctrl = true),
                enabled = !state.proxyRunning,
                onClick = {
                    ui.open(DesktopPage.CONNECT)
                    state.startProxy()
                }
            )
            Item(
                text = "Arrêter le proxy local",
                shortcut = KeyShortcut(Key.P, ctrl = true, shift = true),
                enabled = state.proxyRunning,
                onClick = { state.stopProxy() }
            )
            Separator()
            Item(
                text = "Quitter",
                shortcut = KeyShortcut(Key.Q, ctrl = true),
                onClick = onQuit
            )
        }

        Menu("Affichage", mnemonic = 'A') {
            DesktopPage.entries.forEachIndexed { index, page ->
                RadioButtonItem(
                    text = page.label,
                    selected = ui.page == page,
                    shortcut = when (index) {
                        0 -> KeyShortcut(Key.One, ctrl = true)
                        1 -> KeyShortcut(Key.Two, ctrl = true)
                        else -> KeyShortcut(Key.Three, ctrl = true)
                    },
                    onClick = { ui.open(page) }
                )
            }
        }

        Menu("Outils", mnemonic = 'O') {
            CheckboxItem(
                text = "Proxy Windows pour tout le PC",
                checked = state.systemProxyOn,
                enabled = state.systemProxySupported && (state.proxyRunning || state.systemProxyOn),
                shortcut = KeyShortcut(Key.W, ctrl = true),
                onCheckedChange = { checked ->
                    if (checked) {
                        ui.open(DesktopPage.APPS)
                        state.enableSystemProxy()
                    } else {
                        state.disableSystemProxy()
                    }
                }
            )
            Separator()
            Item(
                text = "Effacer les journaux",
                shortcut = KeyShortcut(Key.L, ctrl = true),
                enabled = state.shareLog.isNotEmpty() || state.proxyLog.isNotEmpty(),
                onClick = { DesktopActions.clearLogs(state, ui) }
            )
            Item(
                text = "Copier le code de liaison",
                shortcut = KeyShortcut(Key.C, ctrl = true, shift = true),
                onClick = { DesktopActions.copyPairingCode(state, ui) }
            )
        }

        Menu("Aide", mnemonic = 'd') {
            Item(
                text = "Guide rapide",
                shortcut = KeyShortcut(Key.F1),
                onClick = { ui.showQuickGuide = true }
            )
            Item(
                text = "Signaler un problème",
                onClick = { DesktopActions.openIssues(ui) }
            )
            Separator()
            Item(
                text = "À propos",
                onClick = { ui.showAbout = true }
            )
        }
    }
}

/**
 * 80 % de la zone utile de l'écran principal, plafonnés à 1040 x 700 dp, avec un plancher
 * de 860 x 560 dp appliqué comme taille minimale de la fenêtre.
 */
private fun initialWindowSize(): DpSize {
    val usable: Rectangle? = try {
        GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
    } catch (_: Throwable) {
        null
    }
    val usableWidth = (usable?.width?.toFloat()) ?: DesktopMetrics.MaxWindowWidth.value
    val usableHeight = (usable?.height?.toFloat()) ?: DesktopMetrics.MaxWindowHeight.value

    val width = minOf(usableWidth * 0.8f, DesktopMetrics.MaxWindowWidth.value)
        .coerceAtLeast(minOf(DesktopMetrics.MinWindowWidth.value, usableWidth))
    val height = minOf(usableHeight * 0.8f, DesktopMetrics.MaxWindowHeight.value)
        .coerceAtLeast(minOf(DesktopMetrics.MinWindowHeight.value, usableHeight))

    return DpSize(width.dp, height.dp)
}

/** Icônes, taille minimale, icône de la barre des tâches, et fenêtre ramenée à l'écran. */
private fun applyWindowChrome(window: androidx.compose.ui.awt.ComposeWindow) {
    try {
        val icons = BrandResources.windowIcons()
        if (icons.isNotEmpty()) window.iconImages = icons
    } catch (_: Throwable) {
    }

    try {
        window.minimumSize = Dimension(
            DesktopMetrics.MinWindowWidth.value.roundToInt(),
            DesktopMetrics.MinWindowHeight.value.roundToInt()
        )
    } catch (_: Throwable) {
    }

    try {
        if (Taskbar.isTaskbarSupported()) {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                BrandResources.taskbarIcon()?.let { image -> taskbar.iconImage = image }
            }
        }
    } catch (_: Throwable) {
    }

    bringFullyOnScreen(window)
}

/**
 * Filet de sécurité : si la fenêtre dépassait la zone utile, elle est ramenée à l'intérieur.
 * Sur un écran normal, la taille initiale fait 80 % de la zone utile et rien n'est déplacé.
 */
private fun bringFullyOnScreen(window: Window) {
    try {
        val configuration = window.graphicsConfiguration ?: return
        val screenBounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val usable = Rectangle(
            screenBounds.x + insets.left,
            screenBounds.y + insets.top,
            screenBounds.width - insets.left - insets.right,
            screenBounds.height - insets.top - insets.bottom
        )
        val current = window.bounds
        val targetX = if (current.width <= usable.width) {
            current.x.coerceIn(usable.x, usable.x + usable.width - current.width)
        } else {
            usable.x
        }
        val targetY = if (current.height <= usable.height) {
            current.y.coerceIn(usable.y, usable.y + usable.height - current.height)
        } else {
            usable.y
        }
        if (targetX != current.x || targetY != current.y) {
            window.setLocation(targetX, targetY)
        }
    } catch (_: Throwable) {
    }
}
