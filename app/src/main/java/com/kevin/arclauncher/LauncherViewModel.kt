package com.kevin.arclauncher

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.data.HomeItem
import com.kevin.arclauncher.data.IconLoader
import com.kevin.arclauncher.data.LauncherLayout
import com.kevin.arclauncher.data.LayoutStore
import com.kevin.arclauncher.data.addToDock
import com.kevin.arclauncher.data.addToHome
import com.kevin.arclauncher.data.groupIntoFolder
import com.kevin.arclauncher.data.hide
import com.kevin.arclauncher.data.removeFromDock
import com.kevin.arclauncher.data.removeFromFolder
import com.kevin.arclauncher.data.removeFromHome
import com.kevin.arclauncher.data.removePackage
import com.kevin.arclauncher.data.renameFolder
import com.kevin.arclauncher.data.ungroup
import com.kevin.arclauncher.data.unhide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val launcherApps = application.getSystemService(LauncherApps::class.java)
    private val userManager = application.getSystemService(UserManager::class.java)
    private val store = LayoutStore(application)

    val iconLoader = application.resources.displayMetrics.let {
        IconLoader(it.densityDpi, (64 * it.density).toInt())
    }

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _layout = MutableStateFlow(store.load())
    val layout = _layout.asStateFlow()

    /** Fires when the Home button is pressed while we're already the front activity. */
    private val _homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePresses = _homePresses.asSharedFlow()

    private var reloadJob: Job? = null

    // LauncherApps pushes install / uninstall / update events for every user profile.
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            iconLoader.evict(packageName)
            val serial = userManager.getSerialNumberForUser(user)
            edit { it.removePackage(packageName, serial) }
            reload()
        }

        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            iconLoader.evict(packageName)
            reload()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { queryApps() }
            _apps.value = list
            if (!_layout.value.initialized && list.isNotEmpty()) seedDefaults(list)
        }
    }

    fun onHomePressed() {
        _homePresses.tryEmit(Unit)
    }

    private fun queryApps(): List<AppEntry> {
        val self = getApplication<Application>().packageName
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                .filter { it.componentName.packageName != self }
                .map {
                    AppEntry(
                        key = "${it.componentName.flattenToShortString()}#$serial",
                        label = it.label.toString(),
                        component = it.componentName,
                        user = user,
                        userSerial = serial,
                        info = it,
                    )
                }
        }
    }

    /** First run: dock gets the default phone / messages / browser / camera apps. */
    private fun seedDefaults(list: List<AppEntry>) {
        val pm = getApplication<Application>().packageManager
        val me = Process.myUserHandle()
        fun keyFor(pkg: String?) = list.firstOrNull { it.packageName == pkg && it.user == me }?.key
        fun defaultFor(intent: Intent) = keyFor(
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?.takeIf { it != "android" } // "android" means the "choose an app" resolver
        )

        val dock = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        ).mapNotNull(::defaultFor).distinct()
        val home = listOf(
            "com.android.vending",
            "com.google.android.apps.photos",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.android.settings",
        ).mapNotNull(::keyFor).filterNot { it in dock }

        edit { it.addToDock(dock).addToHome(home).copy(initialized = true) }
    }

    private fun edit(change: (LauncherLayout) -> LauncherLayout) {
        _layout.update(change)
        store.save(_layout.value)
    }

    fun groupIntoFolder(keys: List<String>, name: String, targetFolderId: String?) =
        edit { it.groupIntoFolder(keys, name, targetFolderId, UUID.randomUUID().toString()) }

    fun addToHome(keys: List<String>) = edit { it.addToHome(keys) }

    /** Returns how many of [keys] are now in the dock. */
    fun addToDock(keys: List<String>): Int {
        edit { it.addToDock(keys) }
        return keys.count { it in _layout.value.dock }
    }

    fun moveToDock(item: HomeItem.App): Boolean {
        if (addToDock(listOf(item.key)) == 0) return false
        edit { it.removeFromHome(item) }
        return true
    }

    fun removeFromHome(item: HomeItem) = edit { it.removeFromHome(item) }
    fun removeFromDock(key: String) = edit { it.removeFromDock(key) }
    fun renameFolder(id: String, name: String) = edit { it.renameFolder(id, name) }
    fun removeFromFolder(id: String, key: String) = edit { it.removeFromFolder(id, key) }
    fun ungroup(id: String) = edit { it.ungroup(id) }
    fun hide(keys: Collection<String>) = edit { it.hide(keys) }
    fun unhide(keys: Collection<String>) = edit { it.unhide(keys) }
}
