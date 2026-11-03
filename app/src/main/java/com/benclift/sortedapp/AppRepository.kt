package com.benclift.sortedapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One launchable app on the phone. */
data class AppItem(
    val label: String,
    val pkg: String,
    val systemCategory: Int,
    val icon: ImageBitmap,
    /** The name the system gave the app; used for sorting into folders even if you rename it. */
    val originalLabel: String = label,
    val customLabel: Boolean = false,
    val customIcon: Boolean = false
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

/** Look-and-feel options you can change in Settings. */
data class HomeSettings(
    val iconSizeDp: Int = 40,
    val columns: Int = 5,
    val favIconSizeDp: Int = 40,
    val favColumns: Int = 6,
    val dimPercent: Int = 18,
    val hiddenFolders: Set<String> = emptySet()
)

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

    /** Whether the on-screen controls (Settings, Add widget, widget buttons) are showing. */
    fun editMode(): Boolean = prefs.getBoolean("editMode", false)

    fun setEditMode(on: Boolean) {
        prefs.edit().putBoolean("editMode", on).apply()
    }

    /** Whether the folder boxes are tucked away behind a single line on the home page. */
    fun foldersHidden(): Boolean = prefs.getBoolean("foldersHidden", false)

    fun setFoldersHidden(hidden: Boolean) {
        prefs.edit().putBoolean("foldersHidden", hidden).apply()
    }

    fun settings(): HomeSettings = HomeSettings(
        iconSizeDp = prefs.getInt("iconSize", 40),
        columns = prefs.getInt("columns", 5),
        favIconSizeDp = prefs.getInt("favIconSize", 40),
        favColumns = prefs.getInt("favColumns", 6),
        dimPercent = prefs.getInt("dim", 18),
        hiddenFolders = HashSet(prefs.getStringSet("hiddenFolders", emptySet()) ?: emptySet())
    )

    fun saveSettings(settings: HomeSettings) {
        prefs.edit()
            .putInt("iconSize", settings.iconSizeDp)
            .putInt("columns", settings.columns)
            .putInt("favIconSize", settings.favIconSizeDp)
            .putInt("favColumns", settings.favColumns)
            .putInt("dim", settings.dimPercent)
            .putStringSet("hiddenFolders", HashSet(settings.hiddenFolders))
            .apply()
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


/** Remembers app names and icons you changed by hand. Stored on the phone only. */
class CustomStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("custom_labels", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "custom_icons").also { it.mkdirs() }

    private fun file(pkg: String) = File(dir, "$pkg.png")

    fun labels(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    fun setLabel(pkg: String, label: String) {
        prefs.edit().putString(pkg, label.trim()).apply()
    }

    fun clearLabel(pkg: String) {
        prefs.edit().remove(pkg).apply()
    }

    fun iconPkgs(): Set<String> =
        dir.listFiles()?.map { it.name.removeSuffix(".png") }?.toSet().orEmpty()

    fun loadIcon(pkg: String): ImageBitmap? {
        val f = file(pkg)
        if (!f.exists()) return null
        return runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull()
    }

    /** Reads the picture you chose, shrinks it to a square icon and keeps a private copy. */
    fun saveIcon(pkg: String, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return false
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, 256, 256, true)
        file(pkg).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        true
    }.getOrDefault(false)

    fun clearIcon(pkg: String) {
        file(pkg).delete()
    }
}
