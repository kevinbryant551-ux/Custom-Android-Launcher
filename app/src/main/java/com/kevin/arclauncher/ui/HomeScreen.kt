package com.kevin.arclauncher.ui

import android.appwidget.AppWidgetHostView
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.HOME_COLUMNS
import com.kevin.arclauncher.data.HomeItem
import com.kevin.arclauncher.data.HomePage
import com.kevin.arclauncher.data.LauncherLayout
import com.kevin.arclauncher.data.Placed
import com.kevin.arclauncher.data.cellKey
import com.kevin.arclauncher.data.placeItems
import com.kevin.arclauncher.data.rowsForPage
import com.kevin.arclauncher.widgets.LongPressWidgetView
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Rough height of one grid row in dp, used to size new widgets from their minHeight. */
const val WIDGET_ROW_HEIGHT_DP = 96f

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** What a tile needs to offer "Move to previous / next page". */
private class PageMover(val page: Int, val pageCount: Int, val move: (HomeItem, Int) -> Unit)

/** Callbacks a movable tile reports its drag through, in px. */
private class DragCallbacks(val start: () -> Unit, val move: (Offset) -> Unit, val end: () -> Unit)

@Composable
fun HomeScreen(
    layout: LauncherLayout,
    appsByKey: Map<String, AppEntry>,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    pagerState: PagerState,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenFolder: (String) -> Unit,
    onAddWidget: (page: Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var backgroundMenuAt by remember { mutableStateOf<Offset?>(null) }
    var confirmRemovePage by remember { mutableStateOf<Int?>(null) }
    var dragActive by remember { mutableStateOf(false) }
    // On-screen bounds of every home item (by cellKey), so the background long-press and the
    // swipe detector can tell items apart from empty space.
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }
    fun onItem(at: Offset) = itemBounds.values.any { it.contains(at) }
    fun onWidget(at: Offset) = itemBounds.any { (k, r) -> k.startsWith("widget:") && r.contains(at) }

    val dockApps = remember(layout.dock, appsByKey) { layout.dock.mapNotNull(appsByKey::get) }
    val pageCount = layout.pages.size

    // After "Add page", jump to it once the pager has measured the new page count.
    var scrollToPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(scrollToPage, pageCount) {
        val target = scrollToPage ?: return@LaunchedEffect
        if (target < pageCount) {
            withFrameNanos { }
            pagerState.animateScrollToPage(target)
            scrollToPage = null
        }
    }
    fun addPageAfter(page: Int) {
        viewModel.addPage(page)
        scrollToPage = page + 1
    }

    Box(
        Modifier
            .fillMaxSize()
            .verticalSwipe(
                canSwipeUp = { true },
                canSwipeDown = { true },
                onSwipeUp = { onOpenDrawer(false) },
                onSwipeDown = actions::expandNotifications,
                ignoreAt = ::onWidget,
                cancelIf = { dragActive },
            )
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    if (onItem(it)) return@detectTapGestures
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    backgroundMenuAt = it
                })
            },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                key = { it },
                userScrollEnabled = !dragActive,
            ) { index ->
                HomePageContent(
                    index = index,
                    page = layout.pages.getOrElse(index) { HomePage() },
                    layout = layout,
                    appsByKey = appsByKey,
                    viewModel = viewModel,
                    actions = actions,
                    mover = PageMover(index, pageCount) { item, to -> viewModel.moveToPage(item, index, to) },
                    itemBounds = itemBounds,
                    onDragActive = { dragActive = it },
                    onOpenDrawer = onOpenDrawer,
                    onOpenFolder = onOpenFolder,
                )
            }
            if (pageCount > 1) PageIndicator(pageCount, pagerState.currentPage)
            DrawerHandle(onClick = { onOpenDrawer(false) })
            Dock(dockApps, viewModel, actions)
        }

        backgroundMenuAt?.let { at ->
            val page = pagerState.currentPage
            fun close() { backgroundMenuAt = null }
            Box(Modifier.offset { IntOffset(at.x.toInt(), at.y.toInt()) }.size(1.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = ::close) {
                    DropdownMenuItem(text = { Text("Add widget") }, onClick = { close(); onAddWidget(page) })
                    DropdownMenuItem(text = { Text("Add page") }, onClick = { close(); addPageAfter(page) })
                    if (pageCount > 1) {
                        DropdownMenuItem(text = { Text("Remove this page") }, onClick = {
                            close()
                            if (layout.pages.getOrNull(page)?.items.isNullOrEmpty()) viewModel.removePage(page) else confirmRemovePage = page
                        })
                    }
                    DropdownMenuItem(
                        text = { Text(if (layout.showSearchBar) "Hide search bar" else "Show search bar") },
                        onClick = { close(); viewModel.setSearchBarVisible(!layout.showSearchBar) },
                    )
                    DropdownMenuItem(text = { Text("Wallpaper") }, onClick = { close(); actions.openWallpaperPicker() })
                    DropdownMenuItem(text = { Text("All apps") }, onClick = { close(); onOpenDrawer(false) })
                }
            }
        }
    }

    confirmRemovePage?.let { page ->
        AlertDialog(
            onDismissRequest = { confirmRemovePage = null },
            title = { Text("Remove this page?") },
            text = { Text("Its apps and folders stay in the app drawer. Widgets on it are removed.") },
            confirmButton = { TextButton(onClick = { confirmRemovePage = null; viewModel.removePage(page) }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemovePage = null }) { Text("Cancel") } },
        )
    }
}

