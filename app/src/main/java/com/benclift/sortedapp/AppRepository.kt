package com.benclift.sortedapp

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One launchable app on the phone. */
data class AppItem(
    val label: String,
    val pkg: String,
    val systemCategory: Int,
    val icon: ImageBitmap
)

/** Reads every launchable app from the system. Call off the main thread. */
fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    return pm.queryIntentActivities(intent, 0)
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            AppItem(
                label = info.loadLabel(pm).toString(),
                pkg = info.activityInfo.packageName,
                systemCategory = info.activityInfo.applicationInfo.category,
                icon = info.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
            )
        }
}

/** Opens an app by package name. */
fun launchApp(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}

/** Remembers the categories you picked by hand. Stored on the phone only. */
class OverrideStore(context: Context) {
    private val prefs = context.getSharedPreferences("category_overrides", Context.MODE_PRIVATE)

    fun all(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    fun set(pkg: String, category: String) {
        prefs.edit().putString(pkg, category).apply()
    }

    fun clear(pkg: String) {
        prefs.edit().remove(pkg).apply()
    }
}
