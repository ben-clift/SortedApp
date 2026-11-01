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
            val chosen = playing ?: controllers.firstOrNull { stateOf(it) == PlaybackState.STATE_PAUSED }
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

/** A compact bar: artwork, title, and previous / play-pause / next. */
@Composable
fun NowPlayingBar(np: NowPlaying, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        // Tapping the artwork or title opens the app that is playing.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable { runCatching { np.controller.sessionActivity?.send() } }
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
            Column(modifier = Modifier.padding(start = 10.dp, end = 6.dp)) {
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

        TransportButton(kind = TransportKind.PREVIOUS, size = 36.dp) {
            runCatching { np.controller.transportControls.skipToPrevious() }
        }
        TransportButton(
            kind = if (np.playing) TransportKind.PAUSE else TransportKind.PLAY,
            size = 44.dp,
            filled = true
        ) {
            runCatching {
                if (np.playing) np.controller.transportControls.pause()
                else np.controller.transportControls.play()
            }
        }
        TransportButton(kind = TransportKind.NEXT, size = 36.dp) {
            runCatching { np.controller.transportControls.skipToNext() }
        }
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
