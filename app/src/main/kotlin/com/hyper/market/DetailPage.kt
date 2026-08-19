package com.hyper.market

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppDetails
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.DetailVideo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun DetailPage(
    app: MarketAppInfo,
    apiClient: XiaomiApiClient,
    settings: AppSettings,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onBack: () -> Unit,
) {
    var details by remember(app) { mutableStateOf<MarketAppDetails?>(null) }
    var error by remember(app) { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val compactTitle by remember { derivedStateOf { scrollState.value > 120 } }
    LaunchedEffect(app) {
        try {
            details = withContext(Dispatchers.IO) { loadRemoteDetail(app, apiClient) }
        } catch (exception: Exception) {
            error = exception.message ?: "详情接口读取失败"
        }
    }
    val detail = details?.app ?: app
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DetailHeader(detail, onBack, onInstall, onOpenInstalled)
            DetailStats(detail)
            error?.let { Text(it, color = Color(0xFFD14343), modifier = Modifier.padding(8.dp)) }
            PreviewSection(detail.getScreenshotUrls(), details?.videos.orEmpty()) { url ->
                scope.launch {
                    try {
                        ImageSaver.save(context, url)
                        Toast.makeText(context, "图片已保存", Toast.LENGTH_SHORT).show()
                    } catch (exception: Exception) {
                        Toast.makeText(
                            context,
                            exception.message ?: "保存图片失败",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            IntroductionSection(detail)
            DetailInfoSection(detail, details?.privacyUrl.orEmpty())
            details?.let { OptionalDetailSections(it, settings, onOpenDetail) }
        }
        AnimatedVisibility(
            visible = compactTitle,
            enter = fadeIn(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(detail.getDisplayName(), fontSize = 25.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun DetailHeader(
    app: MarketAppInfo,
    onBack: () -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    val context = LocalContext.current
    val installed = remember(app.packageName) {
        runCatching { context.packageManager.getPackageInfo(app.packageName, 0) }.isSuccess
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(38.dp))
        }
        DetailAppIcon(app)
        Column(modifier = Modifier.padding(start = 28.dp).weight(1f)) {
            Text(app.getDisplayName(), fontSize = 42.sp, maxLines = 2)
            Text(app.getPublisherName(), fontSize = 18.sp, color = Color(0xFF999999), maxLines = 2)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (installed) {
                    ActionPill("打开") { onOpenInstalled(app) }
                    ActionPill("重新安装") { onInstall(app) }
                } else {
                    ActionPill("安装") { onInstall(app) }
                }
            }
        }
    }
}

@Composable
private fun DetailStats(app: MarketAppInfo) {
    val stats = listOf(
        formatRating(app) to formatCommentLabel(app),
        formatCount(app.getDownloadCount()) to "下载次数",
        formatSize(app.getApkSize()) to "大小",
        app.getAgeClassification().ifEmpty { "—" } to "年龄分级",
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        stats.forEachIndexed { index, stat ->
            if (index > 0) Spacer(Modifier.width(1.dp).height(64.dp).background(Color(0xFFD9D9D9)))
            StatItem(stat.first, stat.second)
        }
    }
}

@Composable
private fun RowScope.StatItem(value: String, label: String) {
    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, color = Color.Black, maxLines = 1)
        Text(label, fontSize = 14.sp, color = Color(0xFF666666), maxLines = 1)
    }
}

@Composable
private fun PreviewSection(
    urls: List<String>,
    videos: List<DetailVideo>,
    onSaveImage: (String) -> Unit,
) {
    SectionLabel("预览")
    if (urls.isEmpty() && videos.isEmpty()) {
        Text("暂无预览", color = Color(0xFF888888), modifier = Modifier.padding(start = 12.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos) { video -> DetailVideoPreview(video) }
        items(urls) { url ->
            RemoteImage(
                url,
                "应用预览",
                Modifier.width(220.dp).height(390.dp).clip(RoundedCornerShape(24.dp)),
                onLongClick = { onSaveImage(url) },
            )
        }
    }
}

@Composable
private fun IntroductionSection(app: MarketAppInfo) {
    var expanded by remember(app) { mutableStateOf(false) }
    val content = app.getIntroduction().ifEmpty { "暂无应用介绍" }
    SectionLabel("应用介绍")
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(tween(260)), cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(if (expanded) content else content.take(MAX_INTRO_LENGTH), fontSize = 18.sp, color = Color(0xFF222222))
            if (content.length > MAX_INTRO_LENGTH) {
                Text(if (expanded) "收起" else "更多", color = AccentBlue, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp).clickable { expanded = !expanded })
            }
        }
    }
}

@Composable
private fun DetailInfoSection(app: MarketAppInfo, privacyUrl: String) {
    val context = LocalContext.current
    SectionLabel("应用信息")
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailInfoRow("包名", app.getPackageName())
            DetailInfoRow("版本", app.getVersionName().ifEmpty { "—" })
            DetailInfoRow("更新时间", formatDate(app.getUpdateTime()))
            DetailInfoRow("备案号", app.getRegistrationNumber().ifEmpty { "—" })
            Row(modifier = Modifier.fillMaxWidth().clickable {
                val url = privacyUrl.ifEmpty { "https://privacy.mi.com/all/zh_CN/" }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("隐私政策", fontSize = 16.sp, color = Color(0xFF666666))
                Text("点击打开", fontSize = 16.sp, color = AccentBlue)
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp, color = Color(0xFF666666))
        Text(value, fontSize = 16.sp, color = Color(0xFF222222), maxLines = 1)
    }
}

@Composable
internal fun DetailAppIcon(app: MarketAppInfo) {
    if (app.getIconUrl().isNotBlank()) RemoteAppIcon(app.getIconUrl(), app.getDisplayName(), Modifier.size(96.dp))
    else InstalledAppIcon(app.getPackageName(), app.getDisplayName(), Modifier.size(96.dp))
}

private suspend fun loadRemoteDetail(app: MarketAppInfo, apiClient: XiaomiApiClient): MarketAppDetails {
    return apiClient.loadDetail(app)
}

internal fun formatCommentScore(score: Double): String =
    if (score > 0) String.format(Locale.CHINA, "%.1f ★", score) else ""

private fun formatRating(app: MarketAppInfo): String =
    if (app.getRatingScore() > 0) String.format(Locale.CHINA, "%.1f ★", app.getRatingScore()) else "—"

private fun formatCommentLabel(app: MarketAppInfo): String =
    if (app.getCommentCount() > 0) "${formatCount(app.getCommentCount())}条评价" else "评价"

private fun formatCount(count: Long): String = when {
    count >= HUNDRED_MILLION -> String.format(Locale.CHINA, "%.1f亿", count / HUNDRED_MILLION.toDouble())
    count >= TEN_THOUSAND -> String.format(Locale.CHINA, "%.1f万", count / TEN_THOUSAND.toDouble())
    count > 0 -> count.toString()
    else -> "—"
}

private fun formatSize(bytes: Long): String =
    if (bytes > 0) String.format(Locale.CHINA, "%.1fMB", bytes / BYTES_PER_MB.toDouble()) else "—"

private fun formatDate(timestamp: Long): String =
    if (timestamp <= 0) "—" else SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timestamp))

private const val MAX_INTRO_LENGTH = 180
private const val MAX_DETAIL_COMMENTS = 5
private const val BYTES_PER_MB = 1024L * 1024L
private const val TEN_THOUSAND = 10_000L
private const val HUNDRED_MILLION = 100_000_000L
