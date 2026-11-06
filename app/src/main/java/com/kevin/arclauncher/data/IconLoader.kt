package com.kevin.arclauncher.data

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads app icons off the main thread and keeps recently used ones in memory.
 * Order: the user's own picture for that app, then the icon pack, then the app's icon.
 */
class IconLoader(private val context: Context, private val densityDpi: Int, private val sizePx: Int) {
    private val cache = LruCache<String, ImageBitmap>(300)
    private var customIcons: Map<String, String> = emptyMap()
    private var pack: IconPack? = null
    private var packName: String? = null

    /** Bumped whenever icon sources change, so composables reload their icon. */
    var version by mutableIntStateOf(0)
        private set

    val iconDir: File get() = File(context.filesDir, "icons").apply { mkdirs() }

    fun cached(key: String): ImageBitmap? = cache.get(key)

    suspend fun configure(custom: Map<String, String>, iconPack: String?) {
        if (custom == customIcons && iconPack == packName) return
        if (iconPack != packName) pack = iconPack?.let { withContext(Dispatchers.IO) { IconPack.load(context, it) } }
        customIcons = custom
        packName = iconPack
        cache.evictAll()
        version++
    }

    suspend fun load(app: AppEntry): ImageBitmap = cache.get(app.key) ?: withContext(Dispatchers.IO) {
        val custom = customIcons[app.key]?.let { BitmapFactory.decodeFile(File(iconDir, it).path) }?.scale(sizePx, sizePx)
        val bitmap = custom
            ?: pack?.iconFor(app.component, densityDpi)?.toBitmap(sizePx, sizePx)
            // getBadgedIcon adds the briefcase badge to work-profile apps.
            ?: app.info.getBadgedIcon(densityDpi).toBitmap(sizePx, sizePx)
        bitmap.asImageBitmap()
    }.also { cache.put(app.key, it) }

    fun evict(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(cache::remove)
    }
}
