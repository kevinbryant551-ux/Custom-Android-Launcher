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
    val showClock: Boolean = true,
    /** The page Home returns to; it carries the clock and search bar. Pages can sit left or right of it. */
    val homePage: Int = 0,
    /** App key -> file name (in the app's files/icons dir) of a picture the user chose as its icon. */
    val customIcons: Map<String, String> = emptyMap(),
    /** Package of the chosen icon pack, or null for the apps' own icons. */
    val iconPack: String? = null,
    val initialized: Boolean = false,
    /** Single-page home from v0.1; moved into [pages] by [migrated]. */
    @SerialName("home") val legacyHome: List<HomeItem> = emptyList(),
)

/**
 * One home page. [items] keeps insertion order; [cells] pins each item (by [cellKey]) to a grid cell.
 * Items without a valid cell are given the first free one by [normalized].
 */
@Serializable
data class HomePage(val items: List<HomeItem> = emptyList(), val cells: Map<String, Cell> = emptyMap())

@Serializable
data class Cell(val x: Int, val y: Int)

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

/** Stable id of an item within a page's [HomePage.cells]. */
val HomeItem.cellKey: String
    get() = when (this) {
        is HomeItem.App -> "app:$key"
        is HomeItem.FolderRef -> "folder:$id"
        is HomeItem.Widget -> "widget:$id"
    }

const val DOCK_CAPACITY = 5
const val HOME_COLUMNS = 4
const val FIRST_PAGE_ROWS = 5 // the home page loses a row to the clock
const val PAGE_ROWS = 6
const val MAX_WIDGET_SPAN_Y = PAGE_ROWS

fun LauncherLayout.rowsFor(page: Int) = if (page == homePage && showClock) FIRST_PAGE_ROWS else PAGE_ROWS

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

    // The folder takes the slot of the first key (in [keys] order) that is on a home page.
    val anchor = keys.firstOrNull { k -> allItems.any { it is HomeItem.App && it.key == k } }?.let { HomeItem.App(it) }
    val alreadyPlaced = allItems.any { it is HomeItem.FolderRef && it.id == folder.id }
    val anchorPage = if (anchor == null) -1 else pages.indexOfFirst { anchor in it.items }
    val ref = HomeItem.FolderRef(folder.id)
    val newPages = pages.mapIndexed { index, page ->
        val kept = page.items.filterNot(::dropped).toMutableList()
        var cells = page.cells
        if (!alreadyPlaced) {
            if (index == anchorPage) {
                val pos = page.items.indexOf(anchor!!)
                kept.add(page.items.take(pos).count { !dropped(it) }, ref)
                page.cells[anchor.cellKey]?.let { cells = cells + (ref.cellKey to it) }
            } else if (anchorPage < 0 && index == pageIndex(currentPage)) {
                kept.add(ref)
            }
        }
        page.copy(items = kept, cells = cells)
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

/** Puts an app from the drawer on [page] at ([x], [y]); if that cell is taken it goes to the first free one. */
fun LauncherLayout.placeApp(key: String, page: Int, x: Int, y: Int): LauncherLayout {
    val app = HomeItem.App(key)
    val p = pageIndex(page)
    // An app appears on the home screen once: take it off wherever it was.
    val cleared = mapItems { items -> items - app }
    val withApp = cleared.copy(pages = cleared.pages.mapIndexed { i, pg -> if (i == p) pg.copy(items = pg.items + app) else pg })
    return withApp.moveItem(p, app, x, y) ?: withApp
}

/** Moves a dock app to [index], shifting the others. */
fun LauncherLayout.moveInDock(key: String, index: Int): LauncherLayout {
    if (key !in dock) return this
    val rest = dock - key
    return copy(dock = rest.toMutableList().apply { add(index.coerceIn(0, rest.size), key) })
}

/** Drops every reference to an uninstalled package (in one user profile). */
fun LauncherLayout.removePackage(packageName: String, userSerial: Long): LauncherLayout {
    fun gone(key: String) = key.startsWith("$packageName/") && key.endsWith("#$userSerial")
    val newFolders = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filterNot(::gone)) }
    val emptied = newFolders.filterValues { it.apps.isEmpty() }.keys
    return copy(
        dock = dock.filterNot(::gone),
        folders = newFolders - emptied,
        hidden = hidden.filterNot(::gone).toSet(),
        customIcons = customIcons.filterKeys { !gone(it) },
    ).mapItems { items ->
        items.filterNot { (it is HomeItem.App && gone(it.key)) || (it is HomeItem.FolderRef && it.id in emptied) }
    }
}

/** Inserts an empty page at [index] (0 = far left), keeping the home page the same page. */
fun LauncherLayout.insertPage(index: Int): LauncherLayout {
    val at = index.coerceIn(0, pages.size)
    return copy(
        pages = pages.toMutableList().apply { add(at, HomePage()) },
        homePage = if (at <= homePage) homePage + 1 else homePage,
    )
}

