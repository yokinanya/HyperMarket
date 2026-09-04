package com.hyper.market

// 安装方式页（miuix 规范）：单选组 = 官方 RadioButtonPreference（标准行高/内边距、
// 17sp 标题 + 14sp summary、按压反馈、RadioButton 视觉），卡片圆角用 miuix 默认值。

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
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
        Card(modifier = Modifier.fillMaxWidth()) {
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
private fun InstallerChoices(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    thirdPartySummary: String,
    onOpenThirdPartyPicker: () -> Unit,
) {
    RadioButtonPreference(
        title = "标准安装",
        summary = "普通应用走系统确认，具备系统权限时自动静默安装",
        selected = settings.installerMode == "标准安装",
        onClick = { onSettingsChange(settings.copy(installerMode = "标准安装")) },
    )
    RadioButtonPreference(
        title = "Root 静默安装",
        summary = "需要 root 权限",
        selected = settings.installerMode == "Root 静默安装",
        onClick = { onSettingsChange(settings.copy(installerMode = "Root 静默安装")) },
    )
    RadioButtonPreference(
        title = "Shizuku 静默安装",
        summary = "需要 Shizuku 正在运行并已授权",
        selected = settings.installerMode == "Shizuku 静默安装",
        onClick = { onSettingsChange(settings.copy(installerMode = "Shizuku 静默安装")) },
    )
    RadioButtonPreference(
        title = "第三方包安装器",
        summary = thirdPartySummary,
        selected = settings.installerMode == "第三方安装器",
        onClick = onOpenThirdPartyPicker,
    )
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
