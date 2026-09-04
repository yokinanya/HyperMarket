package com.hyper.market

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SettingsPage(
    settings: AppSettings,
    scrollState: ScrollState,
    topPadding: Dp,
    bottomBarHeight: Dp,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenDestination: (SettingsDestination) -> Unit,
) {
    // 参考项目标准（Hyper-pick-up MiuixPreferenceSection/MiuixSettingsGroup）：
    // 滚动容器无横向边距，顶部留 topPadding、底部余量 48dp；
    // 小标题用 miuix SmallTitle 自带内边距（横向 28dp、纵向 8dp）；
    // 分组卡片各自 padding(horizontal = 12.dp) + padding(bottom = 12.dp) 形成分组节奏。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = 48.dp + bottomBarHeight),
    ) {
        SettingsSection("更新") {
            SettingsUpdateSection(settings, onSettingsChange, onOpenDestination)
        }
        SettingsSection("搜索") {
            SearchSettings(settings, onSettingsChange)
        }
        SettingsSection("应用详情") {
            DetailSettings(settings, onSettingsChange)
        }
        SettingsSection("通用") {
            GeneralSettings(settings, onSettingsChange, onOpenDestination)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        SectionLabel(title)
        content()
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
    SettingCard {
        // miuix 官方下拉选择组件（示例 DropdownSection 标准），替代自绘展开菜单。
        OverlayDropdownPreference(
            title = "首页",
            summary = "启动时打开的页面",
            items = listOf("今日", "更新", "搜索"),
            selectedIndex = settings.startPage.coerceIn(0, 2),
            onSelectedIndexChange = { page -> onSettingsChange(settings.copy(startPage = page)) },
        )
        SettingSwitchRow("优化应用名称", "移除推广语，名称本身含横线的可能会误裁", settings.optimizeNames) {
            onSettingsChange(settings.copy(optimizeNames = it))
        }
        SettingLinkRow(SettingsDestination.DEVICE, onOpenDestination)
        SettingLinkRow(SettingsDestination.INSTALLER, onOpenDestination)
        SettingLinkRow(SettingsDestination.SAVED, onOpenDestination)
        SettingSwitchRow("预测性返回手势", "返回时显示跟随手势的缩放与位移动画", settings.predictiveBack) {
            onSettingsChange(settings.copy(predictiveBack = it))
        }
        SettingSwitchRow("小米超级岛优化", "请确保授权 Shizuku 或使用模块绕过白名单", settings.xiaomiIslandOptimization) {
            onSettingsChange(settings.copy(xiaomiIslandOptimization = it))
        }
        SettingLinkRow(SettingsDestination.ABOUT, onOpenDestination)
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
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
