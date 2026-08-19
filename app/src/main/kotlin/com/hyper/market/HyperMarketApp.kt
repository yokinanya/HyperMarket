package com.hyper.market

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HyperMarketApp(
    apiClient: XiaomiApiClient,
    initialDetail: MarketAppInfo? = null,
    packageVisibilityRefresh: Int = 0,
    onRequestInstallPermission: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = remember(context) { SettingsStore(context) }
    val updateStore = remember(context) { UpdateStore(context) }
    var profile by remember { androidx.compose.runtime.mutableStateOf(settingsStore.readMarketProfile()) }
    var settings by remember { androidx.compose.runtime.mutableStateOf(settingsStore.read()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(settings.startPage) }
    var detailApp by remember { androidx.compose.runtime.mutableStateOf<MarketAppInfo?>(null) }
    var todayArticleId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var settingsDestination by remember { androidx.compose.runtime.mutableStateOf<SettingsDestination?>(null) }
    var operation by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var showLaunchDialog by remember { androidx.compose.runtime.mutableStateOf(LaunchDialogHelper.shouldShow(context)) }

    LaunchedEffect(profile) { apiClient.setProfile(profile.source, profile.overrides) }

    LaunchedEffect(initialDetail) { if (initialDetail != null) detailApp = initialDetail }
    BackHandler(enabled = detailApp != null || todayArticleId != null || settingsDestination != null) {
        when {
            detailApp != null -> detailApp = null
            todayArticleId != null -> todayArticleId = null
            else -> settingsDestination = null
        }
    }

    fun updateSettings(value: AppSettings) {
        settings = value
        settingsStore.write(value)
    }

    fun updateProfile(value: MarketProfileSettings) {
        profile = value
        settingsStore.writeMarketProfile(value)
        apiClient.setProfile(value.source, value.overrides)
    }

    fun install(app: MarketAppInfo) {
        if (operation != null) return
        if (settings.installerMode == "标准安装" &&
            android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission()
            Toast.makeText(context, "请先允许本应用安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DownloadService.start(context, listOf(app), settings, profile.source, profile.overrides)
            operation = "已提交后台任务：${app.getDisplayName()}"
        } catch (exception: Exception) {
            operation = exception.message ?: "下载失败"
        }
    }

    fun installAll(apps: List<MarketAppInfo>) {
        if (apps.isEmpty() || operation != null) return
        if (settings.installerMode == "标准安装" &&
            android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission()
            Toast.makeText(context, "请先允许本应用安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DownloadService.start(context, apps, settings, profile.source, profile.overrides)
            operation = "全部更新已提交到后台"
        } catch (exception: Exception) {
            operation = exception.message ?: "更新失败"
        }
    }

    fun openInstalled(app: MarketAppInfo) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent == null) {
            operation = "无法打开：${app.displayName}没有可启动入口"
            return
        }
        context.startActivity(launchIntent)
    }

    fun reinstallSaved(entry: SavedPackageEntry) {
        if (settings.installerMode == "标准安装" &&
            android.os.Build.VERSION.SDK_INT >= 26 &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission()
            Toast.makeText(context, "请先允许本应用安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val synchronous = SavedPackageInstaller(com.hyper.market.installer.ApkInstaller())
                .install(context, entry, settings)
            operation = if (synchronous) {
                "已完成安装：${entry.displayName}"
            } else {
                "已提交安装任务：${entry.displayName}"
            }
        } catch (exception: Exception) {
            operation = exception.message ?: "无法安装保存的安装包组"
        }
    }

    fun openSaved(entry: SavedPackageEntry) {
        try {
            SavedPackageInstaller(com.hyper.market.installer.ApkInstaller()).open(context, entry)
        } catch (exception: Exception) {
            operation = exception.message ?: "无法打开保存的安装包"
        }
    }

    val route = when {
        detailApp != null -> "detail"
        todayArticleId != null -> "today-article"
        settingsDestination != null -> "settings-subpage"
        else -> "tab-$selectedTab"
    }
    val isAboutPage = settingsDestination == SettingsDestination.ABOUT
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val surfaceColor = if (isAboutPage) {
            android.graphics.Color.TRANSPARENT
        } else {
            android.graphics.Color.rgb(247, 247, 247)
        }
        window.statusBarColor = surfaceColor
        window.navigationBarColor = surfaceColor
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = !isAboutPage
        }
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }
    MiuixTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isAboutPage) {
                AboutGradientBackground(Modifier.fillMaxSize())
            }
            Scaffold(
                containerColor = if (isAboutPage) Color.Transparent else Color(0xFFF7F7F7),
                bottomBar = {
                    if (detailApp == null && todayArticleId == null && settingsDestination == null) {
                        MarketNavigation(selectedTab) { selectedTab = it }
                    }
                },
            ) { paddingValues ->
                Box(Modifier.fillMaxSize().padding(paddingValues)) {
                    AnimatedContent(
                        targetState = route,
                        transitionSpec = { routeTransition() },
                        label = "route-transition",
                    ) { currentRoute ->
                        when {
                            currentRoute == "detail" -> detailApp?.let {
                                DetailPage(
                                    app = it,
                                    apiClient = apiClient,
                                    settings = settings,
                                    onInstall = ::install,
                                    onOpenInstalled = ::openInstalled,
                                    onOpenDetail = { detailApp = it },
                                    onBack = { detailApp = null },
                                )
                            }
                            currentRoute == "today-article" -> todayArticleId?.let {
                                TodayArticlePage(
                                    resourceId = it,
                                    apiClient = apiClient,
                                    onOpenDetail = { detailApp = it },
                                    onBack = { todayArticleId = null },
                                )
                            }
                            currentRoute == "settings-subpage" -> settingsDestination?.let {
                                SettingsSubpage(
                                    it,
                                    settings,
                                    profile,
                                    apiClient,
                                    updateStore,
                                    ::updateSettings,
                                    ::updateProfile,
                                    ::install,
                                    ::openSaved,
                                    ::reinstallSaved,
                                ) {
                                    settingsDestination = null
                                }
                            }
                            else -> MainPage(
                                selectedTab,
                                apiClient,
                                settings,
                                updateStore,
                                packageVisibilityRefresh,
                                onOpenDetail = { detailApp = it },
                                onOpenArticle = { todayArticleId = it },
                                onInstall = ::install,
                                onInstallAll = ::installAll,
                                onOpenSettings = { settingsDestination = it },
                                onSettingsChange = ::updateSettings,
                            )
                        }
                    }
                    operation?.let { status -> OperationBanner(status) { operation = null } }
                }
            }
        }
    }
    if (showLaunchDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("关于本软件的说明") },
            text = { Text(LaunchDialogHelper.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        LaunchDialogHelper.markShown(context)
                        showLaunchDialog = false
                    },
                ) { Text("知道了") }
            },
        )
    }
}
