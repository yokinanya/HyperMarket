package com.hyper.market

import com.hyper.market.model.InstalledPackageInfo
import com.hyper.market.model.MarketAppInfo
import com.hyper.market.model.UpdateInfo
import org.json.JSONArray
import org.json.JSONObject

internal data class CachedUpdates(
    val updates: List<UpdateInfo>,
    val cachedAt: Long,
)

internal object UpdateCacheCodec {
    fun encode(updates: List<UpdateInfo>): String = JSONArray().apply {
        updates.forEach { put(updateToJson(it)) }
    }.toString()

    fun decode(value: String): List<UpdateInfo> {
        val array = JSONArray(value)
        return List(array.length()) { index -> updateFromJson(array.getJSONObject(index)) }
    }

    private fun updateToJson(update: UpdateInfo) = JSONObject()
        .put("app", appToJson(update.app))
        .put("installed", installedToJson(update.installedPackage))
        .put("diffSize", update.diffSize)

    private fun updateFromJson(value: JSONObject) = UpdateInfo(
        appFromJson(value.getJSONObject("app")),
        installedFromJson(value.getJSONObject("installed")),
        value.optLong("diffSize"),
    )

    private fun appToJson(app: MarketAppInfo) = JSONObject()
        .put("appId", app.appId)
        .put("packageName", app.packageName)
        .put("displayName", app.displayName)
        .put("publisherName", app.publisherName)
        .put("versionName", app.versionName)
        .put("versionCode", app.versionCode)
        .put("iconUrl", app.iconUrl)
        .put("apkSize", app.apkSize)
        .put("ratingScore", app.ratingScore)
        .put("changeLog", app.changeLog)
        .put("ad", app.isAd)
        .put("quickApp", app.isQuickApp)
        .put("reservationApp", app.isReservationApp)
        .put("introduction", app.introduction)
        .put("downloadCount", app.downloadCount)
        .put("commentCount", app.commentCount)
        .put("ageClassification", app.ageClassification)
        .put("updateTime", app.updateTime)
        .put("registrationNumber", app.registrationNumber)
        .put("screenshots", JSONArray(app.screenshotUrls))

    private fun appFromJson(value: JSONObject): MarketAppInfo = MarketAppInfo.Builder()
        .appId(value.optLong("appId"))
        .packageName(value.getString("packageName"))
        .displayName(value.optString("displayName"))
        .publisherName(value.optString("publisherName"))
        .versionName(value.optString("versionName"))
        .versionCode(value.optLong("versionCode"))
        .iconUrl(value.optString("iconUrl"))
        .apkSize(value.optLong("apkSize"))
        .ratingScore(value.optDouble("ratingScore"))
        .changeLog(value.optString("changeLog"))
        .ad(value.optBoolean("ad"))
        .quickApp(value.optBoolean("quickApp"))
        .reservationApp(value.optBoolean("reservationApp"))
        .introduction(value.optString("introduction"))
        .downloadCount(value.optLong("downloadCount"))
        .commentCount(value.optLong("commentCount"))
        .ageClassification(value.optString("ageClassification"))
        .updateTime(value.optLong("updateTime"))
        .registrationNumber(value.optString("registrationNumber"))
        .screenshotUrls(value.optJSONArray("screenshots").toStringList())
        .build()

    private fun installedToJson(installed: InstalledPackageInfo) = JSONObject()
        .put("packageName", installed.packageName)
        .put("versionName", installed.versionName)
        .put("versionCode", installed.versionCode)
        .put("systemApp", installed.isSystemApp)
        .put("installedByMarket", installed.installedByMarket)
        .put("splits", installed.splits)
        .put("oldApkHash", installed.oldApkHash)
        .put("apkSource", installed.apkSource)

    private fun installedFromJson(value: JSONObject) = InstalledPackageInfo(
        value.getString("packageName"),
        value.optString("versionName"),
        value.optLong("versionCode"),
        value.optBoolean("systemApp"),
        value.optString("installedByMarket"),
        value.optString("splits"),
        value.optString("oldApkHash"),
        value.optString("apkSource"),
    )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { index -> optString(index) }
    }
}
