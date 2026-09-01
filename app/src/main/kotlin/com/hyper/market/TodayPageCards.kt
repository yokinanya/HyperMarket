package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.TodayFeaturedItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TodayFeatureCard(item: TodayFeaturedItem, onClick: () -> Unit) {
    val canOpen = item.resourceId.isNotBlank() || item.app != null || item.clickUrl.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth().height(330.dp),
        onClick = if (canOpen) onClick else null,
        showIndication = true,
    ) {
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
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
        )
        if (item.isGoldenAward && item.summary.isNotBlank()) {
            Text(item.summary, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, maxLines = 1)
        } else if (!item.isGoldenAward && item.title.isNotBlank()) {
            Text(item.title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, maxLines = 1)
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
                fontSize = 17.sp,
                lineHeight = 20.sp,
                maxLines = 1,
            )
            Text(
                text = app.publisherName,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TodayLoadingState() {
    repeat(2) { index ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (index == 0) 0.dp else 20.dp)
                .height(330.dp),
        ) {
            Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.secondaryContainer))
        }
    }
}

@Composable
internal fun TodayErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("今日内容加载失败", color = MiuixTheme.colorScheme.error, fontSize = 20.sp)
        Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 15.sp)
        ActionPill("重试", onRetry)
    }
}

@Composable
internal fun TodayEmptyState(onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("暂无今日内容", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 20.sp)
        ActionPill("重新加载", onRetry)
    }
}

private fun todayGradient(item: TodayFeaturedItem): Brush = Brush.verticalGradient(
    if (item.isGoldenAward) {
        listOf(Color(0xFFE4B86C), Color(0xFF9B5E28))
    } else {
        listOf(Color(0xFF7C9FEF), Color(0xFF315BBD))
    },
)

private fun topOverlayColors(item: TodayFeaturedItem): List<Color> = listOf(
    Color.Transparent,
    Color.Transparent,
    if (item.isGoldenAward) Color(0xD99B5E28) else Color(0xD92B55B5),
    if (item.isGoldenAward) Color(0xF09B5E28) else Color(0xF02B55B5),
)

internal fun todayItemKey(item: TodayFeaturedItem, index: Int): String =
    item.resourceId.ifBlank {
        item.clickUrl.ifBlank {
            item.app?.packageName?.ifBlank { item.title }
                ?: item.apps.firstOrNull()?.packageName?.ifBlank { item.title }
                ?: "position:$index"
        }
    }

private const val MAX_TODAY_APPS = 4
