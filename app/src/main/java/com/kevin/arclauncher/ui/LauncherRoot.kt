package com.kevin.arclauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kevin.arclauncher.LauncherActions
import com.kevin.arclauncher.LauncherViewModel

@Composable
fun LauncherRoot(viewModel: LauncherViewModel, actions: LauncherActions) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableStateOf(false) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetPickerPage by rememberSaveable { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState { layout.pages.size }

    // Home pressed while we're in front: back to a clean home screen.
    LaunchedEffect(Unit) {
        viewModel.homePresses.collect {
            val wasClean = !drawerOpen && openFolderId == null && widgetPickerPage == null
            drawerOpen = false
            openFolderId = null
            widgetPickerPage = null
            // Home again on a clean screen goes back to the first page.
            if (wasClean) pagerState.animateScrollToPage(0)
        }
    }

    // Lowest-priority back handler: a launcher has nothing behind it, so back on home does nothing.
    // The drawer and folder register their own handlers later, which take precedence.
    BackHandler { }

    CompositionLocalProvider(LocalIconLoader provides viewModel.iconLoader) {
        // Fade the home layer out under the drawer so only the wallpaper shows through it.
        val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(homeAlpha)) {
            HomeScreen(
                layout = layout,
                appsByKey = appsByKey,
                viewModel = viewModel,
                actions = actions,
                pagerState = pagerState,
                onOpenDrawer = { search -> focusSearch = search; drawerOpen = true },
                onOpenFolder = { openFolderId = it },
                onAddWidget = { widgetPickerPage = it },
            )
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                AppDrawer(
                    apps = apps,
                    layout = layout,
                    viewModel = viewModel,
                    actions = actions,
                    focusSearch = focusSearch,
                    currentPage = pagerState.currentPage,
                    onClose = { drawerOpen = false },
                )
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
}
