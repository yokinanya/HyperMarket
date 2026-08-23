package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SearchField(
    value: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    SearchInputContainer(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        editing = editing,
        onEditingChange = onEditingChange,
        onValueChange = onValueChange,
        onSearch = onSearch,
        onClear = onClear,
        onCancel = onCancel,
    )
}

@Composable
private fun SearchInputContainer(
    modifier: Modifier,
    value: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(60.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, color = MiuixTheme.colorScheme.onSecondaryContainer),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxSize().onFocusChanged {
                    onEditingChange(it.isFocused)
                },
                decorationBox = { field ->
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            MiuixIcons.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (value.isEmpty() && !editing) {
                                Text(
                                    "搜索应用",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 17.sp,
                                )
                            }
                            field()
                        }
                        if (value.isNotEmpty()) ClearSearchButton(onClear)
                    }
                },
            )
        }
        if (value.isNotEmpty()) {
            TextButton(
                text = "取消",
                onClick = onCancel,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ClearSearchButton(onClear: () -> Unit) {
    IconButton(
        onClick = onClear,
        modifier = Modifier.size(40.dp),
        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
    ) {
        Icon(
            MiuixIcons.Close,
            contentDescription = "清除",
            modifier = Modifier.size(12.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
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
