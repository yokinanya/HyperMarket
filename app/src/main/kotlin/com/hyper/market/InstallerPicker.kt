package com.hyper.market

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class InstallerCandidate(val packageName: String, val label: String)

@Composable
internal fun ThirdPartyInstallerPicker(
    show: Boolean,
    candidates: List<InstallerCandidate>,
    selectedPackage: String,
    onDismiss: () -> Unit,
    onSelected: (InstallerCandidate) -> Unit,
) {
    WindowDialog(
        show = show,
        title = "选择包安装器",
        onDismissRequest = onDismiss,
        insideMargin = DpSize(0.dp, 24.dp),
    ) {
        InstallerCandidateList(candidates, selectedPackage, onSelected)
        TextButton(
            text = "取消",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

@Composable
private fun InstallerCandidateList(
    candidates: List<InstallerCandidate>,
    selectedPackage: String,
    onSelected: (InstallerCandidate) -> Unit,
) {
    if (candidates.isEmpty()) {
        Text(
            "没有可用的包安装器",
            color = Color(0xFFD14343),
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
        items(candidates, key = { it.packageName }) { candidate ->
            InstallerCandidateRow(candidate, candidate.packageName == selectedPackage, onSelected)
        }
    }
}

@Composable
private fun InstallerCandidateRow(
    candidate: InstallerCandidate,
    selected: Boolean,
    onSelected: (InstallerCandidate) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(candidate) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(candidate.label, fontSize = 17.sp)
            Text(candidate.packageName, color = Color(0xFF777777), fontSize = 14.sp)
        }
        RadioButton(selected = selected, onClick = { onSelected(candidate) })
    }
}

internal fun findInstallerCandidates(context: Context): List<InstallerCandidate> {
    val packageManager = context.packageManager
    return installerProbeIntents(context).flatMap { intent ->
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }.mapNotNull { info ->
        val packageName = info.activityInfo?.packageName?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        if (packageName == context.packageName) return@mapNotNull null
        val label = info.loadLabel(packageManager).toString().ifBlank { packageName }
        InstallerCandidate(packageName, label)
    }.distinctBy(InstallerCandidate::packageName)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstallerCandidate::label))
}

private fun installerProbeIntents(context: Context): List<Intent> {
    val probeUris = listOf(
        Uri.parse("content://${context.packageName}.fileprovider/installer_probe.apk"),
        Uri.parse("file:///sdcard/Download/installer_probe.apk"),
    )
    val intents = mutableListOf(
        Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_DEFAULT).setType(APK_MIME),
    )
    probeUris.forEach { uri ->
        intents += apkIntent(Intent.ACTION_VIEW, uri)
        intents += apkIntent(Intent.ACTION_INSTALL_PACKAGE, uri)
    }
    return intents
}

private fun apkIntent(action: String, uri: Uri): Intent =
    Intent(action).addCategory(Intent.CATEGORY_DEFAULT).setDataAndType(uri, APK_MIME)

private const val APK_MIME = "application/vnd.android.package-archive"
