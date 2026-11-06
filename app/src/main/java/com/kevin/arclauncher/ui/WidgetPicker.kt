package com.kevin.arclauncher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.kevin.arclauncher.LauncherViewModel
import com.kevin.arclauncher.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** Full-screen list of widgets, grouped by the app that provides them. Tap one to add it. */
@Composable
fun WidgetPicker(
    viewModel: LauncherViewModel,
    appsByKey: Map<String, AppEntry>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val pm = context.packageManager
    val groups by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>?>(null) {
        value = withContext(Dispatchers.IO) {
            val labelByPackage = appsByKey.values.associate { it.packageName to it.label }
            fun appLabel(pkg: String) = labelByPackage[pkg]
                ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            val collator = Collator.getInstance()
            viewModel.widgetProviders()
                .groupBy { appLabel(it.provider.packageName) }
                .toList()
                .sortedWith { a, b -> collator.compare(a.first, b.first) }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0E1116).copy(alpha = 0.94f))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                Text("Widgets", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            val list = groups
            if (list == null) {
                Text("Loading…", color = Color.White, modifier = Modifier.padding(24.dp))
            } else if (list.isEmpty()) {
                Text("No widgets found.", color = Color.White, modifier = Modifier.padding(24.dp))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.first }) { (app, providers) ->
                        Text(
                            app,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                            items(providers, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                                WidgetCard(info, onClick = { onPick(info) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val densityDpi = context.resources.displayMetrics.densityDpi
    val label = remember(info) { info.loadLabel(context.packageManager) }
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (info.loadPreviewImage(context, densityDpi) ?: info.loadIcon(context, densityDpi))
                    ?.let { d -> d.toBitmap(d.intrinsicWidth.coerceIn(1, 600), d.intrinsicHeight.coerceIn(1, 600)).asImageBitmap() }
            }.getOrNull()
        }
    }
    Column(
        Modifier.width(160.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 140.dp), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(140.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
}
