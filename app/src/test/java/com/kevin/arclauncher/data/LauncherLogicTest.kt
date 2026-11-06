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
            pages = listOf(
                HomePage(listOf(HomeItem.FolderRef("old"))),
                HomePage(listOf(HomeItem.App("x"), HomeItem.App("a"), HomeItem.App("b"))),
            ),
            folders = mapOf("old" to Folder("old", "Old", listOf("c"))),
        )
        val out = start.groupIntoFolder(listOf("a", "c"), "Games", null, "new", currentPage = 0)
        assertEquals(emptyList<HomeItem>(), out.pages[0].items)
        assertEquals(listOf(HomeItem.App("x"), HomeItem.FolderRef("new"), HomeItem.App("b")), out.pages[1].items)
        assertEquals(listOf("a", "c"), out.folders["new"]!!.apps)
        assertFalse("old" in out.folders) // emptied folder is removed
    }

    @Test
    fun addingToExistingFolderAppends() {
        val start = LauncherLayout(pages = listOf(HomePage(listOf(HomeItem.FolderRef("f")))), folders = mapOf("f" to Folder("f", "Work", listOf("a"))))
        val out = start.groupIntoFolder(listOf("b"), "ignored", "f", "new")
        assertEquals(listOf("a", "b"), out.folders["f"]!!.apps)
        assertEquals(listOf<HomeItem>(HomeItem.FolderRef("f")), out.allItems)
    }

    @Test
    fun ungroupPutsAppsWhereFolderWas() {
        val start = LauncherLayout(
            pages = listOf(HomePage(listOf(HomeItem.App("x"), HomeItem.FolderRef("f"), HomeItem.App("y")))),
            folders = mapOf("f" to Folder("f", "F", listOf("a", "b"))),
        )
        assertEquals(listOf("x", "a", "b", "y"), start.ungroup("f").allItems.map { (it as HomeItem.App).key })
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
            pages = listOf(HomePage(listOf(HomeItem.App(key), HomeItem.FolderRef("f")))),
            dock = listOf(key),
            folders = mapOf("f" to Folder("f", "F", listOf(key))),
            hidden = setOf(key),
        )
        val out = start.removePackage("com.foo", 0)
        assertEquals(LauncherLayout(), out)
    }

    @Test
    fun oldSinglePageLayoutMigratesToFirstPage() {
        val old = LauncherLayout(legacyHome = listOf(HomeItem.App("a")))
        val out = old.migrated()
        assertEquals(listOf(HomePage(listOf(HomeItem.App("a")))), out.pages)
        assertTrue(out.legacyHome.isEmpty())
    }

    @Test
    fun pagesCanBeAddedEmptyAndLastPageIsKept() {
        val two = LauncherLayout().addPage(0)
        assertEquals(2, two.pages.size)
        assertEquals(1, two.removePage(1).pages.size)
        assertEquals(1, LauncherLayout().removePage(0).pages.size)
    }

    @Test
    fun widgetsAddResizeMoveAndRemove() {
        val w = HomeItem.Widget(7, 4, 2)
        var l = LauncherLayout().addPage(0).addWidget(1, w)
        assertEquals(listOf<HomeItem>(w), l.pages[1].items)
        l = l.resizeWidget(7, 9, 9, 4)
        val resized = l.pages[1].items.single() as HomeItem.Widget
        assertEquals(4, resized.spanX)
        assertEquals(MAX_WIDGET_SPAN_Y, resized.spanY)
        l = l.moveToPage(resized, 1, 0)
        assertEquals(listOf<HomeItem>(resized), l.pages[0].items)
        assertTrue(l.removeWidget(7).allItems.isEmpty())
    }
}
