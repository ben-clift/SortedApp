package com.benclift.sortedapp

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/**
 * Android only lets an app see and control other apps' media if the user grants it
 * "Notification access". This service has no logic of its own; it just gives Android
 * something to grant that access to.
 */
class MediaListenerService : NotificationListenerService()

/** What is playing (or paused) right now, in any app: music, podcast, video, etc. */
data class NowPlaying(
    val controller: MediaController,
    val title: String,
    val subtitle: String,
    val art: ImageBitmap?,
    val playing: Boolean
)

fun hasNotificationAccess(context: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Keeps track of the current media session. Returns null when nothing is playing or paused. */
@Composable
fun rememberNowPlaying(refreshKey: Int): State<NowPlaying?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<NowPlaying?>(null) }

    DisposableEffect(refreshKey) {
        if (!hasNotificationAccess(context)) {
            state.value = null
            return@DisposableEffect onDispose { }
        }

        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, MediaListenerService::class.java)
        var controllers: List<MediaController> = emptyList()
        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()

        fun recompute() {
            fun stateOf(c: MediaController) = c.playbackState?.state
            val playing = controllers.firstOrNull {
                stateOf(it) == PlaybackState.STATE_PLAYING || stateOf(it) == PlaybackState.STATE_BUFFERING
            }
            // Only show something that is actually playing; paused or stopped hides the card.
            val chosen = playing
            if (chosen == null) {
                state.value = null
                return
            }
            val md = chosen.metadata
            val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: ""
            val subtitle = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                ?: ""
            val art = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.asImageBitmap()
            val appName = runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(chosen.packageName, 0)).toString()
            }.getOrDefault(chosen.packageName)

            state.value = NowPlaying(
                controller = chosen,
                title = title.ifBlank { appName },
                subtitle = if (title.isBlank()) "" else subtitle.ifBlank { appName },
                art = art,
                playing = stateOf(chosen) == PlaybackState.STATE_PLAYING ||
                    stateOf(chosen) == PlaybackState.STATE_BUFFERING
            )
        }

        fun setControllers(list: List<MediaController>?) {
            callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            callbacks.clear()
            controllers = list.orEmpty()
            controllers.forEach { c ->
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = recompute()
                    override fun onSessionDestroyed() = recompute()
                }
                runCatching { c.registerCallback(cb) }
                callbacks[c] = cb
            }
            recompute()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { setControllers(it) }
        try {
            manager.addOnActiveSessionsChangedListener(listener, component)
            setControllers(manager.getActiveSessions(component))
        } catch (_: SecurityException) {
            state.value = null
        }

        onDispose {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            callbacks.clear()
        }
    }
    return state
}

/** Where playback is right now, in milliseconds (accounts for time passed since the last update). */
private fun currentPosition(c: MediaController): Long {
    val st = c.playbackState ?: return 0L
    if (st.state != PlaybackState.STATE_PLAYING) return st.position
    val elapsed = android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
    return st.position + (elapsed * st.playbackSpeed).toLong()
}

private fun hasAction(c: MediaController, flag: Long) = ((c.playbackState?.actions ?: 0L) and flag) != 0L

/** Jump forwards or backwards. Falls back to rewind()/fastForward() for apps that don't allow seeking. */
private fun skipBy(c: MediaController, deltaMs: Long) {
    runCatching {
        if (hasAction(c, PlaybackState.ACTION_SEEK_TO)) {
            c.transportControls.seekTo((currentPosition(c) + deltaMs).coerceAtLeast(0L))
        } else if (deltaMs > 0) {
            c.transportControls.fastForward()
        } else {
            c.transportControls.rewind()
        }
    }
}

private fun customActionLike(c: MediaController, word: String): String? =
    c.playbackState?.customActions?.firstOrNull {
        it.action.contains(word, ignoreCase = true) || it.name.toString().contains(word, ignoreCase = true)
    }?.action

/** Back to the start of the programme. */
private fun goToStart(c: MediaController) {
    runCatching {
        val custom = customActionLike(c, "start")
        if (custom != null) c.transportControls.sendCustomAction(custom, null)
        else c.transportControls.seekTo(0L)
    }
}

/** Jump to the live edge of a radio stream. */
private fun goLive(c: MediaController) {
    runCatching {
        val custom = customActionLike(c, "live")
        val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        when {
            custom != null -> c.transportControls.sendCustomAction(custom, null)
            duration > 0 -> c.transportControls.seekTo(duration)
            else -> c.transportControls.fastForward()
        }
    }
}

private val DJ_APPS = setOf("com.benclift.setflow", "com.benclift.beatdeck.test")

/**
 * Opens the app that is playing. For Ben's DJ apps it asks for The Booth (extra "open" = "booth");
 * every other app just opens normally.
 */
