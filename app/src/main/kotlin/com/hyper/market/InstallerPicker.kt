package com.hyper.market

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card

private data class InstallerCandidate(val packageName: String, val label: String)

@Composable
internal fun ThirdPartyInstallerPicker(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    var candidates by remember { mutableStateOf(emptyList<InstallerCandidate>()) }
    var searched by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionPill("选择包安装器") {
            searched = true
            candidates = findCandidates(context)
        }
        if (searched && candidates.isEmpty()) {
            Text("没有可用的包安装器", color = Color(0xFFD14343), fontSize = 14.sp)
        }
        if (settings.customInstallerPackage.isNotBlank()) {
            Text("已选择：${settings.customInstallerPackage}", color = AccentBlue, fontSize = 14.sp)
        }
        candidates.forEach { candidate ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    onSettingsChange(settings.copy(customInstallerPackage = candidate.packageName))
                },
                cornerRadius = 18.dp,
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(candidate.label, fontSize = 16.sp)
                        Text(candidate.packageName, color = Color(0xFF777777), fontSize = 13.sp)
                    }
                    if (candidate.packageName == settings.customInstallerPackage) {
                        Text("已选", color = AccentBlue, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun findCandidates(context: Context): List<InstallerCandidate> {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW).setType(APK_MIME),
        Intent(Intent.ACTION_INSTALL_PACKAGE).setType(APK_MIME),
        Intent(Intent.ACTION_SEND_MULTIPLE).setType(APK_MIME),
    )
    return intents.flatMap { intent ->
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }.mapNotNull { info ->
        val activity = info.activityInfo ?: return@mapNotNull null
        if (activity.packageName == context.packageName) return@mapNotNull null
        InstallerCandidate(
            activity.packageName,
            info.loadLabel(context.packageManager).toString().ifBlank { activity.packageName },
        )
    }
        .distinctBy(InstallerCandidate::packageName)
}

private const val APK_MIME = "application/vnd.android.package-archive"
