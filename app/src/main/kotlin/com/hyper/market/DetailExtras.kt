package com.hyper.market

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.DetailPromotion
import com.hyper.market.model.MarketAppDetails
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OptionalDetailSections(
    details: MarketAppDetails,
    settings: AppSettings,
    onOpenDetail: (MarketAppInfo) -> Unit,
) {
    val visible = (settings.showPromotions && details.promotions.isNotEmpty()) ||
        (settings.showComments && details.comments.isNotEmpty()) ||
        (settings.showSameDeveloper && details.sameDeveloperApps.isNotEmpty())
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(220)) + slideInVertically(folmeSpring(0.3f)) { it / 8 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (settings.showPromotions && details.promotions.isNotEmpty()) {
                PromotionSection(details.promotions)
            }
            if (settings.showComments && details.comments.isNotEmpty()) {
                CommentSection(details)
            }
            if (settings.showSameDeveloper && details.sameDeveloperApps.isNotEmpty()) {
                RelatedAppsSection(details.sameDeveloperApps, onOpenDetail)
            }
        }
    }
}

@Composable
private fun PromotionSection(promotions: List<DetailPromotion>) {
    val context = LocalContext.current
    SectionLabel("优惠活动")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(promotions) { promotion ->
            Card(
                modifier = Modifier.width(260.dp).clickable {
                    if (promotion.jumpUrl.isNotBlank()) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, promotion.jumpUrl.toUri()))
                    }
                },
                cornerRadius = 26.dp,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (promotion.previewImageUrl.isNotBlank()) {
                        RemoteImage(
                            promotion.previewImageUrl,
                            "优惠活动",
                            Modifier.fillMaxWidth().height(150.dp),
                        )
                    }
                    Text(promotion.title.ifBlank { promotion.activityTag }, fontSize = 18.sp)
                    Text(
                        promotion.description,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 15.sp,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentSection(details: MarketAppDetails) {
    SectionLabel("评论与评分")
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            details.comments.take(MAX_DETAIL_COMMENTS).forEach { comment ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${comment.userName.ifBlank { "用户" }}  ${formatCommentScore(comment.score)}",
                        fontSize = 15.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(comment.content, fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun RelatedAppsSection(
    apps: List<MarketAppInfo>,
    onOpenDetail: (MarketAppInfo) -> Unit,
) {
    SectionLabel("同开发者应用")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(apps) { app ->
            Card(
                modifier = Modifier.width(210.dp).clickable { onOpenDetail(app) },
                cornerRadius = 24.dp,
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    DetailAppIcon(app)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(app.displayName, fontSize = 17.sp, maxLines = 1)
                        Text(
                            app.publisherName,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_DETAIL_COMMENTS = 5
