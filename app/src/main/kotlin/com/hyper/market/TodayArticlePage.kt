package com.hyper.market

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.TodayArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun TodayArticlePage(
    resourceId: String,
    apiClient: XiaomiApiClient,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onBack: () -> Unit,
) {
    var article by remember(resourceId) { mutableStateOf<TodayArticle?>(null) }
    var error by remember(resourceId) { mutableStateOf<String?>(null) }
    var reloadKey by remember(resourceId) { mutableStateOf(0) }
    LaunchedEffect(resourceId, reloadKey) {
        error = null
        try {
            article = withContext(Dispatchers.IO) { apiClient.loadTodayArticle(resourceId) }
        } catch (exception: Exception) {
            error = exception.message ?: "专题内容加载失败"
        }
    }
    when {
        article != null -> ArticleLoadedPage(article!!, onOpenDetail, onBack)
        else -> ArticleLoadingPage(error, onBack) { reloadKey++ }
    }
}

@Composable
private fun ArticleLoadedPage(
    article: TodayArticle,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        ArticleHero(article, onBack)
        ArticleBody(article, onOpenDetail)
    }
}

@Composable
private fun ArticleHero(article: TodayArticle, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(articleHeroHeight(article)),
    ) {
        if (article.headerImageUrl.isNotBlank()) {
            RemoteImage(article.headerImageUrl, article.title, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(ARTICLE_FALLBACK_GRADIENT))
        }
        Box(modifier = Modifier.fillMaxSize().background(ARTICLE_HERO_GRADIENT))
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 48.dp)
                .size(56.dp),
        ) {
            Icon(
                MiuixIcons.Back,
                contentDescription = "返回",
                modifier = Modifier.size(38.dp),
                tint = Color.Black,
            )
        }
        ArticleHeroFooter(article, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ArticleHeroFooter(article: TodayArticle, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(article.title, color = Color.White, fontSize = 18.sp, maxLines = 2)
        if (article.apps.size == 1) {
            val app = article.apps.first()
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteAppIcon(app.iconUrl, app.displayName, Modifier.size(48.dp))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(app.displayName, color = Color.White, fontSize = 16.sp, maxLines = 1)
                    Text(
                        app.publisherName,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }
        } else if (article.apps.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                article.apps.take(MAX_ARTICLE_APPS).forEach { app ->
                    RemoteAppIcon(app.iconUrl, app.displayName, Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ArticleBody(article: TodayArticle, onOpenDetail: (MarketAppInfo) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        articleSegments(article).forEach { segment ->
            segment.text?.let { ArticleText(it) }
            segment.imageUrl?.let { ArticleImage(it, article.title) }
        }
        article.apps.forEach { app -> ArticleAppCard(app, onOpenDetail) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ArticleText(text: String) {
    Text(text, fontSize = 18.sp, lineHeight = 29.sp, color = Color(0xFF222222))
}

@Composable
private fun ArticleImage(url: String, title: String) {
    RemoteImage(
        url = url,
        contentDescription = title,
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(24.dp)),
    )
}

@Composable
private fun ArticleAppCard(app: MarketAppInfo, onOpenDetail: (MarketAppInfo) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteAppIcon(app.iconUrl, app.displayName, Modifier.size(62.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(app.displayName, fontSize = 19.sp)
                Text(app.publisherName, fontSize = 14.sp, color = Color(0xFF888888))
            }
            ActionPill("查看") { onOpenDetail(app) }
        }
    }
}

@Composable
private fun ArticleLoadingPage(
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ArticleHeader("今日专题", onBack)
        if (error == null) {
            Text("加载中…", color = Color(0xFF777777), modifier = Modifier.padding(12.dp))
        } else {
            ArticleErrorState(error, onRetry)
        }
    }
}

@Composable
private fun ArticleHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(38.dp))
        }
        Text(title, fontSize = 26.sp, maxLines = 2)
    }
}

private val ARTICLE_HERO_GRADIENT = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.56f to Color.Transparent,
    1.0f to Color(0xE62F5DBA),
)
private val ARTICLE_FALLBACK_GRADIENT = Brush.verticalGradient(
    listOf(Color(0xFF9DB7EE), Color(0xFF2F5DBA)),
)

private val IMAGE_TAG_PATTERN = Regex("(?is)<img\\b[^>]*>")
private val BREAK_TAG_PATTERN = Regex("(?i)<br\\s*/?>")
private val BLOCK_END_TAG_PATTERN = Regex("(?i)</(p|div|h[1-6]|li)>")
private val EXCESS_NEWLINE_PATTERN = Regex("\\n{3,}")
private val BODY_IMAGE_PATTERN = Regex(
    "(?is)<img\\b[^>]*(?:src|data-src)=[\\\"']([^\\\"']+)[\\\"'][^>]*>",
)

private data class ArticleSegment(val text: String? = null, val imageUrl: String? = null)

private fun articleSegments(article: TodayArticle): List<ArticleSegment> {
    val segments = mutableListOf<ArticleSegment>()
    var cursor = 0
    BODY_IMAGE_PATTERN.findAll(article.bodyHtml).forEach { match ->
        appendTextSegment(article.bodyHtml.substring(cursor, match.range.first), segments)
        segments += ArticleSegment(imageUrl = match.groupValues[1])
        cursor = match.range.last + 1
    }
    appendTextSegment(article.bodyHtml.substring(cursor), segments)
    if (segments.none { it.imageUrl != null }) {
        article.imageUrls.forEach { url -> segments += ArticleSegment(imageUrl = url) }
    }
    return segments
}

private fun appendTextSegment(html: String, segments: MutableList<ArticleSegment>) {
    val text = cleanArticleBody(html)
    if (text.isNotBlank()) segments += ArticleSegment(text = text)
}

private fun cleanArticleBody(html: String): String {
    val normalizedHtml = html
        .replace(IMAGE_TAG_PATTERN, "")
        .replace(BREAK_TAG_PATTERN, "\n")
        .replace(BLOCK_END_TAG_PATTERN, "\n")
    return Html.fromHtml(normalizedHtml, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace("\uFFFC", "")
        .replace(EXCESS_NEWLINE_PATTERN, "\n\n")
        .trim()
}

@Composable
private fun ArticleErrorState(message: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("专题内容加载失败", color = Color(0xFFD14343), fontSize = 20.sp)
        Text(message, color = Color(0xFF777777), fontSize = 15.sp)
        ActionPill("重试", onRetry)
    }
}

private const val MAX_ARTICLE_APPS = 3

private fun articleHeroHeight(article: TodayArticle) =
    if (article.apps.size == 1) 447.dp else 509.dp
