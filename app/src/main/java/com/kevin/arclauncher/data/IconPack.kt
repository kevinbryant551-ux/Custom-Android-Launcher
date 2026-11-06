package com.kevin.arclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * A third-party icon pack (the format Nova, ADW and most launchers read): the pack ships an
 * appfilter.xml that maps "ComponentInfo{package/Activity}" to a drawable name in its resources.
 */
class IconPack private constructor(
    val packageName: String,
    private val res: Resources,
    private val drawableByComponent: Map<String, String>,
) {
    /**
     * The pack's icon for [component]; if the pack doesn't list it, a glyph for the app's Android
     * [category] (ApplicationInfo.CATEGORY_*) when the pack has one (the built-in pack does).
     */
    fun iconFor(component: ComponentName, category: Int, densityDpi: Int): Drawable? {
        val name = drawableByComponent[component.flattenToString()] ?: categoryGlyph(category) ?: return null
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
    }

    companion object {
        // Intent actions icon packs register so launchers can find them.
        private val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

        /** Installed icon packs as (package, label). The built-in bear pack is an easter egg and isn't listed. */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return PACK_ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != context.packageName }
                .sortedBy { it.second.lowercase() }
        }

        /** True once tools/iconpack/import.py has added icons to this app. */
        fun hasBuiltIn(context: Context): Boolean =
            context.resources.getIdentifier("appfilter", "xml", context.packageName) != 0

        private fun categoryGlyph(category: Int): String? = when (category) {
            ApplicationInfo.CATEGORY_GAME -> "bb_games"
            ApplicationInfo.CATEGORY_AUDIO -> "bb_music"
            ApplicationInfo.CATEGORY_VIDEO -> "bb_video"
            ApplicationInfo.CATEGORY_IMAGE -> "bb_photos"
            ApplicationInfo.CATEGORY_SOCIAL -> "bb_chat"
            ApplicationInfo.CATEGORY_NEWS -> "bb_forum"
            ApplicationInfo.CATEGORY_MAPS -> "bb_maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "bb_notes"
            else -> null
        }

        /** Reads a pack's appfilter.xml (from res/xml or assets). Slow: call off the main thread. */
        fun load(context: Context, packageName: String): IconPack? = runCatching {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
            }
            val map = HashMap<String, String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
                val component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                parseComponent(component)?.let { map[it] = drawable }
            }
            IconPack(packageName, res, map)
        }.getOrNull()

        /** "ComponentInfo{com.foo/com.foo.Main}" -> "com.foo/com.foo.Main" (relative ".Main" expanded). */
        fun parseComponent(raw: String): String? {
            val inner = raw.substringAfter("ComponentInfo{", "").substringBefore("}")
            val pkg = inner.substringBefore("/", "")
            var cls = inner.substringAfter("/", "")
            if (pkg.isEmpty() || cls.isEmpty()) return null
            if (cls.startsWith(".")) cls = pkg + cls
            return "$pkg/$cls"
        }
    }
}
