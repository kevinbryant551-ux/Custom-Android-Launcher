# Arc Launcher

An original Android home-screen launcher for the Pixel 8a (Android 14/15, works on 10+), written in
**Kotlin** with **Jetpack Compose** and **Material 3**. Inspired by the feature set of launchers like
Microsoft Launcher, with its own look.

## Features

- **Home screen** over your wallpaper: clock and date glance (tap opens alarms), a search pill, a 4-column grid of apps and folders.
- **Dock** of up to 5 apps (seeded on first run with your default Phone, Messages, Browser and Camera).
- **App drawer**: swipe up (or tap the ^ handle). Alphabetical with **sticky letter headers** (`#` for anything that doesn't start with A–Z), an **A–Z fast-scroll strip** you can drag, and **search** (substring or initials, e.g. "gm" finds Google Maps; the keyboard's Go key opens the best match).
- **Bulk selection**: long-press any app in the drawer to enter selection mode, tap more apps to check them, then use the **⋮ menu at the top right**: Group into folder, Add to home screen, Add to dock, App info, Uninstall, Hide / Unhide, Select all, Cancel.
- **Folders** on the home screen: tap to open, tap the name to rename, long-press an app inside for Remove from folder / App info / Uninstall, or Ungroup the whole folder.
- **Long-press menus**: home apps (Remove, Move to dock, App info, Uninstall), dock apps, folders, and empty home space (Wallpaper, All apps).
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
- **Layout** (home, dock, folders, hidden) is one `kotlinx.serialization` JSON blob in `SharedPreferences`. Apps are referenced by `package/.Activity#userSerial`. Layout edits are pure functions in `data/LauncherLayout.kt`, covered by unit tests (`./gradlew testDebugUnitTest`).
- **Gestures**: swipe up/down is detected on the **Initial pointer pass** without consuming touches, so it doesn't fight with taps, long-presses or scrolling.
- Swipe-down-for-notifications calls the hidden `StatusBarManager.expandNotificationsPanel` via reflection (common in launchers); if a future Android blocks it, the gesture just does nothing.

## Not done yet

Drag-and-drop rearranging, multiple home pages, widgets, and icon packs.