/** The item being dragged on a page: where it started and how far the finger has moved (px). */
private data class Drag(val placed: Placed, val offset: Offset)

@Composable
private fun HomePageContent(
    index: Int,
    page: HomePage,
    layout: LauncherLayout,
    appsByKey: Map<String, AppEntry>,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    mover: PageMover,
    itemBounds: SnapshotStateMap<String, Rect>,
    onDragActive: (Boolean) -> Unit,
    onOpenDrawer: (Boolean) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val rows = rowsForPage(index)
    // Hide items whose app is gone (uninstalled, or a paused work profile) without freeing their cell.
    val placed = remember(page, layout.folders, appsByKey) {
        placeItems(page.items, page.cells, HOME_COLUMNS, rows).first.filter { p ->
            when (val item = p.item) {
                is HomeItem.App -> item.key in appsByKey
                is HomeItem.FolderRef -> layout.folders[item.id]?.apps?.any { it in appsByKey } == true
                is HomeItem.Widget -> true
            }
        }
    }
    var drag by remember { mutableStateOf<Drag?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (index == 0) {
            ClockGlance(onClick = actions::openClock)
            if (layout.showSearchBar) {
                SearchPill(onClick = { onOpenDrawer(true) }, onRemove = { viewModel.setSearchBarVisible(false) })
            }
            Spacer(Modifier.height(12.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val cellW = maxWidth / HOME_COLUMNS
            val cellH = maxHeight / rows
            val density = LocalDensity.current
            val cellWpx = with(density) { cellW.toPx() }
            val cellHpx = with(density) { cellH.toPx() }

            fun targetOf(d: Drag): Pair<Int, Int> {
                val x = ((d.placed.x * cellWpx + d.offset.x) / cellWpx).roundToInt().coerceIn(0, HOME_COLUMNS - d.placed.w)
                val y = ((d.placed.y * cellHpx + d.offset.y) / cellHpx).roundToInt().coerceIn(0, rows - d.placed.h)
                return x to y
            }

            fun drop(d: Drag) {
                val (tx, ty) = targetOf(d)
                if (tx == d.placed.x && ty == d.placed.y) return
                val dragged = d.placed.item
                val occupant = placed.firstOrNull { it.item != dragged && it.covers(tx, ty) }?.item
                when {
                    dragged is HomeItem.App && occupant is HomeItem.App && d.placed.w == 1 ->
                        viewModel.dropOnApp(index, occupant, dragged)
                    dragged is HomeItem.App && occupant is HomeItem.FolderRef ->
                        viewModel.addToFolder(occupant.id, dragged.key, index)
                    !viewModel.moveItem(index, dragged, tx, ty) -> actions.toast("No room there")
                }
            }

            fun callbacksFor(p: Placed) = DragCallbacks(
                start = { drag = Drag(p, Offset.Zero); onDragActive(true) },
                move = { delta -> drag = drag?.let { it.copy(offset = it.offset + delta) } },
                end = {
                    drag?.let(::drop)
                    drag = null
                    onDragActive(false)
                },
            )

            if (placed.isEmpty()) {
                Text(
                    if (index == 0) "Swipe up for all apps.\nLong-press apps there to add them here."
                    else "Empty page.\nLong-press here to add a widget.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.75f), shadow = TextShadow),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            placed.forEach { p ->
                key(p.item.cellKey) {
                    val itemKey = p.item.cellKey
                    DisposableEffect(itemKey) { onDispose { itemBounds.remove(itemKey) } }
                    Box(
                        Modifier
                            .offset(cellW * p.x, cellH * p.y)
                            .size(cellW * p.w, cellH * p.h)
                            .onGloballyPositioned { itemBounds[itemKey] = it.boundsInRoot() }
                            .alpha(if (drag?.placed?.item == p.item) 0.25f else 1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val callbacks = callbacksFor(p)
                        when (val item = p.item) {
                            is HomeItem.App -> appsByKey[item.key]?.let { HomeAppTile(item, it, viewModel, actions, mover, callbacks) }
                            is HomeItem.FolderRef -> layout.folders[item.id]?.let { folder ->
                                HomeFolderTile(item, folder.name, folder.apps.mapNotNull(appsByKey::get), viewModel, mover, callbacks) { onOpenFolder(folder.id) }
                            }
                            is HomeItem.Widget -> HomeWidget(item, rows, viewModel, mover, callbacks)
                        }
                    }
                }
            }

            drag?.let { d ->
                val (tx, ty) = targetOf(d)
                // Drop target outline.
                Box(
                    Modifier
                        .offset(cellW * tx, cellH * ty)
                        .size(cellW * d.placed.w, cellH * d.placed.h)
                        .padding(4.dp)
                        .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
                )
                // The item under the finger.
                Box(
                    Modifier
                        .offset { IntOffset((d.placed.x * cellWpx + d.offset.x).roundToInt(), (d.placed.y * cellHpx + d.offset.y).roundToInt()) }
                        .size(cellW * d.placed.w, cellH * d.placed.h)
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
                    contentAlignment = Alignment.Center,
                ) {
                    when (val item = d.placed.item) {
                        is HomeItem.App -> appsByKey[item.key]?.let { AppIconImage(it) }
                        is HomeItem.FolderRef -> layout.folders[item.id]?.let { f -> FolderIcon(f.apps.mapNotNull(appsByKey::get)) }
                        is HomeItem.Widget -> Box(
                            Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.25f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveMenuItems(item: HomeItem, mover: PageMover, close: () -> Unit) {
    if (mover.page > 0) {
        DropdownMenuItem(text = { Text("Move to previous page") }, onClick = { close(); mover.move(item, mover.page - 1) })
    }
    if (mover.page < mover.pageCount - 1) {
        DropdownMenuItem(text = { Text("Move to next page") }, onClick = { close(); mover.move(item, mover.page + 1) })
    }
}

@Composable
private fun HomeAppTile(
    item: HomeItem.App,
    app: AppEntry,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    mover: PageMover,
    drag: DragCallbacks,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    AppTile(
        label = app.label,
        onClick = { actions.launch(app) },
        onLongClick = { menu = true },
        icon = { AppIconImage(app) },
        gestures = Modifier.homeItemGestures(
            label = app.label,
            onTap = { actions.launch(app) },
            onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
            onDragStart = { menu = false; drag.start() },
            onDrag = drag.move,
            onDragEnd = drag.end,
        ),
        menu = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove from home") }, onClick = { menu = false; viewModel.removeFromHome(item) })
                DropdownMenuItem(text = { Text("Move to dock") }, onClick = {
                    menu = false
                    if (!viewModel.moveToDock(item)) actions.toast("Dock is full")
                })
                MoveMenuItems(item, mover) { menu = false }
                DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; actions.appInfo(listOf(app)) })
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; actions.uninstall(listOf(app)) })
            }
        },
    )
}

@Composable
private fun HomeFolderTile(
    item: HomeItem.FolderRef,
    name: String,
    apps: List<AppEntry>,
    viewModel: LauncherViewModel,
    mover: PageMover,
    drag: DragCallbacks,
    onOpen: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    AppTile(
        label = name,
        onClick = onOpen,
        onLongClick = { menu = true },
        icon = { FolderIcon(apps) },
        gestures = Modifier.homeItemGestures(
            label = name,
            onTap = onOpen,
            onLongPress = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
            onDragStart = { menu = false; drag.start() },
            onDrag = drag.move,
            onDragEnd = drag.end,
        ),
        menu = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open / rename") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Ungroup") }, onClick = { menu = false; viewModel.ungroup(item.id) })
                MoveMenuItems(item, mover) { menu = false }
                DropdownMenuItem(text = { Text("Remove from home") }, onClick = { menu = false; viewModel.removeFromHome(item) })
            }
        },
    )
}

