package com.kevin.arclauncher.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Everything the user arranged. App references are [AppEntry.key] strings so they survive restarts. */
@Serializable
data class LauncherLayout(
    val home: List<HomeItem> = emptyList(),
    val dock: List<String> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val initialized: Boolean = false,
)

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
}

const val DOCK_CAPACITY = 5

/**
 * Puts [keys] into a folder: a new one named [name], or the existing [targetFolderId].
 * The apps leave the home grid and any other folder; the folder takes the home slot of the first
 * grouped app (or goes last). Folders left empty are deleted.
 */
fun LauncherLayout.groupIntoFolder(
    keys: List<String>,
    name: String,
    targetFolderId: String?,
    newId: String,
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

    val firstPos = home.indexOfFirst { it is HomeItem.App && it.key in keySet }
    val newHome = home.filterNot(::dropped).toMutableList()
    if (newHome.none { it is HomeItem.FolderRef && it.id == folder.id }) {
        val insertAt = if (firstPos < 0) newHome.size else home.take(firstPos).count { !dropped(it) }
        newHome.add(insertAt, HomeItem.FolderRef(folder.id))
    }
    return copy(home = newHome, folders = newFolders)
}

fun LauncherLayout.addToHome(keys: List<String>): LauncherLayout {
    val present = home.filterIsInstance<HomeItem.App>().map { it.key }.toSet()
    return copy(home = home + keys.distinct().filterNot { it in present }.map { HomeItem.App(it) })
}

fun LauncherLayout.addToDock(keys: List<String>): LauncherLayout =
    copy(dock = (dock + keys.filterNot { it in dock }).distinct().take(DOCK_CAPACITY))

fun LauncherLayout.removeFromHome(item: HomeItem): LauncherLayout = copy(home = home - item)

fun LauncherLayout.removeFromDock(key: String): LauncherLayout = copy(dock = dock - key)

fun LauncherLayout.renameFolder(id: String, name: String): LauncherLayout {
    val f = folders[id] ?: return this
    return copy(folders = folders + (id to f.copy(name = name.trim().ifEmpty { f.name })))
}

fun LauncherLayout.removeFromFolder(id: String, key: String): LauncherLayout {
    val f = folders[id] ?: return this
    val left = f.apps - key
    return if (left.isEmpty()) copy(folders = folders - id, home = home - HomeItem.FolderRef(id))
    else copy(folders = folders + (id to f.copy(apps = left)))
}

/** Dissolves a folder: its apps take its place on the home grid. */
fun LauncherLayout.ungroup(id: String): LauncherLayout {
    val f = folders[id] ?: return this
    val onHome = home.filterIsInstance<HomeItem.App>().map { it.key }.toSet()
    val newHome = home.flatMap { item ->
        if (item == HomeItem.FolderRef(id)) f.apps.filterNot { it in onHome }.map { HomeItem.App(it) } else listOf(item)
    }
    return copy(home = newHome, folders = folders - id)
}

fun LauncherLayout.hide(keys: Collection<String>): LauncherLayout = copy(hidden = hidden + keys)

fun LauncherLayout.unhide(keys: Collection<String>): LauncherLayout = copy(hidden = hidden - keys.toSet())

/** Drops every reference to an uninstalled package (in one user profile). */
fun LauncherLayout.removePackage(packageName: String, userSerial: Long): LauncherLayout {
    fun gone(key: String) = key.startsWith("$packageName/") && key.endsWith("#$userSerial")
    val newFolders = folders.mapValues { (_, f) -> f.copy(apps = f.apps.filterNot(::gone)) }
    val emptied = newFolders.filterValues { it.apps.isEmpty() }.keys
    return copy(
        home = home.filterNot { (it is HomeItem.App && gone(it.key)) || (it is HomeItem.FolderRef && it.id in emptied) },
        dock = dock.filterNot(::gone),
        folders = newFolders - emptied,
        hidden = hidden.filterNot(::gone).toSet(),
    )
}
