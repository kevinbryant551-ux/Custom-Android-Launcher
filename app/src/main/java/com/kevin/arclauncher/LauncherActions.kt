package com.kevin.arclauncher

import com.kevin.arclauncher.data.AppEntry

/** Things the UI asks the system to do. Implemented by [MainActivity]. */
interface LauncherActions {
    fun launch(app: AppEntry)
    fun appInfo(apps: List<AppEntry>)
    fun uninstall(apps: List<AppEntry>)
    fun openWallpaperPicker()
    fun openClock()
    fun expandNotifications()
    fun toast(message: String)
}
