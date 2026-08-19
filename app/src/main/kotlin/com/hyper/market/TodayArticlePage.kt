package com.hyper.market

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ArticleHeader(article?.title.orEmpty(), onBack)
        when {
            error != null -> ArticleErrorState(error.orEmpty()) { reloadKey++ }
            article == null -> Text("加载中…", color = Color(0xFF777777), modifier = Modifier.padding(12.dp))
            else -> article?.let { loaded ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 10 },
                ) {
                    ArticleContent(loaded, onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun ArticleHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(38.dp))
        }
        Text(title.ifBlank { "今日专题" }, fontSize = 26.sp, maxLines = 2)
    }
}

@Composable
private fun ArticleContent(article: TodayArticle, onOpenDetail: (MarketAppInfo) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (article.headerImageUrl.isNotBlank()) {
            RemoteImage(article.headerImageUrl, article.title, Modifier.fillMaxWidth().height(220.dp))
        }
        Text(article.title, fontSize = 26.sp, color = Color(0xFF171717))
        articleSegments(article).forEach { segment ->
            segment.text?.let { text ->
                Text(text, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF333333))
            }
            segment.imageUrl?.let { url ->
                RemoteImage(url, article.title, Modifier.fillMaxWidth().height(190.dp))
            }
        }
        article.apps.forEach { app ->
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
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
    }
}

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
        segments += ArticleSegment(imageUrl = secureImageUrl(match.groupValues[1]))
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
    if (text.isNotBlank()) {
        segments += ArticleSegment(text = text)
    }
}

private fun secureImageUrl(url: String): String = url

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
