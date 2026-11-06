# Arc Launcher

An original Android home-screen launcher for the Pixel 8a (Android 14/15, works on 10+), written in
**Kotlin** with **Jetpack Compose** and **Material 3**. Inspired by the feature set of launchers like
Microsoft Launcher, with its own look.

## Features

- **Home screen** over your wallpaper: clock and date glance (tap opens alarms), a search pill, a 4-column grid of apps, folders and widgets.
- **Movable icons / custom layouts**: each home page is a fixed grid (4 columns; 5 rows on page 1 under the clock, 6 on the others). Long-press an app, folder or widget and drag it to any free cell; an outline shows where it will land and gaps are kept. Drop an app on another app to make a folder there, or onto a folder to add it. Lifting without moving leaves the long-press menu open instead.
- **Multiple home pages**: swipe sideways; dots show where you are. Long-press empty space → **Add page to the left / right** (empty pages are kept), **Make this the home page**, or **Remove this page**. Apps, folders and widgets have **Move to previous / next page**. Home on a clean screen returns to page 1.
- **Widgets**: long-press empty space → **Add widget** opens a picker grouped by app with previews. Android asks once to allow widget access, and widgets with a setup screen open it. Long-press a widget to **Resize** (width 1–4, height 1–6 cells), move it between pages, or **Remove** it.
- **Removable clock and search bar**: long-press either → Remove, or toggle them from the home long-press menu. Hiding the clock gives the home page a sixth row.
- **Dock** of up to 5 apps (seeded on first run with your default Phone, Messages, Browser and Camera). Long-press and drag an icon sideways to reorder it.
- **Drag from the drawer**: long-press an app in the drawer and, without lifting, move it. The drawer gets out of the way and you drop the app on any home cell, onto another app to make a folder, onto a folder, or onto the dock. Hold it at the screen edge to flip pages.
- **Custom icons**: long-press an app (home, dock, or one selected app in the drawer) → **Change icon** to use any picture (Android photo picker, no storage permission), or **Reset icon**. Long-press empty home space → **Icon pack** to use any installed pack in the standard ADW/Nova `appfilter.xml` format.
- **Default launcher prompt**: each time the app opens while it isn't your Home app, it asks once; **Yes** opens Android's Default home app screen.
- **App drawer**: swipe up (or tap the ^ handle). Alphabetical with **sticky letter headers** (`#` for anything that doesn't start with A–Z), an **A–Z fast-scroll strip** you can drag, and **search** (substring or initials, e.g. "gm" finds Google Maps; the keyboard's Go key opens the best match).
- **Bulk selection**: long-press any app in the drawer to enter selection mode, tap more apps to check them, then use the **⋮ menu at the top right**: Group into folder, Add to home screen, Add to dock, App info, Uninstall, Hide / Unhide, Select all, Cancel.
- **Folders** on the home screen: tap to open, tap the name to rename, long-press an app inside for Remove from folder / App info / Uninstall, or Ungroup the whole folder.
- **Long-press menus**: home apps (Remove, Move to dock, App info, Uninstall), dock apps, folders, and empty home space (Add widget, Add page, Remove page, Show/Hide search bar, Wallpaper, All apps).
- **Hidden apps**: hidden from the drawer; show them again from the drawer's ⋮ menu, then select and Unhide.
- Swipe down on the home screen pulls down the notification shade. Pressing Home always returns to a clean home screen.
- Work-profile apps show with their briefcase badge.

## Install on your phone (Linux)

1. On the phone: **Settings > About phone**, tap **Build number** 7 times. Then **Settings > System > Developer options**, turn on **USB debugging**.
2. Plug the phone in and accept the "Allow USB debugging?" prompt.
3. Build and install:

```bash
./build-apk.sh --install
```

or, with an APK you already have:

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. Press **Home** and pick **Arc Launcher** → **Always**. To switch later: **Settings > Apps > Default apps > Home app**.

If several devices are connected (for example a Quest headset), pick the phone with `adb devices` and `adb -s <serial> install -r ...`.

