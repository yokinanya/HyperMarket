package com.hyper.market

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import top.yukonga.miuix.kmp.utils.MiuixIndication

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
    // miuix-nav 标准导航栈：栈底为主 Tab 页，二级页携带载荷入栈，转场由 NavDisplay 驱动。
    val backStack = remember {
        androidx.compose.runtime.mutableStateListOf<top.yukonga.miuix.kmp.nav.core.NavKey>(MarketNav.Tabs)
    }
    val searchListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val settingsScrollState = rememberSaveable(saver = ScrollState.Saver) { ScrollState(0) }
    var operation by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(profile) { apiClient.setProfile(profile.source, profile.overrides) }

    LaunchedEffect(initialDetail) {
        if (initialDetail != null) {
            backStack.add(MarketNav.Detail(initialDetail))
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
        backStack.add(MarketNav.Detail(app))
    }

    fun openArticle(resourceId: String) {
        backStack.add(MarketNav.Article(resourceId))
    }

    fun openSettings(destination: SettingsDestination) {
        backStack.add(MarketNav.Settings(destination))
    }

    fun popNav() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
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

    // 稳定化所有回调：避免 HyperMarketContentState 每次重建时 lambda 全部变为新实例，
    // 导致 Tab 内容无法跳过重组（性能：UI 线程组合风暴）。
    val stableOnSelectedTab = remember { { value: Int -> selectedTab = value } }
    val stableOnOpenDetail = remember { { app: MarketAppInfo -> openDetail(app) } }
    val stableOnOpenArticle = remember { { id: String -> openArticle(id) } }
    val stableOnInstall = remember { { app: MarketAppInfo -> install(app) } }
    val stableOnInstallAll = remember { { apps: List<MarketAppInfo> -> installAll(apps) } }
    val stableOnOpenInstalled = remember { { app: MarketAppInfo -> openInstalled(app) } }
    val stableOnOpenSaved = remember { { entry: SavedPackageEntry -> openSaved(entry) } }
    val stableOnReinstallSaved = remember { { entry: SavedPackageEntry -> reinstallSaved(entry) } }
    val stableOnSettingsChange = remember { { value: AppSettings -> updateSettings(value) } }
    val stableOnProfileChange = remember { { value: MarketProfileSettings -> updateProfile(value) } }
    val stableOnOpenSettings = remember { { destination: SettingsDestination -> openSettings(destination) } }
    // 返回由 miuix-nav NavDisplay 的标准返回驱动（含预测性返回手势）回调到这里统一出栈。
    val stableOnBack = remember { { popNav() } }
    val topNav = backStack.lastOrNull()
    val isAboutPage = topNav is MarketNav.Settings && topNav.destination == SettingsDestination.ABOUT
    val animationsEnabled = systemAnimationsEnabled()
    // 关于页动态渐变已移入 SettingsAboutPage 自身（同时作为其毛玻璃卡片的 blur 采样层），
    // 根级不再渲染 AboutGradientBackground。
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
        // 全局 Hyper 风格按压反馈：所有 plain clickable 重新注入 MiuixIndication
        val indicationColor = MiuixTheme.colorScheme.onBackground
        val miuixIndication = remember(indicationColor) { MiuixIndication(color = indicationColor) }
        CompositionLocalProvider(LocalIndication provides miuixIndication) {
        ConfigureSystemBars(isAboutPage)
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Scaffold(
                containerColor = MiuixTheme.colorScheme.surface,
                // 底栏不在根级 bottomBar 槽位渲染：进子页面时槽位瞬间清空会让底栏突兀消失。
                // 它属于主 Tab 页场景本身（参考项目 MiuixMainContent 的标准结构），随页面过渡一起进出场，
                // 见 HyperMarketContent 主 Tab 分支。
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { paddingValues ->
                // 详情/子页等自带 TopAppBar 的页面需要全屏（TopAppBar 自管状态栏内边距），
                // 根级 padding 只交给主 Tab 页使用，避免状态栏内边距被垫两次出现大片空白。
                Box(Modifier.fillMaxSize()) {
                        HyperMarketContent(
                            HyperMarketContentState(
                                backStack = backStack,
                                rootPadding = paddingValues,
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
                                onSelectedTab = stableOnSelectedTab,
                                onOpenDetail = stableOnOpenDetail,
                                onOpenArticle = stableOnOpenArticle,
                                onInstall = stableOnInstall,
                                onInstallAll = stableOnInstallAll,
                                onOpenInstalled = stableOnOpenInstalled,
                                onOpenSaved = stableOnOpenSaved,
                                onReinstallSaved = stableOnReinstallSaved,
                                onSettingsChange = stableOnSettingsChange,
                                onProfileChange = stableOnProfileChange,
                                onOpenSettings = stableOnOpenSettings,
                                onBack = stableOnBack,
                            ),
                        )
                    }
            }
        }
        InstallResultDialog()
        }
    }
}
