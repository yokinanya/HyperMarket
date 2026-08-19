package com.hyper.market

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import org.json.JSONObject

@Immutable
data class AppSettings(
    val showSystemApps: Boolean = true,
    val incrementalUpdates: Boolean = true,
    val removeSearchAds: Boolean = false,
    val removeQuickApps: Boolean = false,
    val removeReservationApps: Boolean = false,
    val showPromotions: Boolean = false,
    val showComments: Boolean = false,
    val showSameDeveloper: Boolean = false,
    val optimizeNames: Boolean = false,
    val xiaomiIslandOptimization: Boolean = false,
    val startPage: Int = 0,
    val installerMode: String = "标准安装",
    val customInstallerPackage: String = "",
    val noUserAction: Boolean = false,
    val saveToDownloads: Boolean = true,
    val deleteAfterInstall: Boolean = false,
)

@Immutable
data class MarketProfileSettings(
    val source: String = "device",
    val overrides: Map<String, String> = emptyMap(),
    val templates: Map<String, Map<String, String>> = emptyMap(),
    val currentTemplate: String = "",
)

class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("market_settings", Context.MODE_PRIVATE)

    fun read(): AppSettings = AppSettings(
        showSystemApps = preferences.getBoolean(KEY_SHOW_SYSTEM, true),
        incrementalUpdates = preferences.getBoolean(KEY_INCREMENTAL, true),
        removeSearchAds = preferences.getBoolean(KEY_REMOVE_ADS, false),
        removeQuickApps = preferences.getBoolean(KEY_REMOVE_QUICK, false),
        removeReservationApps = preferences.getBoolean(KEY_REMOVE_RESERVE, false),
        showPromotions = preferences.getBoolean(KEY_SHOW_PROMOTIONS, false),
        showComments = preferences.getBoolean(KEY_SHOW_COMMENTS, false),
        showSameDeveloper = preferences.getBoolean(KEY_SHOW_DEVELOPER, false),
        optimizeNames = preferences.getBoolean(KEY_OPTIMIZE_NAMES, false),
        xiaomiIslandOptimization = preferences.getBoolean(KEY_ISLAND, false),
        startPage = preferences.getInt(KEY_START_PAGE, 0),
        installerMode = preferences.getString(KEY_INSTALLER_MODE, "标准安装") ?: "标准安装",
        customInstallerPackage = preferences.getString(KEY_CUSTOM_INSTALLER, "") ?: "",
        noUserAction = preferences.getBoolean(KEY_NO_USER_ACTION, false),
        saveToDownloads = preferences.getBoolean(KEY_SAVE_DOWNLOADS, true),
        deleteAfterInstall = preferences.getBoolean(KEY_DELETE_AFTER_INSTALL, false),
    )

    fun write(settings: AppSettings) {
        preferences.edit()
            .putBoolean(KEY_SHOW_SYSTEM, settings.showSystemApps)
            .putBoolean(KEY_INCREMENTAL, settings.incrementalUpdates)
            .putBoolean(KEY_REMOVE_ADS, settings.removeSearchAds)
            .putBoolean(KEY_REMOVE_QUICK, settings.removeQuickApps)
            .putBoolean(KEY_REMOVE_RESERVE, settings.removeReservationApps)
            .putBoolean(KEY_SHOW_PROMOTIONS, settings.showPromotions)
            .putBoolean(KEY_SHOW_COMMENTS, settings.showComments)
            .putBoolean(KEY_SHOW_DEVELOPER, settings.showSameDeveloper)
            .putBoolean(KEY_OPTIMIZE_NAMES, settings.optimizeNames)
            .putBoolean(KEY_ISLAND, settings.xiaomiIslandOptimization)
            .putInt(KEY_START_PAGE, settings.startPage)
            .putString(KEY_INSTALLER_MODE, settings.installerMode)
            .putString(KEY_CUSTOM_INSTALLER, settings.customInstallerPackage)
            .putBoolean(KEY_NO_USER_ACTION, settings.noUserAction)
            .putBoolean(KEY_SAVE_DOWNLOADS, settings.saveToDownloads)
            .putBoolean(KEY_DELETE_AFTER_INSTALL, settings.deleteAfterInstall)
            .apply()
    }

    fun readMarketProfile(): MarketProfileSettings {
        val raw = preferences.getString(KEY_PROFILE, "").orEmpty()
        val defaultSource = if (isXiaomiDevice()) "device" else "preset"
        if (raw.isBlank()) return MarketProfileSettings(source = defaultSource)
        val json = JSONObject(raw)
        val templates = json.optJSONObject("templates")?.let(::readTemplates).orEmpty()
        return MarketProfileSettings(
            source = json.optString("source", defaultSource),
            overrides = json.optJSONObject("overrides")?.let(::readMap).orEmpty(),
            templates = templates,
            currentTemplate = json.optString("currentTemplate", ""),
        )
    }

    fun writeMarketProfile(profile: MarketProfileSettings) {
        val templates = JSONObject().apply {
            profile.templates.forEach { (name, values) -> put(name, JSONObject(values)) }
        }
        val json = JSONObject()
            .put("source", profile.source)
            .put("overrides", JSONObject(profile.overrides))
            .put("templates", templates)
            .put("currentTemplate", profile.currentTemplate)
        preferences.edit().putString(KEY_PROFILE, json.toString()).apply()
    }

    fun readSearchHistory(): List<String> {
        val storedValue = preferences.all[KEY_HISTORY] ?: return emptyList()
        val history = when (storedValue) {
            is String -> storedValue.split(HISTORY_SEPARATOR)
            is Set<*> -> storedValue.filterIsInstance<String>()
            else -> error("Unsupported search history type: ${storedValue::class.java.name}")
        }.filter(String::isNotBlank)
        if (storedValue is Set<*>) {
            writeSearchHistory(history)
        }
        return history
    }

    fun writeSearchHistory(history: List<String>) {
        preferences.edit().putString(KEY_HISTORY, history.joinToString(HISTORY_SEPARATOR)).apply()
    }

    fun deviceSummary(): List<Pair<String, String>> = listOf(
        "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Android" to Build.VERSION.RELEASE,
        "SDK" to Build.VERSION.SDK_INT.toString(),
        "架构" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
    )

    private fun isXiaomiDevice(): Boolean =
        Build.MANUFACTURER.contains("xiaomi", ignoreCase = true) ||
            Build.BRAND.contains("xiaomi", ignoreCase = true)

    private companion object {
        const val KEY_SHOW_SYSTEM = "show_system"
        const val KEY_INCREMENTAL = "incremental"
        const val KEY_REMOVE_ADS = "remove_ads"
        const val KEY_REMOVE_QUICK = "remove_quick"
        const val KEY_REMOVE_RESERVE = "remove_reserve"
        const val KEY_SHOW_PROMOTIONS = "show_promotions"
        const val KEY_SHOW_COMMENTS = "show_comments"
        const val KEY_SHOW_DEVELOPER = "show_developer"
        const val KEY_OPTIMIZE_NAMES = "optimize_names"
        const val KEY_ISLAND = "xiaomi_island"
        const val KEY_START_PAGE = "start_page"
        const val KEY_INSTALLER_MODE = "installer_mode"
        const val KEY_CUSTOM_INSTALLER = "custom_installer_package"
        const val KEY_NO_USER_ACTION = "no_user_action"
        const val KEY_SAVE_DOWNLOADS = "save_downloads"
        const val KEY_DELETE_AFTER_INSTALL = "delete_after_install"
        const val KEY_HISTORY = "search_history"
        const val KEY_PROFILE = "market_profile"
        const val HISTORY_SEPARATOR = "\u001F"
    }

    private fun readMap(json: JSONObject): Map<String, String> = buildMap {
        json.keys().forEach { key -> put(key, json.optString(key, "")) }
    }

    private fun readTemplates(json: JSONObject): Map<String, Map<String, String>> = buildMap {
        json.keys().forEach { key ->
            json.optJSONObject(key)?.let { put(key, readMap(it)) }
        }
    }
}
