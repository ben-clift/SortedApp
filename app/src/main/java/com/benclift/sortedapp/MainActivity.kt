package com.benclift.sortedapp

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Bumped whenever the app list might have changed, which triggers a reload.
    private val refreshTick = mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshTick.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                SortedScreen(refreshTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTick.intValue++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SortedScreen(refreshTick: Int) {
    val context = LocalContext.current
    val store = remember { OverrideStore(context) }

    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var overrides by remember { mutableStateOf(store.all()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(Categories.ALL) }
    var moving by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(refreshTick) {
        apps = withContext(Dispatchers.Default) { loadApps(context) }
    }

    // Home screen: back button should never close the launcher.
    BackHandler(enabled = true) {
        if (query.isNotEmpty()) query = "" else selected = Categories.ALL
    }

    val categoryOf = remember(apps, overrides) {
        apps.associate { it.pkg to Categorizer.categorize(it, overrides[it.pkg]) }
    }
    val counts = remember(categoryOf) { categoryOf.values.groupingBy { it }.eachCount() }
    val visibleCategories = listOf(Categories.ALL) + Categories.order.filter { (counts[it] ?: 0) > 0 }

    val shown = remember(apps, categoryOf, query, selected) {
        val base = when {
            query.isNotBlank() -> apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
            selected == Categories.ALL -> apps
            else -> apps.filter { categoryOf[it.pkg] == selected }
        }
        base.sortedBy { it.label.lowercase() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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

        Row(modifier = Modifier.fillMaxSize()) {
            // Category sidebar
            LazyColumn(
                modifier = Modifier
                    .width(108.dp)
                    .fillMaxSize()
                    .padding(start = 8.dp)
            ) {
                items(visibleCategories) { category ->
                    val isSelected = query.isBlank() && category == selected
                    val count = if (category == Categories.ALL) apps.size else counts[category] ?: 0
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable {
                                query = ""
                                selected = category
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = "$count", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }
            }

            // App grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(shown, key = { it.pkg }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { launchApp(context, app.pkg) },
                                onLongClick = { moving = app }
                            )
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
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
}
