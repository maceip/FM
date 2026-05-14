package com.google.research.air.cosmo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun CosmoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    frosted: Boolean = false
) {
    val tokens = MaterialTheme.cosmo
    val onSurface = MaterialTheme.colorScheme.onSurface
    val frostedMod = if (frosted) Modifier.cosmoFrosted() else Modifier
    Surface(
        color = if (frosted) Color.Transparent else tokens.color.surface,
        contentColor = onSurface,
        modifier = modifier.fillMaxWidth().then(frostedMod)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.chromeHorizontal, vertical = tokens.spacing.chromeVertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chromeSmallGap)
            ) {
                CosmoMarqueeText(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
                val liveColor = CosmoColor.PaletteRef(
                    role = PaletteRole.Primary,
                    fallback = tokens.color.orangeHot
                ).resolve()
                CosmoSignalTag(
                    text = "LIVE",
                    containerColor = liveColor,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    rotationDegrees = 6f
                )
            }
            DotRow(
                color = onSurface,
                modifier = Modifier.fillMaxWidth().height(tokens.spacing.navRailDotStrip)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(tokens.stroke.heavy)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
fun CosmoBottomBar(
    items: List<CosmoBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false
) {
    val tokens = MaterialTheme.cosmo
    val surface = MaterialTheme.colorScheme.secondaryContainer
    val onSurface = MaterialTheme.colorScheme.onSecondaryContainer
    val onSurfaceDim = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
    val accent = MaterialTheme.colorScheme.tertiaryContainer
    val frostedMod = if (frosted) Modifier.cosmoFrosted() else Modifier
    Surface(
        color = if (frosted) Color.Transparent else surface,
        contentColor = onSurface,
        modifier = modifier.fillMaxWidth().then(frostedMod),
        border = BorderStroke(tokens.stroke.heavy, tokens.color.ink)
    ) {
        Column(Modifier.fillMaxWidth()) {
            DotRow(
                color = tokens.color.ink,
                modifier = Modifier.fillMaxWidth().height(tokens.spacing.navRailDotStrip)
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = tokens.spacing.bottomBarMinHeight),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    NavCell(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                        activeColor = accent,
                        idleColor = onSurfaceDim,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun CosmoNavRail(
    items: List<CosmoBarItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    frosted: Boolean = false
) {
    val tokens = MaterialTheme.cosmo
    val surface = tokens.color.surfaceHighest
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceDim = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.tertiaryContainer
    val frostedMod = if (frosted) Modifier.cosmoFrosted() else Modifier
    Surface(
        color = if (frosted) Color.Transparent else surface,
        contentColor = onSurface,
        modifier = modifier.then(frostedMod),
        border = BorderStroke(tokens.stroke.heavy, MaterialTheme.colorScheme.outline)
    ) {
        Row {
            DotColumn(
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxHeight().width(tokens.spacing.navRailDotStrip)
            )
            Column(
                Modifier.width(tokens.spacing.railWidth).padding(vertical = tokens.spacing.railVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.chromeSmallGap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEachIndexed { index, item ->
                    NavCell(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onSelected(index) },
                        activeColor = accent,
                        idleColor = onSurfaceDim,
                        modifier = Modifier.fillMaxWidth().heightIn(min = tokens.spacing.navRailCellMinHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavCell(
    item: CosmoBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    activeColor: Color,
    idleColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.cosmo
    val selectedContent = MaterialTheme.colorScheme.onTertiaryContainer
    val tint = if (selected) selectedContent else idleColor
    val interaction = remember { MutableInteractionSource() }
    val cellModifier =
        modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = tokens.spacing.navCellVerticalPadding)
    if (selected) {
        CosmoStickerSurface(
            modifier = cellModifier.padding(horizontal = tokens.spacing.chromeSmallGap),
            containerColor = activeColor,
            contentColor = selectedContent,
            shape = RoundedCornerShape(tokens.shape.pill),
            borderWidth = tokens.stroke.medium,
            shadowOffset = tokens.decal.tagShadowOffset,
            rotationDegrees = -2f
        ) {
            NavCellContent(item = item, tint = tint, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp))
        }
    } else {
        NavCellContent(item = item, tint = tint, modifier = cellModifier)
    }
}

@Composable
private fun NavCellContent(
    item: CosmoBarItem,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val tokens = MaterialTheme.cosmo
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.chromeSmallGap)
    ) {
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(tokens.spacing.navIconSize))
        Text(
            text = item.label.uppercase(),
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DotRow(
    color: Color,
    modifier: Modifier = Modifier,
    dotCount: Int? = null
) {
    Canvas(modifier) {
        val radius = size.height / 2f
        val step = radius * 3f
        val count = dotCount ?: ((size.width / step).toInt().coerceAtLeast(1))
        val totalSpan = step * (count - 1)
        val startX = (size.width - totalSpan) / 2f
        val y = size.height / 2f
        for (i in 0 until count) {
            drawCircle(color = color, radius = radius, center = Offset(startX + step * i, y))
        }
    }
}

@Composable
private fun DotColumn(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val radius = size.width / 2f
        val step = radius * 3f
        val count = (size.height / step).toInt().coerceAtLeast(1)
        val totalSpan = step * (count - 1)
        val startY = (size.height - totalSpan) / 2f
        val x = size.width / 2f
        for (i in 0 until count) {
            drawCircle(color = color, radius = radius, center = Offset(x, startY + step * i))
        }
    }
}

data class CosmoBarItem(
    val label: String,
    val icon: ImageVector
)
