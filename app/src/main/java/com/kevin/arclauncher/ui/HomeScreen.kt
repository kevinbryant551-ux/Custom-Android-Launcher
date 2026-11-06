package com.kevin.arclauncher.ui

import android.appwidget.AppWidgetHostView
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import com.kevin.arclauncher.data.Folder
import com.kevin.arclauncher.data.HomeItem
import com.kevin.arclauncher.data.HomePage
import com.kevin.arclauncher.data.LauncherLayout
import com.kevin.arclauncher.data.MAX_WIDGET_SPAN_Y
import com.kevin.arclauncher.widgets.LongPressWidgetView
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val HOME_COLUMNS = 4

/** Height of one widget row on the home grid, in dp (about one app tile). */
const val WIDGET_ROW_HEIGHT_DP = 96f

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** A home grid cell, resolved against the installed apps. */
private sealed interface HomeCell {
    val item: HomeItem
    data class App(override val item: HomeItem.App, val app: AppEntry) : HomeCell
    data class FolderCell(override val item: HomeItem.FolderRef, val folder: Folder, val apps: List<AppEntry>) : HomeCell
    data class Widget(override val item: HomeItem.Widget) : HomeCell
}

/** What a tile needs to offer "Move to previous / next page". */
private class PageMover(val page: Int, val pageCount: Int, val move: (HomeItem, Int) -> Unit)

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
    val gridStates = remember { mutableStateMapOf<Int, LazyGridState>() }
    // Where widgets are on screen, so our swipe and long-press detectors leave them alone.
    val widgetBounds = remember { mutableStateMapOf<Int, Rect>() }
    fun onWidget(at: Offset) = widgetBounds.values.any { it.contains(at) }

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
                canSwipeUp = { gridStates[pagerState.currentPage]?.canScrollForward != true },
                canSwipeDown = { gridStates[pagerState.currentPage]?.canScrollBackward != true },
                onSwipeUp = { onOpenDrawer(false) },
                onSwipeDown = actions::expandNotifications,
                ignoreAt = ::onWidget,
            )
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    if (onWidget(it)) return@detectTapGestures
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
            ) { index ->
                val page = layout.pages.getOrElse(index) { HomePage() }
                HomePageContent(
                    index = index,
                    page = page,
                    layout = layout,
                    appsByKey = appsByKey,
                    viewModel = viewModel,
                    actions = actions,
                    mover = PageMover(index, pageCount) { item, to -> viewModel.moveToPage(item, index, to) },
                    onGridState = { gridStates[index] = it },
                    onWidgetBounds = { id, rect -> if (rect == null) widgetBounds.remove(id) else widgetBounds[id] = rect },
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

@Composable
private fun HomePageContent(
    index: Int,
    page: HomePage,
    layout: LauncherLayout,
    appsByKey: Map<String, AppEntry>,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    mover: PageMover,
    onGridState: (LazyGridState) -> Unit,
    onWidgetBounds: (Int, Rect?) -> Unit,
    onOpenDrawer: (Boolean) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    SideEffect { onGridState(gridState) }

    // Missing apps (uninstalled, or a paused work profile) are skipped rather than shown blank.
    val cells = remember(page, layout.folders, appsByKey) {
        page.items.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> appsByKey[item.key]?.let { HomeCell.App(item, it) }
                is HomeItem.FolderRef -> layout.folders[item.id]?.let { f ->
                    f.apps.mapNotNull(appsByKey::get).takeIf { it.isNotEmpty() }?.let { HomeCell.FolderCell(item, f, it) }
                }
                is HomeItem.Widget -> HomeCell.Widget(item)
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        if (index == 0) {
            ClockGlance(onClick = actions::openClock)
            if (layout.showSearchBar) {
                SearchPill(onClick = { onOpenDrawer(true) }, onRemove = { viewModel.setSearchBarVisible(false) })
            }
            Spacer(Modifier.height(16.dp))
        }
        if (cells.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (index == 0) "Swipe up for all apps.\nLong-press apps there to add them here."
                    else "Empty page.\nLong-press here to add a widget.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.75f), shadow = TextShadow),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(HOME_COLUMNS),
                state = gridState,
                modifier = Modifier.weight(1f),
            ) {
                items(
                    cells,
                    key = { it.item.toString() },
                    span = { GridItemSpan(if (it is HomeCell.Widget) it.item.spanX.coerceAtMost(maxLineSpan) else 1) },
                ) { cell ->
                    when (cell) {
                        is HomeCell.App -> HomeAppTile(cell, viewModel, actions, mover)
                        is HomeCell.FolderCell -> HomeFolderTile(cell, viewModel, mover, onOpen = { onOpenFolder(cell.folder.id) })
                        is HomeCell.Widget -> HomeWidget(cell.item, viewModel, mover, onWidgetBounds)
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
private fun HomeAppTile(cell: HomeCell.App, viewModel: LauncherViewModel, actions: LauncherActions, mover: PageMover) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    AppTile(
        label = cell.app.label,
        onClick = { actions.launch(cell.app) },
        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
        icon = { AppIconImage(cell.app) },
        menu = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Remove from home") }, onClick = { menu = false; viewModel.removeFromHome(cell.item) })
                DropdownMenuItem(text = { Text("Move to dock") }, onClick = {
                    menu = false
                    if (!viewModel.moveToDock(cell.item)) actions.toast("Dock is full")
                })
                MoveMenuItems(cell.item, mover) { menu = false }
                DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; actions.appInfo(listOf(cell.app)) })
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; actions.uninstall(listOf(cell.app)) })
            }
        },
    )
}

