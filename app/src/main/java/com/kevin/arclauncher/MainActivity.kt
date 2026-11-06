package com.kevin.arclauncher

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.kevin.arclauncher.data.AppEntry
import com.kevin.arclauncher.ui.ArcTheme
import com.kevin.arclauncher.ui.LauncherRoot

class MainActivity : ComponentActivity(), LauncherActions {
    private val viewModel: LauncherViewModel by viewModels()

    // Bulk uninstall / app info: the system shows one screen per app, so we queue the intents and
    // start the next one when the previous screen returns.
    private val pending = ArrayDeque<Intent>()
    private val chain = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { startNext() }

    // Widget being added: allocated id, provider and target page, kept across the bind/configure screens.
    private var pendingWidget: Triple<Int, AppWidgetProviderInfo, Int>? = null
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) configureOrPlaceWidget() else cancelPendingWidget()
    }

    private var iconTargetKey: String? = null
    // The Android photo picker needs no storage permission.
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = iconTargetKey
        iconTargetKey = null
        if (uri != null && key != null) viewModel.setCustomIcon(key, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ArcTheme {
                LauncherRoot(viewModel, this)
            }
        }
    }

    // The host only receives widget updates while listening; stop when we're not visible.
    override fun onStart() {
        super.onStart()
        runCatching { viewModel.widgetHost.startListening() }
    }

    override fun onStop() {
        super.onStop()
        runCatching { viewModel.widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask + HOME: pressing Home while we're showing delivers the intent here.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }

    private val launcherApps get() = getSystemService(LauncherApps::class.java)

    override fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            toast("Can't open ${app.label}")
        }
    }

    override fun appInfo(apps: List<AppEntry>) {
        if (apps.size == 1) {
            val app = apps.first()
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
            return
        }
        apps.distinctBy { it.packageName }.forEach {
            pending.addLast(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.packageName, null)))
        }
        startNext()
    }

    override fun uninstall(apps: List<AppEntry>) {
        val (system, removable) = apps.distinctBy { it.packageName }.partition { isBuiltIn(it) }
        if (system.isNotEmpty()) {
            toast("Skipped built-in: " + system.joinToString { it.label } + ". Use App info to disable.")
        }
        removable.forEach {
            // ACTION_DELETE opens the system "Uninstall this app?" dialog; needs REQUEST_DELETE_PACKAGES.
            pending.addLast(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", it.packageName, null)))
        }
        startNext()
    }

    private fun isBuiltIn(app: AppEntry): Boolean {
        val flags = app.info.applicationInfo.flags
        return flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }

    private fun startNext() {
        val next = pending.removeFirstOrNull() ?: return
        try {
            chain.launch(next)
        } catch (e: ActivityNotFoundException) {
            startNext()
        }
    }

    override fun openWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper"))
        } catch (e: ActivityNotFoundException) {
            toast("No wallpaper picker found")
        }
    }

    override fun openClock() {
        try {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // No clock app; nothing to do.
        }
    }

    /** StatusBarManager.expandNotificationsPanel() is hidden API; launchers have long called it via reflection. */
    @SuppressLint("WrongConstant")
    override fun expandNotifications() {
        try {
            val statusBar = getSystemService("statusbar")
            statusBar.javaClass.getMethod("expandNotificationsPanel").invoke(statusBar)
        } catch (e: Throwable) {
            // Blocked on this build; swipe-down simply does nothing.
        }
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun addWidget(provider: AppWidgetProviderInfo, page: Int) {
        val host = viewModel.widgetHost
        val id = host.allocateAppWidgetId()
        pendingWidget = Triple(id, provider, page)
        val manager = AppWidgetManager.getInstance(this)
        if (manager.bindAppWidgetIdIfAllowed(id, provider.profile, provider.provider, null)) {
            configureOrPlaceWidget()
        } else {
            // First time for this provider: the system asks "Allow Arc Launcher to create widgets?".
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile),
            )
        }
    }

    private fun configureOrPlaceWidget() {
        val (id, provider, _) = pendingWidget ?: return
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (provider.configure == null || optional) {
            placePendingWidget()
            return
        }
        try {
            viewModel.widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE_WIDGET, null)
        } catch (e: Exception) {
            toast("Couldn't set up that widget")
            cancelPendingWidget()
        }
    }

    // The configure screen is started by AppWidgetHost with a request code, so its result comes here.
    @Deprecated("AppWidgetHost only offers the request-code API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            if (resultCode == RESULT_OK) placePendingWidget() else cancelPendingWidget()
        }
    }

    private fun placePendingWidget() {
        val (id, provider, page) = pendingWidget ?: return
        pendingWidget = null
        viewModel.placeWidget(page, id, provider)
    }

    private fun cancelPendingWidget() {
        pendingWidget?.let { viewModel.widgetHost.deleteAppWidgetId(it.first) }
        pendingWidget = null
    }

    override fun pickIcon(key: String) {
        iconTargetKey = key
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun isDefaultHome(): Boolean {
        val role = getSystemService(RoleManager::class.java)
        if (role != null && role.isRoleAvailable(RoleManager.ROLE_HOME)) return role.isRoleHeld(RoleManager.ROLE_HOME)
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return home?.activityInfo?.packageName == packageName
    }

    override fun openDefaultHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private companion object {
        const val REQUEST_CONFIGURE_WIDGET = 11
    }
}
