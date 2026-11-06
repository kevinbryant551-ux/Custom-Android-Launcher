package com.kevin.arclauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.IconLoader
import kotlin.math.abs

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader not provided") }

val IconSize = 52.dp

private val LabelShadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1.5f), 6f)

@Composable
fun AppIconImage(app: AppEntry, size: Dp = IconSize, modifier: Modifier = Modifier) {
    val loader = LocalIconLoader.current
    val bitmap by produceState(loader.cached(app.key), app.key) { value = loader.load(app) }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/** Icon + one-line label. Long-press is used for menus and selection mode. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    dimmed: Boolean = false,
    icon: @Composable () -> Unit,
    badge: @Composable BoxScope.() -> Unit = {},
    menu: @Composable () -> Unit = {},
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Options")
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.alpha(if (dimmed) 0.45f else 1f)) {
                icon()
                badge()
            }
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.White, shadow = LabelShadow),
                    modifier = Modifier.padding(horizontal = 4.dp).alpha(if (dimmed) 0.45f else 1f),
                )
            }
        }
        menu()
    }
}

/** Round check mark drawn over an icon's top-right corner in selection mode. */
@Composable
fun BoxScope.SelectionCheck(checked: Boolean) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = 6.dp, y = (-6).dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
    }
}

/** A folder: rounded tile with up to four mini icons in a 2x2 grid. */
@Composable
fun FolderIcon(apps: List<AppEntry>, size: Dp = IconSize) {
    val mini = (size - 14.dp) / 2
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { AppIconImage(it, mini) }
                    if (row.size == 1) Spacer(Modifier.size(mini))
                }
            }
        }
    }
}

/**
 * Detects a mostly-vertical fling over this area without stealing touches from children.
 * It watches the Initial pointer pass (before children see events) and never consumes them.
 * [canSwipeUp]/[canSwipeDown] are checked when the finger first goes down, e.g. "list is at its edge".
 */
fun Modifier.verticalSwipe(
    canSwipeUp: () -> Boolean = { false },
    canSwipeDown: () -> Boolean = { false },
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
): Modifier = pointerInput(Unit) {
    val threshold = 72.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val upAllowed = canSwipeUp()
        val downAllowed = canSwipeDown()
        var last = down.position
        var multiTouch = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size > 1) multiTouch = true
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            last = change.position
            if (!change.pressed) break
        }
        val dx = last.x - down.position.x
        val dy = last.y - down.position.y
        if (multiTouch || abs(dy) < threshold || abs(dy) < abs(dx) * 1.5f) return@awaitEachGesture
        if (dy < 0 && upAllowed) onSwipeUp()
        if (dy > 0 && downAllowed) onSwipeDown()
    }
}
