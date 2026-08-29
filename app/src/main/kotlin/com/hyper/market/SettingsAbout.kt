package com.hyper.market

// 关于页（布局标准 1:1 对齐 NexioSchedule AboutScreen，代码自研）：
// - Hero（图标 88dp / 应用名 35sp Bold / 版本号 14sp）固定在背景层、不随列表滚动，
//   随 scrollProgress 分级淡出并微缩（版本 0.05→0.20、名称 0.20→0.35、图标 0.35→0.50，
//   scale = 1 - progress×0.05）；列表首项 logoSpacer(340dp) 与 Hero 联动，小标题在
//   progress>0.5 淡入，底衬毛玻璃。
// - 底层动态渐变（AboutGradientBackground）挂 blurSource 作为毛玻璃采样层，卡片
//   blurMaterial(blurRadius 60f, 圆角 20dp) 采样它；卡片与采样层互为不同层，无自采样。
//   API <33 无 RuntimeShader：卡片降级不透明底色。
// - 卡片横向 16dp；分组：主卡（ArrowPreference：项目主页 / 反馈与建议）→
//   特别致谢卡（默认展开，项目名 primary 可点击）→ 版权行。

import android.content.Intent
import androidx.core.net.toUri
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val ABOUT_REPO_URL = "https://github.com/yokinanya/HyperMarket"
private const val ABOUT_ISSUES_URL = "https://github.com/yokinanya/HyperMarket/issues"

private data class AboutAppInfo(val name: String, val versionName: String)

private data class AboutCredit(val name: String, val author: String, val url: String)

private val AboutCredits = listOf(
    AboutCredit("Compose Multiplatform", "JetBrains", "https://github.com/JetBrains/compose-multiplatform"),
    AboutCredit("miuix", "Yukonga", "https://github.com/compose-miuix-ui/miuix"),
    AboutCredit("coil", "coil-kt", "https://github.com/coil-kt/coil"),
    AboutCredit("Ktor", "JetBrains", "https://github.com/ktorio/ktor"),
    AboutCredit("Koin", "InsertKoinIO", "https://github.com/InsertKoinIO/koin"),
    AboutCredit("Compose Media Player", "kdroidFilter", "https://github.com/kdroidFilter/ComposeMediaPlayer"),
    AboutCredit("Shizuku", "RikkaApps", "https://github.com/RikkaApps/Shizuku"),
    AboutCredit("HiddenApiBypass", "LSPosed", "https://github.com/LSPosed/AndroidHiddenApiBypass"),
    AboutCredit("HyperNotification", "xzakota", "https://github.com/xzakota/HyperNotification"),
)

