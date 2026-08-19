package com.hyper.market

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableStateOf
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo

class MainActivity : ComponentActivity() {
    private val apiClient by lazy { XiaomiApiClient(this) }
    private val deepLinkApp = mutableStateOf<MarketAppInfo?>(null)
    private val packageVisibilityRefresh = mutableStateOf(0)
    private val packageVisibilityPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) packageVisibilityRefresh.value++
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPackageVisibilityPermission()
        requestNotificationPermission()
        deepLinkApp.value = intent.toMarketApp()
        setContent {
            HyperMarketApp(
                apiClient = apiClient,
                initialDetail = deepLinkApp.value,
                packageVisibilityRefresh = packageVisibilityRefresh.value,
                onRequestInstallPermission = ::requestInstallPermission,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkApp.value = intent.toMarketApp()
    }

    private fun requestInstallPermission() {
        if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) return
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = android.net.Uri.parse("package:$packageName")
        })
    }

    private fun requestPackageVisibilityPermission() {
        val permission = "com.android.permission.GET_INSTALLED_APPS"
        val available = runCatching { packageManager.getPermissionInfo(permission, 0) }.isSuccess
        if (available && checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            packageVisibilityPermission.launch(permission)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.toMarketApp(): MarketAppInfo? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        val queryCandidates = listOf(
            uri.getQueryParameter("packageName"),
            uri.getQueryParameter("package"),
            uri.getQueryParameter("pName"),
            uri.getQueryParameter("pname"),
            uri.getQueryParameter("id"),
        )
        val pathCandidates = uri.pathSegments
        val packageName = (queryCandidates + pathCandidates).filterNotNull()
            .firstOrNull { it.matches(PACKAGE_NAME) }
            ?: return null
        val id = queryCandidates.filterNotNull().firstOrNull { it.toLongOrNull() != null }
            ?: pathCandidates.firstOrNull { it.toLongOrNull() != null }
        if (!packageName.matches(PACKAGE_NAME)) return null
        return MarketAppInfo.Builder()
            .appId(id?.toLongOrNull() ?: data?.getQueryParameter("appId")?.toLongOrNull() ?: 0L)
            .packageName(packageName)
            .displayName(packageName)
            .build()
    }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
    }
}
