package com.hyper.market

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInOut
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

internal fun androidx.compose.animation.AnimatedContentTransitionScope<String>.routeTransition(
    animationsEnabled: Boolean,
) =
    if (!animationsEnabled) {
        androidx.compose.animation.EnterTransition.None togetherWith
            androidx.compose.animation.ExitTransition.None
    } else {
            val forward = routeDepth(targetState) >= routeDepth(initialState)
            val direction = if (forward) 1 else -1
            val enteringDuration = if (isAboutRoute(targetState)) 320 else 300
            val leavingDuration = if (isAboutRoute(initialState)) 260 else 240
            (slideInHorizontally(tween(enteringDuration)) { it * direction / 4 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(leavingDuration)) { -it * direction / 6 } + fadeOut(tween(180)))
    }

private fun isAboutRoute(route: String): Boolean = route == "settings-about"

private fun routeDepth(route: String): Int = when {
    route == "detail" || route == "today-article" || route.startsWith("settings-") -> 1
    else -> 0
}
@Composable
internal fun MarketNavigation(selected: Int, onSelected: (Int) -> Unit) {
    val items = listOf(
        "今日" to MarketIcons.Today,
        "更新" to MiuixIcons.Light.Update,
        "搜索" to MiuixIcons.Light.Search,
        "设置" to MiuixIcons.Light.Settings,
    )
    NavigationBar(mode = NavigationBarDisplayMode.IconAndText) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelected(index) },
                icon = item.second,
                label = item.first,
            )
        }
    }
}

@Composable
internal fun MainPage(
    selected: Int,
    searchSession: SearchSessionState,
    searchListState: LazyListState,
    settingsScrollState: ScrollState,
    apiClient: XiaomiApiClient,
    settings: AppSettings,
    updateStore: UpdateStore,
    packageVisibilityRefresh: Int,
    animationsEnabled: Boolean,
    onSelected: (Int) -> Unit,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenInstalled: (MarketAppInfo) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
    onOpenSettings: (SettingsDestination) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selected) { MAIN_TAB_COUNT }
    LaunchedEffect(selected, animationsEnabled) {
        if (pagerState.currentPage == selected) return@LaunchedEffect
        if (!animationsEnabled) {
            pagerState.scrollToPage(selected)
            return@LaunchedEffect
        }
        val distance = abs(selected - pagerState.currentPage).coerceAtLeast(TAB_MIN_ANIMATION_DISTANCE)
        val duration = TAB_BASE_DURATION_MS + TAB_PER_PAGE_DURATION_MS * distance
        pagerState.animateScrollToPage(
            page = selected,
            animationSpec = tween(durationMillis = duration, easing = EaseInOut),
        )
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect(onSelected)
    }
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        key = { it },
    ) { page ->
        MainTabContent(
            page = page,
            content = MainTabContentOptions(
                searchSession = searchSession,
                searchListState = searchListState,
                settingsScrollState = settingsScrollState,
                apiClient = apiClient,
                settings = settings,
                updateStore = updateStore,
                packageVisibilityRefresh = packageVisibilityRefresh,
                onOpenDetail = onOpenDetail,
                onOpenInstalled = onOpenInstalled,
                onOpenArticle = onOpenArticle,
                onOpenUpdates = onOpenUpdates,
                onInstall = onInstall,
                onInstallAll = onInstallAll,
                onOpenSettings = onOpenSettings,
                onSettingsChange = onSettingsChange,
            ),
        )
    }
}

private data class MainTabContentOptions(
    val searchSession: SearchSessionState,
    val searchListState: LazyListState,
    val settingsScrollState: ScrollState,
    val apiClient: XiaomiApiClient,
    val settings: AppSettings,
    val updateStore: UpdateStore,
    val packageVisibilityRefresh: Int,
    val onOpenDetail: (MarketAppInfo) -> Unit,
    val onOpenInstalled: (MarketAppInfo) -> Unit,
    val onOpenArticle: (String) -> Unit,
    val onOpenUpdates: () -> Unit,
    val onInstall: (MarketAppInfo) -> Unit,
    val onInstallAll: (List<MarketAppInfo>) -> Unit,
    val onOpenSettings: (SettingsDestination) -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
)

@Composable
private fun MainTabContent(page: Int, content: MainTabContentOptions) {
    when (page) {
        0 -> TodayPage(
            content.settings,
            content.apiClient,
            content.updateStore,
            content.packageVisibilityRefresh,
            onOpenDetail = content.onOpenDetail,
            onOpenArticle = content.onOpenArticle,
            onOpenUpdates = content.onOpenUpdates,
        )
        1 -> UpdatesPage(
            content.settings,
            content.apiClient,
            content.updateStore,
            content.packageVisibilityRefresh,
            content.onOpenDetail,
            content.onInstall,
            content.onInstallAll,
        )
        2 -> SearchPage(
            content.searchSession,
            content.searchListState,
            content.settings,
            content.apiClient,
            content.packageVisibilityRefresh,
            content.onOpenDetail,
            content.onInstall,
            content.onOpenInstalled,
        )
        else -> SettingsPage(
            content.settings,
            content.settingsScrollState,
            content.onSettingsChange,
            content.onOpenSettings,
        )
    }
}

private const val MAIN_TAB_COUNT = 4
private const val TAB_MIN_ANIMATION_DISTANCE = 2
private const val TAB_BASE_DURATION_MS = 80
private const val TAB_PER_PAGE_DURATION_MS = 70
