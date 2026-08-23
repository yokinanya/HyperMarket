package com.hyper.market

import android.content.Context
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.installer.ApkInstaller
import com.hyper.market.installer.FileDownloader
import com.hyper.market.installer.InstallCompletion
import com.hyper.market.installer.DownloadNotification
import com.hyper.market.installer.DownloadControl
import com.hyper.market.installer.DownloadCancelledException
import com.hyper.market.installer.DownloadTaskRegistry
import com.hyper.market.installer.DeltaPatcher
import com.hyper.market.installer.InstallOptions
import com.hyper.market.model.MarketAppInfo
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class PreparedInstall(
    val app: MarketAppInfo,
    val files: List<File>,
    val artifacts: List<com.hyper.market.model.ApkArtifact>,
    val options: InstallOptions,
)

class DownloadCoordinator(
    private val apiClient: XiaomiApiClient,
    private val downloader: FileDownloader,
    private val installer: ApkInstaller,
) {
    suspend fun downloadAndInstall(
        context: Context,
        app: MarketAppInfo,
        settings: AppSettings,
        onStatus: suspend (String) -> Unit,
    ) {
        val control = DownloadTaskRegistry.begin()
        InstallUiStateStore.begin(app.getPackageName(), app.getDisplayName())
        DownloadNotification.begin(context, app.getDisplayName())
        try {
            val prepared = prepareInstall(context, app, settings, control) { status ->
                DownloadNotification.update(context, status)
                onStatus(status)
            }
            control.awaitIfPaused()
            val synchronous = installPrepared(context, prepared, onStatus)
            if (synchronous) {
                InstallUiStateStore.complete(app.getPackageName())
                DownloadNotification.complete(context, app.getDisplayName())
            } else {
                DownloadNotification.update(context, "等待安装结果…")
            }
        } catch (_: DownloadCancelledException) {
            InstallUiStateStore.dismiss(app.getPackageName())
            DownloadNotification.cancel(context)
        } catch (exception: Exception) {
            InstallUiStateStore.failure(app.getPackageName(), exception.message ?: "未知错误")
            DownloadNotification.failure(context, exception.message ?: "未知错误")
            throw exception
        } finally {
            DownloadTaskRegistry.finish(control)
        }
    }

    internal suspend fun prepareInstall(
        context: Context,
        app: MarketAppInfo,
        settings: AppSettings,
        control: DownloadControl,
        onStatus: suspend (String) -> Unit,
    ): PreparedInstall = withContext(Dispatchers.IO) {
        val target = resolveDownloadTarget(app)
        val capabilities = InstallerCapabilities.read(context)
        val metadata = apiClient.loadDownloadMetadata(target)
        val rootDirectory = context.getExternalFilesDir("downloads")
            ?: throw IllegalStateException("无法创建 Download 目录")
        val directory = File(rootDirectory, downloadDirectoryName(target))
        val files = metadata.artifacts.mapIndexed { index, artifact ->
            downloadArtifact(
                context, target, settings, directory, artifact, index,
                metadata.artifacts.size, capabilities, control, onStatus,
            )
        }
        val options = InstallOptions(
            settings.installerMode,
            target.packageName,
            target.displayName,
            target.versionName,
            target.versionCode,
            !isInstalled(context, target.packageName),
            settings.noUserAction && capabilities.userActionNotRequiredConfigurable,
            settings.saveToDownloads,
            settings.customInstallerPackage,
            target.iconUrl,
        )
        PreparedInstall(target, files, metadata.artifacts, options)
    }

    internal suspend fun installPrepared(
        context: Context,
        prepared: PreparedInstall,
        onStatus: suspend (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = prepared.app
        InstallUiStateStore.installing(target.packageName)
        onStatus("正在安装 ${target.displayName}…")
        val synchronous = installer.install(
            context,
            prepared.files,
            prepared.artifacts,
            prepared.options,
        )
        if (synchronous) {
            InstallCompletion.complete(
                context,
                prepared.options,
                prepared.files,
                prepared.artifacts.map { it.name },
            )
        } else if (prepared.options.installerMode == "第三方安装器") {
            InstallUiStateStore.awaiting(target.packageName)
            onStatus("已交给第三方安装器，安装结果由第三方应用返回")
        }
        synchronous
    }

    suspend fun downloadAndInstallAll(
        context: Context,
        apps: List<MarketAppInfo>,
        settings: AppSettings,
        onStatus: suspend (String) -> Unit,
    ) {
        BatchDownloadCoordinator(this).downloadAndInstallAll(context, apps, settings, onStatus)
    }

    private fun downloadDirectoryName(app: MarketAppInfo): String =
        "${app.packageName}-${app.versionCode}"

    private suspend fun downloadArtifact(
        context: Context,
        app: MarketAppInfo,
        settings: AppSettings,
        directory: File,
        artifact: com.hyper.market.model.ApkArtifact,
        index: Int,
        total: Int,
        capabilities: InstallerCapabilities,
        control: DownloadControl,
        onStatus: suspend (String) -> Unit,
    ): File {
        val basePath = artifact.diffBasePath.ifBlank {
            installedBasePath(context, app.packageName).orEmpty()
        }.ifBlank { null }
        val useDelta = settings.incrementalUpdates && capabilities.deltaUpdateSupported &&
            artifact.type == "base" && basePath != null
        if (!useDelta) {
            if (settings.incrementalUpdates && artifact.hasDelta() && basePath == null) {
                onStatus("基包不可用，下载完整 APK…")
            } else {
                onStatus("正在下载 ${index + 1}/${total}…")
            }
            return downloader.download(directory, artifact, control) { downloaded, expected ->
                DownloadNotification.update(
                    context,
                    "${app.displayName}：${downloadProgress(index, total, downloaded, expected)}",
                )
                InstallUiStateStore.downloading(
                    app.packageName,
                    overallProgress(index, total, downloaded, expected),
                )
            }
        }
        onStatus("正在下载增量补丁 ${index + 1}/${total}…")
        val patch = downloader.downloadDelta(directory, artifact, control) { downloaded, expected ->
            DownloadNotification.update(
                context,
                "${app.displayName}：${downloadProgress(index, total, downloaded, expected)}",
            )
            InstallUiStateStore.downloading(
                app.packageName,
                overallProgress(index, total, downloaded, expected),
            )
        }
        val output = File(directory, safeArtifactName(artifact.name))
        val patcher = DeltaPatcher()
        patcher.verifyVersion(artifact.diffVersion)
        patcher.apply(patch, File(basePath), output)
        downloader.verify(output, artifact)
        return output
    }

    private fun installedBasePath(context: Context, packageName: String): String? = try {
        context.packageManager.getApplicationInfo(packageName, 0).sourceDir
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        null
    }

    private fun safeArtifactName(name: String): String {
        val normalized = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        if (normalized.isEmpty()) error("安装包名称为空")
        return normalized
    }

    private fun resolveDownloadTarget(app: MarketAppInfo): MarketAppInfo {
        if (app.getAppId() > 0) {
            return app
        }
        return apiClient.findByPackageName(app.getPackageName())
    }

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun downloadProgress(index: Int, total: Int, downloaded: Long, expected: Long): String {
        val suffix = if (expected > 0) " ${downloaded * PERCENT_SCALE / expected}%" else ""
        return "正在下载 ${index + 1}/$total$suffix"
    }

    private fun overallProgress(index: Int, total: Int, downloaded: Long, expected: Long): Int? {
        if (expected <= 0 || total <= 0) return null
        val artifactProgress = downloaded.coerceAtMost(expected).toDouble() / expected
        return (((index + artifactProgress) / total) * PERCENT_SCALE).toInt()
    }

    private companion object {
        const val PERCENT_SCALE = 100
    }
}