@Composable
internal fun SettingsAboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val blur = rememberBarBlur()
    // 染色/卡片混合配方 1:1 对齐 NexioSchedule AboutScreen（logoBlend/cardBlend）。
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }
    val cardBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
                BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
                BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
            )
        }
    }
    val appInfo = remember {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val label = packageInfo.applicationInfo?.labelRes?.takeIf { it != 0 }
            ?.let { context.getString(it) }
            ?: context.applicationInfo?.nonLocalizedLabel?.toString()
            ?: "应用商店"
        AboutAppInfo(label, packageInfo.versionName.orEmpty())
    }
    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName)
    }
    val listState = rememberLazyListState()
    // 滚动进度 0..1：列表滚过 logoSpacer 的比例（NexioSchedule 标准）。
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val spacer = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == "logoSpacer" }
                if (spacer != null && spacer.size > 0) {
                    (listState.firstVisibleItemScrollOffset.toFloat() / spacer.size).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (scrollProgress > 0.5f) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "aboutTopBarAlpha",
    )
    // 顶栏毛玻璃混合色：页面底色是 background（非 surface），混合层也用 background
    // 才能与下方列表底色一致（rememberBarBlur 默认按 surface 配色）。
    val barBlurColors = if (blur.enabled) {
        BlurDefaults.blurColors(
            blendColors = listOf(
                BlendColorEntry(MiuixTheme.colorScheme.background.copy(alpha = 0.7f), BlurBlendMode.SrcOver),
            ),
            brightness = 0f,
            contrast = 1f,
            saturation = 1.2f,
        )
    } else {
        null
    }
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 不透明底色（NexioSchedule 标准）：渐变半透明区域不得透出下层页面。
            .background(MiuixTheme.colorScheme.background),
    ) {
        // 毛玻璃采样层：仅动态渐变背景（卡片/顶栏与其不同层，无自采样递归风险）。
        // 随上滑淡化成纯色（miuix AboutPage 标准 alpha = 1f - scrollProgress，
        // alpha 位于 blurSource 之后故进入 backdrop 捕获，卡片模糊同步淡化）。
        Box(
            modifier = Modifier
                .matchParentSize()
                .blurSource(blur)
                .graphicsLayer { alpha = 1f - scrollProgress },
        ) {
            AboutGradientBackground(Modifier.fillMaxSize())
        }
        // Hero 背景层：不随列表滚动，随 scrollProgress 分级淡出 + 微缩 5%。
        // 绝对位置按 NexioSchedule 标准（状态栏+152dp），顶栏自身用 miuix 64dp 标准。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBar + 152.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AndroidView(
                factory = { factoryContext ->
                    ImageView(factoryContext).apply {
                        scaleType = ImageView.ScaleType.FIT_XY
                        setImageDrawable(appIcon)
                        contentDescription = appInfo.name
                    }
                },
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - iconProgress
                        scaleX = 1f - iconProgress * 0.05f
                        scaleY = 1f - iconProgress * 0.05f
                    },
            )
            Text(
                text = appInfo.name,
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 35.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 5.dp)
                    .graphicsLayer {
                        val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - nameProgress
                        scaleX = 1f - nameProgress * 0.05f
                        scaleY = 1f - nameProgress * 0.05f
                    }
                    // 染色半透字：文本作 DstIn 遮罩，textureBlur 采样渐变背景上色
                    // （NexioSchedule logoBlend 标准，blurRadius 150f）。
                    .then(
                        if (blur.enabled) {
                            Modifier.textureBlur(
                                backdrop = blur.backdrop!!,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = 150f,
                                colors = BlurDefaults.blurColors(blendColors = logoBlend),
                                contentBlendMode = BlendMode.DstIn,
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = "v${appInfo.versionName}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val verProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                        alpha = 1f - verProgress
                        scaleX = 1f - verProgress * 0.05f
                        scaleY = 1f - verProgress * 0.05f
                    },
            )
        }
        // 顶栏：小标题随 scrollProgress>0.5 淡入，底衬毛玻璃（采样渐变背景）。
        // 高度对齐其他子页面的 miuix TopAppBar 标准（状态栏+64dp）；zIndex 高于列表，
        // 混合色用页面 background（与下方列表底色一致，非默认 surface）。
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .fillMaxWidth()
                .height(statusBar + 64.dp)
                .graphicsLayer { alpha = topBarAlpha }
                .barBlurMaterial(blur, MiuixTheme.colorScheme.background, barBlurColors),
        ) {
            Text(
                text = "关于",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 11.5.dp),
            )
        }
        // 返回按钮（常驻左上，层级高于顶栏）。
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 3.dp)
                .size(48.dp)
                .zIndex(2f)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                // 卡片绝对位置按 NexioSchedule 标准（状态栏+72dp 起，含 340dp spacer + 16dp 卡距）。
                top = if (statusBar > 0.dp) statusBar + 72.dp else 100.dp,
                bottom = navBar + 16.dp,
            ),
        ) {
            item(key = "logoSpacer") {
                Spacer(modifier = Modifier.fillMaxWidth().height(340.dp))
            }
            item(key = "main") {
                Spacer(modifier = Modifier.height(16.dp))
                BlurCard(blur, cardBlend) {
                    ArrowPreference(
                        title = "项目主页",
                        summary = "github.com/yokinanya/HyperMarket",
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, ABOUT_REPO_URL.toUri())) },
                    )
                    ArrowPreference(
                        title = "反馈与建议",
                        summary = "通过 GitHub Issues 反馈",
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, ABOUT_ISSUES_URL.toUri())) },
                    )
                }
            }
            item(key = "thanks") {
                Spacer(modifier = Modifier.height(12.dp))
                BlurCard(blur, cardBlend) {
                    var creditsExpanded by remember { mutableStateOf(true) }
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (creditsExpanded) 90f else -90f,
                        animationSpec = tween(durationMillis = 200),
                        label = "aboutCreditsChevron",
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { creditsExpanded = !creditsExpanded }
                            .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 17.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "特别致谢",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Icon(
                                imageVector = MiuixIcons.ChevronForward,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (creditsExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))
                            AboutCredits.forEach { credit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = credit.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, credit.url.toUri()),
                                            )
                                        },
                                    )
                                    Text(
                                        text = credit.author,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            item(key = "copyright") {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(Color.Transparent, Color.Transparent),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "© 2026 ${appInfo.name} · 作者:",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                            Text(
                                text = "yokinanya",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, ABOUT_REPO_URL.toUri()),
                                        )
                                    }
                                    .padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 关于页毛玻璃卡片：横向 16dp、圆角 20dp，blur 可用时 textureBlur 采样渐变背景（cardBlend 配方）。 */
@Composable
private fun BlurCard(
    blur: BarBlur,
    cardBlend: List<BlendColorEntry>,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .blurMaterial(
                blur = blur,
                shape = RoundedCornerShape(20.dp),
                fallback = MiuixTheme.colorScheme.background,
                blurRadius = 60f,
                colorsOverride = if (blur.enabled) {
                    BlurDefaults.blurColors(blendColors = cardBlend)
                } else {
                    null
                },
            ),
        cornerRadius = 20.dp,
        colors = CardDefaults.defaultColors(
            if (blur.enabled) Color.Transparent else MiuixTheme.colorScheme.background,
            Color.Transparent,
        ),
        content = content,
    )
}
