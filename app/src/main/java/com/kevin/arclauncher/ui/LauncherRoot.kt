package com.kevin.arclauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.HOME_COLUMNS
import com.kevin.arclauncher.data.HomeItem
import com.kevin.arclauncher.data.placeItems
import com.kevin.arclauncher.data.rowsFor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** An app being dragged out of the drawer; [position] is the finger, in root coordinates. */
private class DrawerDrag(val app: AppEntry, position: Offset) {
    var position by mutableStateOf(position)
}

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, actions: LauncherActions) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetPickerPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var askDefault by remember { mutableStateOf(false) }
    var drawerDrag by remember { mutableStateOf<DrawerDrag?>(null) }
    // Stays true from the start of a drag-out until the drawer is next opened, so the closing
    // drawer never flashes back into view after the drop.
    var drawerGhosted by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = layout.homePage) { layout.pages.size }
    val dropTargets = remember { HomeDropTargets() }
    val density = LocalDensity.current

    // Once per launch, offer to become the default Home app if we aren't.
    LaunchedEffect(Unit) {
        if (!viewModel.askedToBeDefault) {
            viewModel.askedToBeDefault = true
            askDefault = !actions.isDefaultHome()
        }
    }

    // Home pressed while we're in front: back to a clean home screen.
    LaunchedEffect(Unit) {
        viewModel.homePresses.collect {
            val wasClean = !drawerOpen && openFolderId == null && widgetPickerPage == null
            drawerOpen = false
            openFolderId = null
            widgetPickerPage = null
            // Home again on a clean screen goes back to the home page.
            if (wasClean) pagerState.animateScrollToPage(viewModel.layout.value.homePage)
        }
    }

    // While dragging an app from the drawer, holding it at the screen edge flips pages.
    LaunchedEffect(drawerDrag) {
        val drag = drawerDrag ?: return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        val width = dropTargets.pageGrids.values.maxOfOrNull { it.right } ?: return@LaunchedEffect
        while (true) {
            delay(650)
            val x = drag.position.x
            val page = pagerState.currentPage
            when {
                x < edge && page > 0 -> pagerState.animateScrollToPage(page - 1)
                x > width - edge && page < pagerState.pageCount - 1 -> pagerState.animateScrollToPage(page + 1)
            }
        }
    }

    /** The grid cell under [at] on the current page, if any. */
    fun cellAt(at: Offset): Triple<Int, Int, Int>? {
        val page = pagerState.currentPage
        val grid = dropTargets.pageGrids[page] ?: return null
        if (!grid.contains(at)) return null
        val rows = layout.rowsFor(page)
        val x = ((at.x - grid.left) / (grid.width / HOME_COLUMNS)).toInt().coerceIn(0, HOME_COLUMNS - 1)
        val y = ((at.y - grid.top) / (grid.height / rows)).toInt().coerceIn(0, rows - 1)
        return Triple(page, x, y)
    }

    fun dropFromDrawer(drag: DrawerDrag) {
        val at = drag.position
        val key = drag.app.key
        if (dropTargets.dock?.contains(at) == true) {
            if (viewModel.addToDock(listOf(key)) == 0) actions.toast("Dock is full")
            return
        }
        val (page, x, y) = cellAt(at) ?: return
        val pg = layout.pages.getOrNull(page) ?: return
        val occupant = placeItems(pg.items, pg.cells, HOME_COLUMNS, layout.rowsFor(page)).first.firstOrNull { it.covers(x, y) }?.item
        when (occupant) {
            is HomeItem.App -> if (occupant.key != key) viewModel.dropOnApp(page, occupant, HomeItem.App(key))
            is HomeItem.FolderRef -> viewModel.addToFolder(occupant.id, key, page)
            else -> viewModel.placeApp(key, page, x, y)
        }
    }

    // Lowest-priority back handler: a launcher has nothing behind it, so back on home does nothing.
    // The drawer and folder register their own handlers later, which take precedence.
    BackHandler { }

    CompositionLocalProvider(LocalIconLoader provides viewModel.iconLoader) {
        // Fade the home layer out under the drawer so only the wallpaper shows through it. While an
        // app is dragged out, the drawer turns invisible (it must stay composed to keep the gesture).
        val showDrawer = drawerOpen && !drawerGhosted
        val homeAlpha by animateFloatAsState(if (showDrawer) 0f else 1f, label = "homeAlpha")
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(homeAlpha)) {
                HomeScreen(
                    layout = layout,
                    appsByKey = appsByKey,
                    viewModel = viewModel,
                    actions = actions,
                    pagerState = pagerState,
                    dropTargets = dropTargets,
                    onOpenDrawer = { search -> focusSearch = search; drawerGhosted = false; drawerOpen = true },
                    onOpenFolder = { openFolderId = it },
                    onAddWidget = { widgetPickerPage = it },
                )
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
                modifier = Modifier.alpha(if (drawerGhosted) 0f else 1f),
            ) {
                AppDrawer(
                    apps = apps,
                    layout = layout,
                    viewModel = viewModel,
                    actions = actions,
                    focusSearch = focusSearch,
                    currentPage = pagerState.currentPage,
                    onClose = { drawerOpen = false },
                    onDragOut = { app, at -> drawerDrag = DrawerDrag(app, at); drawerGhosted = true },
                    onDragOutMove = { delta -> drawerDrag?.let { it.position += delta } },
                    onDragOutEnd = {
                        drawerDrag?.let(::dropFromDrawer)
                        drawerDrag = null
                        drawerOpen = false
                    },
                )
            }

            drawerDrag?.let { drag ->
                // Outline of the cell it will land in.
                cellAt(drag.position)?.let { (page, x, y) ->
                    dropTargets.pageGrids[page]?.let { grid -> DropOutline(grid, x, y, layout.rowsFor(page)) }
                }
                // The icon under the finger.
                val half = with(density) { (IconSize / 2).toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((drag.position.x - half).roundToInt(), (drag.position.y - half * 2.2f).roundToInt()) }
                        .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f },
                ) { AppIconImage(drag.app) }
            }

            widgetPickerPage?.let { page ->
                WidgetPicker(
                    viewModel = viewModel,
                    appsByKey = appsByKey,
                    onPick = { info -> widgetPickerPage = null; actions.addWidget(info, page) },
                    onDismiss = { widgetPickerPage = null },
                )
            }

            openFolderId?.let { id ->
                val folder = layout.folders[id]
                if (folder == null) {
                    LaunchedEffect(id) { openFolderId = null }
                } else {
                    FolderOverlay(folder, appsByKey, viewModel, actions, onDismiss = { openFolderId = null })
                }
            }
        }
    }

    if (askDefault) {
        AlertDialog(
            onDismissRequest = { askDefault = false },
            title = { Text("Make Bear Bones your home screen?") },
            text = { Text("Pick Bear Bones under Default home app, and Home will always bring you here.") },
            confirmButton = { TextButton(onClick = { askDefault = false; actions.openDefaultHomeSettings() }) { Text("Yes") } },
            dismissButton = { TextButton(onClick = { askDefault = false }) { Text("Not now") } },
        )
    }
}

@Composable
private fun DropOutline(grid: Rect, x: Int, y: Int, rows: Int) {
    val density = LocalDensity.current
    val cellW = grid.width / HOME_COLUMNS
    val cellH = grid.height / rows
    with(density) {
        Box(
            Modifier
                .offset { IntOffset((grid.left + cellW * x).roundToInt(), (grid.top + cellH * y).roundToInt()) }
                .size(cellW.toDp(), cellH.toDp())
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp)),
        )
    }
}
