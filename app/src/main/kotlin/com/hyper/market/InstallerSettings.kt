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
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun InstallerCard(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val capabilities = remember(context) { InstallerCapabilities.read(context) }
    val selectedInstallerLabel = remember(settings.customInstallerPackage) {
        selectedInstallerLabel(context, settings.customInstallerPackage)
    }
    var showInstallerPicker by remember { mutableStateOf(false) }
    var installerCandidates by remember { mutableStateOf(emptyList<InstallerCandidate>()) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.5.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
            Column {
                InstallerChoices(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    thirdPartySummary = selectedInstallerLabel ?: "选择能够处理安装包的应用",
                    onOpenThirdPartyPicker = {
                        installerCandidates = findInstallerCandidates(context)
                        showInstallerPicker = true
                    },
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 32.dp) {
            Column {
                SwitchPreference(
                    checked = settings.saveToDownloads,
                    onCheckedChange = { onSettingsChange(settings.copy(saveToDownloads = it)) },
                    title = "安装包保存至 Download",
                    summary = "安装时同时将安装包保存到系统 Download 目录",
                )
                if (settings.installerMode == "标准安装" && capabilities.userActionNotRequiredConfigurable) {
                    SwitchPreference(
                        checked = settings.noUserAction,
                        onCheckedChange = { onSettingsChange(settings.copy(noUserAction = it)) },
                        title = "无需用户确认",
                        summary = "尝试在无需用户操作的情况下安装应用",
                    )
                }
            }
        }
    }
    ThirdPartyInstallerPicker(
        show = showInstallerPicker,
        candidates = installerCandidates,
        selectedPackage = settings.customInstallerPackage,
        onDismiss = { showInstallerPicker = false },
        onSelected = { candidate ->
            onSettingsChange(
                settings.copy(
                    installerMode = "第三方安装器",
                    customInstallerPackage = candidate.packageName,
                ),
            )
            showInstallerPicker = false
        },
    )
}
@Composable
private fun ColumnScope.InstallerChoices(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    thirdPartySummary: String,
    onOpenThirdPartyPicker: () -> Unit,
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
    InstallerChoice("第三方包安装器", thirdPartySummary, 73.dp,
        settings.installerMode == "第三方安装器") {
        onOpenThirdPartyPicker()
    }
}

private fun selectedInstallerLabel(context: android.content.Context, packageName: String): String? {
    if (packageName.isBlank()) return null
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().ifBlank { packageName }
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
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
            Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
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
    val primaryColor = MiuixTheme.colorScheme.primary
    Canvas(Modifier.size(48.dp)) {
        val check = Path().apply {
            moveTo(size.width * 0.33f, size.height * 0.50f)
            lineTo(size.width * 0.46f, size.height * 0.64f)
            lineTo(size.width * 0.65f, size.height * 0.38f)
        }
        drawPath(
            path = check,
            color = primaryColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.minDimension * 0.06f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
