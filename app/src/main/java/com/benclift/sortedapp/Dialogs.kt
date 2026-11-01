package com.benclift.sortedapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

private val dialogBackground = Color(0xF01C1C1E)

/** Icon size, icons per row, wallpaper dimming, and which folders show on the home page. */
@Composable
fun SettingsDialog(
    settings: HomeSettings,
    onChange: (HomeSettings) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBackground)
                .padding(16.dp)
        ) {
            Text("Settings", color = Color.White, fontSize = 18.sp)

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item {
                    SettingSlider(
                        title = "Icon size",
                        valueLabel = "${settings.iconSizeDp}",
                        value = settings.iconSizeDp.toFloat(),
                        range = 28f..56f,
                        onValue = { onChange(settings.copy(iconSizeDp = it.roundToInt())) }
                    )
                }
                item {
                    SettingSlider(
                        title = "Folders per row",
                        valueLabel = "${settings.columns}",
                        value = settings.columns.toFloat(),
                        range = 3f..7f,
                        steps = 3,
                        onValue = { onChange(settings.copy(columns = it.roundToInt())) }
                    )
                }
                item {
                    SettingSlider(
                        title = "Wallpaper dimming",
                        valueLabel = "${settings.dimPercent}%",
                        value = settings.dimPercent.toFloat(),
                        range = 0f..80f,
                        onValue = { onChange(settings.copy(dimPercent = it.roundToInt())) }
                    )
                }
                item {
                    Text(
                        "Show these folders on the home page",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(Categories.order) { category ->
                    val shown = category !in settings.hiddenFolders
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(category, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = shown,
                            onCheckedChange = { on ->
                                onChange(
                                    settings.copy(
                                        hiddenFolders = if (on) settings.hiddenFolders - category
                                        else settings.hiddenFolders + category
                                    )
                                )
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onChange(HomeSettings()) }) { Text("Reset to defaults") }
                TextButton(onClick = onClose) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValue: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps)
    }
}

/**
 * Sort the apps in "Other" one at a time. If you've already put apps from the same developer in a
 * folder, that folder is offered as a one-tap suggestion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortOtherDialog(
    apps: List<AppItem>,
    suggest: (AppItem) -> String?,
    onAssign: (AppItem, String) -> Unit,
    onClose: () -> Unit
) {
    // Apps you chose to skip stay in Other but are not shown again in this session.
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val current = apps.firstOrNull { it.pkg !in skipped }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(dialogBackground)
                .padding(16.dp)
        ) {
            if (current == null) {
                Text("All done: nothing left to sort.", color = Color.White, fontSize = 16.sp)
                TextButton(onClick = onClose) { Text("Close") }
            } else {
                val remaining = apps.count { it.pkg !in skipped }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap = current.icon, contentDescription = null, modifier = Modifier.size(56.dp))
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(current.label, color = Color.White, fontSize = 18.sp)
                        Text("$remaining left to sort", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }

                val suggestion = suggest(current)
                if (suggestion != null) {
                    Text(
                        text = "Suggested: $suggestion",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2E6FD1))
                            .clickable { onAssign(current, suggestion) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                Text(
                    "Put it in",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Categories.order.filter { it != Categories.OTHER }.forEach { category ->
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { onAssign(current, category) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { skipped = skipped + current.pkg }) { Text("Skip") }
                    TextButton(onClick = onClose) { Text("Done") }
                }
            }
        }
    }
}
