package com.kevin.arclauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.Folder
import com.kevin.arclauncher.data.HomeItem
import com.kevin.arclauncher.data.LauncherLayout
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val HOME_COLUMNS = 4

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 8f)

/** A home grid cell, resolved against the installed apps. */
private sealed interface HomeCell {
    val item: HomeItem
    data class App(override val item: HomeItem.App, val app: AppEntry) : HomeCell
    data class FolderCell(override val item: HomeItem.FolderRef, val folder: Folder, val apps: List<AppEntry>) : HomeCell
}

@Composable
fun HomeScreen(
    layout: LauncherLayout,
    appsByKey: Map<String, AppEntry>,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    onOpenDrawer: (focusSearch: Boolean) -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    var backgroundMenuAt by remember { mutableStateOf<Offset?>(null) }

    // Missing apps (uninstalled, or a paused work profile) are skipped rather than shown blank.
    val cells = remember(layout, appsByKey) {
        layout.home.mapNotNull { item ->
            when (item) {
                is HomeItem.App -> appsByKey[item.key]?.let { HomeCell.App(item, it) }
                is HomeItem.FolderRef -> layout.folders[item.id]?.let { f ->
                    f.apps.mapNotNull(appsByKey::get).takeIf { it.isNotEmpty() }?.let { HomeCell.FolderCell(item, f, it) }
                }
            }
        }
    }
    val dockApps = remember(layout.dock, appsByKey) { layout.dock.mapNotNull(appsByKey::get) }

    Box(
        Modifier
            .fillMaxSize()
            .verticalSwipe(
                canSwipeUp = { !gridState.canScrollForward },
                canSwipeDown = { !gridState.canScrollBackward },
                onSwipeUp = { onOpenDrawer(false) },
                onSwipeDown = actions::expandNotifications,
            )
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    backgroundMenuAt = it
                })
            },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
            ClockGlance(onClick = actions::openClock)
            SearchPill(onClick = { onOpenDrawer(true) })
            Spacer(Modifier.height(16.dp))
            if (cells.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Swipe up for all apps.\nLong-press apps there to add them here.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f), shadow = TextShadow),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(HOME_COLUMNS),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                ) {
                    items(cells, key = { it.item.toString() }) { cell ->
                        when (cell) {
                            is HomeCell.App -> HomeAppTile(cell, viewModel, actions)
                            is HomeCell.FolderCell -> HomeFolderTile(cell, viewModel, onOpen = { onOpenFolder(cell.folder.id) })
                        }
                    }
                }
            }
            DrawerHandle(onClick = { onOpenDrawer(false) })
            Dock(dockApps, viewModel, actions)
        }

        backgroundMenuAt?.let { at ->
            Box(Modifier.offset { IntOffset(at.x.toInt(), at.y.toInt()) }.size(1.dp)) {
                DropdownMenu(expanded = true, onDismissRequest = { backgroundMenuAt = null }) {
                    DropdownMenuItem(text = { Text("Wallpaper") }, onClick = { backgroundMenuAt = null; actions.openWallpaperPicker() })
                    DropdownMenuItem(text = { Text("All apps") }, onClick = { backgroundMenuAt = null; onOpenDrawer(false) })
                }
            }
        }
    }
}

@Composable
private fun HomeAppTile(cell: HomeCell.App, viewModel: LauncherViewModel, actions: LauncherActions) {
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
                DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; actions.appInfo(listOf(cell.app)) })
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; actions.uninstall(listOf(cell.app)) })
            }
        },
    )
}

@Composable
private fun HomeFolderTile(cell: HomeCell.FolderCell, viewModel: LauncherViewModel, onOpen: () -> Unit) {
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
                DropdownMenuItem(text = { Text("Remove from home") }, onClick = { menu = false; viewModel.removeFromHome(cell.item) })
            }
        },
    )
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

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(12.dp))
        Text("Search apps", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
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
            .padding(top = 4.dp, bottom = 8.dp)
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
