package com.hyper.market

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.installer.DownloadNotification
import com.hyper.market.installer.DownloadNotificationReceiver
import com.hyper.market.installer.DownloadTaskRegistry
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

enum class InstallPhase {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    INSTALLING,
    AWAITING_USER_ACTION,
    COMPLETE,
    FAILED,
}

data class InstallUiState(
    val packageName: String,
    val displayName: String,
    val phase: InstallPhase,
    val progress: Int? = null,
    val message: String = "",
)

object InstallUiStateStore {
    private val mutableStates = MutableStateFlow<Map<String, InstallUiState>>(emptyMap())
    val states: StateFlow<Map<String, InstallUiState>> = mutableStates.asStateFlow()
    private var activePackage: String? = null

    fun observe(packageName: String): Flow<InstallUiState?> =
        states.map { current -> current[packageName] }.distinctUntilChanged()

    @JvmStatic
    @Synchronized
    fun begin(packageName: String, displayName: String) {
        activePackage = packageName
        set(InstallUiState(packageName, displayName, InstallPhase.QUEUED))
    }

    @JvmStatic
    @Synchronized
    fun downloading(packageName: String, progress: Int?) {
        val old = mutableStates.value[packageName] ?: return
        val phase = if (old.phase == InstallPhase.PAUSED) old.phase else InstallPhase.DOWNLOADING
        set(old.copy(phase = phase, progress = progress ?: old.progress))
    }

    @JvmStatic fun installing(packageName: String) = update(packageName, InstallPhase.INSTALLING)
    @JvmStatic fun awaiting(packageName: String) = update(packageName, InstallPhase.AWAITING_USER_ACTION)
    @JvmStatic fun complete(packageName: String) = finish(packageName, InstallPhase.COMPLETE, "")
    @JvmStatic fun failure(packageName: String, message: String) = finish(packageName, InstallPhase.FAILED, message)

    @JvmStatic
    @Synchronized
    fun dismiss(packageName: String) {
        mutableStates.value = mutableStates.value - packageName
    }

    @JvmStatic
    @Synchronized
    fun cancelCurrent() {
        val packageName = activePackage ?: return
        mutableStates.value = mutableStates.value - packageName
        activePackage = null
    }

    @JvmStatic
    @Synchronized
    fun pauseCurrent() = updateActiveDownloads(InstallPhase.PAUSED)

    @JvmStatic
    @Synchronized
    fun resumeCurrent() = updateActiveDownloads(InstallPhase.DOWNLOADING)

    private fun updateActiveDownloads(phase: InstallPhase) {
        mutableStates.value = mutableStates.value.mapValues { (_, state) ->
            if (state.phase == InstallPhase.DOWNLOADING || state.phase == InstallPhase.PAUSED) {
                state.copy(phase = phase)
            } else {
                state
            }
        }
    }

    @Synchronized
    private fun update(packageName: String, phase: InstallPhase, progress: Int? = null) {
        val old = mutableStates.value[packageName] ?: return
        set(old.copy(phase = phase, progress = progress ?: old.progress))
    }

    @Synchronized
    private fun finish(packageName: String, phase: InstallPhase, message: String) {
        update(packageName, phase)
        val old = mutableStates.value[packageName] ?: return
        set(old.copy(phase = phase, message = message))
        if (activePackage == packageName) activePackage = null
    }

    private fun set(state: InstallUiState) {
        mutableStates.value = mutableStates.value + (state.packageName to state)
    }
}

