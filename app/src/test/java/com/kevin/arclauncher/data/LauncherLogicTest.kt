package com.kevin.arclauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LauncherLogicTest {
    @Test
    fun sectionKeys() {
        assertEquals("E", sectionKeyFor("Évernote"))
        assertEquals("#", sectionKeyFor("7-Zip"))
        assertEquals("#", sectionKeyFor("Телеграм"))
        assertEquals("M", sectionKeyFor("  maps"))
        assertEquals("#", sectionKeyFor(""))
    }

    @Test
    fun sectionsAreHashFirstThenAlphabeticalCaseInsensitive() {
        val sections = buildSections(listOf("chrome", "Camera", "1Password", "Ádobe", "apple"), { it }, Locale.US)
        assertEquals(listOf("#", "A", "C"), sections.map { it.key })
        assertEquals(listOf("Ádobe", "apple"), sections[1].items)
        assertEquals(listOf("Camera", "chrome"), sections[2].items)
    }

    @Test
    fun searchMatchesSubstringAndInitials() {
        assertTrue(matchesQuery("Google Maps", "map"))
        assertTrue(matchesQuery("Google Maps", "gm"))
        assertFalse(matchesQuery("Google Maps", "xyz"))
    }

    @Test
    fun groupingTakesFirstAppsSlotAndPullsFromOtherFolders() {
        val start = LauncherLayout(
            home = listOf(HomeItem.App("x"), HomeItem.App("a"), HomeItem.App("b"), HomeItem.FolderRef("old")),
            folders = mapOf("old" to Folder("old", "Old", listOf("c"))),
        )
        val out = start.groupIntoFolder(listOf("a", "c"), "Games", null, "new")
        assertEquals(listOf(HomeItem.App("x"), HomeItem.FolderRef("new"), HomeItem.App("b")), out.home)
        assertEquals(listOf("a", "c"), out.folders["new"]!!.apps)
        assertFalse("old" in out.folders) // emptied folder is removed
    }

    @Test
    fun addingToExistingFolderAppends() {
        val start = LauncherLayout(home = listOf(HomeItem.FolderRef("f")), folders = mapOf("f" to Folder("f", "Work", listOf("a"))))
        val out = start.groupIntoFolder(listOf("b"), "ignored", "f", "new")
        assertEquals(listOf("a", "b"), out.folders["f"]!!.apps)
        assertEquals(listOf<HomeItem>(HomeItem.FolderRef("f")), out.home)
    }

    @Test
    fun ungroupPutsAppsWhereFolderWas() {
        val start = LauncherLayout(
            home = listOf(HomeItem.App("x"), HomeItem.FolderRef("f"), HomeItem.App("y")),
            folders = mapOf("f" to Folder("f", "F", listOf("a", "b"))),
        )
        assertEquals(listOf("x", "a", "b", "y"), start.ungroup("f").home.map { (it as HomeItem.App).key })
    }

    @Test
    fun dockIsCapped() {
        val out = LauncherLayout().addToDock((1..8).map { "app$it" })
        assertEquals(DOCK_CAPACITY, out.dock.size)
    }

    @Test
    fun uninstallRemovesEveryReference() {
        val key = "com.foo/.Main#0"
        val start = LauncherLayout(
            home = listOf(HomeItem.App(key), HomeItem.FolderRef("f")),
            dock = listOf(key),
            folders = mapOf("f" to Folder("f", "F", listOf(key))),
            hidden = setOf(key),
        )
        val out = start.removePackage("com.foo", 0)
        assertEquals(LauncherLayout(), out)
    }
}
