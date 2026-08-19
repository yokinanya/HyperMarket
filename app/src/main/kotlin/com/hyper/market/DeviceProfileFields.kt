package com.hyper.market

import android.os.Build
import android.util.DisplayMetrics
import java.util.Locale
import java.util.UUID

internal data class DeviceProfileField(
    val key: String,
    val label: String = key,
)

internal val DEVICE_PROFILE_FIELDS = listOf(
    DeviceProfileField("co"),
    DeviceProfileField("la"),
    DeviceProfileField("lo"),
    DeviceProfileField("cpuArchitecture"),
    DeviceProfileField("device"),
    DeviceProfileField("model"),
    DeviceProfileField("os"),
    DeviceProfileField("osV2"),
    DeviceProfileField("androidVersion"),
    DeviceProfileField("sdk"),
    DeviceProfileField("resolution"),
    DeviceProfileField("densityDpi"),
    DeviceProfileField("densityScaleFactor"),
    DeviceProfileField("miuiBigVersionCode"),
    DeviceProfileField("miuiBigVersionName"),
    DeviceProfileField("osBigVersionCode"),
    DeviceProfileField("osBigVersionName"),
    DeviceProfileField("marketVersion"),
    DeviceProfileField("pageConfigVersion"),
    DeviceProfileField("webResVersion"),
    DeviceProfileField("hybridFrameworkVersion"),
    DeviceProfileField("buildId"),
    DeviceProfileField("instance_id", "instanceId"),
    DeviceProfileField("hasGMSCore"),
    DeviceProfileField("supportedIslandVersion"),
)

internal fun deviceProfileValues(metrics: DisplayMetrics): Map<String, String> = linkedMapOf(
    "co" to Locale.getDefault().country.ifBlank { "CN" },
    "la" to Locale.getDefault().language.ifBlank { "zh" },
    "lo" to systemProperty("ro.miui.region").ifBlank { "CN" },
    "cpuArchitecture" to Build.SUPPORTED_ABIS.joinToString(","),
    "device" to Build.DEVICE,
    "model" to Build.MODEL,
    "os" to Build.VERSION.INCREMENTAL,
    "osV2" to Build.VERSION.INCREMENTAL,
    "androidVersion" to Build.VERSION.RELEASE,
    "sdk" to Build.VERSION.SDK_INT.toString(),
    "resolution" to orderedResolution(metrics.widthPixels, metrics.heightPixels),
    "densityDpi" to metrics.densityDpi.toString(),
    "densityScaleFactor" to metrics.density.toString(),
    "miuiBigVersionCode" to systemProperty("ro.miui.ui.version.code").ifBlank { "816" },
    "miuiBigVersionName" to systemProperty("ro.miui.ui.version.name").ifBlank { "V816" },
    "osBigVersionCode" to systemProperty("ro.mi.os.version.code").ifBlank { "3" },
    "osBigVersionName" to systemProperty("ro.mi.os.version.name").ifBlank { "OS3.0" },
    "marketVersion" to "40008341",
    "pageConfigVersion" to "18411801",
    "webResVersion" to "3211",
    "hybridFrameworkVersion" to "13180003",
    "buildId" to Build.ID,
    "instance_id" to UUID.randomUUID().toString(),
    "hasGMSCore" to hasGmsCore(),
    "supportedIslandVersion" to supportedIslandVersion(),
)

internal fun presetDeviceProfile(): Map<String, String> = linkedMapOf(
    "co" to "CN",
    "la" to "zh",
    "lo" to "CN",
    "cpuArchitecture" to "arm64-v8a",
    "device" to "popsicle",
    "model" to "2509FPN0BC",
    "os" to "OS3.0.315.0.WPBCNXM",
    "osV2" to "OS3.0.315.0.WPBCNXM",
    "androidVersion" to "15",
    "sdk" to "35",
    "resolution" to "1080*2400",
    "densityDpi" to "420",
    "densityScaleFactor" to "2.625",
    "miuiBigVersionCode" to "816",
    "miuiBigVersionName" to "V816",
    "osBigVersionCode" to "3",
    "osBigVersionName" to "OS3.0",
    "marketVersion" to "40008341",
    "pageConfigVersion" to "18411801",
    "webResVersion" to "3211",
    "hybridFrameworkVersion" to "13180003",
    "buildId" to "BP2A.250605.031.A3",
    "instance_id" to UUID.randomUUID().toString(),
    "hasGMSCore" to "true",
    "supportedIslandVersion" to "3",
)

internal fun effectiveDeviceProfile(profile: MarketProfileSettings, metrics: DisplayMetrics): Map<String, String> {
    val base = when (profile.source) {
        "preset" -> presetDeviceProfile()
        else -> deviceProfileValues(metrics)
    }.toMutableMap()
    if (profile.source == "custom") base.putAll(profile.overrides)
    return base
}

private fun orderedResolution(width: Int, height: Int): String =
    "${minOf(width, height)}*${maxOf(width, height)}"

private fun systemProperty(key: String): String = runCatching {
    val method = Class.forName("android.os.SystemProperties").getDeclaredMethod("get", String::class.java)
    method.invoke(null, key)?.toString().orEmpty()
}.getOrDefault("")

private fun hasGmsCore(): String = systemProperty("ro.miui.has_gmscore").let { it == "1" }.toString()

private fun supportedIslandVersion(): String = "3"
