package com.hyper.market

import android.content.Context
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun loadVisibleUpdates(
    context: Context,
    apiClient: XiaomiApiClient,
    updateStore: UpdateStore,
    settings: AppSettings,
): List<UpdateInfo> = withContext(Dispatchers.IO) {
    val installedPackages = PackageInventory().scan(context)
    val updates = apiClient.loadUpdates(installedPackages)
    updateStore.cacheUpdates(updates)
    filterVisibleUpdates(updates, updateStore, settings)
}

internal suspend fun cachedVisibleUpdates(
    updateStore: UpdateStore,
    settings: AppSettings,
): CachedUpdates? = withContext(Dispatchers.IO) {
    updateStore.cachedUpdates()?.let { cached ->
        cached.copy(updates = filterVisibleUpdates(cached.updates, updateStore, settings))
    }
}

private fun filterVisibleUpdates(
    updates: List<UpdateInfo>,
    updateStore: UpdateStore,
    settings: AppSettings,
): List<UpdateInfo> {
    val ignored = updateStore.ignoredUpdates()
    return updates.filter { update ->
        isVisibleUpdate(update, settings) && ignored.none { it.matches(update) }
    }
}

private fun isVisibleUpdate(update: UpdateInfo, settings: AppSettings): Boolean =
    (settings.showSystemApps || !update.installedPackage.isSystemApp) &&
        (!settings.removeSearchAds || !update.app.isAd) &&
        (!settings.removeQuickApps || !update.app.isQuickApp) &&
        (!settings.removeReservationApps || !update.app.isReservationApp)

private fun IgnoredUpdate.matches(update: UpdateInfo): Boolean =
    packageName == update.app.packageName && (permanent || versionCode == update.app.versionCode)
