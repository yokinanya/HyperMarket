package com.hyper.market

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun DetailActionGroup(
    app: MarketAppInfo,
    state: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    val stateFlow = remember(app.packageName) { InstallUiStateStore.observe(app.packageName) }
    val installState by stateFlow.collectAsState(initial = null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailActionButton(app, state, onInstall, onOpenInstalled)
        // 下载/安装进行中隐藏“更多操作”入口；下载中取消走进度圆球中心方块。
        if (state == DetailActionState.INSTALLED && installState?.phase?.isDetailActive() != true) {
            DetailMoreButton { onInstall(app) }
        }
    }
}

@Composable
private fun DetailMoreButton(onRedownload: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { expanded = true },
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
    ) {
        Icon(MiuixIcons.Light.More, contentDescription = "更多", modifier = Modifier.size(22.dp))
    }
    WindowDialog(show = expanded, title = "更多操作", onDismissRequest = { expanded = false }) {
        TextButton(
            text = "重新下载",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                expanded = false
                onRedownload()
            },
        )
    }
}

@Composable
private fun DetailActionButton(
    app: MarketAppInfo,
    state: DetailActionState,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) = when (state) {
    DetailActionState.NOT_INSTALLED -> InstallActionPill(app, "安装", onInstall)
    DetailActionState.UPDATE_AVAILABLE -> InstallActionPill(app, "更新", onInstall)
    DetailActionState.INSTALLED -> ActionPill("打开", primary = false) { onOpenInstalled(app) }
}

internal fun detailActionState(context: Context, app: MarketAppInfo): DetailActionState {
    val installedCode = try {
        val info = context.packageManager.getPackageInfo(app.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    } catch (_: PackageManager.NameNotFoundException) {
        return DetailActionState.NOT_INSTALLED
    }
    return if (app.versionCode > installedCode) DetailActionState.UPDATE_AVAILABLE
    else DetailActionState.INSTALLED
}

private fun InstallPhase.isDetailActive() = this in ACTIVE_DETAIL_PHASES

private val ACTIVE_DETAIL_PHASES = setOf(
    InstallPhase.QUEUED,
    InstallPhase.DOWNLOADING,
    InstallPhase.PAUSED,
    InstallPhase.INSTALLING,
    InstallPhase.AWAITING_USER_ACTION,
)

internal enum class DetailActionState { NOT_INSTALLED, UPDATE_AVAILABLE, INSTALLED }