/** Tells the widget its real size in dp so it can pick the right layout. */
private fun AppWidgetHostView.reportSizeDp(width: Float, height: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(width, height)))
    } else {
        @Suppress("DEPRECATION")
        updateAppWidgetSize(null, width.toInt(), height.toInt(), width.toInt(), height.toInt())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeWidget(
    widget: HomeItem.Widget,
    rows: Int,
    viewModel: LauncherViewModel,
    mover: PageMover,
    drag: DragCallbacks,
) {
    val info = remember(widget.id) { viewModel.appWidgetInfo(widget.id) }
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }
    var hostView by remember { mutableStateOf<LongPressWidgetView?>(null) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(hostView, sizePx) {
        val view = hostView ?: return@LaunchedEffect
        if (sizePx != IntSize.Zero) with(density) { view.reportSizeDp(sizePx.width.toDp().value, sizePx.height.toDp().value) }
    }

    Box(Modifier.fillMaxSize().padding(4.dp).onGloballyPositioned { sizePx = it.size }) {
        if (info == null) {
            // Provider uninstalled or its profile is paused.
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.35f))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
                contentAlignment = Alignment.Center,
            ) { Text("Widget unavailable", color = Color.White) }
        } else {
            AndroidView(
                factory = { context ->
                    (viewModel.widgetHost.createView(context, widget.id, info) as LongPressWidgetView).also { hostView = it }
                },
                update = { view ->
                    view.onLongPress = { menu = true }
                    view.onDragStart = { menu = false; drag.start() }
                    view.onDrag = { dx, dy -> drag.move(Offset(dx, dy)) }
                    view.onDragEnd = drag.end
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Resize") }, onClick = { menu = false; resizing = true })
            MoveMenuItems(widget, mover) { menu = false }
            DropdownMenuItem(text = { Text("Remove widget") }, onClick = { menu = false; viewModel.removeWidget(widget.id) })
        }
    }

    if (resizing) {
        ResizeDialog(
            spanX = widget.spanX,
            spanY = widget.spanY.coerceAtMost(rows),
            maxY = rows,
            onDismiss = { resizing = false },
            onConfirm = { x, y -> resizing = false; viewModel.resizeWidget(widget.id, x, y) },
        )
    }
}

