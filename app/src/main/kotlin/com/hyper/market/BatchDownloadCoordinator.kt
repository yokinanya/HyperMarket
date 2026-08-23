package com.hyper.market

import android.content.Context
import com.hyper.market.installer.DownloadCancelledException
import com.hyper.market.installer.DownloadControl
import com.hyper.market.installer.DownloadNotification
import com.hyper.market.installer.DownloadTaskRegistry
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class BatchDownloadCoordinator(
    private val coordinator: DownloadCoordinator,
) {
    suspend fun downloadAndInstallAll(
        context: Context,
        apps: List<MarketAppInfo>,
        settings: AppSettings,
        onStatus: suspend (String) -> Unit,
    ) {
        val control = DownloadTaskRegistry.begin()
        apps.forEach { InstallUiStateStore.begin(it.packageName, it.displayName) }
        DownloadNotification.begin(context, "批量更新")
        try {
            val prepared = prepareAll(context, apps, settings, control, onStatus)
            installAll(context, prepared, control, onStatus)
            DownloadNotification.complete(context, "批量更新")
        } catch (_: DownloadCancelledException) {
            apps.forEach { InstallUiStateStore.dismiss(it.packageName) }
            DownloadNotification.cancel(context)
        } catch (exception: Exception) {
            control.cancel()
            DownloadNotification.failure(context, exception.message ?: "未知错误")
            throw exception
        } finally {
            DownloadTaskRegistry.finish(control)
        }
    }

    private suspend fun prepareAll(
        context: Context,
        apps: List<MarketAppInfo>,
        settings: AppSettings,
        control: DownloadControl,
        onStatus: suspend (String) -> Unit,
    ): List<PreparedInstall> = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_APPS)
        val downloads = apps.map { app ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    coordinator.prepareInstall(context, app, settings, control) { status ->
                        onStatus("${app.displayName}：$status")
                    }
                }
            }
        }
        try {
            downloads.awaitAll()
        } catch (exception: Exception) {
            control.cancel()
            downloads.forEach { it.cancel() }
            throw exception
        }
    }

    private suspend fun installAll(
        context: Context,
        prepared: List<PreparedInstall>,
        control: DownloadControl,
        onStatus: suspend (String) -> Unit,
    ) {
        prepared.forEach { item ->
            control.awaitIfPaused()
            val synchronous = coordinator.installPrepared(context, item, onStatus)
            if (synchronous) {
                InstallUiStateStore.complete(item.app.packageName)
                DownloadNotification.installationComplete(context, item.app.displayName)
            } else {
                DownloadNotification.update(context, "等待 ${item.app.displayName} 安装结果…")
            }
        }
    }

    private companion object {
        const val MAX_CONCURRENT_APPS = 2
    }
}
