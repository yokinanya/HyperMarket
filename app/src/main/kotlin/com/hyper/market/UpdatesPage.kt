package com.hyper.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card

private sealed interface UpdatesState {
    data object Loading : UpdatesState
    data class Loaded(val updates: List<UpdateInfo>) : UpdatesState
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
    var refreshKey by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<UpdatesState>(UpdatesState.Loading) }
    LaunchedEffect(
        refreshKey,
        settings.showSystemApps,
        settings.removeSearchAds,
        settings.removeQuickApps,
        settings.removeReservationApps,
        settings.incrementalUpdates,
        packageVisibilityRefresh,
    ) {
        state = UpdatesState.Loading
        state = try {
            UpdatesState.Loaded(
                loadVisibleUpdates(context, apiClient, updateStore, settings),
            )
        } catch (exception: Exception) {
            UpdatesState.Failed(exception.message ?: "更新检查失败")
        }
    }
    when (val current = state) {
        UpdatesState.Loading -> UpdatesLoading()
        is UpdatesState.Failed -> UpdatesError(current.message) { refreshKey++ }
        is UpdatesState.Loaded -> UpdatesList(
            current.updates,
            settings.optimizeNames,
            onOpenDetail,
            onInstall,
            onInstallAll,
            onIgnore = { update, permanent ->
                updateStore.ignore(update, permanent)
                refreshKey++
            },
            onRefresh = { refreshKey++ },
        )
    }
}

@Composable
private fun UpdatesList(
    updates: List<UpdateInfo>,
    optimizeNames: Boolean,
    onOpenDetail: (MarketAppInfo) -> Unit,
    onInstall: (MarketAppInfo) -> Unit,
    onInstallAll: (List<MarketAppInfo>) -> Unit,
    onIgnore: (UpdateInfo, Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberLazyListState()
    val refreshThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 38.dp)
            .pointerInput(onRefresh, refreshThreshold) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                        var previousY = down.position.y
                        var distance = 0f
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            val delta = change.position.y - previousY
                            if (delta > 0f && listState.firstVisibleItemIndex == 0 &&
                                listState.firstVisibleItemScrollOffset == 0
                            ) {
                                distance += delta
                            }
                            previousY = change.position.y
                            if (!change.pressed) break
                        }
                        if (distance >= refreshThreshold) onRefresh()
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle("更新")
            UpdateHeader(updates, onInstallAll)
        }
        if (updates.isEmpty()) {
            item { EmptyUpdates() }
        } else {
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    UpdatesPanel(updates, optimizeNames, onOpenDetail, onInstall, onIgnore)
                }
            }
        }
    }
}

@Composable
private fun UpdatesLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AccentBlue)
        Text("正在检查更新…", color = Color(0xFF777777), modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun UpdatesError(message: String, onRetry: () -> Unit) {
    PageColumn {
        PageTitle("更新")
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("更新检查失败", fontSize = 20.sp)
                Text(message, color = Color(0xFF777777))
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
