package com.benclift.sortedapp

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Icon size on the home grid and in the app list. Make this smaller or bigger to taste. */
private val ICON_SIZE = 40.dp

/** How many tiles fit across the home grid. */
private const val HOME_COLUMNS = 5

/** Favourites get bigger icons and fewer columns. */
private val FAV_ICON_SIZE = 58.dp
private const val FAV_COLUMNS = 4

private val shadow = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), 6f)

private val labelStyle = TextStyle(
    color = Color.White,
    fontSize = 10.sp,
    textAlign = TextAlign.Center,
    shadow = shadow
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SortedScreen(
    refreshTick: Int,
    homeTick: Int,
    widgetIds: List<Int>,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    onAddWidget: (AppWidgetProviderInfo) -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val context = LocalContext.current
    val store = remember { OverrideStore(context) }
    val uiStore = remember { UiStore(context) }
    val widgetStore = remember { WidgetStore(context) }
    val listState = rememberLazyListState()
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Nothing to do: the pill hides itself when the app resumes and is the default home.
    }

    var widgetHeights by remember { mutableStateOf(widgetStore.heights()) }
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var overrides by remember { mutableStateOf(store.all()) }
    var pinned by remember { mutableStateOf(uiStore.pinned()) }
    var drawerOpen by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<AppItem?>(null) }
    var pickingWidget by remember { mutableStateOf(false) }
    var removingWidget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(refreshTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // Pressing Home while already here: close everything and scroll back to the top.
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            drawerOpen = false
            openFolder = null
            moving = null
            pickingWidget = false
            removingWidget = null
            listState.animateScrollToItem(0)
        }
    }

    // Is this app currently the phone's default home app?
    val isDefaultHome = remember(refreshTick) {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    // This is a home screen: the back button never closes it, it only closes things on top.
    BackHandler(enabled = true) {
        if (openFolder != null) openFolder = null else drawerOpen = false
    }

    val grouped = remember(apps, overrides) {
        apps.groupBy { Categorizer.categorize(it, overrides[it.pkg]) }
            .mapValues { (_, list) -> list.sortedBy { it.label.lowercase() } }
    }
    val folders = Categories.order.filter { grouped.containsKey(it) }
    val pinnedApps = remember(apps, pinned) {
        val byPkg = apps.associateBy { it.pkg }
        pinned.mapNotNull { byPkg[it] }
    }

    // ---------------------------------------------------------------- Home page
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                // Swipe up anywhere on the home page to open the full app list.
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -30f) drawerOpen = true }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 110.dp)
        ) {

            // One-tap shortcut to the phone setting that makes the Home button open this app.
            if (!isDefaultHome) {
                item(key = "make-default") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tap to make SortedApp your Home button app",
                            style = labelStyle.copy(fontSize = 13.sp, shadow = null),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable {
                                    // Best option: Android's own "Set as default home app?" prompt.
                                    val roleManager = if (Build.VERSION.SDK_INT >= 29) {
                                        context.getSystemService(RoleManager::class.java)
                                    } else null
                                    val canAsk = roleManager != null &&
                                        roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                                        !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                                    if (canAsk && roleManager != null) {
                                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                                    } else {
                                        // Fallback: open the phone's Home app setting.
                                        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(
                                                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            item(key = "widgets-header") {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { pickingWidget = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Add widget", style = labelStyle.copy(fontSize = 12.sp), modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            items(widgetIds, key = { "widget-$it" }) { id ->
                WidgetBox(
                    id = id,
                    host = widgetHost,
                    manager = widgetManager,
                    savedHeightDp = widgetHeights[id],
                    onResize = { dp ->
                        widgetStore.setHeight(id, dp)
                        widgetHeights = widgetStore.heights()
                    },
                    onRemove = { removingWidget = id }
                )
            }

            // ---- Favourites: a few apps with bigger icons ----
            item(key = "favourites") {
                if (pinnedApps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Favourites",
                            style = labelStyle.copy(fontSize = 12.sp, textAlign = TextAlign.Start),
                            modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp)
                        )
                        pinnedApps.chunked(FAV_COLUMNS).forEach { rowApps ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                rowApps.forEach { app ->
                                    AppTile(
                                        app = app,
                                        modifier = Modifier.weight(1f),
                                        iconSize = FAV_ICON_SIZE,
                                        onClick = { launchApp(context, app.pkg) },
                                        onLongClick = { moving = app }
                                    )
                                }
                                repeat(FAV_COLUMNS - rowApps.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Favourites: long-press any app and choose Add to favourites",
                        style = labelStyle.copy(fontSize = 11.sp, shadow = null),
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            item(key = "grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    // One folder per category, HOME_COLUMNS per row, each row spread across the full width.
                    folders.chunked(HOME_COLUMNS).forEach { rowFolders ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowFolders.forEach { category ->
                                FolderTile(
                                    title = category,
                                    apps = grouped[category].orEmpty(),
                                    modifier = Modifier.weight(1f),
                                    onClick = { openFolder = category }
                                )
                            }
                            // Keep the last row's tiles the same width as the others.
                            repeat(HOME_COLUMNS - rowFolders.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            item(key = "version") {
                val versionName = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                        .getOrNull().orEmpty()
                }
                Text(
                    text = "SortedApp v$versionName",
                    style = labelStyle.copy(fontSize = 9.sp, shadow = null),
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        // "Smart search" bar: opens the full app list with the keyboard ready.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { drawerOpen = true }
        ) {
            Text("Search apps", style = labelStyle.copy(fontSize = 15.sp, shadow = null), color = Color.White)
        }
    }

    // ---------------------------------------------------------------- App list (swipe up / search)
    if (drawerOpen) {
        AppDrawer(
            apps = apps,
            onClose = { drawerOpen = false },
            onLaunch = { launchApp(context, it.pkg) },
            onMore = { moving = it }
        )
    }

    // ---------------------------------------------------------------- Folder popup
    openFolder?.let { category ->
        val list = grouped[category].orEmpty()
        Dialog(
            onDismissRequest = { openFolder = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xEE1C1C1E))
                    .padding(12.dp)
            ) {
                Text(
                    text = "$category · ${list.size}",
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 440.dp)
                ) {
                    items(list, key = { it.pkg }) { app ->
                        AppTile(
                            app = app,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                openFolder = null
                                launchApp(context, app.pkg)
                            },
                            onLongClick = { moving = app }
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- Long-press menu
    moving?.let { app ->
        val isPinned = app.pkg in pinned
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text(app.label) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    item {
                        TextButton(onClick = {
                            pinned = if (isPinned) pinned - app.pkg else pinned + app.pkg
                            uiStore.setPinned(pinned)
                            moving = null
                        }) { Text(if (isPinned) "Remove from favourites" else "Add to favourites") }
                    }
                    item {
                        TextButton(onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.pkg}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            moving = null
                        }) { Text("App info / uninstall") }
                    }
                    item {
                        Text(
                            "Move to folder",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                        )
                    }
                    item {
                        TextButton(onClick = {
                            store.clear(app.pkg)
                            overrides = store.all()
                            moving = null
                        }) { Text("Automatic") }
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

    // ---------------------------------------------------------------- Widget picker
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

/** One app: small icon with a one-line label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppItem,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    iconSize: Dp = ICON_SIZE
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 6.dp)
    ) {
        Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(iconSize))
        Text(
            text = app.label,
            style = if (iconSize > 48.dp) labelStyle.copy(fontSize = 12.sp) else labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** A category folder: a rounded tile showing the first four apps in a 2x2 grid. */
@Composable
fun FolderTile(
    title: String,
    apps: List<AppItem>,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ICON_SIZE)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            val preview = apps.take(4)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.chunked(2).forEach { rowApps ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        rowApps.forEach { Image(bitmap = it.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
        Text(
            text = title,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** Full list of every app, A to Z, with search. Opens with the keyboard ready. */
@Composable
fun AppDrawer(
    apps: List<AppItem>,
    onClose: () -> Unit,
    onLaunch: (AppItem) -> Unit,
    onMore: (AppItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BackHandler(enabled = true) { onClose() }

    val shown = remember(apps, query) {
        val base = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        base.sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.clickable { if (query.isNotEmpty()) query = "" else onClose() }
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(66.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            items(shown, key = { it.pkg }) { app ->
                AppTile(
                    app = app,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onClose()
                        onLaunch(app)
                    },
                    onLongClick = { onMore(app) }
                )
            }
        }
    }
}

/**
 * A hosted widget. Its starting height is generous so the whole widget is visible; use the
 * small + and - buttons in its corner to make it taller or shorter (remembered), or x to remove.
 */
@Composable
fun WidgetBox(
    id: Int,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    savedHeightDp: Int?,
    onResize: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val info = remember(id) { manager.getAppWidgetInfo(id) } ?: return
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp - 24

    // Start taller than the widget's bare minimum, which often crops its contents.
    val defaultHeightDp = remember(id) {
        val minDp = with(density) { info.minHeight.toDp().value }
        val cells = if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) {
            info.targetCellHeight * 74f
        } else 0f
        maxOf(minDp, cells, 120f).toInt()
    }
    val heightDp = (savedHeightDp ?: defaultHeightDp).coerceIn(60, 600)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                host.createView(ctx, id, info).apply { setAppWidget(id, info) }
            },
            update = { view ->
                // Tell the widget how much room it has so it lays itself out to fit.
                view.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            },
            modifier = Modifier.fillMaxSize()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            WidgetButton(onClick = { onResize((heightDp - 40).coerceAtLeast(60)) }) {
                Text("\u2013", color = Color.White, fontSize = 16.sp)
            }
            WidgetButton(onClick = { onResize((heightDp + 40).coerceAtMost(600)) }) {
                Icon(Icons.Default.Add, contentDescription = "Taller", tint = Color.White, modifier = Modifier.size(14.dp))
            }
            WidgetButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove widget", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun WidgetButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onClick() }
    ) { content() }
}
