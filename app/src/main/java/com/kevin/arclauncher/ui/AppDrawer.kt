package com.kevin.arclauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.DOCK_CAPACITY
import com.kevin.arclauncher.data.Folder
import com.kevin.arclauncher.data.LauncherLayout
import com.kevin.arclauncher.data.SECTION_INDEX
import com.kevin.arclauncher.data.buildSections
import com.kevin.arclauncher.data.matchesQuery
import com.kevin.arclauncher.data.sectionOrder
import kotlinx.coroutines.launch

const val DRAWER_COLUMNS = 4

/** Rows of the drawer's LazyColumn: a sticky letter header, then rows of [DRAWER_COLUMNS] apps. */
private sealed interface DrawerRow {
    val section: String
    val id: String

    data class Header(override val section: String) : DrawerRow {
        override val id get() = "h:$section"
    }

    data class Apps(override val section: String, val apps: List<AppEntry>, val n: Int) : DrawerRow {
        override val id get() = "r:$section:$n"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    layout: LauncherLayout,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    focusSearch: Boolean,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var groupDialog by remember { mutableStateOf(false) }
    var scrubLetter by remember { mutableStateOf<String?>(null) }
    val selecting = selected.isNotEmpty()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        when {
            selecting -> selected = emptySet()
            query.isNotEmpty() -> query = ""
            else -> onClose()
        }
    }
    LaunchedEffect(focusSearch) { if (focusSearch) focusRequester.requestFocus() }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) keyboard?.hide() }

    val visible = remember(apps, layout.hidden, showHidden, query) {
        apps.filter { (showHidden || it.key !in layout.hidden) && matchesQuery(it.label, query) }
    }
    val sections = remember(visible) { buildSections(visible, { it.label }) }
    val ordered = remember(sections) { sections.flatMap { it.items } }
    val rows = remember(sections) {
        sections.flatMap { s ->
            listOf<DrawerRow>(DrawerRow.Header(s.key)) +
                s.items.chunked(DRAWER_COLUMNS).mapIndexed { i, chunk -> DrawerRow.Apps(s.key, chunk, i) }
        }
    }
    val headerIndex = remember(rows) {
        rows.withIndex().filter { it.value is DrawerRow.Header }.associate { it.value.section to it.index }
    }
    val currentSection by remember(rows) { derivedStateOf { rows.getOrNull(listState.firstVisibleItemIndex)?.section } }

    fun toggle(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }
    val selectedApps = ordered.filter { it.key in selected }
    fun finish() {
        selected = emptySet()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116).copy(alpha = 0.82f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    allHidden = selectedApps.isNotEmpty() && selectedApps.all { it.key in layout.hidden },
                    onCancel = ::finish,
                    onGroup = { groupDialog = true },
                    onAddHome = {
                        viewModel.addToHome(selectedApps.map { it.key })
                        actions.toast("Added ${selectedApps.size} to home screen")
                        finish()
                    },
                    onAddDock = {
                        val added = viewModel.addToDock(selectedApps.map { it.key })
                        actions.toast(if (added < selectedApps.size) "Dock holds $DOCK_CAPACITY apps; added what fit" else "Added to dock")
                        finish()
                    },
                    onInfo = { actions.appInfo(selectedApps); finish() },
                    onUninstall = { actions.uninstall(selectedApps); finish() },
                    onHide = { viewModel.hide(selected); actions.toast("Hidden. Show them again from the ⋮ menu."); finish() },
                    onUnhide = { viewModel.unhide(selected); finish() },
                    onSelectAll = { selected = ordered.map { it.key }.toSet() },
                )
            } else {
                SearchBar(
                    query = query,
                    onQuery = { query = it },
                    focusRequester = focusRequester,
                    showHidden = showHidden,
                    onToggleHidden = { showHidden = !showHidden },
                    onWallpaper = actions::openWallpaperPicker,
                    onGo = {
                        val best = ordered.firstOrNull { it.label.startsWith(query.trim(), ignoreCase = true) } ?: ordered.firstOrNull()
                        best?.let { actions.launch(it) }
                    },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 8.dp, end = 32.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .verticalSwipe(canSwipeDown = { !listState.canScrollBackward && !selecting }, onSwipeDown = onClose),
                ) {
                    if (rows.isEmpty()) {
                        item { Text("No apps match “$query”", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(24.dp)) }
                    }
                    rows.forEach { row ->
                        when (row) {
                            is DrawerRow.Header -> stickyHeader(key = row.id) { SectionHeader(row.section) }
                            is DrawerRow.Apps -> item(key = row.id) {
                                Row(Modifier.fillMaxWidth()) {
                                    row.apps.forEach { app ->
                                        val checked = app.key in selected
                                        AppTile(
                                            label = app.label,
                                            dimmed = app.key in layout.hidden,
                                            onClick = { if (selecting) toggle(app.key) else actions.launch(app) },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                toggle(app.key)
                                            },
                                            icon = { AppIconImage(app) },
                                            badge = { if (selecting) SelectionCheck(checked) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(DRAWER_COLUMNS - row.apps.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }

                if (query.isBlank() && rows.isNotEmpty()) {
                    FastScroller(
                        present = headerIndex.keys,
                        current = scrubLetter ?: currentSection,
                        onScrub = { letter ->
                            scrubLetter = letter
                            if (letter != null) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                // Jump to this letter, or the next one that has apps.
                                val target = headerIndex.entries
                                    .filter { sectionOrder(it.key) >= sectionOrder(letter) }
                                    .minByOrNull { sectionOrder(it.key) } ?: headerIndex.entries.maxBy { sectionOrder(it.key) }
                                scope.launch { listState.scrollToItem(target.value) }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().navigationBarsPadding(),
                    )
                }

                scrubLetter?.let { ScrubBubble(it, Modifier.align(Alignment.Center)) }
            }
        }
    }

    if (groupDialog) {
        GroupDialog(
            count = selectedApps.size,
            existing = layout.folders.values.sortedBy { it.name.lowercase() },
            onDismiss = { groupDialog = false },
            onConfirm = { name, folderId ->
                groupDialog = false
                viewModel.groupIntoFolder(selectedApps.map { it.key }, name, folderId)
                val label = folderId?.let { layout.folders[it]?.name } ?: name.ifBlank { "Folder" }
                actions.toast("“$label” is on your home screen")
                finish()
                onClose()
            },
        )
    }
}

@Composable
private fun SectionHeader(letter: String) {
    // Opaque-ish background so rows scroll underneath the pinned header cleanly.
    Column(Modifier.fillMaxWidth().background(Color(0xFF0E1116).copy(alpha = 0.92f))) {
        Text(
            letter,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    focusRequester: FocusRequester,
    showHidden: Boolean,
    onToggleHidden: () -> Unit,
    onWallpaper: () -> Unit,
    onGo: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (showHidden) "Hide hidden apps" else "Show hidden apps") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    onClick = { menu = false; onToggleHidden() },
                )
                DropdownMenuItem(text = { Text("Wallpaper") }, leadingIcon = { Icon(Icons.Default.Home, null) }, onClick = { menu = false; onWallpaper() })
                DropdownMenuItem(text = { Text("Tip: long-press an app to select several") }, onClick = { menu = false }, enabled = false)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    allHidden: Boolean,
    onCancel: () -> Unit,
    onGroup: () -> Unit,
    onAddHome: () -> Unit,
    onAddDock: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel selection", tint = Color.White) }
        Text("$count selected", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Actions for selected apps", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                @Composable
                fun Entry(label: String, icon: ImageVector, action: () -> Unit) =
                    DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) }, onClick = { menu = false; action() })
                Entry("Group into folder", Icons.Default.Add, onGroup)
                Entry("Add to home screen", Icons.Default.Home, onAddHome)
                Entry("Add to dock", Icons.Default.KeyboardArrowDown, onAddDock)
                Entry("App info", Icons.Default.Info, onInfo)
                Entry("Uninstall", Icons.Default.Delete, onUninstall)
                if (allHidden) Entry("Unhide", Icons.Default.Lock, onUnhide) else Entry("Hide", Icons.Default.Lock, onHide)
                HorizontalDivider()
                Entry("Select all", Icons.Default.Check, onSelectAll)
                Entry("Cancel", Icons.Default.Close, onCancel)
            }
        }
    }
}

