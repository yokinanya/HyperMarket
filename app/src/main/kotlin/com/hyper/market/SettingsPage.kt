package com.hyper.market

import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsPage(
    settings: AppSettings,
    scrollState: ScrollState,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    val compactTitle by remember { derivedStateOf { scrollState.value > 90 } }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 12.dp, top = 38.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PageTitle("设置")
            Column(
                modifier = Modifier.offset(y = (-20.25).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("更新")
                    SettingsUpdateSection(settings, onSettingsChange, onOpenDestination)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("搜索")
                    SearchSettings(settings, onSettingsChange)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("应用详情")
                    DetailSettings(settings, onSettingsChange)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("通用")
                    GeneralSettings(settings, onSettingsChange, onOpenDestination)
                }
            }
        }
        AnimatedVisibility(
            visible = compactTitle,
            enter = fadeIn(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Text(
                "设置",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun SettingsUpdateSection(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    val context = LocalContext.current
    val capabilities = remember(context) { InstallerCapabilities.read(context) }
    SettingCard {
        SettingSwitchRow("显示系统应用", "在可更新列表中包含系统应用", settings.showSystemApps) {
            onSettingsChange(settings.copy(showSystemApps = it))
        }
        if (capabilities.deltaUpdateSupported) {
            SettingSwitchRow("增量更新", "可用时下载补丁包并合成为 APK", settings.incrementalUpdates) {
                onSettingsChange(settings.copy(incrementalUpdates = it))
            }
        }
        SettingLinkRow(SettingsDestination.IGNORED, onOpenDestination)
        SettingLinkRow(SettingsDestination.MANUAL, onOpenDestination)
        SettingLinkRow(SettingsDestination.HISTORY, onOpenDestination)
    }
}

@Composable
private fun SearchSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SettingCard {
        SettingSwitchRow("去除推广应用", "隐藏搜索结果中标记为推广的App", settings.removeSearchAds) {
            onSettingsChange(settings.copy(removeSearchAds = it))
        }
        SettingSwitchRow("去除快应用", "隐藏搜索结果中标记为快应用的App", settings.removeQuickApps) {
            onSettingsChange(settings.copy(removeQuickApps = it))
        }
        SettingSwitchRow("去除预约应用", "隐藏搜索结果中尚未上线的预约App", settings.removeReservationApps) {
            onSettingsChange(settings.copy(removeReservationApps = it))
        }
    }
}

@Composable
private fun DetailSettings(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    SettingCard {
        SettingSwitchRow("显示优惠活动", "在应用详情页显示优惠活动内容", settings.showPromotions) {
            onSettingsChange(settings.copy(showPromotions = it))
        }
        SettingSwitchRow("显示用户评论", "在应用详情页显示用户评论", settings.showComments) {
            onSettingsChange(settings.copy(showComments = it))
        }
        SettingSwitchRow("显示同开发者应用", "在应用详情页显示同开发者的其他应用", settings.showSameDeveloper) {
            onSettingsChange(settings.copy(showSameDeveloper = it))
        }
    }
}

@Composable
private fun GeneralSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    var showStartPageDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    SettingCard {
        SettingHomeRow(settings.startPage) {
            showStartPageDialog = true
        }
        SettingSwitchRow("优化应用名称", "移除推广语，名称本身含横线的可能会误裁", settings.optimizeNames) {
            onSettingsChange(settings.copy(optimizeNames = it))
        }
        SettingLinkRow(SettingsDestination.DEVICE, onOpenDestination)
        SettingLinkRow(SettingsDestination.INSTALLER, onOpenDestination)
        SettingLinkRow(SettingsDestination.SAVED, onOpenDestination)
        SettingSwitchRow("小米超级岛优化", "请确保授权 Shizuku 或使用模块绕过白名单", settings.xiaomiIslandOptimization) {
            onSettingsChange(settings.copy(xiaomiIslandOptimization = it))
        }
        SettingLinkRow(SettingsDestination.ABOUT, onOpenDestination)
    }
    if (showStartPageDialog) {
        StartPageDialog(
            selectedPage = settings.startPage,
            onDismiss = { showStartPageDialog = false },
            onSelected = { page ->
                showStartPageDialog = false
                onSettingsChange(settings.copy(startPage = page))
            },
        )
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingSwitchRow(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    SwitchPreference(
        checked = checked,
        onCheckedChange = onChecked,
        title = title,
        summary = summary,
    )
}

@Composable
private fun SettingLinkRow(destination: SettingsDestination, onOpen: (SettingsDestination) -> Unit) {
    SettingLinkRow(destination.title, destination.summary) { onOpen(destination) }
}

@Composable
private fun SettingLinkRow(title: String, summary: String, onClick: () -> Unit) {
    ArrowPreference(title = title, summary = summary, onClick = onClick)
}

@Composable
private fun SettingHomeRow(startPage: Int, onClick: () -> Unit) {
    val value = when (startPage) {
        1 -> "更新"
        2 -> "搜索"
        else -> "今日"
    }
    ArrowPreference(
        title = "首页",
        summary = "启动时打开的页面",
        endActions = { Text(value) },
        onClick = onClick,
    )
}
