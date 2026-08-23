package com.hyper.market

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo

internal data class HyperMarketContentState(
    val route: String,
    val displayedDetailApp: MarketAppInfo?,
    val displayedArticleId: String?,
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
    AnimatedContent(
        targetState = state.route,
        transitionSpec = { routeTransition(state.animationsEnabled) },
        label = "route-transition",
    ) { currentRoute ->
        when {
            currentRoute == "detail" -> state.displayedDetailApp?.let {
                DetailPage(
                    app = it,
                    apiClient = state.apiClient,
                    settings = state.settings,
                    packageVisibilityRefresh = state.packageVisibilityRefresh,
                    onInstall = state.onInstall,
                    onOpenInstalled = state.onOpenInstalled,
                    onOpenDetail = state.onOpenDetail,
                    onBack = state.onBack,
                )
            }
            currentRoute == "today-article" -> state.displayedArticleId?.let {
                TodayArticlePage(
                    resourceId = it,
                    apiClient = state.apiClient,
                    onOpenDetail = state.onOpenDetail,
                    onInstall = state.onInstall,
                    onBack = state.onBack,
                )
            }
            currentRoute.startsWith("settings-") -> settingsDestinationForRoute(currentRoute)?.let {
                SettingsSubpage(
                    it,
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
            else -> MainPage(
                state.selectedTab,
                state.searchSession,
                state.searchListState,
                state.settingsScrollState,
                state.apiClient,
                state.settings,
                state.updateStore,
                state.packageVisibilityRefresh,
                animationsEnabled = state.animationsEnabled,
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
    }
}
