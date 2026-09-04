package com.hyper.market

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class HyperMarketContentState(
    val backStack: SnapshotStateList<NavKey>,
    val rootPadding: PaddingValues,
    val selectedTab: Int,
    val searchSession: SearchSessionState,
    val searchListState: LazyListState,
    val settingsScrollState: ScrollState,
    val apiClient: XiaomiApiClient,
    val settings: AppSettings,
    val profile: MarketProfileSettings,
    val updateStore: UpdateStore,
    val packageVisibilityRefresh: Int,
    val animationsEnabled: Boolean,
    val onSelectedTab: (Int) -> Unit,
    val onOpenDetail: (MarketAppInfo) -> Unit,
    val onOpenArticle: (String) -> Unit,
    val onInstall: (MarketAppInfo) -> Unit,
    val onInstallAll: (List<MarketAppInfo>) -> Unit,
    val onOpenInstalled: (MarketAppInfo) -> Unit,
    val onOpenSaved: (SavedPackageEntry) -> Unit,
    val onReinstallSaved: (SavedPackageEntry) -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onProfileChange: (MarketProfileSettings) -> Unit,
    val onOpenSettings: (SettingsDestination) -> Unit,
    val onBack: () -> Unit,
)

@Composable
internal fun HyperMarketContent(state: HyperMarketContentState) {
    // miuix-nav 官方标准导航（miuix 指南 miuix-nav 章节）：
    // transition/effects 两种状态都用官方 MiuixDefault + 圆角裁切/压暗效果层（用户最终选择
    // "用回 miuix 的"）；预测性返回开关只控手势是否跟手——关 = NavDisplay 之后的 BackHandler
    // 优先拦截返回，离散出栈播放 miuix 标准弹出过渡，页面不跟手。
    NavDisplay(
        backStack = state.backStack,
        onBack = state.onBack,
        transition = if (state.animationsEnabled) NavTransitions.MiuixDefault else NavTransitions.None,
        effects = if (state.animationsEnabled) {
            NavDisplayEffects(
                cornerClipRadius = rememberNavSystemCornerRadius(),
                cornerClipMode = NavCornerClipMode.All,
                dimAmount = 0.32f,
                backdropColor = MiuixTheme.colorScheme.background,
            )
        } else {
            NavDisplayEffects.None
        },
    ) {
        entry<MarketNav.Tabs> {
            // 主 Tab 页场景：底栏是场景的一部分（参考项目 MiuixMainContent 标准结构），
            // 随转场一起进出场。底栏改为悬浮叠层（HyperOS 形态）：页面内容可滚入底栏后方
            // 供 textureBlur 实时采样；顶栏各 Tab 自管状态栏（沉浸 + 模糊），不垫顶部内边距。
            val layoutDirection = LocalLayoutDirection.current
            val density = LocalDensity.current
            var bottomBarHeight by remember { mutableStateOf(0.dp) }
            val sceneBlur = rememberBarBlur()
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = state.rootPadding.calculateStartPadding(layoutDirection),
                        end = state.rootPadding.calculateEndPadding(layoutDirection),
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .blurSource(sceneBlur),
                ) {
                    MainPage(
                        state.selectedTab,
                        state.searchSession,
                        state.searchListState,
                        state.settingsScrollState,
                        state.apiClient,
                        state.settings,
                        state.updateStore,
                        state.packageVisibilityRefresh,
                        bottomBarHeight = bottomBarHeight,
                        onSelected = state.onSelectedTab,
                        onOpenDetail = state.onOpenDetail,
                        onOpenInstalled = state.onOpenInstalled,
                        onOpenArticle = state.onOpenArticle,
                        onOpenUpdates = { state.onSelectedTab(1) },
                        onInstall = state.onInstall,
                        onInstallAll = state.onInstallAll,
                        onOpenSettings = state.onOpenSettings,
                        onSettingsChange = state.onSettingsChange,
                    )
                }
                // 底栏必须是 blurSource 内容盒的同级兄弟（自采样会 native 闪退，见 BarBlur 注释）。
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } }
                        .barBlurMaterial(sceneBlur, MiuixTheme.colorScheme.surface),
                ) {
                    MarketNavigation(state.selectedTab, sceneBlur) { state.onSelectedTab(it) }
                }
            }
        }
        entry<MarketNav.Detail> { route ->
            DetailPage(
                app = route.app,
                apiClient = state.apiClient,
                settings = state.settings,
                packageVisibilityRefresh = state.packageVisibilityRefresh,
                onInstall = state.onInstall,
                onOpenInstalled = state.onOpenInstalled,
                onOpenDetail = state.onOpenDetail,
                onBack = state.onBack,
            )
        }
        entry<MarketNav.Article> { route ->
            TodayArticlePage(
                resourceId = route.resourceId,
                apiClient = state.apiClient,
                onOpenDetail = state.onOpenDetail,
                onInstall = state.onInstall,
                onBack = state.onBack,
            )
        }
        entry<MarketNav.Settings> { route ->
            SettingsSubpage(
                route.destination,
                state.settings,
                state.profile,
                state.apiClient,
                state.updateStore,
                state.onSettingsChange,
                state.onProfileChange,
                state.onInstall,
                state.onOpenSaved,
                state.onReinstallSaved,
                state.onBack,
            )
        }
    }
    // 预测性返回关闭时（参考项目预测性返回开关的标准做法）：在 NavDisplay 之后注册
    // BackHandler，优先于 NavDisplay 内置手势处理器拦截返回 → 返回变为离散出栈，
    // 播放 miuix 标准弹出过渡（系统风格滑动），页面不再跟手驱动。
    BackHandler(enabled = !state.settings.predictiveBack && state.backStack.size > 1) {
        state.onBack()
    }
}
