package com.hyper.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchField(
    value: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    // miuix 官方 SearchBar + InputField：胶囊输入框、内置搜索/清除图标、
    // 返回手势收起（收起时 InputField 自行清空输入并放弃焦点）。
    SearchBar(
        inputField = {
            InputField(
                query = value,
                onQueryChange = onValueChange,
                onSearch = { onSearch() },
                expanded = editing,
                onExpandedChange = onEditingChange,
                label = "搜索应用",
            )
        },
        onExpandedChange = onEditingChange,
        expanded = editing,
        modifier = Modifier.fillMaxWidth(),
        // 外层 LazyColumn 已有 12dp 横向边距，SearchBar 默认 insideMargin(12dp) 会叠加成 24dp。
        insideMargin = DpSize(0.dp, 0.dp),
        content = {},
    )
}

@Composable
internal fun HistoryHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HISTORY_HEADER_TOP_PADDING, start = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("搜索历史", fontSize = 18.sp, color = MiuixTheme.colorScheme.onBackground)
        Text(
            "清除历史",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier
                .clickable(onClick = onClear)
                .padding(horizontal = 2.dp, vertical = 14.dp),
        )
    }
}

@Composable
internal fun HistoryChip(value: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.offset(y = HISTORY_CHIP_OFFSET_Y).clickable(onClick = onClick),
        cornerRadius = 24.dp,
    ) {
        Text(value, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
internal fun SearchResultCard(
    app: MarketAppInfo,
    action: SearchAction,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(app) }, cornerRadius = 30.dp) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteAppIcon(app.getIconUrl(), app.getDisplayName(), Modifier.size(52.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.getDisplayName(), fontSize = 17.sp, maxLines = 1)
                }
                Text(
                    app.getPublisherName(),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Text(
                    "${app.getVersionName()}  ${formatAppSize(app.getApkSize())}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            when (action) {
                SearchAction.OPEN -> ActionPill("打开", primary = false) { onOpenInstalled(app) }
                SearchAction.INSTALL -> InstallActionPill(app, "安装", onInstall)
                SearchAction.UPDATE -> InstallActionPill(app, "更新", onInstall)
            }
        }
    }
}

internal enum class SearchAction { INSTALL, UPDATE, OPEN }

private fun formatAppSize(bytes: Long): String =
    if (bytes <= 0) "" else "%.1fMB".format(java.util.Locale.CHINA, bytes / (1024f * 1024f))

private val HISTORY_HEADER_TOP_PADDING = 4.5.dp
private val HISTORY_CHIP_OFFSET_Y = (-9.5).dp
