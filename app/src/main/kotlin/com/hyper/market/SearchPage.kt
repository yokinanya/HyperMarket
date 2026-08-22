package com.hyper.market

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SearchPage(
    session: SearchSessionState,
    listState: LazyListState,
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
) {
    val context = LocalContext.current
    val store = remember(context) { SettingsStore(context) }
    var keyword by session.keyword
    var results by session.results
    var history by session.history
    var searchedKeyword by session.searchedKeyword
    var page by session.page
    var hasMore by session.hasMore
    var error by session.error
    var loading by session.loading
    var editing by session.editing
    var showHistory by session.showHistory
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(editing) {
        if (editing) keyboard?.show()
    }

    fun search(
        queryValue: String = keyword,
        targetPage: Int = 0,
        keepInputOpen: Boolean = editing,
    ) {
        val query = queryValue.trim()
        if (query.isEmpty() || loading || (targetPage > 0 && !hasMore)) return
        keyboard?.hide()
        editing = keepInputOpen
        showHistory = false
        loading = true
        error = null
        if (targetPage == 0) results = emptyList()
        scope.launch {
            try {
                val pageResults = withContext(Dispatchers.IO) { apiClient.search(query, targetPage) }
                val pageApps = pageResults.apps
                results = if (targetPage == 0) {
                    pageApps
                } else {
                    (results + pageApps).distinctBy { it.getPackageName() }
                }
                searchedKeyword = query
                page = targetPage
                hasMore = pageResults.hasMore()
                if (targetPage == 0) {
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
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("搜索", bottomPadding = SEARCH_TITLE_BOTTOM_PADDING)
            SearchField(
                value = keyword,
                editing = editing,
                onEditingChange = {
                    editing = it
                    if (it) showHistory = true
                },
                onValueChange = { keyword = it },
                onSearch = { search(keepInputOpen = editing) },
                onClear = { keyword = "" },
                onCancel = {
                    keyword = ""
                    results = emptyList()
                    searchedKeyword = ""
                    page = 0
                    hasMore = false
                    error = null
                    editing = false
                    showHistory = false
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                },
            )
        }
        if (showHistory && keyword.isEmpty() && history.isNotEmpty()) {
            item {
                HistoryHeader(onClear = {
                    history = emptyList()
                    store.writeSearchHistory(emptyList())
                    showHistory = false
                })
            }
            items(history, key = { it }) { item ->
                HistoryChip(item) {
                    keyword = item
                    search(item, keepInputOpen = true)
                }
            }
        }
        error?.let { message ->
            item { Text(message, color = Color(0xFFD14343), modifier = Modifier.padding(8.dp)) }
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
        }
        if (!loading && searchedKeyword.isNotBlank() && visibleResults.isEmpty() && error == null) {
            item {
                Text(
                    "未找到结果",
                    color = Color(0xFF777777),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
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

private const val MAX_HISTORY = 8
private val SEARCH_TITLE_BOTTOM_PADDING = 15.dp
