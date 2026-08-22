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
    updates.filter { update ->
        (settings.showSystemApps || !update.installedPackage.isSystemApp) &&
            (!settings.removeSearchAds || !update.app.isAd()) &&
            (!settings.removeQuickApps || !update.app.isQuickApp()) &&
            (!settings.removeReservationApps || !update.app.isReservationApp()) &&
            !updateStore.isIgnored(update)
    }
}
