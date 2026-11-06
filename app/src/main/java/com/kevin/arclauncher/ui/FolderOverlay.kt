package com.kevin.arclauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.Folder

/** An opened folder: tap the name to rename it, long-press an app for options. */
@Composable
fun FolderOverlay(
    folder: Folder,
    appsByKey: Map<String, AppEntry>,
    viewModel: LauncherViewModel,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    val apps = remember(folder, appsByKey) { folder.apps.mapNotNull(appsByKey::get) }

    fun saveName() {
        if (name.isNotBlank() && name != folder.name) viewModel.renameFolder(folder.id, name)
    }
    fun close() {
        saveName()
        onDismiss()
    }
    BackHandler(onBack = ::close)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            // Swallow taps on the panel so they don't reach the scrim and close it.
            modifier = Modifier.fillMaxWidth(0.88f).pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.padding(16.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveName(); focusManager.clearFocus() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(GridCells.Fixed(4), modifier = Modifier.heightIn(max = 440.dp).padding(top = 8.dp)) {
                    items(apps, key = { it.key }) { app ->
                        var menu by remember { mutableStateOf(false) }
                        AppTile(
                            label = app.label,
                            onClick = { actions.launch(app); close() },
                            onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                            icon = { AppIconImage(app) },
                            menu = {
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Remove from folder") }, onClick = { menu = false; viewModel.removeFromFolder(folder.id, app.key) })
                                    DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; actions.appInfo(listOf(app)) })
                                    DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; actions.uninstall(listOf(app)) })
                                }
                            },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { viewModel.ungroup(folder.id); onDismiss() }) { Text("Ungroup") }
                    TextButton(onClick = ::close) { Text("Done") }
                }
            }
        }
    }
}
