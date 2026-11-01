package com.benclift.sortedapp

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val HOST_ID = 1024
        private const val REQ_BIND = 1
        private const val REQ_CONFIGURE = 2
    }

    // Bumped whenever the app list might have changed, which triggers a reload.
    private val refreshTick = mutableIntStateOf(0)

    // Bumped when the Home button is pressed while this app is already on screen.
    private val homeTick = mutableIntStateOf(0)

    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var widgetStore: WidgetStore
    private val widgetIds = mutableStateListOf<Int>()
    private var pendingWidgetId = -1

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTick.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        widgetHost = AppWidgetHost(this, HOST_ID)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetStore = WidgetStore(this)
        // Drop any saved widgets whose app has since been uninstalled.
        widgetStore.load().forEach { id ->
            if (widgetManager.getAppWidgetInfo(id) != null) widgetIds.add(id) else widgetStore.remove(id)
        }

        // Refresh the list when apps are installed, updated or removed.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SortedScreen(
                    refreshTick = refreshTick.intValue,
                    homeTick = homeTick.intValue,
                    widgetIds = widgetIds,
                    widgetHost = widgetHost,
                    widgetManager = widgetManager,
                    onAddWidget = ::addWidget,
                    onRemoveWidget = ::removeWidget
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed: go back to the top of the home page and close anything open.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeTick.intValue++
        }
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) { }
    }

    override fun onStop() {
        try { widgetHost.stopListening() } catch (_: Exception) { }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // ---- Adding and removing widgets -------------------------------------------------

    private fun addWidget(provider: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        if (widgetManager.bindAppWidgetIdIfAllowed(id, provider.provider)) {
            configureOrFinish(id)
        } else {
            // Android asks you to approve this app showing that widget.
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQ_BIND)
        }
    }

    private fun configureOrFinish(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            // Some widgets (e.g. a specific contact or clock style) have a setup screen first.
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            } catch (_: Exception) {
                finishAdd(id)
            }
        } else {
            finishAdd(id)
        }
    }

    private fun finishAdd(id: Int) {
        widgetStore.add(id)
        widgetIds.add(id)
        pendingWidgetId = -1
    }

    private fun cancelAdd() {
        if (pendingWidgetId != -1) {
            widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
        }
    }

    private fun removeWidget(id: Int) {
        widgetHost.deleteAppWidgetId(id)
        widgetStore.remove(id)
        widgetIds.remove(id)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND ->
                if (resultCode == RESULT_OK && pendingWidgetId != -1) configureOrFinish(pendingWidgetId) else cancelAdd()
            REQ_CONFIGURE ->
                if (resultCode == RESULT_OK && pendingWidgetId != -1) finishAdd(pendingWidgetId) else cancelAdd()
        }
    }
}
