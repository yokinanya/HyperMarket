package com.hyper.market

import top.yukonga.miuix.kmp.theme.MiuixTheme

import android.content.Intent
import androidx.core.net.toUri

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.TodayFeaturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 今日页内存缓存：Tab 切回时避免重新请求网络与重建列表尖峰。
 * 页面离开 Pager 视口后组合会被销毁，remember 状态丢失；缓存保证切回即时渲染。
 */
internal object TodayFeedCache {
    @Volatile var items: List<TodayFeaturedItem> = emptyList()
    @Volatile var hasMore: Boolean = false
    @Volatile var page: Int = 0
    @Volatile var loadedAt: Long = 0L

    fun isUsable(now: Long = System.currentTimeMillis()): Boolean =
        items.isNotEmpty() && now - loadedAt < TODAY_CACHE_MAX_AGE_MS

    fun store(items: List<TodayFeaturedItem>, hasMore: Boolean, page: Int) {
        this.items = items
        this.hasMore = hasMore
        this.page = page
        loadedAt = System.currentTimeMillis()
    }

    private const val TODAY_CACHE_MAX_AGE_MS = 10 * 60 * 1000L
}

@Composable
fun TodayPage(
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    updateStore: UpdateStore,
    packageVisibilityRefresh: Int,
    topPadding: Dp,
    bottomBarHeight: Dp,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(TodayFeedCache.items) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(TodayFeedCache.page) }
    var hasMore by remember { mutableStateOf(TodayFeedCache.hasMore) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var updatesState by remember {
        mutableStateOf<TodayUpdatesState>(TodayUpdatesState.Loading)
    }
    val scope = rememberCoroutineScope()
    val cacheRevision by updateStore.cacheRevision.collectAsState()

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        page = 0
        hasMore = false
        loadMoreError = null
        if (refreshKey == 0 && TodayFeedCache.isUsable()) {
            items = TodayFeedCache.items
            hasMore = TodayFeedCache.hasMore
            page = TodayFeedCache.page
            loading = false
            return@LaunchedEffect
        }
        try {
            val firstPage = withContext(Dispatchers.IO) { apiClient.loadToday(0) }
            items = firstPage.items
            hasMore = firstPage.hasMore()
            TodayFeedCache.store(firstPage.items, firstPage.hasMore(), 0)
        } catch (exception: Exception) {
            error = exception.message ?: "今日内容加载失败"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(
        settings.showSystemApps,
        settings.removeSearchAds,
        settings.removeQuickApps,
        settings.removeReservationApps,
        settings.incrementalUpdates,
        packageVisibilityRefresh,
        cacheRevision,
    ) {
        updatesState = try {
            val cached = cachedVisibleUpdates(updateStore, settings)
            TodayUpdatesState.Loaded(cached?.updates.orEmpty())
        } catch (exception: Exception) {
            android.util.Log.w("TodayPage", "update card unavailable", exception)
            TodayUpdatesState.Failed(exception.message ?: "更新检查失败")
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollEndHaptic()
            .overScrollVertical()
            .padding(start = 12.dp, end = 12.dp),
        contentPadding = PaddingValues(top = topPadding, bottom = 12.dp + bottomBarHeight),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            TodayUpdateCard(
                state = updatesState,
                onOpenUpdates = onOpenUpdates,
            )
        }
        when {
            loading -> item { TodayLoadingState() }
            error != null -> item { TodayErrorState(error.orEmpty()) { refreshKey++ } }
            items.isEmpty() -> item { TodayEmptyState { refreshKey++ } }
            else -> {
                itemsIndexed(
                    items = items,
                    key = { index, item -> todayItemKey(item, index) },
                ) { _, item ->
                    TodayFeatureCard(item) {
                        when {
                            item.resourceId.isNotBlank() -> onOpenArticle(item.resourceId)
                            item.app != null -> onOpenDetail(item.app)
                            item.clickUrl.isNotBlank() -> context.startActivity(
                                Intent(Intent.ACTION_VIEW, item.clickUrl.toUri()),
                            )
                        }
                    }
                }
                if (hasMore) {
                    item {
                        ActionPill(if (loadingMore) "加载中…" else "加载更多") {
                            if (loadingMore) return@ActionPill
                            loadingMore = true
                            loadMoreError = null
                            scope.launch {
                                try {
                                    val nextPage = withContext(Dispatchers.IO) {
                                        apiClient.loadToday(page + 1)
                                    }
                                    val combined = items + nextPage.items
                                    items = combined.mapIndexed { index, item -> index to item }
                                        .distinctBy { (index, item) -> todayItemKey(item, index) }
                                        .map { it.second }
                                    page += 1
                                    hasMore = nextPage.hasMore()
                                    TodayFeedCache.store(items, hasMore, page)
                                } catch (exception: Exception) {
                                    loadMoreError = exception.message ?: "更多今日内容加载失败"
                                } finally {
                                    loadingMore = false
                                }
                            }
                        }
                    }
                }
                loadMoreError?.let { message ->
                    item { Text(message, color = MiuixTheme.colorScheme.error, fontSize = 14.sp) }
                }
            }
        }
    }
}
