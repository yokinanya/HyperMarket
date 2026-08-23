package com.hyper.market

import android.content.Intent
import androidx.core.net.toUri
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val showCompactTitle by remember {
        derivedStateOf { scrollState.firstVisibleItemIndex > 0 }
    }
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
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 3.dp)
                .size(48.dp)
                .zIndex(1f)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
        }
        Text(
            "关于",
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(if (showCompactTitle) 1f else 0f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 11.5.dp),
        )
    }
}

@Composable
private fun AboutHero() {
    val context = LocalContext.current
    val appIcon = androidx.compose.runtime.remember {
        context.packageManager.getApplicationIcon(context.packageName)
    }
    val packageInfo = androidx.compose.runtime.remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp).padding(top = 112.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            factory = {
                ImageView(it).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageDrawable(appIcon)
                    contentDescription = "应用商店"
                }
            },
            modifier = Modifier.size(94.dp),
        )
        Text(
            "应用商店",
            color = MiuixTheme.colorScheme.primary,
            fontSize = 32.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "v${packageInfo.versionName.orEmpty()} ($versionCode)",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(
            "开放开源代码许可",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
        )
    }
}

@Composable
private fun AboutLinksSection(context: android.content.Context) {
    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        AboutLinks.forEach { (name, url) ->
            ArrowPreference(
                title = name,
                summary = url,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://$url".toUri()))
                },
            )
        }
    }
}
