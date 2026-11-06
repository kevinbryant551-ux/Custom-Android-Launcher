package com.kevin.arclauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    fun iconFor(component: ComponentName, densityDpi: Int): Drawable? {
        val name = drawableByComponent[component.flattenToString()] ?: return null
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawableForDensity(id, densityDpi, null) }.getOrNull()
    }

    companion object {
        // Intent actions icon packs register so launchers can find them.
        private val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")

        /** Installed icon packs as (package, label), sorted by label. */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return PACK_ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
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
