package com.hyper.market

import top.yukonga.miuix.kmp.theme.MiuixTheme

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SearchPage(
    session: SearchSessionState,
    listState: LazyListState,
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    packageVisibilityRefresh: Int,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
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
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var requestGeneration by remember { mutableIntStateOf(0) }
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
        requestGeneration += 1
        val generation = requestGeneration
        searchJob = scope.launch {
            try {
                val pageResults = withContext(Dispatchers.IO) { apiClient.search(query, targetPage) }
                if (generation != requestGeneration) return@launch
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
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.message ?: "搜索失败"
            } finally {
                if (generation == requestGeneration) loading = false
            }
        }
    }

    val visibleResults = results.filterNot { app ->
        (settings.removeSearchAds && app.isAd()) ||
            (settings.removeQuickApps && app.isQuickApp()) ||
            (settings.removeReservationApps && app.isReservationApp())
    }
    var installedVersions by remember {
        androidx.compose.runtime.mutableStateOf(emptyMap<String, Long>())
    }
    LaunchedEffect(visibleResults, packageVisibilityRefresh) {
        installedVersions = withContext(Dispatchers.IO) {
            visibleResults.mapNotNull { app ->
                val packageName = app.getPackageName()
                try {
                    val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                    packageName to PackageInfoCompat.getLongVersionCode(packageInfo)
                } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                    null
                }
            }.toMap()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 38.dp, end = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("搜索", bottomPadding = SEARCH_TITLE_BOTTOM_PADDING)
            SearchField(
                value = keyword,
                editing = editing,
                onEditingChange = {
                    val focused = it
                    editing = focused
                    if (focused) showHistory = keyword.isEmpty()
                },
                onValueChange = {
                    keyword = it
                    if (it.isBlank()) {
                        requestGeneration += 1
                        searchJob?.cancel()
                        searchJob = null
                        session.clearResults()
                        showHistory = history.isNotEmpty() && editing
                    }
                },
                onSearch = { search(keepInputOpen = editing) },
                onClear = {
                    keyword = ""
                    requestGeneration += 1
                    searchJob?.cancel()
                    searchJob = null
                    session.clearResults()
                    showHistory = history.isNotEmpty() && editing
                },
                onCancel = {
                    keyboard?.hide()
                    requestGeneration += 1
                    searchJob?.cancel()
                    searchJob = null
                    session.clearResults()
                    keyword = ""
                    editing = false
                    showHistory = history.isNotEmpty()
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
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history, key = { it }) { item ->
                        HistoryChip(item) {
                            keyword = item
                            search(item, keepInputOpen = true)
                        }
                    }
                }
            }
        }
        error?.let { message ->
            item { Text(message, color = MiuixTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        }
        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!loading && searchedKeyword.isNotBlank() && visibleResults.isEmpty() && error == null) {
            item {
                Text(
                    "未找到结果",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        items(visibleResults, key = { it.getPackageName() }) { app ->
            val installedVersion = installedVersions[app.getPackageName()]
            val action = when {
                installedVersion == null -> SearchAction.INSTALL
                app.getVersionCode() > installedVersion -> SearchAction.UPDATE
                else -> SearchAction.OPEN
            }
            SearchResultCard(
                app = app,
                action = action,
                onOpenDetail = onOpenDetail,
                onInstall = onInstall,
                onOpenInstalled = onOpenInstalled,
            )
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
