package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppDetails
import com.hyper.market.model.MarketAppInfo
import java.util.Locale
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.basic.Card
import com.hyper.market.installer.DownloadNotificationReceiver
import com.hyper.market.installer.DownloadTaskRegistry

@Composable
fun DetailPage(
    app: MarketAppInfo,
    apiClient: XiaomiApiClient,
    settings: AppSettings,
    packageVisibilityRefresh: Int,
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
    val displayName = optimizedAppName(detail.displayName, settings.optimizeNames)
    val actionState = remember(
        detail.packageName,
        detail.versionCode,
        packageVisibilityRefresh,
    ) { detailActionState(context, detail) }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DetailHeader(detail, displayName, actionState, onInstall, onOpenInstalled)
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
            CompactDetailBar(detail, displayName, actionState, onInstall, onOpenInstalled)
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 3.dp).size(48.dp),
        ) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun CompactDetailBar(
    app: MarketAppInfo,
    displayName: String,
    actionState: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(COMPACT_DETAIL_BAR_HEIGHT)
            .background(PageBackground),
    ) {
        Text(
            displayName,
            fontSize = 25.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)) {
            DetailActionGroup(app, actionState, onInstall, onOpenInstalled)
        }
    }
}

@Composable
private fun DetailHeader(
    app: MarketAppInfo,
    displayName: String,
    actionState: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DetailAppIcon(app)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                displayName,
                style = detailHeaderTextStyle(24.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                app.getPublisherName(),
                style = detailHeaderTextStyle(14.sp),
                color = Color(0xFF999999),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                DetailActionGroup(app, actionState, onInstall, onOpenInstalled)
            }
        }
    }
}

@Composable
private fun DetailActionGroup(
    app: MarketAppInfo,
    state: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    val installStates by InstallUiStateStore.states.collectAsState()
    val installState = installStates[app.packageName]
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailActionButton(app, state, onInstall, onOpenInstalled)
        if (state == DetailActionState.INSTALLED || installState?.phase?.isDetailActive() == true) {
            DetailMoreButton(
                activeDownload = installState?.phase?.isDetailActive() == true,
                onRedownload = { onInstall(app) },
            )
        }
    }
}

@Composable
private fun DetailMoreButton(activeDownload: Boolean, onRedownload: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box {
        Box(
            modifier = Modifier.size(32.dp).background(Color(0xFFECECEC), RoundedCornerShape(28.dp))
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Light.More, contentDescription = "更多", modifier = Modifier.size(22.dp))
        }
        if (expanded) {
            val offset = with(density) { IntOffset((-168).dp.roundToPx(), 40.dp.roundToPx()) }
            Popup(
                alignment = Alignment.TopStart,
                offset = offset,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Card(modifier = Modifier.width(200.dp), cornerRadius = 18.dp) {
                    Text(
                        if (activeDownload) "取消下载" else "重新下载",
                        fontSize = 17.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            expanded = false
                            if (activeDownload) cancelActiveDownload() else onRedownload()
                        }.padding(horizontal = 22.dp, vertical = 20.dp),
                    )
                }
            }
        }
    }
}

private fun cancelActiveDownload() {
    DownloadTaskRegistry.applyCurrent(DownloadNotificationReceiver.ACTION_CANCEL)
    InstallUiStateStore.cancelCurrent()
}

private fun InstallPhase.isDetailActive(): Boolean = when (this) {
    InstallPhase.QUEUED,
    InstallPhase.DOWNLOADING,
    InstallPhase.PAUSED,
    InstallPhase.INSTALLING,
    InstallPhase.AWAITING_USER_ACTION,
    -> true
    else -> false
}

@Composable
private fun DetailActionButton(
    app: MarketAppInfo,
    state: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    when (state) {
        DetailActionState.NOT_INSTALLED -> InstallActionPill(app, "安装", onInstall)
        DetailActionState.UPDATE_AVAILABLE -> InstallActionPill(app, "更新", onInstall)
        DetailActionState.INSTALLED -> ActionPill("打开", primary = false) { onOpenInstalled(app) }
    }
}

private fun detailActionState(
    context: android.content.Context,
    app: MarketAppInfo,
): DetailActionState {
    val installedCode = try {
        val info = context.packageManager.getPackageInfo(app.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        return DetailActionState.NOT_INSTALLED
    }
    return if (app.versionCode > installedCode) {
        DetailActionState.UPDATE_AVAILABLE
    } else {
        DetailActionState.INSTALLED
    }
}

private enum class DetailActionState {
    NOT_INSTALLED,
    UPDATE_AVAILABLE,
    INSTALLED,
}

private fun detailHeaderTextStyle(fontSize: androidx.compose.ui.unit.TextUnit) = TextStyle(
    fontSize = fontSize,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

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

private suspend fun loadRemoteDetail(app: MarketAppInfo, apiClient: XiaomiApiClient): MarketAppDetails {
    val resolved = if (app.getAppId() > 0) app else apiClient.findByPackageName(app.getPackageName())
    return apiClient.loadDetail(resolved)
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

private const val MAX_DETAIL_COMMENTS = 5
private val COMPACT_DETAIL_BAR_HEIGHT = 56.dp
private const val BYTES_PER_MB = 1024L * 1024L
private const val TEN_THOUSAND = 10_000L
private const val HUNDRED_MILLION = 100_000_000L
