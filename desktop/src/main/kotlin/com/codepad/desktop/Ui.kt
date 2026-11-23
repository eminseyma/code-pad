package com.codepad.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tüm pencere/alan köşeleri: Android'deki yumuşak, yuvarlak görünüm. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private enum class BtnKind { Primary, Soft, Ghost }

@Composable
private fun BaseBtn(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    kind: BtnKind,
    danger: Boolean,
    compact: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val accent = if (danger) cs.error else cs.primary

    val bgT: Color
    val fgT: Color
    val bdT: Color
    when {
        !enabled -> {
            bgT = if (kind == BtnKind.Primary) cs.onSurface.copy(alpha = 0.10f) else Color.Transparent
            fgT = cs.onSurface.copy(alpha = 0.38f)
            bdT = if (kind == BtnKind.Soft) cs.onSurface.copy(alpha = 0.10f) else Color.Transparent
        }
        kind == BtnKind.Primary -> {
            bgT = when {
                pressed -> lerp(accent, Color.Black, 0.14f)
                hovered -> lerp(accent, Color.White, 0.14f)
                else -> accent
            }
            fgT = if (danger) cs.onError else cs.onPrimary
            bdT = Color.Transparent
        }
        kind == BtnKind.Soft -> {
            bgT = accent.copy(alpha = if (pressed) 0.24f else if (hovered) 0.16f else 0.07f)
            fgT = accent
            bdT = accent.copy(alpha = if (hovered || pressed) 0.55f else 0.28f)
        }
        else -> {
            bgT = accent.copy(alpha = if (pressed) 0.18f else if (hovered) 0.10f else 0f)
            fgT = accent
            bdT = Color.Transparent
        }
    }
    val bg by animateColorAsState(bgT, tween(140))
    val fg by animateColorAsState(fgT, tween(140))
    val bd by animateColorAsState(bdT, tween(140))
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(90))

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .then(if (bd.alpha > 0f) Modifier.border(1.dp, bd, shape) else Modifier)
            .hoverable(src)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = if (compact) 32.dp else 40.dp)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 12.5.sp else 14.sp,
                ),
            ) { content() }
        }
    }
}

/** Dolu ana düğme. */
@Composable
fun PButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    danger: Boolean = false,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = BaseBtn(onClick, modifier, enabled, shape, BtnKind.Primary, danger, compact, content)

/** Yumuşak çerçeveli ikincil düğme. */
@Composable
fun SButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    danger: Boolean = false,
    compact: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) = BaseBtn(onClick, modifier, enabled, shape, BtnKind.Soft, danger, compact, content)

/** Çerçevesiz, üzerine gelince yanan düğme. */
@Composable
fun GButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    danger: Boolean = false,
    compact: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = BaseBtn(onClick, modifier, enabled, shape, BtnKind.Ghost, danger, compact, content)

/** Yuvarlak simge düğmesi; isteğe bağlı ipucu (tooltip) gösterir. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    danger: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    val accent = if (danger) cs.error else cs.primary
    val bg by animateColorAsState(accent.copy(alpha = if (pressed) 0.22f else if (hovered) 0.12f else 0f), tween(120))
    val fg by animateColorAsState(if (hovered) accent else cs.onSurfaceVariant, tween(120))
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(90))

    val inner: @Composable () -> Unit = {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(38.dp)
                .clip(CircleShape)
                .background(bg)
                .hoverable(src)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides fg) { content() }
        }
    }
    if (tooltip != null) {
        TooltipArea(
            tooltip = {
                Surface(shape = RoundedCornerShape(8.dp), color = cs.inverseSurface, shadowElevation = 4.dp) {
                    Text(
                        tooltip,
                        color = cs.inverseOnSurface,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            },
            modifier = modifier,
            delayMillis = 450,
            tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 18.dp)),
        ) { inner() }
    } else {
        Box(modifier) { inner() }
    }
}

/** Üzerine gelince hafifçe büyüyen kutu efekti. */
@Composable
fun Modifier.hoverScale(src: MutableInteractionSource, amount: Float = 1.025f): Modifier {
    val h by src.collectIsHoveredAsState()
    val s by animateFloatAsState(if (h) amount else 1f, tween(140))
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/** Liste kenarında ince, yumuşak kaydırma çubuğu. */
@Composable
fun ListScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = modifier.fillMaxHeight(),
        style = defaultScrollbarStyle().copy(
            thickness = 6.dp,
            shape = RoundedCornerShape(3.dp),
            unhoverColor = muted.copy(alpha = 0.25f),
            hoverColor = muted.copy(alpha = 0.55f),
        ),
    )
}

/** Boş ekranlar için ortalanmış, sıcak bir bilgi alanı. */
@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier, symbol: String = "</>") {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(cs.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = cs.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 13.sp, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** Not silinince altta çıkan "Geri al" çubuğu (6 sn sonra kaybolur). */
@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val id = Store.lastDeleted
    LaunchedEffect(id) {
        if (id != null) {
            delay(6_000)
            if (Store.lastDeleted == id) Store.lastDeleted = null
        }
    }
    AnimatedVisibility(
        visible = id != null,
        modifier = modifier.padding(20.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(cs.inverseSurface)
                .padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Not silindi", color = cs.inverseOnSurface, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            GButton(onClick = { id?.let { Store.restoreNote(it) } }) {
                Text("GERİ AL", color = cs.inversePrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
