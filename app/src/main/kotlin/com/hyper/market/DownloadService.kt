package com.hyper.market

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.installer.ApkInstaller
import com.hyper.market.installer.DownloadTaskRegistry
import com.hyper.market.installer.DownloadTaskStore
import com.hyper.market.installer.DownloadNotification
import com.hyper.market.installer.FileDownloader
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var taskRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == NOTIFICATION_VISIBILITY_ACTION) {
            updateForeground(intent.getBooleanExtra(EXTRA_VISIBLE, true))
            if (!taskRunning) stopSelf(startId)
            return START_NOT_STICKY
        }
        val command = if (intent?.action == CONTROL_ACTION) {
            DownloadTaskStore.consumeCommand(this).ifBlank { error("下载控制命令为空") }
        } else {
            ""
        }
        if (command.isNotBlank()) {
            DownloadTaskRegistry.requestForNextTask(command)
            if (taskRunning) return START_REDELIVER_INTENT
        }
        if (taskRunning) return START_REDELIVER_INTENT
        val task = if (command.isNotBlank()) DownloadTaskStore.restore(this)
        else intent ?: error("下载服务缺少任务参数")
        DownloadTaskStore.save(this, task)
        startTask(task, startId)
        return START_REDELIVER_INTENT
    }

    private fun startTask(task: Intent, startId: Int) {
        val apps = decodeApps(task.getStringExtra(EXTRA_APPS).orEmpty())
        require(apps.isNotEmpty()) { "下载服务任务为空" }
        val settings = decodeSettings(task)
        val profileSource = task.getStringExtra("profileSource") ?: "device"
        val profileOverrides = decodeProfileOverrides(task)
        val apiClient = XiaomiApiClient(this).also { it.setProfile(profileSource, profileOverrides) }
        val title = if (apps.size == 1) apps.first().getDisplayName() else "批量更新"
        taskRunning = true
        startForeground(
            DownloadNotification.notificationId(),
            DownloadNotification.foreground(this, "准备下载 $title"),
        )
        scope.launch {
            try {
                DownloadCoordinator(apiClient, FileDownloader(apiClient.downloadHeaders()), ApkInstaller())
                    .downloadAndInstallAll(this@DownloadService, apps, settings) { }
            } catch (exception: Exception) {
                DownloadNotification.failure(this@DownloadService, exception.message ?: "下载失败")
            } finally {
                taskRunning = false
                DownloadTaskRegistry.clearPendingAction()
                DownloadTaskStore.clear(this@DownloadService)
                stopForeground(STOP_FOREGROUND_REMOVE)
                DownloadNotification.cancelOngoing(this@DownloadService)
                stopSelf()
            }
        }
    }

    private fun updateForeground(visible: Boolean) {
        if (!taskRunning) {
            DownloadNotification.cancelOngoing(this)
            return
        }
        if (!visible) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            DownloadNotification.hideOngoing(this)
            return
        }
        startForeground(
            DownloadNotification.notificationId(),
            DownloadNotification.foreground(this, "正在下载…"),
        )
        DownloadNotification.refresh(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun decodeApps(raw: String): List<MarketAppInfo> {
        val array = JSONArray(raw)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            MarketAppInfo.Builder()
                .appId(item.getLong("appId"))
                .packageName(item.getString("packageName"))
                .displayName(item.getString("displayName"))
                .publisherName(item.optString("publisherName"))
                .versionName(item.optString("versionName"))
                .versionCode(item.optLong("versionCode"))
                .iconUrl(item.optString("iconUrl"))
                .apkSize(item.optLong("apkSize"))
                .build()
        }
    }

    private fun decodeSettings(intent: Intent): AppSettings = AppSettings(
        showSystemApps = intent.getBooleanExtra("showSystemApps", true),
        incrementalUpdates = intent.getBooleanExtra("incrementalUpdates", true),
        removeSearchAds = intent.getBooleanExtra("removeSearchAds", false),
        removeQuickApps = intent.getBooleanExtra("removeQuickApps", false),
        removeReservationApps = intent.getBooleanExtra("removeReservationApps", false),
        showPromotions = intent.getBooleanExtra("showPromotions", false),
        showComments = intent.getBooleanExtra("showComments", false),
        showSameDeveloper = intent.getBooleanExtra("showSameDeveloper", false),
        optimizeNames = intent.getBooleanExtra("optimizeNames", false),
        xiaomiIslandOptimization = intent.getBooleanExtra("xiaomiIslandOptimization", false),
        startPage = intent.getIntExtra("startPage", 0),
        installerMode = intent.getStringExtra("installerMode") ?: "标准安装",
        customInstallerPackage = intent.getStringExtra("customInstallerPackage").orEmpty(),
        noUserAction = intent.getBooleanExtra("noUserAction", false),
        saveToDownloads = intent.getBooleanExtra("saveToDownloads", true),
    )

    companion object {
        const val CONTROL_ACTION = "com.hyper.market.action.DOWNLOAD_CONTROL"
        const val NOTIFICATION_VISIBILITY_ACTION =
            "com.hyper.market.action.DOWNLOAD_NOTIFICATION_VISIBILITY"

        fun setProgressNotificationVisible(context: Context, visible: Boolean) {
            val intent = Intent(context, DownloadService::class.java)
                .setAction(NOTIFICATION_VISIBILITY_ACTION)
                .putExtra(EXTRA_VISIBLE, visible)
            context.startService(intent)
        }

        fun start(
            context: Context,
            apps: List<MarketAppInfo>,
            settings: AppSettings,
            profileSource: String = "device",
            profileOverrides: Map<String, String> = emptyMap(),
        ) {
            require(apps.isNotEmpty()) { "下载任务不能为空" }
            val intent = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_APPS, encodeApps(apps))
                .putSettings(settings, profileSource, profileOverrides)
            com.hyper.market.installer.DownloadTaskStore.save(context, intent)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun encodeApps(apps: List<MarketAppInfo>): String = JSONArray().apply {
            apps.forEach { app ->
                put(org.json.JSONObject()
                    .put("appId", app.getAppId())
                    .put("packageName", app.getPackageName())
                    .put("displayName", app.getDisplayName())
                    .put("publisherName", app.getPublisherName())
                    .put("versionName", app.getVersionName())
                    .put("versionCode", app.getVersionCode())
                    .put("iconUrl", app.getIconUrl())
                    .put("apkSize", app.getApkSize()))
            }
        }.toString()

        private fun Intent.putSettings(
            settings: AppSettings,
            profileSource: String,
            profileOverrides: Map<String, String>,
        ): Intent = apply {
            putExtra("showSystemApps", settings.showSystemApps)
            putExtra("incrementalUpdates", settings.incrementalUpdates)
            putExtra("removeSearchAds", settings.removeSearchAds)
            putExtra("removeQuickApps", settings.removeQuickApps)
            putExtra("removeReservationApps", settings.removeReservationApps)
            putExtra("showPromotions", settings.showPromotions)
            putExtra("showComments", settings.showComments)
            putExtra("showSameDeveloper", settings.showSameDeveloper)
            putExtra("optimizeNames", settings.optimizeNames)
            putExtra("xiaomiIslandOptimization", settings.xiaomiIslandOptimization)
            putExtra("startPage", settings.startPage)
            putExtra("installerMode", settings.installerMode)
            putExtra("customInstallerPackage", settings.customInstallerPackage)
            putExtra("noUserAction", settings.noUserAction)
            putExtra("saveToDownloads", settings.saveToDownloads)
            putExtra("profileSource", profileSource)
            putExtra("profileOverrides", JSONObject(profileOverrides).toString())
        }

        private const val EXTRA_APPS = "download_apps"
        private const val EXTRA_VISIBLE = "download_notification_visible"
    }

    private fun decodeProfileOverrides(intent: Intent): Map<String, String> {
        val raw = intent.getStringExtra("profileOverrides").orEmpty()
        if (raw.isBlank()) return emptyMap()
        val json = JSONObject(raw)
        return buildMap {
            json.keys().forEach { key -> put(key, json.optString(key, "")) }
        }
    }
}
