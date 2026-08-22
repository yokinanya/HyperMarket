package com.hyper.market

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update

internal fun androidx.compose.animation.AnimatedContentTransitionScope<String>.routeTransition() =
    when {
        initialState.startsWith("tab-") && targetState.startsWith("tab-") ->
            EnterTransition.None togetherWith ExitTransition.None
        else -> {
            val forward = routeDepth(targetState) >= routeDepth(initialState)
            val direction = if (forward) 1 else -1
            val enteringDuration = if (isAboutRoute(targetState)) 320 else 300
            val leavingDuration = if (isAboutRoute(initialState)) 260 else 240
            (slideInHorizontally(tween(enteringDuration)) { it * direction / 4 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(leavingDuration)) { -it * direction / 6 } + fadeOut(tween(180)))
        }
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
    onOpenDetail: (MarketAppInfo) -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
    onOpenSettings: (SettingsDestination) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val direction = if (targetState >= initialState) 1 else -1
            (slideInHorizontally(tween(260)) { it * direction / 3 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(220)) { -it * direction / 4 } + fadeOut(tween(140)))
        },
        label = "tab-transition",
    ) { tab ->
        when (tab) {
            0 -> TodayPage(
                settings,
                apiClient,
                updateStore,
                packageVisibilityRefresh,
                onOpenDetail,
                onOpenArticle,
                onOpenUpdates,
            )
            1 -> UpdatesPage(
                settings,
                apiClient,
                updateStore,
                packageVisibilityRefresh,
                onOpenDetail,
                onInstall,
                onInstallAll,
            )
            2 -> SearchPage(searchSession, searchListState, settings, apiClient, onOpenDetail, onInstall)
            else -> SettingsPage(settings, settingsScrollState, onSettingsChange, onOpenSettings)
        }
    }
}

@Composable
internal fun OperationBanner(status: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .background(Color(0xFF202020), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .clickable(onClick = onDismiss),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(status, color = Color.White, modifier = Modifier.padding(16.dp))
    }
}
