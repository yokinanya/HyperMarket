package com.hyper.market

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.installer.DownloadNotificationReceiver
import com.hyper.market.installer.DownloadTaskRegistry
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
    fun pauseCurrent() = activePackage?.let { update(it, InstallPhase.PAUSED) }

    @JvmStatic
    @Synchronized
    fun resumeCurrent() = activePackage?.let { update(it, InstallPhase.DOWNLOADING) }

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
    val states by InstallUiStateStore.states.collectAsState()
    val state = states[app.packageName]
    if (state?.phase?.isActive() == true) {
        InstallProgressPill(state) { handleInstallClick(state, app, onInstall) }
        return
    }
    Button(
        onClick = { handleInstallClick(state, app, onInstall) },
        modifier = Modifier.width(InstallPillWidth),
        cornerRadius = 28.dp,
        minWidth = 0.dp,
        minHeight = 32.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state?.label(idleLabel) ?: idleLabel, color = Color.White, style = installLabelStyle)
        }
    }
}

@Composable
private fun InstallProgressPill(state: InstallUiState, onClick: () -> Unit) {
    val label = state.label("")
    val target = state.fillProgress()
    val progress by animateFloatAsState(target, tween(300), label = "download-fill")
    val enabled = state.phase == InstallPhase.DOWNLOADING || state.phase == InstallPhase.PAUSED
    BoxWithConstraints(
        modifier = Modifier.width(InstallPillWidth).height(32.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFECECEC))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(maxWidth * progress)
                .clipToBounds()
                .background(AccentBlue),
        )
        Text(
            label,
            color = Color.White,
            style = installLabelStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        )
    }
}

private fun handleInstallClick(
    state: InstallUiState?,
    app: MarketAppInfo,
    onInstall: (MarketAppInfo) -> Unit,
) {
    when (state?.phase) {
        InstallPhase.DOWNLOADING -> {
            DownloadTaskRegistry.applyCurrent(DownloadNotificationReceiver.ACTION_PAUSE)
            InstallUiStateStore.pauseCurrent()
        }
        InstallPhase.PAUSED -> {
            DownloadTaskRegistry.applyCurrent(DownloadNotificationReceiver.ACTION_RESUME)
            InstallUiStateStore.resumeCurrent()
        }
        else -> onInstall(app)
    }
}

private fun InstallUiState.label(idleLabel: String): String = when (phase) {
    InstallPhase.QUEUED -> "获取链接"
    InstallPhase.DOWNLOADING -> progress?.let { "$it%" } ?: "获取链接"
    InstallPhase.PAUSED -> "已暂停"
    InstallPhase.INSTALLING -> "正在安装"
    InstallPhase.AWAITING_USER_ACTION -> "等待安装确认"
    InstallPhase.COMPLETE -> idleLabel
    InstallPhase.FAILED -> idleLabel
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
private val InstallPillWidth = 88.dp
private val TERMINAL_PHASES = setOf(InstallPhase.COMPLETE, InstallPhase.FAILED)
private val installLabelStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 16.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
