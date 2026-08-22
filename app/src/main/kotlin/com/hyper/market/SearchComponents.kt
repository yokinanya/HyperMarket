package com.hyper.market

import android.content.Context
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Search

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
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SearchInputContainer(
            modifier = Modifier.weight(1f),
            value = value,
            editing = editing,
            onEditingChange = onEditingChange,
            onValueChange = onValueChange,
            onSearch = onSearch,
            onClear = onClear,
        )
        if (editing) {
            Text(
                "取消",
                color = AccentBlue,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 18.dp).clickable(onClick = onCancel),
            )
        }
    }
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
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(Color(0xFFE7E7E7)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 18.sp, color = Color(0xFF222222)),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlue),
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
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF999999),
                    )
                    Spacer(Modifier.size(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && !editing) {
                            Text("搜索应用", color = Color(0xFF999999), fontSize = 17.sp)
                        }
                        field()
                    }
                    if (value.isNotEmpty()) ClearSearchButton(onClear)
                }
            },
        )
    }
}

@Composable
private fun ClearSearchButton(onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9))
            .clickable(onClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MiuixIcons.Close,
            contentDescription = "清除",
            modifier = Modifier.size(12.dp),
            tint = Color(0xFF999999),
        )
    }
}

@Composable
internal fun HistoryHeader(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = HISTORY_HEADER_TOP_PADDING, start = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text("搜索历史", fontSize = 18.sp, color = Color.Black)
        Text(
            "清除历史",
            fontSize = 14.sp,
            color = AccentBlue,
            modifier = Modifier.padding(end = 4.dp).clickable(onClick = onClear),
        )
    }
}

@Composable
internal fun HistoryChip(value: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.offset(y = HISTORY_CHIP_OFFSET_Y).clickable(onClick = onClick),
        cornerRadius = 24.dp,
    ) {
        Text(value, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
internal fun SearchResultCard(
    app: MarketAppInfo,
    context: Context,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
) {
    val installed = isInstalled(context, app.getPackageName())
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(app) }, cornerRadius = 30.dp) {
        Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            RemoteAppIcon(app.getIconUrl(), app.getDisplayName(), Modifier.size(58.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.getDisplayName(), fontSize = 20.sp, maxLines = 1)
                    if (app.isAd()) {
                        Text("推广", color = Color(0xFF777777), fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
                Text(app.getPublisherName(), fontSize = 16.sp, color = Color(0xFF777777), maxLines = 1)
                Text("${app.getVersionName()}  ${formatAppSize(app.getApkSize())}", fontSize = 16.sp, color = Color(0xFF777777))
            }
            InstallActionPill(app, if (installed) "更新" else "安装", onInstall)
        }
    }
}

private fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: Exception) {
    false
}

private fun formatAppSize(bytes: Long): String =
    if (bytes <= 0) "" else "%.1fMB".format(java.util.Locale.CHINA, bytes / (1024f * 1024f))

private val HISTORY_HEADER_TOP_PADDING = 4.5.dp
private val HISTORY_CHIP_OFFSET_Y = (-9.5).dp
