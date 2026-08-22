package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hyper.market.model.DetailVideo

@Composable
internal fun DetailVideoPreview(video: DetailVideo) {
    var closed by remember(video.videoUrl) { mutableStateOf(false) }
    if (closed) {
        ClosedVideoCard { closed = false }
        return
    }
    ActiveVideoCard(video) { closed = true }
}

@Composable
private fun ActiveVideoCard(video: DetailVideo, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(video.videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    var muted by remember(video.videoUrl) { mutableStateOf(true) }
    var failed by remember(video.videoUrl) { mutableStateOf(false) }
    var playing by remember(video.videoUrl) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                playing = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(
        modifier = Modifier.width(VIDEO_WIDTH).height(VIDEO_HEIGHT)
            .clip(RoundedCornerShape(VIDEO_RADIUS))
            .background(Color.Black),
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            VideoFailureCard {
                failed = false
                player.seekTo(0)
                player.prepare()
                player.play()
            }
        } else {
            VideoControls(
                muted = muted,
                playing = playing,
                onMute = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                },
                onPlay = {
                    if (playing) player.pause() else player.play()
                },
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun VideoControls(
    muted: Boolean,
    playing: Boolean,
    onMute: () -> Unit,
    onPlay: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(VIDEO_CONTROL_PADDING),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(VIDEO_BUTTON_GAP)) {
            ActionPill(if (muted) "取消静音" else "静音", onMute)
            ActionPill("关闭", onClose)
        }
        ActionPill(if (playing) "暂停" else "播放", onPlay)
    }
}

@Composable
private fun VideoFailureCard(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("视频加载失败", color = Color.White, fontSize = 16.sp)
        ActionPill("重试", onRetry)
    }
}

@Composable
private fun ClosedVideoCard(onOpen: () -> Unit) {
    Box(
        modifier = Modifier.width(VIDEO_WIDTH).height(VIDEO_HEIGHT)
            .clip(RoundedCornerShape(VIDEO_RADIUS))
            .background(Color(0xFF252525)),
        contentAlignment = Alignment.Center,
    ) {
        ActionPill("重新打开", onOpen)
    }
}

private val VIDEO_WIDTH = 220.dp
private val VIDEO_HEIGHT = 390.dp
private val VIDEO_RADIUS = 24.dp
private val VIDEO_CONTROL_PADDING = 10.dp
private val VIDEO_BUTTON_GAP = 6.dp
