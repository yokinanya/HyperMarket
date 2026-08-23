package com.hyper.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface UpdatesState {
    data object Loading : UpdatesState
    data class Loaded(
        val updates: List<UpdateInfo>,
        val cachedAt: Long? = null,
        val refreshError: String? = null,
    ) : UpdatesState
    data class Failed(val message: String) : UpdatesState
}

@Composable
fun UpdatesPage(
    settings: AppSettings,
    apiClient: XiaomiApiClient,
    updateStore: UpdateStore,
    packageVisibilityRefresh: Int = 0,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as MarketApplication
    var refreshRequestId by remember { mutableIntStateOf(0) }
    var handledRefreshRequestId by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<UpdatesState>(UpdatesState.Loading) }
    LaunchedEffect(
        refreshRequestId,
        settings.showSystemApps,
        settings.removeSearchAds,
        settings.removeQuickApps,
        settings.removeReservationApps,
        settings.incrementalUpdates,
        packageVisibilityRefresh,
    ) {
        val cached = cachedVisibleUpdates(updateStore, settings)
        val previous = state as? UpdatesState.Loaded
        val fallback = cached?.let { UpdatesState.Loaded(it.updates, it.cachedAt) } ?: previous
        fallback?.let { state = it }
        val manualRefresh = refreshRequestId != handledRefreshRequestId
        if (manualRefresh) handledRefreshRequestId = refreshRequestId
        val autoRefresh = !manualRefresh
        if (autoRefresh && !application.claimUpdateAutoRefresh()) return@LaunchedEffect
        if (fallback == null) state = UpdatesState.Loading
        state = try {
            UpdatesState.Loaded(
                loadVisibleUpdates(context, apiClient, updateStore, settings),
            )
        } catch (exception: CancellationException) {
            if (autoRefresh) application.releaseUpdateAutoRefresh()
            throw exception
        } catch (exception: Exception) {
            val message = exception.message ?: "更新检查失败"
            fallback?.copy(refreshError = message) ?: UpdatesState.Failed(message)
        } finally {
            isRefreshing = false
        }
    }
    when (val current = state) {
        UpdatesState.Loading -> UpdatesLoading()
        is UpdatesState.Failed -> UpdatesError(current.message) { refreshRequestId++ }
        is UpdatesState.Loaded -> PullToRefresh(
            isRefreshing = isRefreshing,
            refreshTexts = EMPTY_REFRESH_TEXTS,
            onRefresh = {
                isRefreshing = true
                refreshRequestId++
            },
        ) {
            UpdatesList(
                current.updates,
                settings.optimizeNames,
                onOpenDetail,
                onInstall,
                onInstallAll,
                refreshError = current.refreshErrorMessage(),
                onRetry = {
                    isRefreshing = true
                    refreshRequestId++
                },
                onIgnore = { update, permanent ->
                    updateStore.ignore(update, permanent)
                    state = current.copy(updates = current.updates - update)
                },
            )
        }
    }
}

@Composable
private fun UpdatesList(
    updates: List<UpdateInfo>,
    optimizeNames: Boolean,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
    refreshError: String?,
    onRetry: () -> Unit,
    onIgnore: (UpdateInfo, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 38.dp, end = 12.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("更新")
            UpdateHeader(updates, onInstallAll)
        }
        if (refreshError != null) {
            item { UpdateCacheError(refreshError, onRetry) }
        }
        if (updates.isEmpty()) {
            item { EmptyUpdates() }
        } else {
            items(updates, key = { it.app.packageName }) { update ->
                UpdateCard(
                    update = update,
                    optimizeNames = optimizeNames,
                    onOpenDetail = onOpenDetail,
                    onInstall = onInstall,
                    onIgnore = onIgnore,
                )
            }
        }
    }
}

@Composable
private fun UpdateCacheError(message: String, onRetry: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                message,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            top.yukonga.miuix.kmp.basic.TextButton(text = "重试", onClick = onRetry)
        }
    }
}

private fun UpdatesState.Loaded.refreshErrorMessage(): String? = when {
    refreshError != null && cachedAt != null ->
        "刷新失败，当前显示缓存：$refreshError"
    refreshError != null -> "刷新失败，保留当前列表：$refreshError"
    else -> null
}

@Composable
private fun UpdatesLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            "正在检查更新…",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun UpdatesError(message: String, onRetry: () -> Unit) {
    PageColumn {
        PageTitle("更新")
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("更新检查失败", fontSize = 20.sp)
                Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                ActionPill("重试", onRetry)
            }
        }
    }
}

internal fun formatTotalSize(updates: List<UpdateInfo>): String =
    formatBytes(updates.sumOf { update ->
        if (update.diffSize > 0 && update.diffSize < update.app.apkSize) {
            update.diffSize
        } else {
            update.app.apkSize
        }
    })

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "大小未知"
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1024) {
        "%.1fGB".format(java.util.Locale.CHINA, megabytes / 1024)
    } else {
        "%.1fMB".format(java.util.Locale.CHINA, megabytes)
    }
}

private val EMPTY_REFRESH_TEXTS = List(4) { "" }
