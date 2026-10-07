package com.linkbridge.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Fenêtre principale, organisée comme un utilitaire de bureau :
 *
 * - barre latérale de navigation à gauche, réduite à des icônes sous 900 dp de large ;
 * - zone de contenu plafonnée à 1000 dp, avec ses marges et une barre de défilement visible ;
 * - cartes sur deux colonnes au-dessus de 900 dp, une seule en dessous ;
 * - barre d'état en bas, en texte simple.
 */
@Composable
fun App(state: DesktopAppState, ui: DesktopUiState) {
    LinkBridgeDesktopTheme {
        Surface(color = LinkBridgeTheme.Canvas, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoColumns = maxWidth >= DesktopMetrics.TwoColumnBreakpoint

                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Sidebar(state = state, ui = ui, collapsed = !twoColumns)
                        Box(Modifier.width(1.dp).fillMaxHeight().background(LinkBridgeTheme.Outline))
                        ContentArea(state = state, ui = ui, twoColumns = twoColumns)
                    }
                    StatusBar(state = state, ui = ui)
                }
            }
        }
    }
}

/** Barre latérale : 190 dp avec libellés, 48 dp en icônes seules sous 900 dp. */
@Composable
private fun Sidebar(state: DesktopAppState, ui: DesktopUiState, collapsed: Boolean) {
    val width = if (collapsed) DesktopMetrics.SidebarRailWidth else DesktopMetrics.SidebarWidth
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(LinkBridgeTheme.Chrome)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (collapsed) 0.dp else 12.dp,
                    end = if (collapsed) 0.dp else 12.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start
        ) {
            BrandMark(modifier = Modifier.size(22.dp).alpha(0.95f), size = 64)
            if (!collapsed) {
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        LinkBridgeInfo.NAME,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Version ${LinkBridgeInfo.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(LinkBridgeTheme.Outline))

        DesktopPage.entries.forEach { page ->
            SidebarEntry(
                page = page,
                selected = ui.page == page,
                collapsed = collapsed,
                onClick = { ui.open(page) }
            )
        }

        Spacer(Modifier.weight(1f))

        if (!collapsed) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(LinkBridgeTheme.Outline))
            Text(
                LinkBridgeInfo.TAGLINE,
                style = MaterialTheme.typography.labelSmall,
                color = LinkBridgeTheme.OnSurfaceMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Entrée de navigation : un fond gris et un trait vertical à gauche quand la page est active.
 * Le violet n'est pas utilisé ici, il reste réservé aux liens et au focus.
 */
@Composable
private fun SidebarEntry(
    page: DesktopPage,
    selected: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) LinkBridgeTheme.Ink else LinkBridgeTheme.OnSurfaceMuted

    HoverTooltip(
        text = if (collapsed) "${page.label}  ${page.shortcutHint}" else "",
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) LinkBridgeTheme.Selection else Color.Transparent)
                .clickable(onClick = onClick)
                .handCursor(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(28.dp)
                    .background(if (selected) LinkBridgeTheme.Ink else Color.Transparent)
            )
            Row(
                Modifier
                    .weight(1f)
                    .padding(
                        start = if (collapsed) 0.dp else 10.dp,
                        end = if (collapsed) 0.dp else 10.dp,
                        top = 7.dp,
                        bottom = 7.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start
            ) {
                PageGlyph(page = page, tint = tint, modifier = Modifier.size(14.dp))
                if (!collapsed) {
                    Spacer(Modifier.width(9.dp))
                    Text(
                        page.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                        color = tint
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        page.shortcutHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

/** Icônes de navigation dessinées à la main : aucune dépendance, aucun fichier à charger. */
@Composable
private fun PageGlyph(page: DesktopPage, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension
        val strokeWidth = side * 0.12f
        when (page) {
            DesktopPage.SHARE -> {
                // Flèche vers le haut : la connexion sort de cette machine.
                drawLine(tint, Offset(side * 0.5f, side * 0.88f), Offset(side * 0.5f, side * 0.14f), strokeWidth, StrokeCap.Butt)
                drawLine(tint, Offset(side * 0.22f, side * 0.42f), Offset(side * 0.5f, side * 0.12f), strokeWidth, StrokeCap.Butt)
                drawLine(tint, Offset(side * 0.78f, side * 0.42f), Offset(side * 0.5f, side * 0.12f), strokeWidth, StrokeCap.Butt)
            }
            DesktopPage.CONNECT -> {
                // Flèche vers le bas : la connexion arrive sur cette machine.
                drawLine(tint, Offset(side * 0.5f, side * 0.12f), Offset(side * 0.5f, side * 0.86f), strokeWidth, StrokeCap.Butt)
                drawLine(tint, Offset(side * 0.22f, side * 0.58f), Offset(side * 0.5f, side * 0.88f), strokeWidth, StrokeCap.Butt)
                drawLine(tint, Offset(side * 0.78f, side * 0.58f), Offset(side * 0.5f, side * 0.88f), strokeWidth, StrokeCap.Butt)
            }
            DesktopPage.APPS -> {
                // Quatre carrés : les applications qui passent par le relais.
                val cell = side * 0.4f
                val gap = side * 0.2f
                listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (column, row) ->
                    drawRect(
                        color = tint,
                        topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                        size = androidx.compose.ui.geometry.Size(cell, cell)
                    )
                }
            }
        }
    }
}

/** Titre de page fixe, puis contenu défilant plafonné à 1000 dp. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ContentArea(state: DesktopAppState, ui: DesktopUiState, twoColumns: Boolean) {
    val scroll = rememberScrollState()
    val adapter = rememberScrollbarAdapter(scroll)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(LinkBridgeTheme.Chrome)
                .padding(horizontal = DesktopMetrics.PagePadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                ui.page.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                ui.page.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LinkBridgeTheme.OnSurfaceMuted
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LinkBridgeTheme.Outline))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(vertical = 14.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .widthIn(max = DesktopMetrics.ContentMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = DesktopMetrics.PagePadding)
                    ) {
                        when (ui.page) {
                            DesktopPage.SHARE -> ShareScreen(
                                state = state,
                                wide = twoColumns,
                                onCopyCode = { DesktopActions.copyPairingCode(state, ui) }
                            )
                            DesktopPage.CONNECT -> ConnectScreen(state = state, wide = twoColumns)
                            DesktopPage.APPS -> AppsScreen(state = state, wide = twoColumns)
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = adapter,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

/** Barre d'état en texte simple : aucun badge, aucune pastille. */
@Composable
private fun StatusBar(state: DesktopAppState, ui: DesktopUiState) {
    LaunchedEffect(ui.noticeId) {
        if (ui.notice != null) {
            delay(6_000)
            ui.clearNotice()
        }
    }

    Box(Modifier.fillMaxWidth().height(1.dp).background(LinkBridgeTheme.Outline))
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopMetrics.StatusBarHeight)
            .background(LinkBridgeTheme.Chrome)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusWord("Relais", if (state.sharing) "actif" else "arrêté", state.sharing)
        Separator()
        StatusWord("Proxy local", if (state.proxyRunning) "actif" else "arrêté", state.proxyRunning)
        Separator()
        if (state.systemProxySupported) {
            StatusWord("Proxy Windows", if (state.systemProxyOn) "actif" else "arrêté", state.systemProxyOn)
        } else {
            Text(
                "Proxy Windows : indisponible",
                style = MaterialTheme.typography.labelMedium,
                color = LinkBridgeTheme.OnSurfaceMuted
            )
        }
        Spacer(Modifier.weight(1f))
        ui.notice?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = LinkBridgeTheme.Ink,
                maxLines = 1
            )
            Separator()
        }
        Text(
            "${LinkBridgeInfo.NAME} ${LinkBridgeInfo.version}",
            style = MaterialTheme.typography.labelSmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
    }
}

@Composable
private fun StatusWord(label: String, word: String, active: Boolean) {
    Text(
        "$label : $word",
        style = MaterialTheme.typography.labelMedium,
        color = if (active) LinkBridgeTheme.Success else LinkBridgeTheme.OnSurfaceMuted
    )
}

@Composable
private fun Separator() {
    Text(
        "|",
        style = MaterialTheme.typography.labelSmall,
        color = LinkBridgeTheme.Outline,
        modifier = Modifier.padding(horizontal = 10.dp)
    )
}
