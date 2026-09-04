package com.hyper.market

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTitleDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ActionPill(label: String, onClick: () -> Unit) {
    ActionPill(label, primary = true, onClick = onClick)
}

@Composable
internal fun ActionPill(label: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        cornerRadius = ACTION_PILL_RADIUS,
        minWidth = 0.dp,
        minHeight = ACTION_PILL_HEIGHT,
        colors = if (primary) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
    }
}

private val ACTION_PILL_RADIUS = 28.dp
private val ACTION_PILL_HEIGHT = 34.dp

@Composable
internal fun AppIcon(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.take(1),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun InstalledAppIcon(packageName: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(packageName) { applicationIcon(context, packageName) }
    if (drawable == null) IconStatusTile("未安装", modifier)
    else AndroidView(
        factory = { ImageViewFactory.create(it, drawable, label) },
        modifier = modifier.clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
internal fun RemoteAppIcon(url: String, label: String, modifier: Modifier = Modifier) {
    if (url.isBlank()) {
        IconStatusTile("无图标", modifier)
        return
    }
    RemoteImage(
        url,
        label,
        modifier.clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
internal fun RemoteImage(
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
) {
    val context = LocalContext.current
    var retryToken by remember(url) { mutableIntStateOf(0) }
    val request = remember(url, retryToken) {
        ImageRequest.Builder(context)
            .data(url)
            .build()
    }
    val painter = rememberAsyncImagePainter(
        model = request,
    )
    val state by painter.state.collectAsState()
    when (val currentState = state) {
        AsyncImagePainter.State.Empty,
        is AsyncImagePainter.State.Loading,
        -> IconStatusTile("加载中", modifier)

        is AsyncImagePainter.State.Error -> {
            val message = currentState.result.throwable.message ?: "图片下载失败"
            IconStatusTile("加载失败：$message", modifier) { retryToken++ }
        }

        is AsyncImagePainter.State.Success -> {
            val imageModifier = if (onLongClick == null) {
                modifier
            } else {
                modifier.combinedClickable(onClick = {}, onLongClick = onLongClick)
            }
            Image(
                painter = painter,
                contentDescription = contentDescription,
                alignment = alignment,
                contentScale = contentScale,
                modifier = imageModifier,
            )
        }
    }
}

@Composable
private fun IconStatusTile(
    status: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = (if (onRetry == null) modifier else modifier.clickable(onClick = onRetry))
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            status,
            color = MiuixTheme.colorScheme.onSecondaryContainer,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun applicationIcon(context: android.content.Context, packageName: String): Drawable? =
    try { context.packageManager.getApplicationIcon(packageName) } catch (_: Exception) { null }

private object ImageViewFactory {
    fun create(context: android.content.Context, drawable: Drawable, label: String) =
        android.widget.ImageView(context).apply {
            contentDescription = label
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageDrawable(drawable)
        }
}

@Composable
internal fun GradientFeatureCard(
    title: String,
    subtitle: String,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        onClick = onClick,
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(colors))
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text(subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** 容器自带 12dp 横向边距时的分区小标题内边距：16dp + 12dp = 28dp，与卡片文字对齐。 */
internal val SectionLabelPaddedContainerMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = SmallTitleDefaults.InsideMargin,
) {
    SmallTitle(text = text, modifier = modifier, insideMargin = insideMargin)
}

@Composable
internal fun RowScope.SettingText(title: String, summary: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
        Text(summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
    )
}
