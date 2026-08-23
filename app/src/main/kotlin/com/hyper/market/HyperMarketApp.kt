package com.hyper.market

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import android.widget.Toast
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun HyperMarketApp(
    apiClient: XiaomiApiClient,
    initialDetail: MarketAppInfo? = null,
    packageVisibilityRefresh: Int = 0,
    onRequestInstallPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeController = remember { ThemeController(ColorSchemeMode.System) }
    val settingsStore = remember(context) { SettingsStore(context) }
    val updateStore = remember(context) { UpdateStore(context) }
    var profile by remember { androidx.compose.runtime.mutableStateOf(settingsStore.readMarketProfile()) }
    var settings by remember { androidx.compose.runtime.mutableStateOf(settingsStore.read()) }
    val searchSession = remember { SearchSessionState(settingsStore.readSearchHistory()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(settings.startPage) }
    var detailApp by remember { androidx.compose.runtime.mutableStateOf<MarketAppInfo?>(null) }
    var displayedDetailApp by remember { androidx.compose.runtime.mutableStateOf(initialDetail) }
    var todayArticleId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var displayedArticleId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var settingsDestination by remember { androidx.compose.runtime.mutableStateOf<SettingsDestination?>(null) }
    val searchListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var operation by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showLaunchDialog by remember { androidx.compose.runtime.mutableStateOf(LaunchDialogHelper.shouldShow(context)) }

    LaunchedEffect(profile) { apiClient.setProfile(profile.source, profile.overrides) }

    LaunchedEffect(initialDetail) {
        if (initialDetail != null) {
            detailApp = initialDetail
            displayedDetailApp = initialDetail
        }
    }
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
    }

    fun openDetail(app: MarketAppInfo) {
        displayedDetailApp = app
        detailApp = app
    }

    fun openArticle(resourceId: String) {
        displayedArticleId = resourceId
        todayArticleId = resourceId
    }

    fun install(app: MarketAppInfo) {
        if (operation != null) return
        onRequestNotificationPermission()
        if (settings.installerMode == "标准安装" &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission()
            Toast.makeText(context, "请先允许本应用安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DownloadService.start(context, listOf(app), settings, profile.source, profile.overrides)
        } catch (exception: Exception) {
            operation = exception.message ?: "下载失败"
        }
    }

    fun installAll(apps: List<MarketAppInfo>) {
        if (apps.isEmpty() || operation != null) return
        onRequestNotificationPermission()
        if (settings.installerMode == "标准安装" &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            onRequestInstallPermission()
            Toast.makeText(context, "请先允许本应用安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DownloadService.start(context, apps, settings, profile.source, profile.overrides)
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
        onRequestNotificationPermission()
        if (settings.installerMode == "标准安装" &&
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
        settingsDestination != null -> "settings-${settingsDestination!!.name.lowercase()}"
        else -> "tabs"
    }
    val isAboutPage = settingsDestination == SettingsDestination.ABOUT
    val isArticlePage = todayArticleId != null
    val animationsEnabled = systemAnimationsEnabled()
    val aboutBackgroundAlpha by animateFloatAsState(
        targetValue = if (isAboutPage) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(320) else snap(),
        label = "about-background-alpha",
    )
    LaunchedEffect(operation) {
        val message = operation ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (operation == message) operation = null
    }
    MiuixTheme(controller = themeController) {
        ConfigureSystemBars(isAboutPage, isArticlePage)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (aboutBackgroundAlpha > 0f) {
                AboutGradientBackground(
                    Modifier.fillMaxSize().graphicsLayer { alpha = aboutBackgroundAlpha },
                )
            }
            Scaffold(
                containerColor = if (isAboutPage || isArticlePage) {
                    Color.Transparent
                } else {
                    MiuixTheme.colorScheme.surface
                },
                bottomBar = {
                    if (detailApp == null && todayArticleId == null && settingsDestination == null) {
                        MarketNavigation(selectedTab) { selectedTab = it }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                val pageModifier = if (isAboutPage || isArticlePage) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxSize().padding(paddingValues)
                }
                    Box(pageModifier) {
                        HyperMarketContent(
                            HyperMarketContentState(
                                route = route,
                                displayedDetailApp = displayedDetailApp,
                                displayedArticleId = displayedArticleId,
                                selectedTab = selectedTab,
                                searchSession = searchSession,
                                searchListState = searchListState,
                                settingsScrollState = settingsScrollState,
                                apiClient = apiClient,
                                settings = settings,
                                profile = profile,
                                updateStore = updateStore,
                                packageVisibilityRefresh = packageVisibilityRefresh,
                                animationsEnabled = animationsEnabled,
                                onSelectedTab = { selectedTab = it },
                                onOpenDetail = ::openDetail,
                                onOpenArticle = ::openArticle,
                                onInstall = ::install,
                                onInstallAll = ::installAll,
                                onOpenInstalled = ::openInstalled,
                                onOpenSaved = ::openSaved,
                                onReinstallSaved = ::reinstallSaved,
                                onSettingsChange = ::updateSettings,
                                onProfileChange = ::updateProfile,
                                onOpenSettings = { settingsDestination = it },
                                onBack = {
                                    when {
                                        detailApp != null -> detailApp = null
                                        todayArticleId != null -> todayArticleId = null
                                        else -> settingsDestination = null
                                    }
                                },
                            ),
                        )
                    }
            }
        }
        LaunchNotice(showLaunchDialog) {
            LaunchDialogHelper.markShown(context)
            showLaunchDialog = false
        }
        InstallResultDialog()
    }
}
