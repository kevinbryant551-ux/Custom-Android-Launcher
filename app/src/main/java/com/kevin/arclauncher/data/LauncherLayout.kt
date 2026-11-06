package com.kevin.arclauncher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything the user arranged. App references are [AppEntry.key] strings so they survive restarts. */
@Serializable
data class LauncherLayout(
    val pages: List<HomePage> = listOf(HomePage()),
    val dock: List<String> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val showSearchBar: Boolean = true,
    val initialized: Boolean = false,
    /** Single-page home from v0.1; moved into [pages] by [migrated]. */
    @SerialName("home") val legacyHome: List<HomeItem> = emptyList(),
)

@Serializable
data class HomePage(val items: List<HomeItem> = emptyList())

@Serializable
data class Folder(val id: String, val name: String, val apps: List<String>)

@Serializable
sealed interface HomeItem {
    @Serializable
    @SerialName("app")
    data class App(val key: String) : HomeItem

    @Serializable
    @SerialName("folder")
    data class FolderRef(val id: String) : HomeItem

    /** An app widget bound to [id] (an AppWidgetHost id), [spanX] columns by [spanY] rows. */
    @Serializable
    @SerialName("widget")
    data class Widget(val id: Int, val spanX: Int, val spanY: Int) : HomeItem
}

const val DOCK_CAPACITY = 5
const val MAX_WIDGET_SPAN_Y = 6

fun LauncherLayout.migrated(): LauncherLayout {
    if (legacyHome.isEmpty()) return if (pages.isEmpty()) copy(pages = listOf(HomePage())) else this
    val first = HomePage(legacyHome + pages.firstOrNull()?.items.orEmpty())
    return copy(pages = listOf(first) + pages.drop(1), legacyHome = emptyList())
}

val LauncherLayout.allItems: List<HomeItem> get() = pages.flatMap { it.items }

private fun LauncherLayout.mapItems(change: (List<HomeItem>) -> List<HomeItem>) =
    copy(pages = pages.map { it.copy(items = change(it.items)) })

private fun LauncherLayout.pageIndex(page: Int) = page.coerceIn(0, pages.lastIndex)

/**
 * Puts [keys] into a folder: a new one named [name], or the existing [targetFolderId].
 * The apps leave the home pages and any other folder; the folder takes the home slot of the first
 * grouped app (or goes last on [currentPage]). Folders left empty are deleted.
 */
fun LauncherLayout.groupIntoFolder(
    keys: List<String>,
    name: String,
    targetFolderId: String?,
    newId: String,
    currentPage: Int = 0,
): LauncherLayout {
    if (keys.isEmpty()) return this
    val keySet = keys.toSet()
    var newFolders = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filterNot { it in keySet }) }
    val target = targetFolderId?.let { newFolders[it] }
    val folder = target?.copy(apps = target.apps + keys.distinct())
        ?: Folder(newId, name.trim().ifEmpty { "Folder" }, keys.distinct())
    newFolders = newFolders + (folder.id to folder)
    val emptied = newFolders.filterValues { it.apps.isEmpty() }.keys
    newFolders = newFolders - emptied

    fun dropped(item: HomeItem) =
        (item is HomeItem.App && item.key in keySet) || (item is HomeItem.FolderRef && item.id in emptied)

    val alreadyPlaced = allItems.any { it is HomeItem.FolderRef && it.id == folder.id && folder.id !in emptied }
    val firstPage = pages.indexOfFirst { p -> p.items.any { it is HomeItem.App && it.key in keySet } }
    val newPages = pages.mapIndexed { index, page ->
        val kept = page.items.filterNot(::dropped).toMutableList()
        if (!alreadyPlaced) {
            if (index == firstPage) {
                val firstPos = page.items.indexOfFirst { it is HomeItem.App && it.key in keySet }
                kept.add(page.items.take(firstPos).count { !dropped(it) }, HomeItem.FolderRef(folder.id))
            } else if (firstPage < 0 && index == pageIndex(currentPage)) {
                kept.add(HomeItem.FolderRef(folder.id))
            }
        }
        page.copy(items = kept)
    }
    return copy(pages = newPages, folders = newFolders)
}

fun LauncherLayout.addToHome(keys: List<String>, page: Int = 0): LauncherLayout {
    val present = allItems.filterIsInstance<HomeItem.App>().map { it.key }.toSet()
    val added = keys.distinct().filterNot { it in present }.map { HomeItem.App(it) }
    val p = pageIndex(page)
    return copy(pages = pages.mapIndexed { i, pg -> if (i == p) pg.copy(items = pg.items + added) else pg })
}

