package com.hyper.market

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import com.hyper.market.installer.ApkInstaller
import com.hyper.market.installer.InstallCompletion
import com.hyper.market.installer.InstallOptions
import com.hyper.market.model.ApkArtifact
import java.io.File

class SavedPackageInstaller(private val installer: ApkInstaller) {
    fun install(context: Context, entry: SavedPackageEntry, settings: AppSettings): Boolean {
        val files = entry.artifacts.mapIndexed { index, artifact ->
            materialize(context, entry, artifact.path, artifact.fileName, index)
        }
        val artifacts = files.mapIndexed { index, file ->
            val type = if (index == 0) "base" else "split"
            ApkArtifact(entry.artifacts[index].fileName, type, "", file.length(), "", "", 0, "")
        }
        val options = InstallOptions(
            settings.installerMode,
            entry.packageName,
            entry.displayName,
            entry.versionName,
            entry.versionCode,
            !isInstalled(context, entry.packageName),
            settings.noUserAction && InstallerCapabilities.read(context).userActionNotRequiredConfigurable,
            false,
            settings.customInstallerPackage,
            entry.iconUrl,
        )
        val synchronous = installer.install(context, files, artifacts, options)
        if (synchronous) {
            InstallCompletion.complete(context, options, files, artifacts.map(ApkArtifact::getName))
        }
        return synchronous
    }

    fun open(context: Context, entry: SavedPackageEntry) {
        val uris = entry.artifacts.map { artifact -> uriFor(context, artifact.path) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_VIEW).setDataAndType(uris.single(), APK_MIME)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType(APK_MIME)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(APK_MIME))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = clipData(context, uris)
        context.startActivity(intent)
    }

    private fun materialize(
        context: Context,
        entry: SavedPackageEntry,
        location: String,
        fileName: String,
        index: Int,
    ): File {
        if (!location.startsWith("content://")) {
            val file = if (location.startsWith("file://")) {
                File(requireNotNull(location.toUri().path) { "保存的安装包路径为空：$location" })
            } else {
                File(location)
            }
            return file.also { check(it.isFile) { "保存的安装包不存在：$location" } }
        }
        val directory = File(context.cacheDir, "saved-install/${safeSegment(entry.id)}")
        check(directory.exists() || directory.mkdirs()) { "无法创建保存安装包临时目录" }
        val target = File(directory, "$index-${safeSegment(fileName)}")
        context.contentResolver.openInputStream(location.toUri()).use { input ->
            checkNotNull(input) { "无法读取保存的安装包：$location" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        check(target.isFile && target.length() > 0) { "保存的安装包为空：$location" }
        return target
    }

    private fun uriFor(context: Context, location: String): Uri {
        val parsed = location.toUri()
        if (parsed.scheme == "content") return parsed
        val file = if (parsed.scheme == "file") {
            File(requireNotNull(parsed.path) { "保存的安装包路径为空：$location" })
        } else {
            File(location)
        }
        check(file.isFile) { "保存的安装包不存在：$location" }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun clipData(context: Context, uris: List<Uri>): android.content.ClipData {
        val clipData = android.content.ClipData.newUri(
            context.contentResolver,
            "APK",
            uris.first(),
        )
        uris.drop(1).forEach { uri -> clipData.addItem(android.content.ClipData.Item(uri)) }
        return clipData
    }

    private fun safeSegment(value: String): String = value
        .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        .ifBlank { "saved-package" }

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
