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

/** Remembers which widgets are on the home page, in order. */
class WidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun load(): List<Int> =
        (prefs.getString("ids", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

    private fun save(ids: List<Int>) {
        prefs.edit().putString("ids", ids.joinToString(",")).apply()
    }

    fun add(id: Int) = save(load() + id)

    fun remove(id: Int) {
        save(load() - id)
        saveHeights(heights() - id)
    }

    /** Heights (in dp) you set for each widget, keyed by widget id. */
    fun heights(): Map<Int, Int> =
        (prefs.getString("heights", "") ?: "").split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            val id = parts.getOrNull(0)?.toIntOrNull()
            val dp = parts.getOrNull(1)?.toIntOrNull()
            if (id != null && dp != null) id to dp else null
        }.toMap()

    fun setHeight(id: Int, dp: Int) = saveHeights(heights() + (id to dp))

    private fun saveHeights(map: Map<Int, Int>) {
        prefs.edit().putString("heights", map.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }
}

/** Remembers which category boxes you collapsed. */
class UiStore(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    fun collapsed(): Set<String> = prefs.getStringSet("collapsed", emptySet()) ?: emptySet()

    fun setCollapsed(categories: Set<String>) {
        prefs.edit().putStringSet("collapsed", categories).apply()
    }

    /** Apps pinned to the home grid, in the order you added them (package names). */
    fun pinned(): List<String> =
        (prefs.getString("pinned", "") ?: "").split(",").filter { it.isNotBlank() }

    fun setPinned(pkgs: List<String>) {
        prefs.edit().putString("pinned", pkgs.joinToString(",")).apply()
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
