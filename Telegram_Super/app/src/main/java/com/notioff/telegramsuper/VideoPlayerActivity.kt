package com.notioff.telegramsuper

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import android.content.pm.ActivityInfo
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlin.math.abs

class VideoPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make activity fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val videoPath = intent.getStringExtra("video_path")
        if (videoPath.isNullOrEmpty()) {
            android.util.Log.e("VideoPlayer", "Video path is null or empty")
            return finish()
        }

        setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    videoPath = videoPath,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun VideoPlayerScreen(videoPath: String, onClose: () -> Unit) {
    val context = LocalContext.current
    
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalTime by remember { mutableStateOf(0L) }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    val libVlc = remember { LibVLC(context, arrayListOf("-vvv")) }
    val mediaPlayer = remember { 
        MediaPlayer(libVlc).apply {
            setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> isPlaying = true
                    MediaPlayer.Event.Paused -> isPlaying = false
                    MediaPlayer.Event.EncounteredError -> {
                        android.util.Log.e("VLC", "MediaPlayer encountered error")
                        isPlaying = false
                    }
                    MediaPlayer.Event.Stopped -> onClose()
                    MediaPlayer.Event.TimeChanged -> currentTime = event.timeChanged
                    MediaPlayer.Event.LengthChanged -> totalTime = event.lengthChanged
                }
            }
        }
    }

    DisposableEffect(videoPath) {
        onDispose {
            mediaPlayer.release()
            libVlc.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .videoGestures(
                context = context,
                mediaPlayer = mediaPlayer,
                onTap = { showControls = !showControls }
            )
    ) {
        // VLC Video View
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    mediaPlayer.attachViews(this, null, false, false)
                    val media = Media(libVlc, videoPath)
                    mediaPlayer.media = media
                    mediaPlayer.play()
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { layout ->
                // Views already attached in factory
            }
        )

        // Overlay Controls
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Center Play/Pause
                IconButton(
                    onClick = {
                        if (isPlaying) mediaPlayer?.pause() else mediaPlayer?.play()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(80.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Bottom Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTime(currentTime), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = if (totalTime > 0) currentTime.toFloat() / totalTime.toFloat() else 0f,
                        onValueChange = { percent ->
                            val newTime = (percent * totalTime).toLong()
                            mediaPlayer.time = newTime
                            currentTime = newTime
                            showControls = true
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatTime(totalTime), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    
                    val activity = context as? Activity
                    IconButton(onClick = {
                        val isPortrait = activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT || 
                                         activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        activity?.requestedOrientation = if (isPortrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    }) {
                        Text("🔄", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun Modifier.videoGestures(
    context: Context,
    mediaPlayer: MediaPlayer?,
    onTap: () -> Unit
): Modifier = this.pointerInput(Unit) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val activity = context as? Activity
    
    var startX = 0f
    var startY = 0f
    var startVolume = 0
    var startBrightness = 0f
    var startTime = 0L
    val screenWidth = size.width
    val screenHeight = size.height
    
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    
    detectDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            startY = offset.y
            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            startBrightness = activity?.window?.attributes?.screenBrightness ?: 0.5f
            if (startBrightness < 0) startBrightness = 0.5f
            startTime = mediaPlayer?.time ?: 0L
        },
        onDrag = { change, dragAmount ->
            change.consume()
            val diffX = change.position.x - startX
            val diffY = startY - change.position.y // Invert Y (up is positive)
            
            if (abs(diffX) > abs(diffY)) {
                // Horizontal scroll = Seek
                val seekPercentage = diffX / screenWidth
                val timeOffset = (seekPercentage * 60000).toLong() // 1 min max seek per full swipe
                mediaPlayer?.time = (startTime + timeOffset).coerceAtLeast(0L).coerceAtMost(mediaPlayer?.length ?: 0L)
            } else {
                // Vertical scroll
                if (startX < screenWidth / 2) {
                    // Left side = Brightness
                    val brightnessPercentage = diffY / (screenHeight / 2)
                    var newBrightness = startBrightness + brightnessPercentage
                    newBrightness = newBrightness.coerceIn(0.01f, 1f)
                    
                    activity?.window?.attributes = activity?.window?.attributes?.apply {
                        screenBrightness = newBrightness
                    }
                } else {
                    // Right side = Volume
                    val volumePercentage = diffY / (screenHeight / 2)
                    val volumeOffset = (volumePercentage * maxVolume).toInt()
                    val newVolume = (startVolume + volumeOffset).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                }
            }
        },
        onDragEnd = {
            // End of drag
        },
        onDragCancel = {
            // Drag cancelled
        }
    )
}.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.all { !it.isConsumed } && event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                onTap()
            }
        }
    }
}
