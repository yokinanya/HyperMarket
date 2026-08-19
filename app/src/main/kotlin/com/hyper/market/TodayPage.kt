package com.hyper.market

import android.content.Intent
import android.net.Uri

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.TodayFeaturedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card

@Composable
fun TodayPage(
    apiClient: XiaomiApiClient,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenArticle: (String) -> Unit,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 38.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        PageTitle("今日")
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
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 20.dp),
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

@Composable
private fun TodayFeatureCard(item: TodayFeaturedItem, onClick: () -> Unit) {
    val canOpen = item.resourceId.isNotBlank() || item.app != null || item.clickUrl.isNotBlank()
    val modifier = Modifier
        .fillMaxWidth()
        .height(330.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(enabled = canOpen, onClick = onClick)
    Card(modifier = modifier, cornerRadius = 16.dp) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (item.coverImageUrl.isNotBlank()) {
                RemoteImage(
                    item.coverImageUrl,
                    item.title,
                    Modifier.fillMaxSize(),
                    alignment = BiasAlignment(0f, -0.25f),
                )
            } else {
                Box(Modifier.fillMaxSize().background(todayGradient(item)))
            }
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(topOverlayColors(item))),
            )
            TodayFeatureFooter(item, Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun TodayFeatureFooter(item: TodayFeaturedItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (item.isGoldenAward) "金米奖 · ${item.title}" else "进行中的活动",
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 16.sp,
            maxLines = 1,
        )
        if (item.isGoldenAward && item.summary.isNotBlank()) {
            Text(item.summary, color = Color.White, fontSize = 18.sp, maxLines = 1)
        } else if (!item.isGoldenAward && item.title.isNotBlank()) {
            Text(item.title, color = Color.White, fontSize = 18.sp, maxLines = 1)
        }
        if (item.apps.size > 1) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.apps.take(MAX_TODAY_APPS).forEach { app ->
                    RemoteAppIcon(app.iconUrl, app.displayName, Modifier.size(32.dp))
                }
            }
        } else if (item.app != null) {
            TodayAppRow(item.app)
        }
    }
}

@Composable
private fun TodayAppRow(app: MarketAppInfo) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteAppIcon(app.iconUrl, app.displayName, Modifier.size(48.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = app.displayName,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
            )
            Text(
                text = app.publisherName,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TodayLoadingState() {
    repeat(2) { index ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (index == 0) 0.dp else 20.dp)
                .height(330.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(Color(0xFFE9EDF2)),
        )
    }
}

@Composable
private fun TodayErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("今日内容加载失败", color = Color(0xFFD14343), fontSize = 20.sp)
        Text(message, color = Color(0xFF777777), fontSize = 15.sp)
        ActionPill("重试", onRetry)
    }
}

@Composable
private fun TodayEmptyState(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("暂无今日内容", color = Color(0xFF777777), fontSize = 20.sp)
        ActionPill("重新加载", onRetry)
    }
}

private fun todayGradient(item: TodayFeaturedItem): Brush = Brush.verticalGradient(
    if (item.isGoldenAward) {
        listOf(Color(0xFF7ACEEA), Color(0xFF187DB7))
    } else {
        listOf(Color(0xFF7C9FEF), Color(0xFF315BBD))
    },
)

private fun topOverlayColors(item: TodayFeaturedItem): List<Color> = listOf(
    Color.Transparent,
    Color.Transparent,
    if (item.isGoldenAward) Color(0xD91478B0) else Color(0xD92B55B5),
    if (item.isGoldenAward) Color(0xF01478B0) else Color(0xF02B55B5),
)

private fun todayItemKey(item: TodayFeaturedItem, index: Int): String =
    item.resourceId.ifBlank {
        item.clickUrl.ifBlank {
            item.app?.packageName?.ifBlank { item.title }
                ?: item.apps.firstOrNull()?.packageName?.ifBlank { item.title }
                ?: "position:$index"
        }
    }

private const val MAX_TODAY_APPS = 4