@Composable
private fun ResizeDialog(spanX: Int, spanY: Int, maxY: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var x by remember { mutableStateOf(spanX) }
    var y by remember { mutableStateOf(spanY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize widget") },
        text = {
            Column {
                Stepper("Width", x, 1..HOME_COLUMNS) { x = it }
                Stepper("Height", y, 1..maxY) { y = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(x, y) }) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−", fontSize = 22.sp) }
        Text("$value", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        TextButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(if (i == current) 8.dp else 6.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.45f)),
            )
        }
    }
}

@Composable
private fun ClockGlance(onClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Tick on each minute boundary, only while the launcher is visible.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }
    val dateTime = remember(now) { LocalDateTime.now() }
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    val time = dateTime.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm"))
    val date = dateTime.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    Column(
        Modifier.padding(top = 24.dp, start = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
    ) {
        Text(time, style = MaterialTheme.typography.displayLarge.copy(color = Color.White, fontWeight = FontWeight.Light, shadow = TextShadow))
        Text(date, style = MaterialTheme.typography.titleMedium.copy(color = Color.White, shadow = TextShadow))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPill(onClick: () -> Unit, onRemove: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .combinedClickable(onClick = onClick, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(12.dp))
            Text("Search apps", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Remove search bar") }, onClick = { menu = false; onRemove() })
        }
    }
}

@Composable
private fun DrawerHandle(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "All apps",
            tint = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 2.dp).size(28.dp),
        )
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, viewModel: LauncherViewModel, actions: LauncherActions) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            Text("Dock: add apps from the drawer", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(20.dp))
        }
        apps.forEach { app ->
            var menu by remember { mutableStateOf(false) }
            AppTile(
                label = app.label,
                showLabel = false,
                onClick = { actions.launch(app) },
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                icon = { AppIconImage(app) },
                modifier = Modifier.weight(1f),
                menu = {
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Remove from dock") }, onClick = { menu = false; viewModel.removeFromDock(app.key) })
                        DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; actions.appInfo(listOf(app)) })
                    }
                },
            )
        }
    }
}
