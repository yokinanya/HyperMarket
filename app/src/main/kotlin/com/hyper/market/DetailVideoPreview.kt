package com.hyper.market

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hyper.market.model.DetailVideo

@Composable
internal fun DetailVideoPreview(video: DetailVideo) {
    var closed by remember(video.videoUrl) { mutableStateOf(false) }
    var retryKey by remember(video.videoUrl) { mutableStateOf(0) }
    if (closed) {
        ClosedVideoCard { closed = false; retryKey++ }
        return
    }
    key(retryKey) { ActiveVideoCard(video) { closed = true } }
}

@Composable
private fun ActiveVideoCard(video: DetailVideo, onClose: () -> Unit) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    Box(
        modifier = Modifier.width(220.dp).height(390.dp)
            .background(Color.Black, RoundedCornerShape(24.dp)),
    ) {
        AndroidView(
            factory = {
                VideoView(context).apply {
                    videoView = this
                    setVideoURI(Uri.parse(video.videoUrl))
                    setOnPreparedListener { mediaPlayer ->
                        player = mediaPlayer
                        mediaPlayer.isLooping = true
                        setVolume(mediaPlayer, muted)
                        start()
                        playing = true
                    }
                    setOnErrorListener { _, _, _ ->
                        failed = true
                        playing = false
                        true
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            VideoFailureCard { failed = false; videoView?.setVideoURI(Uri.parse(video.videoUrl)) }
        } else {
            VideoControls(
                muted = muted,
                playing = playing,
                onMute = {
                    muted = !muted
                    player?.let { setVolume(it, muted) }
                },
                onPlay = {
                    if (playing) videoView?.pause() else videoView?.start()
                    playing = !playing
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
        modifier = Modifier.fillMaxSize().padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        modifier = Modifier.width(220.dp).height(390.dp)
            .background(Color(0xFF252525), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ActionPill("重新打开", onOpen)
    }
}

private fun setVolume(player: MediaPlayer, muted: Boolean) {
    val volume = if (muted) 0f else 1f
    player.setVolume(volume, volume)
}