/** Inserts an empty page right after [after]. */
fun LauncherLayout.addPage(after: Int): LauncherLayout = insertPage(after + 1)

/** Removes a page (never the last one left). */
fun LauncherLayout.removePage(index: Int): LauncherLayout {
    if (pages.size <= 1 || index !in pages.indices) return this
    val home = when {
        index < homePage -> homePage - 1
        index == homePage -> homePage.coerceAtMost(pages.size - 2)
        else -> homePage
    }
    return copy(pages = pages.filterIndexed { i, _ -> i != index }, homePage = home)
}

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

data class Placed(val item: HomeItem, val x: Int, val y: Int, val w: Int, val h: Int) {
    fun covers(cx: Int, cy: Int) = cx in x until x + w && cy in y until y + h
    fun overlaps(ox: Int, oy: Int, ow: Int, oh: Int) = ox < x + w && x < ox + ow && oy < y + h && y < oy + oh
}

/** Cells an item covers: 1x1 for apps and folders, the widget's span (clamped to the grid) for widgets. */
fun HomeItem.span(columns: Int, rows: Int): Pair<Int, Int> =
    if (this is HomeItem.Widget) spanX.coerceIn(1, columns) to spanY.coerceIn(1, rows) else 1 to 1

/**
 * Lays items out on a [columns] x [rows] grid. Items keep their saved cell when it's free; the rest
 * take the first free spot, reading left-to-right, top-to-bottom. Returns what fit and what didn't.
 */
fun placeItems(items: List<HomeItem>, cells: Map<String, Cell>, columns: Int, rows: Int): Pair<List<Placed>, List<HomeItem>> {
    val taken = Array(rows) { BooleanArray(columns) }
    fun fits(x: Int, y: Int, w: Int, h: Int) =
        x >= 0 && y >= 0 && x + w <= columns && y + h <= rows && (y until y + h).all { r -> (x until x + w).none { taken[r][it] } }
    fun take(x: Int, y: Int, w: Int, h: Int) {
        for (r in y until y + h) for (c in x until x + w) taken[r][c] = true
    }

    val placed = arrayOfNulls<Placed>(items.size)
    val waiting = mutableListOf<Int>()
    items.forEachIndexed { i, item ->
        val (w, h) = item.span(columns, rows)
        val cell = cells[item.cellKey]
        if (cell != null && fits(cell.x, cell.y, w, h)) {
            take(cell.x, cell.y, w, h)
            placed[i] = Placed(item, cell.x, cell.y, w, h)
        } else {
            waiting += i
        }
    }
    val overflow = mutableListOf<HomeItem>()
    for (i in waiting) {
        val item = items[i]
        val (w, h) = item.span(columns, rows)
        val spot = (0..rows - h).asSequence().flatMap { y -> (0..columns - w).asSequence().map { x -> x to y } }
            .firstOrNull { (x, y) -> fits(x, y, w, h) }
        if (spot == null) {
            overflow += item
        } else {
            take(spot.first, spot.second, w, h)
            placed[i] = Placed(item, spot.first, spot.second, w, h)
        }
    }
    return placed.filterNotNull() to overflow
}

/**
 * Gives every home item a concrete cell. Items that don't fit on their page spill onto the next
 * page (adding one if needed), so nothing on the home screen is ever silently hidden.
 */
fun LauncherLayout.normalized(): LauncherLayout {
    val out = mutableListOf<HomePage>()
    var carry = emptyList<HomeItem>()
    var i = 0
    while (i < pages.size || carry.isNotEmpty()) {
        val page = pages.getOrElse(i) { HomePage() }
        val (placed, overflow) = placeItems(page.items + carry, page.cells, HOME_COLUMNS, rowsFor(i))
        val order = placed.map { it.item }
        out += HomePage(
            items = (page.items + carry).filter { it in order },
            cells = placed.associate { it.item.cellKey to Cell(it.x, it.y) },
        )
        carry = overflow
        i++
    }
    return copy(pages = out, homePage = homePage.coerceIn(0, out.lastIndex))
}

/** Moves an item to cell ([x], [y]) on [page]. Returns null if it doesn't fit there. */
fun LauncherLayout.moveItem(page: Int, item: HomeItem, x: Int, y: Int): LauncherLayout? {
    val pg = pages.getOrNull(page) ?: return null
    val rows = rowsFor(page)
    val (w, h) = item.span(HOME_COLUMNS, rows)
    if (x < 0 || y < 0 || x + w > HOME_COLUMNS || y + h > rows) return null
    val others = placeItems(pg.items, pg.cells, HOME_COLUMNS, rows).first.filter { it.item != item }
    if (others.any { it.overlaps(x, y, w, h) }) return null
    return copy(pages = pages.mapIndexed { i, p -> if (i == page) p.copy(cells = p.cells + (item.cellKey to Cell(x, y))) else p })
}

