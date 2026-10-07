package com.linkbridge.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Ligne d'état d'une carte : rond coloré puis texte, jamais comprimée par un bouton. */
@Composable
fun StatusLine(active: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (active) LinkBridgeTheme.Success else LinkBridgeTheme.Outline)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Petit rond d'état coloré, utilisé dans la barre d'état et les en-têtes de carte. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** Carte de section : titre, sous-titre optionnel, action facultative à droite. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    container: Color = LinkBridgeTheme.Surface,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, LinkBridgeTheme.OutlineSoft),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(DesktopMetrics.CardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkBridgeTheme.OnSurfaceMuted
                        )
                    }
                }
                if (trailing != null) trailing()
            }
            content()
        }
    }
}

/** Champ compact, aligné sur la densité d'un logiciel de bureau. */
@Composable
fun DesktopTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.defaultMinSize(minHeight = DesktopMetrics.FieldHeight),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        supportingText = if (supporting == null) null else {
            { Text(supporting, style = MaterialTheme.typography.labelSmall) }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp)
    )
}

/** Bouton principal, hauteur fixe pour que toutes les rangées d'actions s'alignent. */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = LinkBridgeTheme.Purple
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesktopMetrics.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Color.White)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesktopMetrics.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Zone de journal : fond Ink, texte Mint, barre de défilement toujours visible. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogView(
    lines: List<String>,
    emptyText: String,
    modifier: Modifier = Modifier,
    height: Dp = DesktopMetrics.LogHeight
) {
    val scroll = rememberScrollState()
    val adapter = rememberScrollbarAdapter(scroll)

    // Le journal suit la dernière ligne écrite.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(LinkBridgeTheme.Ink)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .verticalScroll(scroll)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 22.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (lines.isEmpty()) {
                Text(
                    emptyText,
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            lines.forEach { line ->
                Text(line, color = LinkBridgeTheme.Mint, style = MaterialTheme.typography.bodySmall)
            }
        }
        VerticalScrollbar(
            adapter = adapter,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

/** Deux colonnes au-dessus du seuil de 900 dp, une seule en dessous. */
@Composable
fun TwoColumn(
    wide: Boolean,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DesktopMetrics.Gap)) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DesktopMetrics.Gap),
                content = left
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DesktopMetrics.Gap),
                content = right
            )
        }
    } else {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DesktopMetrics.Gap)
        ) {
            left()
            right()
        }
    }
}

/** Infobulle au survol, locale et sans ressource externe. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoverTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled || text.isBlank()) {
        content()
        return
    }
    TooltipArea(
        tooltip = {
            Surface(
                color = LinkBridgeTheme.Ink,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        },
        modifier = modifier,
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp))
    ) {
        content()
    }
}

/** Séparateur horizontal discret. */
@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = LinkBridgeTheme.OutlineSoft)
}

/** Texte sur une seule ligne, tronqué avec des points de suspension. */
@Composable
fun SingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle? = null,
    weight: FontWeight? = null
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style ?: MaterialTheme.typography.bodyMedium,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Curseur main sur un élément cliquable non Material. */
fun Modifier.handCursor(): Modifier = this.pointerHoverIcon(PointerIcon.Hand)

/**
 * Texte tronqué sur une seule ligne. L'infobulle n'apparaît que si le texte a réellement
 * été coupé, ce qui évite un décalage quand le nom tient dans la largeur.
 */
@Composable
fun TruncatedTextWithTooltip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle? = null,
    weight: FontWeight? = null
) {
    var truncated by remember { mutableStateOf(false) }
    HoverTooltip(text = if (truncated) text else "", modifier = modifier) {
        Text(
            text = text,
            color = color,
            style = style ?: MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result -> truncated = result.hasVisualOverflow }
        )
    }
}