/**
 * The A–Z strip on the right edge. Touch or drag on it to jump; letters with no apps are dimmed and
 * jump to the next letter that has some.
 */
@Composable
private fun FastScroller(present: Set<String>, current: String?, onScrub: (String?) -> Unit, modifier: Modifier = Modifier) {
    var heightPx by remember { mutableStateOf(1) }
    Column(
        modifier
            .width(28.dp)
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                fun letterAt(y: Float) = SECTION_INDEX[((y / heightPx) * SECTION_INDEX.size).toInt().coerceIn(0, SECTION_INDEX.lastIndex)]
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    var last: String? = letterAt(down.position.y).also(onScrub)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        val letter = letterAt(change.position.y)
                        if (letter != last) { last = letter; onScrub(letter) }
                    }
                    onScrub(null)
                }
            }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SECTION_INDEX.forEach { letter ->
            val color = when {
                letter == current -> MaterialTheme.colorScheme.primary
                letter in present -> Color.White
                else -> Color.White.copy(alpha = 0.3f)
            }
            Text(letter, color = color, fontSize = 11.sp, fontWeight = if (letter == current) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun ScrubBubble(letter: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun GroupDialog(
    count: Int,
    existing: List<Folder>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, existingFolderId: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group $count apps") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New folder name") },
                    placeholder = { Text("Folder") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(name, null) }),
                )
                if (existing.isNotEmpty()) {
                    Text("Or add to an existing folder", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        existing.forEach { f -> TextButton(onClick = { onConfirm(f.name, f.id) }) { Text(f.name) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, null) }) { Text("Create folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
