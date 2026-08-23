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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.TodayFeaturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TodayPage(
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    updateStore: UpdateStore,
    packageVisibilityRefresh: Int,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<TodayFeaturedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
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
        try {
            val firstPage = withContext(Dispatchers.IO) { apiClient.loadToday(0) }
            items = firstPage.items
            hasMore = firstPage.hasMore()
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
            .padding(start = 12.dp, top = 38.dp, end = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { PageTitle("今日", bottomPadding = 0.dp) }
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
