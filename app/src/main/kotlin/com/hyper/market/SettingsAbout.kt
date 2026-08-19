package com.hyper.market

import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

private val AboutLinks = listOf(
    "Compose Multiplatform" to "github.com/JetBrains/compose-multiplatform",
    "miuix" to "github.com/compose-miuix-ui/miuix",
    "coil" to "github.com/coil-kt/coil",
    "ktor" to "github.com/ktorio/ktor",
    "Koin" to "github.com/InsertKoinIO/koin",
    "Compose Media Player" to "github.com/kdroidFilter/ComposeMediaPlayer",
    "Shizuku" to "github.com/RikkaApps/Shizuku",
    "HiddenApiBypass" to "github.com/LSPosed/AndroidHiddenApiBypass",
    "HyperNotification" to "github.com/xzakota/HyperNotification",
)

@Composable
internal fun SettingsAboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    val showCompactTitle = scrollState.firstVisibleItemIndex > 0
    Box(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { AboutHero() }
            item { AboutLinksSection(context) }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 3.dp).size(48.dp),
        ) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
        }
        Text(
            "关于",
            color = Color(0xFF111111),
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(if (showCompactTitle) 1f else 0f)
                .padding(top = 11.5.dp),
        )
    }
}

@Composable
private fun AboutHero() {
    Box(modifier = Modifier.fillMaxWidth().height(486.5.dp)) {
        AndroidView(
            factory = {
                ImageView(it).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageDrawable(it.packageManager.getApplicationIcon(it.packageName))
                    contentDescription = "应用商店"
                }
            },
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 141.dp).size(94.dp),
        )
        Text(
            "应用商店",
            color = Color(0xFF8D2874),
            fontSize = 32.sp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 245.dp),
        )
        Text(
            "v2.1.6_fix (132)",
            color = Color(0xFF5F5960),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 297.dp),
        )
        Text(
            "开放开源代码许可",
            color = MutedBlue,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = 460.dp)
                .fillMaxWidth()
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun AboutLinksSection(context: android.content.Context) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.62f), RoundedCornerShape(32.dp)),
    ) {
        AboutLinks.forEach { (name, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(73.dp)
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$url")))
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontSize = 17.sp, color = Color(0xFF111111))
                    Text(url, fontSize = 14.sp, color = Color(0xFF666666))
                }
                AboutChevron()
            }
        }
    }
}

@Composable
private fun AboutChevron() {
    Canvas(Modifier.size(24.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.68f, size.height * 0.16f)
            lineTo(size.width * 0.96f, size.height * 0.5f)
            lineTo(size.width * 0.68f, size.height * 0.84f)
        }
        drawPath(
            path,
            color = Color(0xFF999999),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
