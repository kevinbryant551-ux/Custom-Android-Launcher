package com.kevin.arclauncher.data

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads app icons off the main thread and keeps recently used ones in memory. */
class IconLoader(private val densityDpi: Int, private val sizePx: Int) {
    private val cache = LruCache<String, ImageBitmap>(300)

    fun cached(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(app: AppEntry): ImageBitmap = cache.get(app.key) ?: withContext(Dispatchers.IO) {
        // getBadgedIcon adds the briefcase badge to work-profile apps.
        app.info.getBadgedIcon(densityDpi).toBitmap(sizePx, sizePx).asImageBitmap()
    }.also { cache.put(app.key, it) }

    fun evict(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
    }
}
