package com.hyper.market

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.net.toUri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo

class MainActivity : ComponentActivity() {
    private val apiClient by lazy { XiaomiApiClient(this) }
    private val deepLinkApp = mutableStateOf<MarketAppInfo?>(null)
    private val packageVisibilityRefresh = mutableIntStateOf(0)
    private val packageVisibilityPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) packageVisibilityRefresh.intValue++
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    private var notificationPermissionRequested = false
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            packageVisibilityRefresh.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestPackageVisibilityPermission()
        registerPackageChangeReceiver()
        deepLinkApp.value = intent.toMarketApp()
        setContent {
            HyperMarketApp(
                apiClient = apiClient,
                initialDetail = deepLinkApp.value,
                packageVisibilityRefresh = packageVisibilityRefresh.intValue,
                onRequestInstallPermission = ::requestInstallPermission,
                onRequestNotificationPermission = ::requestNotificationPermission,
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageChangeReceiver)
        super.onDestroy()
    }

    private fun registerPackageChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkApp.value = intent.toMarketApp()
    }

    private fun requestInstallPermission() {
        if (packageManager.canRequestPackageInstalls()) return
        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:$packageName".toUri()
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
            !notificationPermissionRequested &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequested = true
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.toMarketApp(): MarketAppInfo? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        val queryCandidates = listOf(
            uri.getQueryParameter("packageName"),
            uri.getQueryParameter("package"),
            uri.getQueryParameter("pkg"),
            uri.getQueryParameter("pName"),
            uri.getQueryParameter("pname"),
            uri.getQueryParameter("id"),
            getStringExtra(Intent.EXTRA_PACKAGE_NAME),
        )
        val pathCandidates = uri.pathSegments
        val packageName = (queryCandidates + pathCandidates).filterNotNull()
            .firstOrNull(::isPackageName)
            ?: return null
        val id = queryCandidates.filterNotNull().firstOrNull { it.toLongOrNull() != null }
            ?: pathCandidates.firstOrNull { it.toLongOrNull() != null }
        if (!isPackageName(packageName)) return null
        return MarketAppInfo.Builder()
            .appId(id?.toLongOrNull() ?: data?.getQueryParameter("appId")?.toLongOrNull() ?: 0L)
            .packageName(packageName)
            .displayName(packageName)
            .build()
    }

    private companion object {
        val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

        fun isPackageName(value: String): Boolean =
            value.length in 3 until 256 && value.matches(PACKAGE_NAME)
    }
}
