package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.Switch

private val SETTINGS_SPINNER_OFFSET_X = 19.dp

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
                .padding(horizontal = 12.dp, vertical = 38.dp),
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
            Text("设置", color = Color(0xFF111111), fontSize = 26.sp, modifier = Modifier.padding(top = 10.dp))
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
        modifier = Modifier.fillMaxWidth().animateContentSize(tween(240)),
        cornerRadius = 32.dp,
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingSwitchRow(title: String, summary: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChecked(!checked) }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, summary)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingLinkRow(destination: SettingsDestination, onOpen: (SettingsDestination) -> Unit) {
    SettingLinkRow(destination.title, destination.summary) { onOpen(destination) }
}

@Composable
private fun SettingLinkRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingText(title, summary)
        SettingChevron()
    }
}

@Composable
private fun SettingHomeRow(startPage: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(73.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("首页", fontSize = 17.sp, color = Color(0xFF202020))
            Text("启动时打开的页面", fontSize = 14.sp, color = Color(0xFF8A8A8A))
        }
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                when (startPage) {
                    1 -> "更新"
                    2 -> "搜索"
                    else -> "今日"
                },
                fontSize = 14.sp,
                color = Color(0xFFAAAAAA),
                modifier = Modifier.offset(x = 3.dp),
            )
            HomeSpinnerIcon()
        }
    }
}

@Composable
private fun HomeSpinnerIcon() {
    Box(
        modifier = Modifier.size(48.dp).offset(x = SETTINGS_SPINNER_OFFSET_X),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            DropdownArrowEndAction(actionColor = Color(0xFFAAAAAA))
        }
    }
}

@Composable
private fun SettingChevron() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.16f)
            lineTo(size.width * 0.9f, size.height * 0.5f)
            lineTo(size.width * 0.58f, size.height * 0.84f)
        }
        drawPath(
            path = path,
            color = Color(0xFFAAAAAA),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