fun LauncherLayout.addToDock(keys: List<String>): LauncherLayout =
    copy(dock = (dock + keys.filterNot { it in dock }).distinct().take(DOCK_CAPACITY))

fun LauncherLayout.removeFromHome(item: HomeItem): LauncherLayout = mapItems { it - item }

fun LauncherLayout.removeFromDock(key: String): LauncherLayout = copy(dock = dock - key)

fun LauncherLayout.renameFolder(id: String, name: String): LauncherLayout {
    val f = folders[id] ?: return this
    return copy(folders = folders + (id to f.copy(name = name.trim().ifEmpty { f.name })))
}

fun LauncherLayout.removeFromFolder(id: String, key: String): LauncherLayout {
    val f = folders[id] ?: return this
    val left = f.apps - key
    return if (left.isEmpty()) copy(folders = folders - id).mapItems { it - HomeItem.FolderRef(id) }
    else copy(folders = folders + (id to f.copy(apps = left)))
}

/** Dissolves a folder: its apps take its place on the home page. */
fun LauncherLayout.ungroup(id: String): LauncherLayout {
    val f = folders[id] ?: return this
    val onHome = allItems.filterIsInstance<HomeItem.App>().map { it.key }.toSet()
    return copy(folders = folders - id).mapItems { items ->
        items.flatMap { item ->
            if (item == HomeItem.FolderRef(id)) f.apps.filterNot { it in onHome }.map { HomeItem.App(it) } else listOf(item)
        }
    }
}

fun LauncherLayout.hide(keys: Collection<String>): LauncherLayout = copy(hidden = hidden + keys)

fun LauncherLayout.unhide(keys: Collection<String>): LauncherLayout = copy(hidden = hidden - keys.toSet())

/** Drops every reference to an uninstalled package (in one user profile). */
fun LauncherLayout.removePackage(packageName: String, userSerial: Long): LauncherLayout {
    fun gone(key: String) = key.startsWith("$packageName/") && key.endsWith("#$userSerial")
    val newFolders = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filterNot(::gone)) }
    val emptied = newFolders.filterValues { it.apps.isEmpty() }.keys
    return copy(
        dock = dock.filterNot(::gone),
        folders = newFolders - emptied,
        hidden = hidden.filterNot(::gone).toSet(),
    ).mapItems { items ->
        items.filterNot { (it is HomeItem.App && gone(it.key)) || (it is HomeItem.FolderRef && it.id in emptied) }
    }
}

/** Inserts an empty page right after [after]. */
fun LauncherLayout.addPage(after: Int): LauncherLayout =
    copy(pages = pages.toMutableList().apply { add((after + 1).coerceIn(0, size), HomePage()) })

/** Removes a page (never the last one left). */
fun LauncherLayout.removePage(index: Int): LauncherLayout =
    if (pages.size <= 1 || index !in pages.indices) this else copy(pages = pages.filterIndexed { i, _ -> i != index })

fun LauncherLayout.addWidget(page: Int, widget: HomeItem.Widget): LauncherLayout {
    val p = pageIndex(page)
    return copy(pages = pages.mapIndexed { i, pg -> if (i == p) pg.copy(items = pg.items + widget) else pg })
}

fun LauncherLayout.resizeWidget(id: Int, spanX: Int, spanY: Int, columns: Int): LauncherLayout = mapItems { items ->
    items.map {
        if (it is HomeItem.Widget && it.id == id) it.copy(spanX = spanX.coerceIn(1, columns), spanY = spanY.coerceIn(1, MAX_WIDGET_SPAN_Y)) else it
    }
}

fun LauncherLayout.removeWidget(id: Int): LauncherLayout = mapItems { items -> items.filterNot { it is HomeItem.Widget && it.id == id } }

/** Moves an item one page left (-1) or right (+1), appending it there. */
fun LauncherLayout.moveToPage(item: HomeItem, from: Int, to: Int): LauncherLayout {
    if (from !in pages.indices || to !in pages.indices || from == to) return this
    return copy(pages = pages.mapIndexed { i, pg ->
        when (i) {
            from -> pg.copy(items = pg.items - item)
            to -> pg.copy(items = pg.items + item)
            else -> pg
        }
    })
}
