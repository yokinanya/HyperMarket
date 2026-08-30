package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val UpdateCardBottomPadding = 17.dp

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
                    style = compactTextStyle(17.sp, 20.sp),
                )
                Text(
                    formatTotalSize(updates),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = compactTextStyle(14.sp, 16.sp),
                )
            }
            if (updates.isNotEmpty()) {
                ActionPill("全部更新") { onInstallAll(updates.map { it.app }) }
            }
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
    var expanded by remember(app.packageName) { mutableStateOf(false) }
    // 展开收起动画 = NexioSchedule 更新日志卡同款：箭头 tween(200) ±90° 旋转 +
    // AnimatedVisibility(expandVertically/shrinkVertically) 纵向展开收起。
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "updateCardChevron",
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDetail(app) }
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = UpdateCardBottomPadding),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteAppIcon(app.iconUrl, displayName, Modifier.size(48.dp))
            Column(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            ) {
                Text(
                    displayName,
                    style = compactTextStyle(17.sp, 20.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    Text(
                        "版本:",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = compactTextStyle(14.sp, 16.sp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${installed.versionName} → ${app.versionName}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = compactTextStyle(14.sp, 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                UpdateSizeText(update)
            }
            InstallActionPill(app, "更新", onInstall)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    app.changeLog,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = compactTextStyle(14.sp, 16.sp),
                    maxLines = if (expanded) 4 else 1,
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
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InlineTextAction("忽略本次", onClick = { onIgnore(update, false) })
                    InlineTextAction("永久忽略", onClick = { onIgnore(update, true) })
                }
            }
        }
        }
    }
}

@Composable
private fun UpdateSizeText(update: UpdateInfo) {
    val style = compactTextStyle(14.sp, 16.sp)
    if (update.diffSize <= 0 || update.diffSize >= update.app.apkSize) {
        Text(
            formatBytes(update.app.apkSize),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = style,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatBytes(update.diffSize), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = style)
        Text(
            formatBytes(update.app.apkSize),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = style,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@Composable
internal fun EmptyUpdates() {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Text(
            "所有已安装应用均为最新版本",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun InlineTextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

private fun compactTextStyle(fontSize: TextUnit, lineHeight: TextUnit) = TextStyle(
    fontSize = fontSize,
    lineHeight = lineHeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
