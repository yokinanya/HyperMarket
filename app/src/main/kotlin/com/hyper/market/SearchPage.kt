package com.hyper.market

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search

@Composable
fun SearchPage(
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }
    var keyword by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<MarketAppInfo>()) }
    var history by remember { mutableStateOf(store.readSearchHistory()) }
    var searchedKeyword by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    fun search(queryValue: String = keyword, targetPage: Int = 1) {
        val query = queryValue.trim()
        if (query.isEmpty() || loading || (targetPage > 1 && !hasMore)) return
        keyboard?.hide()
        editing = false
        loading = true
        error = null
        scope.launch {
            try {
                val pageResults = withContext(Dispatchers.IO) { apiClient.search(query, targetPage) }
                results = if (targetPage == 1) {
                    pageResults
                } else {
                    (results + pageResults).distinctBy { it.getPackageName() }
                }
                searchedKeyword = query
                page = targetPage
                hasMore = pageResults.isNotEmpty()
                if (targetPage == 1) {
                    history = (listOf(query) + history.filterNot { it == query }).take(MAX_HISTORY)
                    store.writeSearchHistory(history)
                }
            } catch (exception: Exception) {
                error = exception.message ?: "搜索失败"
            } finally {
                loading = false
            }
        }
    }

    val visibleResults = results.filterNot { app ->
        (settings.removeSearchAds && app.isAd()) ||
            (settings.removeQuickApps && app.isQuickApp()) ||
            (settings.removeReservationApps && app.isReservationApp())
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("搜索")
            SearchField(
                value = keyword,
                editing = editing,
                onEditingChange = { editing = it },
                onValueChange = { keyword = it },
                onSearch = { search() },
                onCancel = {
                    keyword = ""
                    results = emptyList()
                    searchedKeyword = ""
                    page = 1
                    hasMore = false
                    editing = false
                    keyboard?.hide()
                },
            )
        }
        if (editing && keyword.isEmpty()) {
            item {
                HistoryHeader(onClear = {
                    history = emptyList()
                    store.writeSearchHistory(emptyList())
                })
            }
            items(history, key = { it }) { item ->
                HistoryChip(item) {
                    keyword = item
                    search(item)
                }
            }
        }
        error?.let { message ->
            item { Text(message, color = Color(0xFFD14343), modifier = Modifier.padding(8.dp)) }
        }
        if (loading) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(28.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
        }
        if (!loading && keyword.isNotBlank() && visibleResults.isEmpty() && error == null) {
            item { Text("未找到结果", color = Color(0xFF777777), fontSize = 18.sp, modifier = Modifier.padding(12.dp)) }
        }
        items(visibleResults, key = { it.getPackageName() }) { app ->
            SearchResultCard(app, context, onOpenDetail, onInstall)
        }
        if (searchedKeyword.isNotBlank() && hasMore) {
            item {
                ActionPill(if (loading) "加载中…" else "加载更多") {
                    search(searchedKeyword, page + 1)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 18.sp, color = Color(0xFF222222)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(60.dp))
                .background(Color(0xFFE7E7E7))
                .onFocusChanged { onEditingChange(it.isFocused) }
                .padding(horizontal = 24.dp, vertical = 7.dp),
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MiuixIcons.Search, contentDescription = "搜索", modifier = Modifier.size(24.dp), tint = Color(0xFF999999))
                    Spacer(Modifier.size(14.dp))
                    if (value.isEmpty()) Text("搜索应用", color = Color(0xFF999999), fontSize = 18.sp)
                    field()
                }
            },
        )
        if (editing) {
            Text("取消", color = AccentBlue, fontSize = 18.sp, modifier = Modifier.padding(start = 18.dp).clickable(onClick = onCancel))
        }
    }
}

@Composable
private fun HistoryHeader(onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 28.dp, start = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("搜索历史", fontSize = 27.sp, color = Color.Black)
        Text("清除历史", fontSize = 18.sp, color = AccentBlue, modifier = Modifier.padding(end = 12.dp).clickable(onClick = onClear))
    }
}

@Composable
private fun HistoryChip(value: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), cornerRadius = 24.dp) {
        Text(value, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp))
    }
}

@Composable
private fun SearchResultCard(
    app: MarketAppInfo,
    context: Context,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
) {
    val installed = remember(app.getPackageName()) { isInstalled(context, app.getPackageName()) }
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
            ActionPill(if (installed) "更新" else "安装") { onInstall(app) }
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

private const val MAX_HISTORY = 8
