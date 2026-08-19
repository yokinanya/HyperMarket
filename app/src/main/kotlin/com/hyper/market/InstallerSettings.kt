package com.hyper.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch

private val DownloadSummaryWidth = 290.dp

@Composable
internal fun InstallerCard(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.5.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
            Column { installerChoices(settings, onSettingsChange) }
        }
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
            Column {
                InstallerToggle(
                    "安装包保存至 Download",
                    "安装时同时将安装包保存到系统 Download 目录",
                    91.25.dp,
                    settings.saveToDownloads,
                ) { onSettingsChange(settings.copy(saveToDownloads = it)) }
                InstallerToggle(
                    "无需用户确认",
                    "尝试在无需用户操作的情况下安装应用",
                    73.dp,
                    settings.noUserAction,
                ) { onSettingsChange(settings.copy(noUserAction = it)) }
            }
        }
        if (settings.installerMode == "第三方安装器") {
            ThirdPartyInstallerPicker(settings, onSettingsChange)
        }
    }
}

@Composable
private fun ColumnScope.installerChoices(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    InstallerChoice("标准安装", "普通应用走系统确认，具备系统权限时自动静默安装", 73.dp,
        settings.installerMode == "标准安装") {
        onSettingsChange(settings.copy(installerMode = "标准安装"))
    }
    InstallerChoice("Root 静默安装", "需要 root 权限", 73.dp,
        settings.installerMode == "Root 静默安装") {
        onSettingsChange(settings.copy(installerMode = "Root 静默安装"))
    }
    InstallerChoice("Shizuku 静默安装", "需要 Shizuku 正在运行并已授权", 73.dp,
        settings.installerMode == "Shizuku 静默安装") {
        onSettingsChange(settings.copy(installerMode = "Shizuku 静默安装"))
    }
    InstallerChoice("第三方包安装器", "选择能够处理安装包的应用", 73.dp,
        settings.installerMode == "第三方安装器") {
        onSettingsChange(settings.copy(installerMode = "第三方安装器"))
    }
}

@Composable
private fun InstallerChoice(
    title: String,
    summary: String,
    height: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, fontSize = 17.sp)
            Text(summary, color = Color(0xFF777777), fontSize = 14.sp)
        }
        Box(
            modifier = Modifier.padding(end = 5.dp).size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) CheckMark()
        }
    }
}

@Composable
private fun CheckMark() {
    Canvas(Modifier.size(48.dp)) {
        val check = Path().apply {
            moveTo(size.width * 0.33f, size.height * 0.50f)
            lineTo(size.width * 0.46f, size.height * 0.64f)
            lineTo(size.width * 0.65f, size.height * 0.38f)
        }
        drawPath(
            path = check,
            color = AccentBlue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.minDimension * 0.06f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun InstallerToggle(
    title: String,
    summary: String,
    height: Dp,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(height).clickable { onChecked(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 1.dp)) {
            Text(title, fontSize = 17.sp)
            Text(
                summary,
                color = Color(0xFF777777),
                fontSize = 14.sp,
                modifier = if (title == "安装包保存至 Download") {
                    Modifier.width(DownloadSummaryWidth)
                } else {
                    Modifier
                },
            )
        }
        Box(modifier = Modifier.padding(end = 16.dp)) {
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
