package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun IgnoredUpdatesPage(store: UpdateStore) {
    var entries by remember { mutableStateOf(store.ignoredUpdates()) }
    val permanent = entries.filter { it.permanent }
    val temporary = entries.filterNot { it.permanent }
    Column {
        SectionLabel("永久忽略更新", insideMargin = SectionLabelPaddedContainerMargin)
        IgnoredGroup(permanent, store) { entries = store.ignoredUpdates() }
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("仅忽略本次更新", insideMargin = SectionLabelPaddedContainerMargin)
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.displayName, fontSize = 17.sp)
                        Text(
                            "${entry.packageName}\n${entry.versionName} · " +
                                if (entry.permanent) "永久忽略" else "忽略本次更新",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
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
fun UpdateHistoryPage(store: UpdateStore, refreshKey: Any? = null) {
    var entries by remember(refreshKey) { mutableStateOf(store.history()) }
    if (entries.isEmpty()) {
        CenteredDataEmpty("暂无更新记录")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries
            .groupBy { historyDateLabel(it.installedAt) }
            .forEach { (date, dayEntries) ->
                // 每天一组：标题→卡片间距 = 标题自带 8dp（对齐设置页节奏），
                // 组间 12dp 由外层 spacedBy 提供。
                Column {
                    SectionLabel(date, insideMargin = SectionLabelPaddedContainerMargin)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        dayEntries.forEach { entry -> HistoryCard(entry) }
                    }
                }
            }
    }
}

@Composable
private fun HistoryCard(entry: UpdateHistoryEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.iconUrl.isNotBlank()) {
                RemoteAppIcon(entry.iconUrl, entry.displayName, Modifier.size(48.dp))
            } else {
                InstalledAppIcon(entry.packageName, entry.displayName, Modifier.size(48.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(entry.displayName, fontSize = 17.sp, maxLines = 1)
                Text(
                    "${if (entry.firstInstall) "安装" else "更新"} ${entry.versionName}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                historyTimeLabel(entry.installedAt),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun historyDateLabel(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy年M月d日", java.util.Locale.CHINA).format(java.util.Date(timestamp))

private fun historyTimeLabel(timestamp: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(timestamp))

/** 保存的安装包编辑态：长按进入多选、底栏批量删除（MGAide「我的下载」同款交互）。 */
internal class SavedPackagesEditState {
    var isEditing by mutableStateOf(false)
    var selectedIds by mutableStateOf(emptySet<String>())
    fun enterEditing(id: String) {
        isEditing = true
        selectedIds = setOf(id)
    }
    fun toggle(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    fun reset() {
        isEditing = false
        selectedIds = emptySet()
    }
}

@Composable
internal fun SavedPackagesPage(
    store: UpdateStore,
    onOpen: (SavedPackageEntry) -> Unit,
    onReinstall: (SavedPackageEntry) -> Unit,
    editState: SavedPackagesEditState,
    refreshKey: Any? = null,
) {
    var entries by remember(refreshKey) { mutableStateOf(store.savedPackages()) }
    var pendingAction by remember { mutableStateOf<SavedPackageEntry?>(null) }
    if (entries.isEmpty()) {
        CenteredDataEmpty("暂无安装包")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "${entries.size} 个安装包 · 共 ${formatFileSize(entries.sumOf { it.size })}",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 4.dp),
        )
        entries.forEach { entry ->
            SavedPackageCard(
                entry = entry,
                editState = editState,
                onShowActions = { pendingAction = entry },
            )
        }
    }
    pendingAction?.let { entry ->
        // miuix 弹窗：询问对该安装包的操作，左「删除」右「安装」（安装为主操作）。
        WindowDialog(
            show = true,
            title = entry.displayName,
            summary = "${entry.versionName} · ${formatFileSize(entry.size)}",
            onDismissRequest = { pendingAction = null },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "删除",
                    onClick = {
                        pendingAction = null
                        store.deleteSavedPackage(entry)
                        entries = store.savedPackages()
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "安装",
                    onClick = {
                        pendingAction = null
                        onOpen(entry)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedPackageCard(
    entry: SavedPackageEntry,
    editState: SavedPackagesEditState,
    onShowActions: () -> Unit,
) {
    val selected = entry.id in editState.selectedIds
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (editState.isEditing) editState.toggle(entry.id) else onShowActions()
                    },
                    onLongClick = { if (!editState.isEditing) editState.enterEditing(entry.id) },
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteAppIcon(
                entry.iconUrl,
                entry.displayName,
                Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.displayName, fontSize = 17.sp, maxLines = 1)
                Text(
                    "${entry.versionName} · ${formatFileSize(entry.size)}" +
                        if (entry.artifacts.size > 1) " · ${entry.artifacts.size} 个文件" else "",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
                Text(
                    "保存于 ${formatTimestamp(entry.savedAt)}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            if (editState.isEditing) {
                // 编辑态右侧 = miuix Checkbox（26dp 圆形勾选，MGAide MgCheckbox 即仿此设计）。
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = null,
                    colors = CheckboxDefaults.checkboxColors(
                        uncheckedBackgroundColor = Color.Transparent,
                        uncheckedForegroundColor = MiuixTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.size(26.dp),
                )
            } else {
                // 非编辑态右侧 = 设置页列表同款右箭头（miuix ArrowPreference 的 ArrowRight，10×16dp）。
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(width = 10.dp, height = 16.dp),
                )
            }
        }
    }
}

/** 编辑底栏（MGAide「我的下载」DownloadEditDeleteBar 同款）：删除按钮用 miuix Delete 图标。 */
@Composable
internal fun SavedPackagesEditBar(
    selectedCount: Int,
    blur: BarBlur,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barHeight = 64.dp
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val enabled = selectedCount > 0
    val tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceContainerVariant
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight + navBarPadding)
            .barBlurMaterial(blur, MiuixTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.outline.copy(alpha = 0.5f),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(barHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // 圆形按压反馈区（clip 成圆套住图标与文字，MGAide EditActionButton 同款）。
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .pressable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                        ),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除",
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("删除", fontSize = 12.sp, color = tint)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = title,
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "确认",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun DataEmpty(message: String) {
    Card(modifier = Modifier.fillMaxWidth().height(55.dp)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                message,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun CenteredDataEmpty(message: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().height(620.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 18.sp)
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
