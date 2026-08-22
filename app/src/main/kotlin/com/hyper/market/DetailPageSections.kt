package com.hyper.market

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.DetailVideo
import com.hyper.market.model.MarketAppInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun PreviewSection(
    urls: List<String>,
    videos: List<DetailVideo>,
    onSaveImage: (String) -> Unit,
) {
    SectionLabel("预览")
    if (urls.isEmpty() && videos.isEmpty()) {
        Text("暂无预览", color = Color(0xFF888888), modifier = Modifier.padding(start = 12.dp))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos) { video -> DetailVideoPreview(video) }
        items(urls) { url ->
            RemoteImage(
                url,
                "应用预览",
                Modifier.width(220.dp).height(390.dp).clip(RoundedCornerShape(24.dp)),
                onLongClick = { onSaveImage(url) },
            )
        }
    }
}

@Composable
internal fun IntroductionSection(app: MarketAppInfo) {
    var expanded by remember(app) { mutableStateOf(false) }
    val content = app.getIntroduction().ifEmpty { "暂无应用介绍" }
    SectionLabel("应用介绍")
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(tween(260)), cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(if (expanded) content else content.take(DETAIL_INTRO_LIMIT), fontSize = 18.sp, color = Color(0xFF222222))
            if (content.length > DETAIL_INTRO_LIMIT) {
                Text(
                    if (expanded) "收起" else "更多",
                    color = AccentBlue,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
internal fun DetailInfoSection(app: MarketAppInfo, privacyUrl: String) {
    val context = LocalContext.current
    SectionLabel("应用信息")
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DetailInfoRow("包名", app.getPackageName())
            DetailInfoRow("版本", app.getVersionName().ifEmpty { "—" })
            DetailInfoRow("更新时间", detailFormatDate(app.getUpdateTime()))
            DetailInfoRow("备案号", app.getRegistrationNumber().ifEmpty { "—" })
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    val url = privacyUrl.ifEmpty { "https://privacy.mi.com/all/zh_CN/" }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("隐私政策", fontSize = 16.sp, color = Color(0xFF666666))
                Text("点击打开", fontSize = 16.sp, color = AccentBlue)
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp, color = Color(0xFF666666))
        Text(value, fontSize = 16.sp, color = Color(0xFF222222), maxLines = 1)
    }
}

@Composable
internal fun DetailAppIcon(app: MarketAppInfo) {
    if (app.getIconUrl().isNotBlank()) {
        RemoteAppIcon(app.getIconUrl(), app.getDisplayName(), Modifier.size(96.dp))
    } else {
        InstalledAppIcon(app.getPackageName(), app.getDisplayName(), Modifier.size(96.dp))
    }
}

private fun detailFormatDate(timestamp: Long): String =
    if (timestamp <= 0) "—" else SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(timestamp))

private const val DETAIL_INTRO_LIMIT = 180
