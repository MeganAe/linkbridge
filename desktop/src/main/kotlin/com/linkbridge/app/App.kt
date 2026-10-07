package com.linkbridge.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Fenêtre principale, organisée comme un logiciel de bureau :
 *
 * - barre latérale de navigation à gauche, réduite à des icônes sous 900 dp de large ;
 * - zone de contenu limitée à 1000 dp, avec ses marges et une barre de défilement visible ;
 * - cartes sur deux colonnes au-dessus de 900 dp, une seule en dessous ;
 * - barre d'état en bas, qui résume le relais, le proxy local et le proxy Windows.
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
                        Box(Modifier.width(1.dp).fillMaxHeight().background(LinkBridgeTheme.OutlineSoft))
                        ContentArea(state = state, ui = ui, twoColumns = twoColumns)
                    }
                    StatusBar(state = state, ui = ui)
                }
            }
        }
    }
}

/** Barre latérale : 210 dp avec libellés, 56 dp en icônes seules sous 900 dp. */
@Composable
private fun Sidebar(state: DesktopAppState, ui: DesktopUiState, collapsed: Boolean) {
    val width = if (collapsed) DesktopMetrics.SidebarRailWidth else DesktopMetrics.SidebarWidth
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(LinkBridgeTheme.SurfaceAlt)
            .padding(vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = if (collapsed) 0.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start
        ) {
            BrandMark(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                size = 64
            )
            if (!collapsed) {
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(LinkBridgeInfo.NAME, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${LinkBridgeInfo.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LinkBridgeTheme.OnSurfaceMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        DesktopPage.entries.forEach { page ->
            SidebarEntry(
                page = page,
                selected = ui.page == page,
                collapsed = collapsed,
                relayActive = state.sharing,
                onClick = { ui.open(page) }
            )
        }

        Spacer(Modifier.weight(1f))

        if (!collapsed) {
            Text(
                LinkBridgeInfo.TAGLINE,
                style = MaterialTheme.typography.labelSmall,
                color = LinkBridgeTheme.OnSurfaceMuted,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        }
    }
}

@Composable
private fun SidebarEntry(
    page: DesktopPage,
    selected: Boolean,
    collapsed: Boolean,
    relayActive: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) LinkBridgeTheme.Lavender else Color.Transparent
    val tint = if (selected) LinkBridgeTheme.Ink else LinkBridgeTheme.OnSurfaceMuted

    HoverTooltip(
        text = if (collapsed) "${page.label}  ${page.shortcutHint}" else "",
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (collapsed) 8.dp else 10.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .clickable(onClick = onClick)
                .handCursor()
                .padding(horizontal = if (collapsed) 0.dp else 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start
        ) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                PageGlyph(page = page, tint = tint)
                if (relayActive && page == DesktopPage.SHARE) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(LinkBridgeTheme.Success)
                    )
                }
            }
            if (!collapsed) {
                Spacer(Modifier.width(10.dp))
                Text(
                    page.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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

/** Icônes de navigation dessinées à la main : aucune dépendance, aucun fichier à charger. */
@Composable
private fun PageGlyph(page: DesktopPage, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val side = size.minDimension
        val strokeWidth = side * 0.11f
        when (page) {
            DesktopPage.SHARE -> {
                // Flèche vers le haut : la connexion sort de cette machine.
                drawLine(tint, Offset(side * 0.5f, side * 0.86f), Offset(side * 0.5f, side * 0.16f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(side * 0.24f, side * 0.44f), Offset(side * 0.5f, side * 0.14f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(side * 0.76f, side * 0.44f), Offset(side * 0.5f, side * 0.14f), strokeWidth, StrokeCap.Round)
            }
            DesktopPage.CONNECT -> {
                // Flèche vers le bas : la connexion arrive sur cette machine.
                drawLine(tint, Offset(side * 0.5f, side * 0.14f), Offset(side * 0.5f, side * 0.84f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(side * 0.24f, side * 0.56f), Offset(side * 0.5f, side * 0.86f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(side * 0.76f, side * 0.56f), Offset(side * 0.5f, side * 0.86f), strokeWidth, StrokeCap.Round)
            }
            DesktopPage.APPS -> {
                val cell = side * 0.34f
                val gap = side * 0.14f
                listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (column, row) ->
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(column * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(side * 0.08f)
                    )
                }
            }
        }
    }
}

/** Titre de page fixe, puis contenu défilant limité à 1000 dp. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContentArea(state: DesktopAppState, ui: DesktopUiState, twoColumns: Boolean) {
    val scroll = rememberScrollState()
    val adapter = rememberScrollbarAdapter(scroll)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = DesktopMetrics.ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        start = DesktopMetrics.PagePadding,
                        end = DesktopMetrics.PagePadding,
                        top = 16.dp,
                        bottom = 10.dp
                    )
            ) {
                Text(ui.page.label, style = MaterialTheme.typography.headlineSmall)
                Text(
                    ui.page.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LinkBridgeTheme.OnSurfaceMuted
                )
            }
        }
        SoftDivider()

        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(top = 14.dp, bottom = 18.dp)
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

/** Barre d'état : relais, proxy local, proxy Windows, version et notes temporaires. */
@Composable
private fun StatusBar(state: DesktopAppState, ui: DesktopUiState) {
    LaunchedEffect(ui.noticeId) {
        if (ui.notice != null) {
            delay(6_000)
            ui.clearNotice()
        }
    }

    SoftDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .height(DesktopMetrics.StatusBarHeight)
            .background(LinkBridgeTheme.SurfaceAlt)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusItem(label = "Relais", active = state.sharing)
        StatusSeparator()
        StatusItem(label = "Proxy local", active = state.proxyRunning)
        StatusSeparator()
        StatusItem(
            label = "Proxy Windows",
            active = state.systemProxyOn,
            available = state.systemProxySupported
        )
        Spacer(Modifier.weight(1f))
        ui.notice?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = LinkBridgeTheme.Purple,
                maxLines = 1
            )
            StatusSeparator()
        }
        Text(
            "${LinkBridgeInfo.NAME} ${LinkBridgeInfo.version}",
            style = MaterialTheme.typography.labelSmall,
            color = LinkBridgeTheme.OnSurfaceMuted
        )
    }
}

@Composable
private fun StatusItem(label: String, active: Boolean, available: Boolean = true) {
    StatusDot(
        when {
            !available -> LinkBridgeTheme.Outline
            active -> LinkBridgeTheme.Success
            else -> LinkBridgeTheme.Outline
        }
    )
    Spacer(Modifier.width(6.dp))
    Text(
        "$label\u202F: ${if (!available) "indisponible" else if (active) "actif" else "inactif"}",
        style = MaterialTheme.typography.labelMedium,
        color = LinkBridgeTheme.Ink
    )
}

@Composable
private fun StatusSeparator() {
    Box(
        Modifier
            .padding(horizontal = 12.dp)
            .size(1.dp, 14.dp)
            .background(LinkBridgeTheme.Outline)
    )
}