@Composable
private fun HomeFolderTile(cell: HomeCell.FolderCell, viewModel: LauncherViewModel, mover: PageMover, onOpen: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    AppTile(
        label = cell.folder.name,
        onClick = onOpen,
        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
        icon = { FolderIcon(cell.apps) },
        menu = {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open / rename") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("Ungroup") }, onClick = { menu = false; viewModel.ungroup(cell.folder.id) })
                MoveMenuItems(cell.item, mover) { menu = false }
                DropdownMenuItem(text = { Text("Remove from home") }, onClick = { menu = false; viewModel.removeFromHome(cell.item) })
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
    viewModel: LauncherViewModel,
    mover: PageMover,
    onBounds: (Int, Rect?) -> Unit,
) {
    val info = remember(widget.id) { viewModel.appWidgetInfo(widget.id) }
    val density = LocalDensity.current
    var menu by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }
    var hostView by remember { mutableStateOf<LongPressWidgetView?>(null) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(widget.id) { onDispose { onBounds(widget.id, null) } }
    LaunchedEffect(hostView, sizePx) {
        val view = hostView ?: return@LaunchedEffect
        if (sizePx != IntSize.Zero) with(density) { view.reportSizeDp(sizePx.width.toDp().value, sizePx.height.toDp().value) }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height((WIDGET_ROW_HEIGHT_DP * widget.spanY).dp)
            .padding(4.dp)
            .onGloballyPositioned {
                onBounds(widget.id, it.boundsInRoot())
                sizePx = it.size
            },
    ) {
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
                update = { it.onLongPress = { menu = true } },
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
            spanY = widget.spanY,
            onDismiss = { resizing = false },
            onConfirm = { x, y -> resizing = false; viewModel.resizeWidget(widget.id, x, y) },
        )
    }
}

@Composable
private fun ResizeDialog(spanX: Int, spanY: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var x by remember { mutableStateOf(spanX) }
    var y by remember { mutableStateOf(spanY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize widget") },
        text = {
            Column {
                Stepper("Width", x, 1..HOME_COLUMNS) { x = it }
                Stepper("Height", y, 1..MAX_WIDGET_SPAN_Y) { y = it }
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