@Composable
internal fun InstallResultDialog() {
    val states by InstallUiStateStore.states.collectAsState()
    val result = states.values.lastOrNull { it.phase == InstallPhase.FAILED }
    WindowDialog(
        show = result != null,
        title = "安装失败",
        summary = result?.resultMessage(),
        onDismissRequest = { result?.let { InstallUiStateStore.dismiss(it.packageName) } },
    ) {
        TextButton(
            text = "确定",
            onClick = { result?.let { InstallUiStateStore.dismiss(it.packageName) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun InstallActionPill(
    app: MarketAppInfo,
    idleLabel: String,
    onInstall: (MarketAppInfo) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val stateFlow = androidx.compose.runtime.remember(app.packageName) {
        InstallUiStateStore.observe(app.packageName)
    }
    val state by stateFlow.collectAsState(initial = null)
    val activeState = state
    val active = activeState?.phase?.isActive() == true
    // 记住最近一次活动状态：取消/完成后圆球能沿变形动画淡出，而不是瞬间消失。
    var lastActiveState by remember { mutableStateOf<InstallUiState?>(null) }
    if (activeState != null) lastActiveState = activeState
    val shownState = lastActiveState

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val idleWidth = remember(idleLabel, density.fontScale) {
        with(density) {
            textMeasurer
                .measure(AnnotatedString(idleLabel), style = installLabelStyle, maxLines = 1)
                .size
                .width
                .toDp() + 32.dp
        }
    }
    // 一体变形组件：同一个表面同时驱动宽度、底色与内容透明度，
    // 50% 圆角在宽态是胶囊、收窄到 34dp 时恰为正圆，形状全程连续无跳变。
    val animationsEnabled = systemAnimationsEnabled()
    val width by animateDpAsState(
        targetValue = if (active) 34.dp else idleWidth,
        animationSpec = if (animationsEnabled) tween(200) else snap(),
        label = "installMorphWidth",
    )
    val surfaceColor by animateColorAsState(
        targetValue = if (active) Color.Transparent else MiuixTheme.colorScheme.primary,
        animationSpec = if (animationsEnabled) tween(200) else snap(),
        label = "installMorphColor",
    )
    val morphT by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(200) else snap(),
        label = "installMorphContent",
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(34.dp)
            .clip(CircleShape)
            .background(surfaceColor)
            .clickable(enabled = !active) { handleInstallClick(context, state, app, onInstall) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            idleLabel,
            style = installLabelStyle,
            color = MiuixTheme.colorScheme.onPrimary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .wrapContentSize(Alignment.Center, unbounded = true)
                .graphicsLayer { alpha = 1f - morphT },
        )
        if (shownState != null && morphT > 0f) {
            Box(
                modifier = Modifier.graphicsLayer { alpha = morphT },
                contentAlignment = Alignment.Center,
            ) {
                InstallProgressCircle(shownState) {
                    handleInstallClick(context, shownState, app, onInstall)
                }
            }
        }
    }
}

@Composable
private fun InstallProgressCircle(state: InstallUiState, onClick: () -> Unit) {
    val target = state.fillProgress()
    val animationSpec = if (systemAnimationsEnabled()) tween<Float>(300) else snap()
    val progress by animateFloatAsState(target, animationSpec, label = "download-fill")
    val enabled = state.phase == InstallPhase.DOWNLOADING || state.phase == InstallPhase.PAUSED
    Box(
        modifier = Modifier.size(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 描边进度环（miuix 官方 CircularProgressIndicator：轨道 secondaryContainer + primary 弧）。
        CircularProgressIndicator(
            progress = progress,
            size = 34.dp,
            strokeWidth = 2.dp,
        )
        // 中心圆角矩形（应用商店下载标记）：按压反馈精确到方形（裁剪到 3dp 圆角内），
        // 点击取消下载。
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MiuixTheme.colorScheme.primary)
                .clickable(enabled = enabled, onClick = onClick),
        )
    }
}

private fun handleInstallClick(
    context: android.content.Context,
    state: InstallUiState?,
    app: MarketAppInfo,
    onInstall: (MarketAppInfo) -> Unit,
) {
    when (state?.phase) {
        InstallPhase.DOWNLOADING, InstallPhase.PAUSED -> {
            // 点击中心方形 = 取消下载：发送取消指令并立即清掉按钮状态，
            // 下载协程收到 DownloadCancelledException 后自行 dismiss 剩余状态。
            DownloadTaskRegistry.applyCurrent(DownloadNotificationReceiver.ACTION_CANCEL)
            InstallUiStateStore.dismiss(state.packageName)
            DownloadNotification.cancelOngoing(context)
        }
        else -> onInstall(app)
    }
}

private fun InstallUiState.resultMessage(): String = when (phase) {
    InstallPhase.COMPLETE -> "“$displayName”已成功安装。"
    InstallPhase.FAILED -> message.ifBlank { "安装过程中发生未知错误。" }
    else -> ""
}

private fun InstallPhase.isActive(): Boolean = this in ACTIVE_PHASES

private fun InstallUiState.fillProgress(): Float = when (phase) {
    InstallPhase.QUEUED -> 0f
    InstallPhase.DOWNLOADING, InstallPhase.PAUSED -> ((progress ?: 0) / 100f).coerceIn(0f, 1f)
    InstallPhase.INSTALLING, InstallPhase.AWAITING_USER_ACTION -> 1f
    else -> 0f
}

private val ACTIVE_PHASES = setOf(
    InstallPhase.QUEUED,
    InstallPhase.DOWNLOADING,
    InstallPhase.PAUSED,
    InstallPhase.INSTALLING,
    InstallPhase.AWAITING_USER_ACTION,
)
private val installLabelStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 16.sp,
)
