package com.hyper.market

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IgnoredUpdate(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val permanent: Boolean,
    val updatedAt: Long,
)

data class UpdateHistoryEntry(
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val installedAt: Long,
    val firstInstall: Boolean,
    val iconUrl: String = "",
)

data class SavedPackageEntry(
    val path: String,
    val packageName: String,
    val displayName: String,
    val versionName: String,
    val versionCode: Long,
    val size: Long,
    val savedAt: Long,
    val id: String = path,
    val fileName: String = path.substringAfterLast('/').ifBlank { path },
    val artifacts: List<SavedPackageArtifact> = listOf(
        SavedPackageArtifact(path, fileName, size),
    ),
    val iconUrl: String = "",
)

data class SavedPackageArtifact(
    val path: String,
    val fileName: String,
    val size: Long,
)

class UpdateStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences(
        "market_update_data",
        Context.MODE_PRIVATE,
    )
    private val cacheRevisionState = MutableStateFlow(0L)
    internal val cacheRevision = cacheRevisionState.asStateFlow()

    fun ignoredUpdates(): List<IgnoredUpdate> = readArray(KEY_IGNORED).map(::ignoredFromJson)

    internal fun cachedUpdates(now: Long = System.currentTimeMillis()): CachedUpdates? {
        val cachedAt = preferences.getLong(KEY_UPDATE_CACHE_TIME, 0L)
        if (cachedAt <= 0L || now - cachedAt > UPDATE_CACHE_MAX_AGE_MS) return null
        val encoded = preferences.getString(KEY_UPDATE_CACHE, null) ?: return null
        return CachedUpdates(UpdateCacheCodec.decode(encoded), cachedAt)
    }

    internal fun cacheUpdates(updates: List<UpdateInfo>, cachedAt: Long = System.currentTimeMillis()) {
        preferences.edit {
            putString(KEY_UPDATE_CACHE, UpdateCacheCodec.encode(updates))
            putLong(KEY_UPDATE_CACHE_TIME, cachedAt)
        }
        cacheRevisionState.value++
    }

    fun ignore(update: UpdateInfo, permanent: Boolean) {
        val entry = IgnoredUpdate(
            update.app.packageName,
            update.app.displayName,
            update.app.versionName,
            update.app.versionCode,
            permanent,
            System.currentTimeMillis(),
        )
        val remaining = ignoredUpdates().filterNot { it.packageName == entry.packageName }
        writeArray(KEY_IGNORED, remaining.map(::ignoredToJson) + ignoredToJson(entry))
    }

    fun restoreIgnored(packageName: String) {
        writeArray(KEY_IGNORED, ignoredUpdates()
            .filterNot { it.packageName == packageName }
            .map(::ignoredToJson))
    }

    fun isIgnored(update: UpdateInfo): Boolean = ignoredUpdates().any {
        it.packageName == update.app.packageName && (it.permanent || it.versionCode == update.app.versionCode)
    }

    fun history(): List<UpdateHistoryEntry> = readArray(KEY_HISTORY).map(::historyFromJson)

    fun recordHistory(app: MarketAppInfo, firstInstall: Boolean) {
        val entry = UpdateHistoryEntry(
            app.packageName,
            app.displayName,
            app.versionName,
            app.versionCode,
            System.currentTimeMillis(),
            firstInstall,
            app.iconUrl,
        )
        val remaining = history().filterNot {
            it.packageName == entry.packageName && it.versionCode == entry.versionCode
        }
        writeArray(KEY_HISTORY, (listOf(entry) + remaining).map(::historyToJson))
        clearUpdateCache()
    }

    fun clearHistory() = preferences.edit { remove(KEY_HISTORY) }

    fun savedPackages(): List<SavedPackageEntry> = readArray(KEY_SAVED).map(::savedFromJson)

    fun recordSavedPackage(app: MarketAppInfo, file: File) {
        recordSavedPackage(app, file.absolutePath, file.length())
    }

    fun recordSavedPackage(app: MarketAppInfo, location: String, size: Long) {
        recordSavedPackageGroup(
            app,
            listOf(SavedPackageArtifact(location, location.substringAfterLast('/'), size)),
        )
    }

    fun recordSavedPackageGroup(app: MarketAppInfo, artifacts: List<SavedPackageArtifact>) {
        require(artifacts.isNotEmpty()) { "保存的安装包不能为空" }
        val normalizedArtifacts = artifacts.distinctBy(SavedPackageArtifact::path)
        val entry = SavedPackageEntry(
            path = normalizedArtifacts.first().path,
            packageName = app.packageName,
            displayName = app.displayName,
            versionName = app.versionName,
            versionCode = app.versionCode,
            size = normalizedArtifacts.sumOf(SavedPackageArtifact::size),
            savedAt = System.currentTimeMillis(),
            id = UUID.randomUUID().toString(),
            fileName = if (normalizedArtifacts.size == 1) {
                normalizedArtifacts.first().fileName
            } else {
                "${app.packageName}-${app.versionCode}.apk"
            },
            artifacts = normalizedArtifacts,
            iconUrl = app.iconUrl,
        )
        val remaining = savedPackages().filterNot { it.path == entry.path }
        writeArray(KEY_SAVED, (listOf(entry) + remaining).map(::savedToJson))
    }

    fun deleteSavedPackage(entry: SavedPackageEntry) {
        entry.artifacts.forEach(::deleteSavedArtifact)
        writeArray(KEY_SAVED, savedPackages()
            .filterNot { it.path == entry.path }
            .map(::savedToJson))
    }

    private fun deleteSavedArtifact(artifact: SavedPackageArtifact) {
        if (artifact.path.startsWith("content://")) {
            val deleted = applicationContext.contentResolver.delete(artifact.path.toUri(), null, null)
            if (deleted != 1) error("无法删除安装包：${artifact.path}")
            return
        }
        val file = if (artifact.path.startsWith("file://")) {
            File(requireNotNull(artifact.path.toUri().path) { "保存的安装包路径为空" })
        } else {
            File(artifact.path)
        }
        if (file.exists() && !file.delete()) error("无法删除安装包：${artifact.path}")
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private fun writeArray(key: String, values: List<JSONObject>) {
        val array = JSONArray()
        values.forEach(array::put)
        preferences.edit { putString(key, array.toString()) }
    }

    private fun clearUpdateCache() {
        preferences.edit {
            remove(KEY_UPDATE_CACHE)
            remove(KEY_UPDATE_CACHE_TIME)
        }
        cacheRevisionState.value++
    }

    private fun ignoredToJson(value: IgnoredUpdate) = JSONObject()
        .put("packageName", value.packageName)
        .put("displayName", value.displayName)
        .put("versionName", value.versionName)
        .put("versionCode", value.versionCode)
        .put("permanent", value.permanent)
        .put("updatedAt", value.updatedAt)

    private fun ignoredFromJson(value: JSONObject) = IgnoredUpdate(
        value.getString("packageName"),
        value.getString("displayName"),
        value.getString("versionName"),
        value.getLong("versionCode"),
        value.getBoolean("permanent"),
        value.getLong("updatedAt"),
    )

    private fun historyToJson(value: UpdateHistoryEntry) = JSONObject()
        .put("packageName", value.packageName)
        .put("displayName", value.displayName)
        .put("versionName", value.versionName)
        .put("versionCode", value.versionCode)
        .put("installedAt", value.installedAt)
        .put("firstInstall", value.firstInstall)
        .put("iconUrl", value.iconUrl)

    private fun historyFromJson(value: JSONObject) = UpdateHistoryEntry(
        value.getString("packageName"),
        value.getString("displayName"),
        value.getString("versionName"),
        value.getLong("versionCode"),
        value.getLong("installedAt"),
        value.getBoolean("firstInstall"),
        value.optString("iconUrl", ""),
    )

    private fun savedToJson(value: SavedPackageEntry) = JSONObject()
        .put("id", value.id)
        .put("path", value.path)
        .put("fileName", value.fileName)
        .put("packageName", value.packageName)
        .put("displayName", value.displayName)
        .put("versionName", value.versionName)
        .put("versionCode", value.versionCode)
        .put("size", value.size)
        .put("savedAt", value.savedAt)
        .put("icon", value.iconUrl)
        .put("artifacts", JSONArray().apply {
            value.artifacts.forEach { artifact ->
                put(JSONObject()
                    .put("uri", artifact.path)
                    .put("fileName", artifact.fileName)
                    .put("size", artifact.size))
            }
        })

    private fun savedFromJson(value: JSONObject): SavedPackageEntry {
        val path = value.getString("path")
        val fallbackName = path.substringAfterLast('/').ifBlank { path }
        val artifacts = value.optJSONArray("artifacts")?.let { array ->
            List(array.length()) { index ->
                val artifact = array.getJSONObject(index)
                SavedPackageArtifact(
                    artifact.getString("uri"),
                    artifact.optString("fileName", fallbackName),
                    artifact.optLong("size", 0L),
                )
            }
        }.orEmpty().ifEmpty {
            listOf(SavedPackageArtifact(path, fallbackName, value.optLong("size", 0L)))
        }
        return SavedPackageEntry(
            path = artifacts.first().path,
            packageName = value.getString("packageName"),
            displayName = value.getString("displayName"),
            versionName = value.getString("versionName"),
            versionCode = value.getLong("versionCode"),
            size = value.optLong("size", artifacts.sumOf(SavedPackageArtifact::size)),
            savedAt = value.getLong("savedAt"),
            id = value.optString("id", path),
            fileName = value.optString("fileName", artifacts.first().fileName),
            artifacts = artifacts,
            iconUrl = value.optString("icon", ""),
        )
    }

    private companion object {
        const val KEY_IGNORED = "ignored_updates"
        const val KEY_HISTORY = "update_history"
        const val KEY_SAVED = "saved_packages"
        const val KEY_UPDATE_CACHE = "available_updates_cache"
        const val KEY_UPDATE_CACHE_TIME = "available_updates_cache_time"
        const val UPDATE_CACHE_MAX_AGE_MS = 21_600_000L
    }
}
