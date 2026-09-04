package com.hyper.market

import top.yukonga.miuix.kmp.theme.MiuixTheme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
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
    topPadding: Dp,
    bottomBarHeight: Dp,
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
    val density = LocalDensity.current
    // 固定搜索栏总高（InputField 最小高 45dp + 底部间距 12dp；onSizeChanged 校正）。
    var searchHeaderHeight by remember { mutableStateOf(57.dp) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .padding(start = 12.dp, end = 12.dp),
            contentPadding = PaddingValues(top = topPadding + searchHeaderHeight, bottom = 12.dp + bottomBarHeight),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
        // 搜索栏固定在顶部（与顶栏标题一样不随内容滚动）：列表内容从其下方滚过，
        // 不透明 surface 打底遮蔽；topPadding 使其紧贴可折叠顶栏底部，随顶栏收合上移。
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = topPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { searchHeaderHeight = with(density) { it.height.toDp() } }
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                SearchField(
                    value = keyword,
                    editing = editing,
                    onEditingChange = {
                        val focused = it
                        editing = focused
                        if (focused) {
                            showHistory = keyword.isEmpty()
                        } else {
                            // 收起（返回手势）= 旧“取消”按钮的完整收尾逻辑；
                            // InputField 收起时会自行清空输入并放弃焦点。
                            keyboard?.hide()
                            requestGeneration += 1
                            searchJob?.cancel()
                            searchJob = null
                            session.clearResults()
                            showHistory = history.isNotEmpty()
                        }
                    },
                    onValueChange = {
                        keyword = it
                        if (it.isBlank()) {
                            // 输入框内清除图标走这里：取消任务并清空结果。
                            requestGeneration += 1
                            searchJob?.cancel()
                            searchJob = null
                            session.clearResults()
                            showHistory = history.isNotEmpty()
                        }
                    },
                    onSearch = { search(keepInputOpen = editing) },
                )
            }
        }
    }
}

private const val MAX_HISTORY = 8
