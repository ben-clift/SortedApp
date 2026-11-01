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
                    widgetIds = widgetIds,
                    widgetHost = widgetHost,
                    widgetManager = widgetManager,
                    onAddWidget = ::addWidget,
                    onRemoveWidget = ::removeWidget
                )
            }
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SortedScreen(
    refreshTick: Int,
    widgetIds: List<Int>,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val context = LocalContext.current
    val store = remember { OverrideStore(context) }
    val uiStore = remember { UiStore(context) }

    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var overrides by remember { mutableStateOf(store.all()) }
    var collapsed by remember { mutableStateOf(uiStore.collapsed()) }
    var query by remember { mutableStateOf("") }
    var moving by remember { mutableStateOf<AppItem?>(null) }
    var pickingWidget by remember { mutableStateOf(false) }
    var removingWidget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // This is a home screen: the back button should never close it.
    BackHandler(enabled = true) { query = "" }

    val grouped = remember(apps, overrides) {
        apps.groupBy { Categorizer.categorize(it, overrides[it.pkg]) }
            .mapValues { (_, list) -> list.sortedBy { it.label.lowercase() } }
    }
    val boxes = Categories.order.filter { grouped.containsKey(it) }

    val searchResults = remember(apps, query) {
        if (query.isBlank()) emptyList()
        else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.clickable { query = "" }
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (query.isNotBlank()) {
            item(key = "results") {
                AppBox(
                    title = "Results",
                    apps = searchResults,
                    collapsible = false,
                    isCollapsed = false,
                    onToggle = {},
                    onLaunch = { launchApp(context, it.pkg) },
                    onMove = { moving = it }
                )
            }
        } else {
            // ---- Widgets ----
            item(key = "widgets-header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Widgets",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { pickingWidget = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Add widget", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            items(widgetIds, key = { "widget-$it" }) { id ->
                WidgetBox(
                    id = id,
                    host = widgetHost,
                    manager = widgetManager,
                    onRemove = { removingWidget = id }
                )
            }

            // ---- One compact box per category ----
            items(boxes, key = { "box-$it" }) { category ->
                val list = grouped[category].orEmpty()
                val isCollapsed = category in collapsed
                AppBox(
                    title = category,
                    apps = list,
                    collapsible = true,
                    isCollapsed = isCollapsed,
                    onToggle = {
                        collapsed = if (isCollapsed) collapsed - category else collapsed + category
                        uiStore.setCollapsed(collapsed)
                    },
                    onLaunch = { launchApp(context, it.pkg) },
                    onMove = { moving = it }
                )
            }
        }
    }

    // Long-press an app to move it to a different category.
    moving?.let { app ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move ${app.label} to…") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = {
                            store.clear(app.pkg)
                            overrides = store.all()
                            moving = null
                        }) { Text("Reset to automatic") }
                    }
                    items(Categories.order) { category ->
                        TextButton(onClick = {
                            store.set(app.pkg, category)
                            overrides = store.all()
                            moving = null
                        }) { Text(category) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } }
        )
    }

    // Widget picker: every widget installed apps offer for a home screen.
    if (pickingWidget) {
        val providers = remember {
            val pm = context.packageManager
            widgetManager.installedProviders
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .map { info ->
                    val pkg = info.provider.packageName
                    val appName = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    Triple(appName, info.loadLabel(pm), info)
                }
                .sortedWith(compareBy({ it.first.lowercase() }, { it.second.lowercase() }))
        }
        AlertDialog(
            onDismissRequest = { pickingWidget = false },
            title = { Text("Add a widget") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(providers) { (appName, widgetName, info) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    pickingWidget = false
                                    onAddWidget(info)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(widgetName, fontSize = 15.sp)
                            Text(appName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickingWidget = false }) { Text("Cancel") } }
        )
    }

    removingWidget?.let { id ->
        AlertDialog(
            onDismissRequest = { removingWidget = null },
            title = { Text("Remove this widget?") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveWidget(id)
                    removingWidget = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removingWidget = null }) { Text("Cancel") } }
        )
    }
}

/** A hosted widget with a small remove button in the corner. */
@Composable
fun WidgetBox(
    id: Int,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    onRemove: () -> Unit
) {
    val info = remember(id) { manager.getAppWidgetInfo(id) } ?: return
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp - 24
    val heightDp = with(density) { info.minHeight.toDp().value }.coerceAtLeast(70f).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                host.createView(ctx, id, info).apply {
                    setAppWidget(id, info)
                    updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onRemove() }
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove widget",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** A rounded box with a title and a tight grid of small app icons. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppBox(
    title: String,
    apps: List<AppItem>,
    collapsible: Boolean,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onLaunch: (AppItem) -> Unit,
    onMove: (AppItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .then(if (collapsible) Modifier.clickable { onToggle() } else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (collapsible && isCollapsed) "${apps.size}  ▸" else "${apps.size}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
        }

        if (!isCollapsed) {
            FlowRow(modifier = Modifier.fillMaxWidth()) {
                apps.forEach { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onMove(app) }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp, start = 1.dp, end = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
