package com.hyper.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun IgnoredUpdatesPage(store: UpdateStore) {
    var entries by remember { mutableStateOf(store.ignoredUpdates()) }
    val permanent = entries.filter { it.permanent }
    val temporary = entries.filterNot { it.permanent }
    Column {
        SectionLabel("永久忽略更新")
        Spacer(Modifier.height(6.dp))
        IgnoredGroup(permanent, store) { entries = store.ignoredUpdates() }
        Spacer(Modifier.height(12.dp))
        SectionLabel("仅忽略本次更新")
        Spacer(Modifier.height(6.dp))
        IgnoredGroup(temporary, store) { entries = store.ignoredUpdates() }
    }
}

@Composable
private fun IgnoredGroup(
    entries: List<IgnoredUpdate>,
    store: UpdateStore,
    onChanged: () -> Unit,
) {
    if (entries.isEmpty()) {
        DataEmpty("暂无忽略的更新")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry ->
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.displayName, fontSize = 20.sp)
                        Text(
                            "${entry.packageName}\n${entry.versionName} · " +
                                if (entry.permanent) "永久忽略" else "忽略本次更新",
                            color = Color(0xFF777777),
                            fontSize = 15.sp,
                        )
                    }
                    ActionPill("恢复") {
                        store.restoreIgnored(entry.packageName)
                        onChanged()
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateHistoryPage(store: UpdateStore) {
    var entries by remember { mutableStateOf(store.history()) }
    var confirmClear by remember { mutableStateOf(false) }
    if (entries.isEmpty()) {
        CenteredDataEmpty("暂无更新记录")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ActionPill("清空记录") {
                confirmClear = true
            }
        }
        entries.forEach { entry -> HistoryCard(entry) }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = "清空更新历史？",
            message = "清空后无法恢复这些记录。",
            onDismiss = { confirmClear = false },
            onConfirm = {
                store.clearHistory()
                entries = emptyList()
                confirmClear = false
            },
        )
    }
}

@Composable
private fun HistoryCard(entry: UpdateHistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.displayName, fontSize = 20.sp)
            Text(
                "${entry.versionName} · ${if (entry.firstInstall) "首次安装" else "更新完成"}",
                color = Color(0xFF555555),
            )
            Text(entry.packageName, color = Color(0xFF888888), fontSize = 14.sp)
            Text(formatTimestamp(entry.installedAt), color = Color(0xFF888888), fontSize = 14.sp)
        }
    }
}

@Composable
fun SavedPackagesPage(
    store: UpdateStore,
    onOpen: (SavedPackageEntry) -> Unit,
    onReinstall: (SavedPackageEntry) -> Unit,
) {
    var entries by remember { mutableStateOf(store.savedPackages()) }
    var pendingDelete by remember { mutableStateOf<SavedPackageEntry?>(null) }
    if (entries.isEmpty()) {
        CenteredDataEmpty("暂无保存的安装包")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEach { entry ->
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemoteAppIcon(
                        entry.iconUrl,
                        entry.displayName,
                        Modifier.size(58.dp).padding(end = 14.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.displayName, fontSize = 20.sp)
                        Text(
                            "${entry.versionName} · ${formatFileSize(entry.size)}" +
                                if (entry.artifacts.size > 1) " · ${entry.artifacts.size} 个文件" else "",
                            color = Color(0xFF555555),
                        )
                        Text(entry.fileName, color = Color(0xFF888888), fontSize = 14.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill("点击打开") { onOpen(entry) }
                        ActionPill("重新下载") { onReinstall(entry) }
                        ActionPill("删除") { pendingDelete = entry }
                    }
                }
            }
        }
    }
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "删除保存的安装包？",
            message = entry.path,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                store.deleteSavedPackage(entry)
                entries = store.savedPackages()
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DataEmpty(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(CardWhite),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            message,
            color = Color(0xFF777777),
            fontSize = 17.sp,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun CenteredDataEmpty(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().height(752.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = Color(0xFF777777), fontSize = 18.sp)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = java.util.Calendar.getInstance()
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(timestamp))
    if (sameDay(date, now)) return "今天 $time"
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay(date, now)) return "昨天 $time"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
}

private fun sameDay(first: java.util.Calendar, second: java.util.Calendar): Boolean =
    first.get(java.util.Calendar.YEAR) == second.get(java.util.Calendar.YEAR) &&
        first.get(java.util.Calendar.DAY_OF_YEAR) == second.get(java.util.Calendar.DAY_OF_YEAR)

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "大小未知"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024) {
        "%.1fGB".format(java.util.Locale.CHINA, megabytes / 1024)
    } else {
        "%.1fMB".format(java.util.Locale.CHINA, megabytes)
    }
}

private fun savedPackageName(location: String): String =
    location.substringAfterLast('/').ifBlank { location }
