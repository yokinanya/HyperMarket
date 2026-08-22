package com.hyper.market

import android.content.Intent
import android.net.Uri

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    var refreshKey by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var updatesState by remember {
        mutableStateOf<TodayUpdatesState>(TodayUpdatesState.Loading)
    }
    val scope = rememberCoroutineScope()

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
            visible = true
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
    ) {
        updatesState = TodayUpdatesState.Loading
        updatesState = try {
            TodayUpdatesState.Loaded(
                loadVisibleUpdates(context, apiClient, updateStore, settings),
            )
        } catch (exception: Exception) {
            android.util.Log.w("TodayPage", "update card unavailable", exception)
            TodayUpdatesState.Failed(exception.message ?: "更新检查失败")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PageTitle("今日")
        TodayUpdateCard(
            state = updatesState,
            onOpenUpdates = onOpenUpdates,
        )
        val hasUpdateCard = (updatesState as? TodayUpdatesState.Loaded)?.updates?.isNotEmpty() == true
        when {
            loading -> TodayLoadingState()
            error != null -> TodayErrorState(error.orEmpty()) { refreshKey++ }
            items.isEmpty() -> TodayEmptyState { refreshKey++ }
            else -> {
                items.forEachIndexed { index, item ->
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(360, index * 70)) +
                            slideInVertically(tween(420, index * 70)) { it / 8 },
                        modifier = Modifier.padding(
                            top = if (index == 0 && !hasUpdateCard) 0.dp else 20.dp,
                        ),
                    ) {
                        TodayFeatureCard(item) {
                            when {
                                item.resourceId.isNotBlank() -> onOpenArticle(item.resourceId)
                                item.app != null -> onOpenDetail(item.app)
                                item.clickUrl.isNotBlank() -> context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.clickUrl)),
                                )
                            }
                        }
                    }
                }
                if (hasMore) {
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
                loadMoreError?.let { message ->
                    Text(message, color = Color(0xFFD14343), fontSize = 14.sp)
                }
            }
        }
    }
}