private fun openPlayingApp(context: android.content.Context, c: MediaController) {
    val pkg = c.packageName
    if (pkg in DJ_APPS) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            launch.putExtra("open", "booth").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(launch) }.isSuccess) return
        }
    }
    val sent = runCatching { c.sessionActivity?.send() }.isSuccess && c.sessionActivity != null
    if (!sent) launchApp(context, pkg)
}

/** A now-playing card: artwork and title on top, then start / back 20s / play-pause / forward 20s / live. */
@Composable
fun NowPlayingBar(np: NowPlaying, modifier: Modifier = Modifier, onDismiss: () -> Unit = {}) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            // Swipe right (or left) to close the card; it comes back the next time something plays.
            .pointerInput(np.controller.packageName) {
                val distance = 90.dp.toPx()
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var dx = 0f
                        var dy = 0f
                        var horizontal = false
                        var last = down.position
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                if (horizontal) {
                                    ch.consume()
                                    if (kotlin.math.abs(dx) >= distance) onDismiss()
                                }
                                break
                            }
                            dx += ch.position.x - last.x
                            dy += ch.position.y - last.y
                            last = ch.position
                            if (!horizontal && kotlin.math.abs(dx) > viewConfiguration.touchSlop * 2 &&
                                kotlin.math.abs(dx) > kotlin.math.abs(dy) * 2f
                            ) horizontal = true
                            if (horizontal) ch.consume()
                        }
                    }
                }
            }
            .clickable { openPlayingApp(context, np.controller) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Tapping the card background, artwork or title opens the app that is playing.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { openPlayingApp(context, np.controller) }
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                if (np.art != null) {
                    Image(
                        bitmap = np.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = np.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (np.subtitle.isNotBlank()) {
                    Text(
                        text = np.subtitle,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            TextControl("START") { goToStart(np.controller) }
            SkipControl(forward = false) { skipBy(np.controller, -20_000L) }
            TransportButton(
                kind = if (np.playing) TransportKind.PAUSE else TransportKind.PLAY,
                size = 48.dp,
                filled = true
            ) {
                runCatching {
                    if (np.playing) np.controller.transportControls.pause()
                    else np.controller.transportControls.play()
                }
            }
            SkipControl(forward = true) { skipBy(np.controller, 20_000L) }
            TextControl("LIVE") { goLive(np.controller) }
        }
    }
}

@Composable
private fun TextControl(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** A circular arrow with "20" inside: jump back or forward 20 seconds. */
@Composable
private fun SkipControl(forward: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val w = this.size.width
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.08f)
            val start = if (forward) -60f else 240f
            val sweep = if (forward) 290f else -290f
            drawArc(Color.White, start, sweep, false, style = stroke)
            // Arrow head near the top, pointing along the direction of travel.
            val tipX = if (forward) w * 0.58f else w * 0.42f
            val dir = if (forward) 1f else -1f
            drawPath(
                Path().apply {
                    moveTo(tipX + dir * w * 0.14f, w * 0.02f)
                    lineTo(tipX - dir * w * 0.04f, w * 0.20f)
                    lineTo(tipX - dir * w * 0.04f, -w * 0.16f + w * 0.02f)
                    close()
                },
                Color.White
            )
        }
        Text("20", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

enum class TransportKind { PLAY, PAUSE, NEXT, PREVIOUS }

@Composable
private fun TransportButton(kind: TransportKind, size: Dp, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 2.dp)
            .size(size)
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .clickable { onClick() }
    ) {
        TransportIcon(kind, if (filled) Color.Black else Color.White, size * 0.5f)
    }
}

/** Draws play, pause, next and previous without needing the large extended icon pack. */
@Composable
private fun TransportIcon(kind: TransportKind, tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = this.size.width
        val h = this.size.height
        when (kind) {
            TransportKind.PLAY -> drawPath(
                Path().apply {
                    moveTo(w * 0.2f, h * 0.05f)
                    lineTo(w * 0.95f, h * 0.5f)
                    lineTo(w * 0.2f, h * 0.95f)
                    close()
                },
                tint
            )
            TransportKind.PAUSE -> {
                drawRect(tint, Offset(w * 0.1f, h * 0.05f), Size(w * 0.28f, h * 0.9f))
                drawRect(tint, Offset(w * 0.62f, h * 0.05f), Size(w * 0.28f, h * 0.9f))
            }
            TransportKind.NEXT -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.05f, h * 0.1f)
                        lineTo(w * 0.7f, h * 0.5f)
                        lineTo(w * 0.05f, h * 0.9f)
                        close()
                    },
                    tint
                )
                drawRect(tint, Offset(w * 0.78f, h * 0.1f), Size(w * 0.17f, h * 0.8f))
            }
            TransportKind.PREVIOUS -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.95f, h * 0.1f)
                        lineTo(w * 0.3f, h * 0.5f)
                        lineTo(w * 0.95f, h * 0.9f)
                        close()
                    },
                    tint
                )
                drawRect(tint, Offset(w * 0.05f, h * 0.1f), Size(w * 0.17f, h * 0.8f))
            }
        }
    }
}
