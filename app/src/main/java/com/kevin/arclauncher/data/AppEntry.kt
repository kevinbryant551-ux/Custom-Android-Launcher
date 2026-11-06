package com.kevin.arclauncher.data

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle

/**
 * One launchable activity. [key] is "package/.Activity#userSerial", so the same app in a work
 * profile is a separate entry.
 */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val info: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName
}
