package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val UpdateCardBottomPadding = 16.dp

@Composable
internal fun UpdateHeader(
    updates: List<UpdateInfo>,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
) {
    if (updates.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "${updates.size} 个应用待更新",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                )
                Text(
                    formatTotalSize(updates),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                )
            }
            if (updates.isNotEmpty()) {
                ActionPill("全部更新") { onInstallAll(updates.map { it.app }) }
            }
        }
    }
}

/**
 * 融合型更新列表（应用商店样式）：全部待更新条目合并在同一张卡片内，
 * 条目之间用 16dp 内缩分隔线分隔。
 */
@Composable
internal fun MergedUpdateCard(
    updates: List<UpdateInfo>,
    optimizeNames: Boolean,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onIgnore: (UpdateInfo, Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        updates.forEachIndexed { index, update ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            UpdateCard(
                update = update,
                optimizeNames = optimizeNames,
                onOpenDetail = onOpenDetail,
                onInstall = onInstall,
                onIgnore = onIgnore,
            )
        }
    }
}

@Composable
internal fun UpdateCard(
    update: UpdateInfo,
    optimizeNames: Boolean,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onIgnore: (UpdateInfo, Boolean) -> Unit,
) {
    val app = update.app
    val displayName = optimizedAppName(app.displayName, optimizeNames)
    val installed = update.installedPackage
    // 预览行展示首行（"\n" 之前）；展开区只补剩余行，不重复已预览部分。
    val changelogText = app.changeLog.replace("\r\n", "\n")
    val changelogFirstLine = changelogText.substringBefore('\n')
    val changelogRest = changelogText.substringAfter('\n', missingDelimiterValue = "")
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    // 展开收起动画 = NexioSchedule 更新日志卡同款：箭头 tween(200) ±90° 旋转 +
    // AnimatedVisibility(expandVertically/shrinkVertically) 纵向展开收起。
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "updateCardChevron",
    )
    // 详情点击与预览行共用一个按压源：点预览行时按压高亮画在整条目区
    // （卡片层 indication），而不是只有预览行；融合卡片 Surface 会把
    // 高亮裁剪到圆角轮廓内。
    val cardInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = cardInteraction,
                indication = LocalIndication.current,
            ) { onOpenDetail(app) }
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = UpdateCardBottomPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAppIcon(app.iconUrl, displayName, Modifier.size(48.dp))
            Column(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            ) {
                Text(
                    displayName,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        "版本:",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${installed.versionName} → ${app.versionName}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UpdateSizeText(update)
            }
            InstallActionPill(app, "更新", onInstall)
        }
        // 预览行常显、不参与收展动画：一行日志 + 右端 chevron 开关；
        // 整行点击切换，indication = null 按压经共享源点亮整条目。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                ) { expanded = !expanded }
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                changelogFirstLine,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
                    .graphicsLayer { rotationZ = chevronRotation },
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        // 剩余内容 = 一个整体，特别致谢同款 AnimatedVisibility 纵向展开/收起；
        // 与预览行 0 间距，只展示预览未展示的剩余日志。
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (changelogRest.isNotBlank()) {
                    Text(
                        changelogRest,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    InlineTextAction("忽略本次", onClick = { onIgnore(update, false) })
                    InlineTextAction("永久忽略", onClick = { onIgnore(update, true) })
                }
            }
        }
    }
}

@Composable
private fun UpdateSizeText(update: UpdateInfo) {
    if (update.diffSize <= 0 || update.diffSize >= update.app.apkSize) {
        Text(
            formatBytes(update.app.apkSize),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            formatBytes(update.diffSize),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
        )
        Text(
            formatBytes(update.app.apkSize),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

/** 展开区文字操作（无背景，primary 蓝字）。 */
@Composable
private fun InlineTextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
internal fun EmptyUpdates() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            "所有已安装应用均为最新版本",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(16.dp),
        )
    }
}
