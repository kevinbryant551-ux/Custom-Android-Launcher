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

    @Test
    fun savedCellsAreKeptAndOthersFillFirstFreeSpot() {
        val a = HomeItem.App("a")
        val b = HomeItem.App("b")
        val w = HomeItem.Widget(1, 2, 2)
        val (placed, overflow) = placeItems(listOf(a, b, w), mapOf(a.cellKey to Cell(3, 4), w.cellKey to Cell(0, 0)), 4, 5)
        assertTrue(overflow.isEmpty())
        assertEquals(Placed(a, 3, 4, 1, 1), placed[0])
        assertEquals(Placed(b, 2, 0, 1, 1), placed[1]) // first free cell after the 2x2 widget
        assertEquals(Placed(w, 0, 0, 2, 2), placed[2])
    }

    @Test
    fun overflowSpillsOntoANewPage() {
        val apps = (1..FIRST_PAGE_ROWS * HOME_COLUMNS + 3).map { HomeItem.App("app$it") }
        val out = LauncherLayout(pages = listOf(HomePage(apps))).normalized()
        assertEquals(2, out.pages.size)
        assertEquals(FIRST_PAGE_ROWS * HOME_COLUMNS, out.pages[0].items.size)
        assertEquals(3, out.pages[1].items.size)
        assertEquals(Cell(0, 0), out.pages[1].cells[apps.last().let { apps[apps.size - 3] }.cellKey])
    }

    @Test
    fun moveItemOnlyIntoFreeCells() {
        val a = HomeItem.App("a")
        val b = HomeItem.App("b")
        val l = LauncherLayout(pages = listOf(HomePage(listOf(a, b)))).normalized()
        assertEquals(null, l.moveItem(0, a, 1, 0)) // b is there
        val moved = l.moveItem(0, a, 2, 3)!!.normalized()
        assertEquals(Cell(2, 3), moved.pages[0].cells[a.cellKey])
        assertEquals(Cell(1, 0), moved.pages[0].cells[b.cellKey]) // b stays put
    }

    @Test
    fun droppingAppOnAppMakesFolderInTargetCell() {
        val a = HomeItem.App("a")
        val b = HomeItem.App("b")
        val l = LauncherLayout(pages = listOf(HomePage(listOf(a, b), mapOf(a.cellKey to Cell(0, 0), b.cellKey to Cell(3, 2)))))
        val out = l.groupIntoFolder(listOf("b", "a"), "Folder", null, "f").normalized()
        assertEquals(listOf<HomeItem>(HomeItem.FolderRef("f")), out.pages[0].items)
        assertEquals(Cell(3, 2), out.pages[0].cells["folder:f"])
        assertEquals(listOf("b", "a"), out.folders["f"]!!.apps)
    }

    @Test
    fun pagesLeftOfHomeKeepHomeAndRemovalAdjustsIt() {
        var l = LauncherLayout()
        l = l.insertPage(0) // new page to the left of home
        assertEquals(2, l.pages.size)
        assertEquals(1, l.homePage)
        l = l.addPage(1) // to the right of home
        assertEquals(1, l.homePage)
        l = l.removePage(0)
        assertEquals(0, l.homePage)
        assertEquals(FIRST_PAGE_ROWS, l.rowsFor(0))
        assertEquals(PAGE_ROWS, l.copy(showClock = false).rowsFor(0))
    }

    @Test
    fun dockReorders() {
        val l = LauncherLayout(dock = listOf("a", "b", "c", "d"))
        assertEquals(listOf("b", "c", "a", "d"), l.moveInDock("a", 2).dock)
        assertEquals(listOf("d", "a", "b", "c"), l.moveInDock("d", 0).dock)
    }

    @Test
    fun appFromDrawerLandsInChosenCellAndMovesIfAlreadyOnHome() {
        val a = HomeItem.App("a")
        var l = LauncherLayout(pages = listOf(HomePage(listOf(a)), HomePage())).normalized()
        l = l.placeApp("b", 0, 3, 4).normalized()
        assertEquals(Cell(3, 4), l.pages[0].cells["app:b"])
        l = l.placeApp("a", 1, 2, 2).normalized()
        assertFalse(a in l.pages[0].items)
        assertEquals(Cell(2, 2), l.pages[1].cells[a.cellKey])
    }

    @Test
    fun iconPackComponentsParse() {
        assertEquals("com.foo/com.foo.Main", IconPack.parseComponent("ComponentInfo{com.foo/com.foo.Main}"))
        assertEquals("com.foo/com.foo.Main", IconPack.parseComponent("ComponentInfo{com.foo/.Main}"))
        assertEquals(null, IconPack.parseComponent(":LAUNCHER_ACTION_APP_DRAWER"))
    }
}

