package com.hyper.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.mutableIntStateOf

enum class SettingsDestination(val title: String, val summary: String) {
    IGNORED("忽略的更新", "查看并恢复已忽略的应用更新"),
    MANUAL("手动检查更新", "按包名与版本号查询单个应用的更新"),
    HISTORY("更新历史", "查看通过本应用完成的安装与更新"),
    DEVICE("设备信息", "请求使用的设备指纹与版本参数"),
    INSTALLER("安装方式", "选择安装器与安装包保存选项"),
    SAVED("保存的安装包", "管理已下载的安装包"),
    ABOUT("关于", "版本信息与项目地址"),
}

@Composable
fun SettingsSubpage(
    destination: SettingsDestination,
    settings: AppSettings,
    profile: MarketProfileSettings,
    apiClient: XiaomiApiClient,
    updateStore: UpdateStore,
    onSettingsChange: (AppSettings) -> Unit,
    onProfileChange: (MarketProfileSettings) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenSaved: (SavedPackageEntry) -> Unit,
    onReinstallSaved: (SavedPackageEntry) -> Unit,
    onBack: () -> Unit,
) {
    var confirmClearHistory by remember { mutableStateOf(false) }
    var historyVersion by remember { mutableIntStateOf(0) }
    if (destination == SettingsDestination.ABOUT) {
        SettingsAboutPage(onBack)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            SubpageHeader(
                destination.title,
                onBack,
                trailing = if (destination == SettingsDestination.HISTORY) {
                    {
                        IconButton(onClick = { confirmClearHistory = true }) {
                            Icon(
                                MiuixIcons.Close,
                                contentDescription = "清空记录",
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                } else null,
            )
        }
        when (destination) {
            SettingsDestination.IGNORED -> item { IgnoredUpdatesPage(updateStore) }
            SettingsDestination.HISTORY -> item { UpdateHistoryPage(updateStore, historyVersion) }
            SettingsDestination.SAVED -> item {
                SavedPackagesPage(updateStore, onOpenSaved, onReinstallSaved)
            }
            SettingsDestination.DEVICE -> item {
                DeviceProfilePage(profile, onProfileChange)
            }
            SettingsDestination.MANUAL -> item { ManualUpdateCard(apiClient, onInstall) }
            SettingsDestination.INSTALLER -> item {
                InstallerCard(settings, onSettingsChange)
            }
        }
    }
    if (confirmClearHistory) {
        ConfirmDialog(
            title = "清空更新历史？",
            message = "清空后无法恢复这些记录。",
            onDismiss = { confirmClearHistory = false },
            onConfirm = {
                updateStore.clearHistory()
                historyVersion += 1
                confirmClearHistory = false
            },
        )
    }
}

@Composable
private fun SubpageHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(38.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.offset(y = 7.dp).size(48.dp),
            ) {
                Icon(
                    MiuixIcons.Back,
                    contentDescription = "返回",
                    modifier = Modifier.size(24.dp),
                )
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                trailing?.invoke()
            }
        }
        PageTitle(title, bottomPadding = 8.dp)
    }
}