To remove it: switch the Home app back to Pixel Launcher, then `adb uninstall com.kevin.arclauncher`.

## How it's built (design choices)

- **HOME intent filter** (`MAIN` + `HOME` + `DEFAULT`) is what makes Android offer it as a home app. The activity is **`singleTask`**, so pressing Home while it's open delivers `onNewIntent`, which closes the drawer and folders.
- **Wallpaper showing through**: the theme sets `android:windowShowWallpaper` with a transparent window background, and the app runs **edge-to-edge** with transparent system bars. Colors come from **Material You dynamic color** (`dynamicDarkColorScheme`), so menus match your wallpaper.
- **`LauncherApps`** (not `PackageManager`) lists launchable activities for every user profile and pushes install / uninstall / update callbacks, so the drawer updates live. A `<queries>` block covers Android 11+ **package visibility**.
- **Icons** are loaded off the main thread with `LauncherActivityInfo.getBadgedIcon` and kept in an **LRU cache**.
- **Sorting** uses a locale-aware `Collator`; accents are stripped with Unicode **NFD normalization** so "Évernote" files under E. The drawer is a `LazyColumn` with **`stickyHeader`** per letter and rows of 4; the fast-scroller maps the touch position to a letter and calls `scrollToItem` on that header (letters with no apps jump to the next one that has some).
- **Uninstall** uses the system **`ACTION_DELETE`** dialog (needs the normal `REQUEST_DELETE_PACKAGES` permission). For several apps, intents are queued and each starts when the previous dialog returns (`registerForActivityResult`), so you confirm them one by one. Built-in system apps are skipped with a note, since they can only be disabled from App info.
- **Pages** are a Compose `HorizontalPager`; each page is a fixed cell grid laid out with `BoxWithConstraints` and absolute offsets. Each page stores a `cells` map (item → x, y). A pure `placeItems` function keeps saved cells, gives new items the first free spot, and `normalized()` spills anything that doesn't fit onto the next page, so nothing is ever hidden.
- **Drag-and-drop** uses one pointer detector per tile (`homeItemGestures`): tap before the long-press timeout launches, long-press opens the menu, and moving past touch slop turns into a drag. The tile stays in place (dimmed) while a "ghost" copy follows the finger. Widgets do the same through their `AppWidgetHostView`, calling `requestDisallowInterceptTouchEvent` so the pager doesn't steal the drag.
- **Widgets** use the platform **`AppWidgetHost`** (started in `onStart`, stopped in `onStop`): `allocateAppWidgetId` → `bindAppWidgetIdIfAllowed`, or the system **`ACTION_APPWIDGET_BIND`** permission prompt → the provider's configure activity via `startAppWidgetConfigureActivityForResult` → placed with a size from `targetCellWidth/Height` (Android 12+) or `minWidth/minHeight`. The real size is reported back with `updateAppWidgetSize` so widgets pick the right layout. Widgets are Android Views shown through Compose **`AndroidView`** interop; a custom `AppWidgetHostView` watches `onInterceptTouchEvent` to catch long-presses before the widget's own buttons do. Swipe and long-press detectors skip widget areas so scrolling widgets still scroll.
- **Layout** (pages, dock, folders, hidden) is one `kotlinx.serialization` JSON blob in `SharedPreferences`. Apps are referenced by `package/.Activity#userSerial`. Layout edits are pure functions in `data/LauncherLayout.kt`, covered by unit tests (`./gradlew testDebugUnitTest`).
- **Gestures**: swipe up/down is detected on the **Initial pointer pass** without consuming touches, so it doesn't fight with taps, long-presses or scrolling.
- Swipe-down-for-notifications calls the hidden `StatusBarManager.expandNotificationsPanel` via reflection (common in launchers); if a future Android blocks it, the gesture just does nothing.

## Not done yet

Dragging home items across pages (drawer drags can flip pages; home items use Move to previous / next page) and dragging home items into the dock.
