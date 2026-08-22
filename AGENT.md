# HyperMarket 项目交接说明

最后更新：2026-08-22

这份文档用于在另一台机器继续开发和测试。当前继续进行原版功能和视觉对齐；网络限流期间的空态不会被误标为数据功能完成。

## 1. 项目目标

本项目是 HyperMarket Android 应用的重构版本。需要持续对齐原版已有功能和 UI，包括：

- 首页、今日、更新、搜索、设置四个主区域。
- 今日文章、应用详情、搜索结果和更新卡片的真实网络数据流。
- 下载、split APK、保存安装包、安装器选择和安装结果通知。
- 设备信息、手动检查更新、更新历史、忽略更新、关于页面等设置子页面。
- 页面进入/返回、滚动、弹窗、下拉菜单、开关、加载和过渡动画。
- 原版的图片加载、布局尺寸、字体层级、颜色、系统栏和交互区域。

原则是让失败直接暴露：网络、解析、下载、安装和权限错误不能用假成功或静默降级掩盖。

## 2. 当前仓库结构


```text
.
├── app/
│   └── src/main/
│       ├── java/       # API、安装器、下载服务、Shizuku 和兼容逻辑
│       ├── kotlin/     # Compose 页面、状态、数据流和 UI 组件
│       ├── res/        # Manifest、主题、图标、FileProvider 等资源
│       └── jniLibs/    # 运行所需的 patcherV3 native 库
├── build.gradle
├── settings.gradle
├── gradle.properties
├── README.md
├── THIRD_PARTY_NOTICES.md
├── AGENT.md
└── .gitignore
```

以下内容只用于本机逆向或构建，不应进入 Git：

- `base.apk`：原版安装包。本次整理已从工作区删除；继续逆向时从同一台测试手机重新提取。
- `base-reverse/`：由 apktool/JADX 生成的反编译目录，目前约 177MB，已加入 `.gitignore`。它不是重构版运行时依赖。
- `.gradle/`、`.kotlin/`、`build/`、`app/build/`：Gradle 缓存和构建输出，均已清理并忽略。
- `*.apk`、`*.aab`、`*.apks`、`*.xapk`、`*.zip`、`*.log`、截图和本地 agent 元数据：均不提交。

`app/src/main/jniLibs/` 中的 `.so` 是应用运行所需的 native 依赖，不是安装包，必须保留并提交。

## 3. 已完成或已验证的功能

下面是已经写入重构版并在当前 Android 测试机上做过实际验证的内容。这里的“已完成”表示主流程已有实现，不代表所有像素和所有异常分支都已经与原版完全一致。

### 3.1 应用壳、导航和系统窗口

- `MainActivity.kt` 使用 Compose 入口，支持主页面、详情页、今日文章页和设置子页面之间的返回栈行为。
- 主导航包含“今日”“更新”“搜索”“设置”。
- 页面路由使用 `AnimatedContent`，已加入淡入淡出和左右滑动过渡。
- 重构包名统一为 `org.hyper.market`，debug/release 不再通过 applicationId 后缀切换；原版包名 `com.hyper.market` 只用于原版对照。
- 深链支持 `market://`、`mimarket://` 以及部分 Xiaomi Web URL，并能解析包名、应用 ID 后进入详情。
- 已处理状态栏、导航栏、沉浸式内容区域和关于页的透明系统栏。
- 启动时会请求安装未知来源、已安装应用可见性和 Android 13 通知权限；权限拒绝会以明确状态暴露，不伪造成功。

### 3.2 小米市场数据层

主要文件：

- `app/src/main/java/com/hyper/market/api/XiaomiApiClient.java`
- `XiaomiApiSigner.java`
- `XiaomiDetailParser.java`
- `XiaomiDetailExtrasParser.java`
- `app/src/main/kotlin/com/hyper/market/api/KtorMarketHttpClient.kt`

已实现：

- 搜索、详情、今日内容、更新数据和下载元数据的请求封装。
- 原版 `_n`、`_s`、`_v` 请求签名逻辑的独立实现。
- 详情页额外数据、视频、评论和 promotion 数据的解析入口。
- 详情和下载元数据请求已补齐原版入口参数、来源链、能力开关、会话字段、主机拼接和 bspatch 版本；下载器会使用服务端返回的 host，而不是无条件假设固定下载域名。
- 更新请求已按原版处理包向量：按包名排序、去重、排除原版逻辑包名，并补入 `com.miui.core` 基线包；更新响应按原版 `listApp` 后 `miuiApp` 的顺序解析。
- 增量更新已补齐原版 `updateinfo/diffsize` 二次请求：初始更新列表保持快速返回，只对实际存在更新的 APK 计算 `oldApkHash`，再用已安装版本号获取 `diffFileSize`；更新总大小和单项大小均优先显示真实差分大小。
- 图片 URL 和缩略图字段处理。
- 已修复今日接口把无效缩略图传给接口导致 400、图片空白和文章无法打开的问题；当前会过滤明显无效的图片字段，并使用可用头像/图片字段。
- 网络、JSON 和服务端错误会显示到应用状态中，便于继续定位。
- API 请求头已按原版 `cc5` 分流：GET 使用动态设备 User-Agent 与市场版本名/版本号，POST 只使用动态 User-Agent；`x-pkg-name` 仅保留给下载器，不再额外加到 API 请求。
- 请求签名已按原版 `gc5` 对齐：签名正文先 URL 解码查询串，再按原版 signed-key 集合、排序和 `_p` 生成签名；不在 signed-key 白名单中的参数必须完全跳过，不能直接拼入正文。此前 `joinSignedPairs()` 错误追加了非白名单参数，导致 `_s` 与原版不一致并触发搜索风控；2026-08-22 已修正。
- 搜索请求已按原版 `tb5.D()` 对齐：参数顺序、`useExpId` 列表，以及 Header `x-version-code=40007441` 与查询 `marketVersion=40008341` 分离；RFC3986 URL 编码不能把 `*` 交给会放行它的普通表单编码器。服务端返回拒绝时保留明确的 code/message，不转为空结果。
- 2026-08-21 复核：官方 `NativeSearchResultFragment` 会额外发送 `ad`、`isEncrypt=1`、`isDarkMode`、`fromExternal` 和部分 H5 基础字段。原版 HyperMarket 搜索没有这些字段，也不做请求加密。重构版已从搜索请求中移除这些官方字段；继续发送 `isEncrypt=1` 却不加密，会被服务端当成违规。
- 原版 `tb5.z()` 的身份引导链路已补回：当本地没有 `dctx` 时，请求 `/apm/expId`，缓存响应中的 `server_dctx` 和动态 `exp_id`；读取顺序与原版一致，为 `system_dctx → server_dctx → legacy dctx`。`xmsfVersion` 使用设备真实版本，读取失败时才采用原版默认值 `70005022`。
- 官方通用网络层 `ParameterInterceptor` 的流程也已确认：`Connection` 默认开启 `needBaseParams`，拦截器先合并基础参数，再按 `/apm/` 路径调用 `SignatureUtil.getSignituredUrl()` 添加 `_n/_s/_v`。注意官方市场和目标原版 HyperMarket 的 TrustZone 追加位置不同；重构版以目标原版 `com.hyper.market` 的 `tb5/gc5` 路径为准。
- 官方 `TrustZoneSignHelper` 使用设备凭据服务得到 `fid`，再对 `fid + "," + timestamp` 的 UTF-8 字节调用普通设备密钥签名。目标原版 HyperMarket 直接绑定账号/查找设备提供的 `ISecurityDeviceCredentialManager`，使用事务 1/2/3 检查支持、读取 ID 和签名；重构版按目标原版保留同一可选 Binder 路径。设备服务不可用时必须省略 `tzSign/tzNonce`，不能用本地生成值冒充设备身份。
- 原版设备 profile 的动态版本字段已核对真机：`pageConfigVersion=18411801`、`webResVersion=3211`、`hybridFrameworkVersion=13180003`；这两个页面/资源版本不能继续使用早期默认值 `18432101/3193`。
- 原版 profile 的“预设信息”主要影响设置页显示；在当前真机对照中，“预设信息”和“从设备获取”两条请求都使用真实设备运行参数，只有“自定义”才覆盖 API 字段。`setProfile()` 已按该行为处理，不能把预设 UI 字段直接当成网络请求参数。
- `XiaomiDeviceIdentity.java`、`XiaomiIdentityServices.java` 已按原版实现真实设备身份链路：首次安装时间、`installDay/launchDay/activedTimeInterval`、持久化 `instance_id`、OAID、系统属性、`com.miui.hybrid`/`com.xiaomi.xmsf` 版本，以及可选的 `dctx/tzNonce/tzSign` Binder 服务读取。服务权限由系统签名控制时，按原版省略可选字段，不伪造值。
- Shizuku 已按原版改用 `rikka.shizuku.ShizukuProvider`，Manifest 声明 `moe.shizuku.manager.permission.API_V23`、Provider 的 `INTERACT_ACROSS_USERS_FULL` 保护权限和应用级 `moe.shizuku.client.V3_SUPPORT` 元数据；不能通过把原版包名硬编码到安全签名请求中来绕过系统调用方校验。
- 2026-08-21 真机复测：使用“从设备获取”、移除 API 多余请求头，并将 HTTP engine 对齐为原版 CIO 后，今日接口、首页文章和更新卡片均返回真实内容；首页恢复为 17 个待更新应用，图片和更新图标均已加载。重构版额外的 `networkSecurityConfig` 已移除，避免 Android TLS 抛出 hostname-aware trust manager 错误。
- API JSON 和表单请求已迁移到 Ktor 3.5.1，并使用原版同样的 CIO engine；下载器仍保留断点续传所需的流式实现。
- `org.hyper.market` 的搜索输入、提交和真实结果列表已恢复。2026-08-22 真机搜索 `qq` 返回 `code=0`、18 个应用且 `hasMore=true`，QQ、腾讯视频、赫兹等卡片正常显示；搜索框的焦点、光标、键盘、清除和取消交互也已对齐。原版 `com.hyper.market` 继续只用于并行对照。
- 图片显示和保存统一使用 Coil 3.5.0；旧的手写 `RemoteImageLoader` 已删除。

### 3.3 今日、更新、搜索和详情

- `TodayPage.kt`：今日主卡片、文章入口、精选应用卡片和加载状态。
- `TodayPageCards.kt`：今日专题卡、专题底部应用信息、加载/错误/空态组件；已从页面状态文件拆分，保持原有视觉参数。
- 今日页更新卡已按原版状态机修正：检查中、检查失败和零更新时不占位；只有确认存在更新后才从标题下方展开。卡内两行 64dp 应用图标使用连续跑马灯，第一行向左、第二行向右，约每 2 秒移动一个图标。
- `TodayArticlePage.kt`：文章内容、图片、应用入口和返回流程；已验证首页今日卡片可以进入文章，再进入应用详情。
- 2026-08-22 真机从今日“夏日出行攻略”卡片进入文章页，单应用专题的 447dp 头图、标题/应用渐变底栏、正文、图片和“查看”入口正常；点击文章返回按钮后今日页恢复。
- 今日专题卡的尺寸、图片裁切和更新卡位置已对齐；底部文字层级调整为原版更接近的 16/20/16/14sp，并关闭字体额外内边距，避免主标题和应用信息显得偏小、偏松。
- 修正更新卡隐藏时首张今日专题卡仍额外下移 20dp 的问题：无更新卡时首张专题卡紧接标题区域，更新卡显示时才保留原版 20dp 间距。
- 今日文章页已按原版 `dr4.h` / `tb5.z` 对齐：509dp 全幅头图、透明状态栏、底部渐变标题与应用图标、富文本正文/图片；`topicBanner` block 会参与头图解析，不能只读取顶层 `bannerInfo`。
- `UpdatesPage.kt`、`UpdateCards.kt`：更新列表、卡片展开、变更日志、单个更新、全部更新和忽略更新入口。
- 更新列表现在同时应用系统应用、推广、快应用、预约应用和忽略项过滤；原版真机对照已恢复为 17 条、1.4GB，前五项顺序和差分大小均已对齐。
- 更新卡片现在整个卡片和操作区域都能正确点击；变更日志不会在错误位置提前截断。
- 更新页差分大小已按原版分开渲染：真实差分大小正常显示，完整 APK 大小使用删除线；应用名称优化默认开启，并支持无空格的中英文连字符，`哔哩哔哩-弹幕…`、`高德地图-高德打车…` 会恢复为原版主名称。
- `SearchPage.kt`：搜索提交、搜索历史、历史项加载、加载更多和推广/快应用/预约过滤。解析已按原版跳过没有 `packageName` 的非应用分组，空态只在实际提交后显示。真实结果首屏、`page=1` 加载更多和“去除推广应用”均已验证；快应用/预约过滤判定已按原版 `type=quickGame`、`subscribeState > 0 && versionCode <= 0` 补齐。2026-08-22 安装修复版后重新搜索 `game`，真实结果和页面状态正常；仍需在服务端返回对应类型时做最终数量回归。
- 搜索首页会展示已有历史；历史查询保持原版的输入态、取消入口和清除按钮，首次请求分页从 0 开始。
- 搜索框已按原版拆分为外层圆角容器和内层输入控件：未聚焦/聚焦宽度、取消按钮收缩、键盘弹出、占位文字隐藏、蓝色起始光标、清除按钮和历史区域位置均已在真机节点上复核。
- 搜索输入控件语义边界已按原版修正：聚焦时 `EditText=[32,392][907,518]`、搜索图标 `[74,429][127,482]`，不再从文字起点 `x=148` 才开始响应点击。取消会真实释放焦点，再次点击能恢复取消按钮和搜索历史。
- 搜索会话已提升到导航层：从 QQ 搜索结果进入详情并返回后，关键词、已加载结果、分页和过滤后的列表均保持，不再退回空白搜索首页。
- `SettingsStore.readSearchHistory()` 已兼容旧版 `String` 与 `Set` 存储形式，避免升级后历史记录失效。
- “首页”设置已按原版改为 Miuix 三选一菜单（今日/更新/搜索），不再错误地在今日和更新之间直接二态切换。
- `DetailPage.kt`、`DetailExtras.kt`、`DetailVideoPreview.kt`：应用图标、版本信息、截图/视频、详情文字、安装和打开已安装应用的入口。
- `DetailPageSections.kt`：详情预览、应用介绍、应用信息和详情图标区块；从详情页面状态文件拆分，保持原有 UI 行为。
- 2026-08-22 从真实 `game` 搜索结果进入“神庙逃亡”详情，图标、开发者、安装按钮、四项统计、预览图、应用介绍和返回搜索流程均正常；返回后关键词与结果列表保持。
- 详情页深层滚动已补齐原版紧凑顶部栏：滚动后保留居中应用名和右侧“更新/安装”按钮，遮住旧头部操作区但不遮挡统计值；紧凑栏高度按真机节点调整为 56dp，小幅滚动真机验证通过。
- 详情设置中的“显示用户评论”开关已完成一次真实切换回归；QQ 详情数据无评论时保持无额外空卡片，测试后已恢复关闭。
- 详情视频已迁移到 AndroidX Media3 1.10.1/ExoPlayer，保留循环播放、播放/暂停、静音、失败重试和关闭/重新打开。

### 3.4 下载、安装和通知

主要文件位于 `app/src/main/java/com/hyper/market/installer/` 及以下 Kotlin 文件：

- `DownloadService.kt`
- `DownloadCoordinator.kt`
- `FileDownloader.java`
- `DownloadArchive.java`
- `DeltaPatcher.java`
- `ApkInstaller.java`
- `RootApkInstaller.java`
- `ExternalApkInstaller.java`
- `InstallerPicker.kt`
- `InstallerSettings.kt`
- `SavedPackageInstaller.kt`

已有实现：

- APK 和 split APK 下载、文件大小校验、取消和后台任务状态。
- 标准 Android `PackageInstaller` 会话安装。
- 外部安装 Activity、root/Shizuku 安装器分支和安装器能力探测。
- Root 模式的 split APK 已按测试机 `pm` 实际语法统一使用 `pm install -r --user 0 base.apk split.apk...`；设备不支持的 `pm install-multi-package` 已移除。
- 第三方安装器改为先通过 FileProvider 分享临时 APK，只有第三方返回成功后才按设置保存到 Download、删除临时文件并写入历史；取消或失败不会提前写入“已保存安装包”记录。
- 下载过程通知、安装失败通知和历史记录写入入口；安装成功只更新应用内状态与历史，不再弹出 Toast、结果通知或成功弹窗。
- 第三方安装器模式保留必要的普通下载通知，但不会写入小米超级岛扩展参数；标准安装模式继续遵循超级岛开关。
- 第三方安装器回调不再只依赖不可靠的 Activity result code；返回后会短暂轮询目标包版本，只有已安装版本达到目标版本才记录成功，取消或目标版本未更新会明确失败。
- 下载进度按钮在空闲、下载、暂停和安装状态使用固定宽度，文字只有一层并相对整个按钮居中；蓝色进度层按按钮总宽度显式计算并从左侧填充，不再从中间展开或因状态文字变化造成按钮跳宽。
- 暂停操作首次点击即更新下载控制与 UI 状态；后续进度回调只刷新百分比，不会把 `PAUSED` 覆盖回 `DOWNLOADING`。
- 保存安装包、打开保存包、重新安装和删除确认流程。
- 安装未知来源权限不足时会跳转系统设置并显示明确提示。
- 已验证安装方式页、标准安装/第三方安装器条件 UI、保存安装包页和手动更新空包名错误提示。
- 2026-08-22 手动更新页使用设备真实已安装 QQ（`com.tencent.mobileqq`、`versionCode=13188`）查询成功，返回 `9.3.35` 更新并显示“下载并安装”入口；输入布局与原版保持一致。
- 同日验证更新卡片“展开 → 忽略本次 → 更新列表刷新为 17 项 → 忽略的更新页恢复 → 空态恢复”的完整往返流程，测试状态已还原。

当前测试机上不要把“安装任务提交成功”当成“真实安装完成”；需要在后续回归中分别验证标准、Shizuku/root 和外部安装器的真实结果。

### 3.5 设置主页面和子页面

`SettingsPage.kt` 和 `SettingsSubpage.kt` 已包含：

- 开始页面选择。
- 更新设置、搜索设置、推广/快应用/预约过滤开关。
- 忽略的更新。
- 手动检查更新。
- 更新历史。
- 设备信息。
- 安装方式。
- 保存的安装包。
- 关于。

目前设置主页面的顶部标题、更新卡片、搜索卡片和“首页/今日”选择器已经按原版截图调整过；常规下拉选择使用 Miuix 组件，普通行的右箭头保留了一个小型 Canvas 绘制以匹配原版线条尺寸。

### 3.6 设备信息和安装方式

- `DeviceProfileFields.kt` 定义 25 个设备字段和顺序。
- `DeviceProfilePage.kt` 渲染设备信息页面。
- `DeviceProfileSourceDialog.kt` 使用 Miuix `Dialog`、`Card`、下拉项和选中标记，支持“自定义”“预设信息”“从设备获取”。
- 已在真机上验证三种来源切换、字段变化和弹窗尺寸。
- `InstallerSettings.kt` 已按原版调整安装器卡片、下载/保存选项、无需用户确认、开关和第三方安装器条件内容；原版没有独立的“安装成功后删除”开关，遗留的 delete-after-install 资源不作为运行设置使用。
- “无需用户确认”按 Android 31+ 安装能力动态显示，不因 Debug 构建标志隐藏；安装成功后临时下载文件统一清理，保存至 Download 只控制是否先归档。
- 2026-08-22 根据真机对照补齐模式条件：`无需用户确认` 只在“标准安装”模式且系统能力允许时显示；第三方包安装器仍使用原版的“选择包安装器”按钮和下方包查看器列表结构，切换模式后列表即时出现/隐藏。
- 2026-08-22 按用户明确要求，第三方包安装器选择改回弹窗交互：点击“选择包安装器”打开独立弹窗，候选列表在弹窗内部滚动，选择后关闭并保存，取消/系统返回均关闭弹窗；不再由页面自行展开候选列表。
- 2026-08-22 真机回归发现关于页曾因将自适应 `ic_launcher` XML 直接传给 `painterResource` 崩溃；现改为读取系统应用图标 Drawable 并通过 `AndroidView` 显示。修复后已在测试机正常打开关于页，顶部返回按钮已提升为明确的 Compose 点击层并通过点击回到设置页；Debug 构建与 lint 已通过。

### 3.7 关于页

- `SettingsAbout.kt` 包含应用图标、版本号、开源许可标题和 9 个 GitHub 项目链接。
- 链接可以打开外部浏览器。
- 初始滚动状态下顶部居中“关于”按原版保持隐藏，滚动后显示紧凑标题。
- `AboutGradient.kt` 已接入根据原版反编译结果实现的 RuntimeShader：包含 4 个渐变节点、节点动画、Perlin 噪声、亮度/饱和度参数和系统栏透明效果；节点配色已按原版真机截图调整。
- 页面路由过渡已调整：主 Tab 不再与内部 Tab 动画叠加；详情/设置子页面和关于页统一按进入/返回方向横向滑动；关于页使用独立 route key，背景独立淡入淡出，避免渐变背景突然切换；今日文章正文不再叠加第二层上滑。2026-08-22 已在设备上验证关于页进入和返回正常，Debug 构建与 lint 通过；Release 需在本轮动画修改后重新验证。
- 设置页和搜索页的 `ScrollState`/`LazyListState` 已提升到应用路由层保存；路由内容按 route key 保留详情/文章/设置子页面快照，返回时不会因状态先清空而闪白或回到错误菜单位置。2026-08-22 真机回归确认：设置页下滑进入关于后返回，仍保留在通用区域，不再回到顶部。
- 关于页顶部返回按钮和收缩标题已增加 `WindowInsets.statusBars`，不会再覆盖系统状态栏区域。
- 首页卡片对照发现：活动封面顶部标题来自远端封面图，不是本地叠加文本；金米奖卡片的本地底部遮罩已从错误的蓝色改为原版相近的暖橙/棕色，避免“金米奖 · 期数”文本与背景冲突。
- `MiuiFocusBridge.java` 已接入 HyperNotification focus-api 1.4 的 V2/V3 模板。该库以 Java 21 编译，重构版通过明确的反射适配层保持 Java 17 工程兼容；API 27 以下不启用该分支。
- `MarketApplication.java` 已初始化 AndroidHiddenApiBypass 6.1，系统属性和安装器的非 SDK API 访问不再依赖裸反射是否偶然放行。
- 已删除未被入口引用的 `app/src/main/java/com/hyper/market/ui/` 原生 View 原型；实际运行 UI 统一使用 Compose/Miuix，避免两套页面实现漂移。

## 4. 当前未完成和已知差异

### 4.1 搜索已恢复，过滤分支仍需回归

关于页的顶部系统栏、返回箭头、图标/标题/版本坐标、开放源码许可标题、链接卡片圆角/透明度和右侧箭头已有原版截图基准；渐变采用原版 RuntimeShader 结构并按同屏截图调色。自适应图标加载崩溃已修复，顶部返回按钮改为置顶 Compose 点击层，2026-08-22 在测试机完成打开、滚动内容、点击返回和系统返回流程验证。后续若继续精调，只应基于同一动画时间的像素采样，不能再叠加与原版无关的校正层。

仍需在较低 API 设备上验证 HyperNotification 的 API 27 边界，以及在真实授权/白名单条件下验证 V2/V3 通知是否被系统接收。测试机不会向重构版授予系统设备凭据权限；原版 Binder 尝试和明确日志仍保留，但系统 `tzSign` 成功路径只能在具备相应授权的环境验证。搜索不依赖伪造该权限，已通过真实 `server_dctx` 和正确 `_s` 恢复。

2026-08-22 同机对照中，官方 `com.xiaomi.market`、目标原版 `com.hyper.market` 都能搜索 `qq`。继续静态复核原版 `gc5.b()` 后发现，签名正文只包含 signed-key 白名单参数；重构版此前把非白名单能力参数也拼入正文，导致 `_s` 错误。修正后，`org.hyper.market` 同样返回 `code=0`、18 个真实应用和 `hasMore=true`。因此此前“剩余差异位于 TLS/应用进程身份”的判断已撤销，根因是本地签名实现偏差。

最终构建又以 `wechat` 做了独立回归：`page=0` 返回 18 个应用，点击“加载更多”后 `page=1` 继续返回 18 个应用，两次均为 `code=0`、`hasMore=true`。分页请求、结果追加和按钮状态已恢复；“去除推广应用”已确认会移除首项推广卡，快应用和预约过滤仍需用含对应类型的数据验证。

搜索身份链也已按原版补齐：`/apm/expId` 成功返回并缓存 `server_dctx` 与动态 `exp_id`。该链路是原版的真实实现，但本轮决定性修复仍是跳过非 signed-key 参数；不能把搜索恢复错误归因于复制包名、复制原版身份或绕过系统权限。

本轮官方 APK 逆向得到的可复核位置（均位于仓库外临时目录，不提交 Git）：

- `/tmp/xiaomi-market-official-jadx/sources/com/xiaomi/market/business_ui/search/NativeSearchResultFragment.java`：搜索结果页参数构造和 `getPageRequestApi() == "search"`。
- `/tmp/xiaomi-market-parameter-single/sources/com/xiaomi/market/common/network/connection/Parameter.java`：官方基础参数集合和 `getBaseParametersForH5ToNative()`。
- `/tmp/xiaomi-market-interceptor-single/sources/com/xiaomi/market/common/network/retrofit/interceptor/ParameterInterceptor.java`：基础参数合并、普通签名和 TrustZone 参数追加顺序。
- `/tmp/xiaomi-market-signature-single/sources/com/xiaomi/market/data/SignatureUtil.java`：官方 `_n/_s/_v` 签名正文、签名键集合和 HMAC 算法选择。
- `/tmp/xiaomi-market-trustzone-single/sources/com/xiaomi/market/common/network/connection/TrustZoneSignHelper.java`：`fid` 获取、TrustZone 签名输入和缓存周期。
- `/tmp/hypermarket-original-jadx/sources/defpackage/gc5.java`：目标原版签名正文的最终依据。
- `/tmp/hypermarket-original-jadx/sources/defpackage/tb5.java`：目标原版搜索参数、`/apm/expId`、`server_dctx` 和动态实验 ID 链路。
- `/tmp/hypermarket-original-jadx/sources/defpackage/qe.java`：目标原版 OAID、设备凭据 Binder 和 TrustZone 可选链路。

当前网络排查结论：搜索主链路已经恢复，不需要、也不能通过伪造官方包名或复制受保护设备身份解决。后续只应验证分页、去重、推广/快应用/预约过滤、详情跳转和错误态，不要重新引入非白名单参数参与 `_s` 计算。

### 4.2 全页面像素级对齐仍未完成

以下方向已有实现但还需要按原版逐页截图回归：

- 今日首屏、网络加载中、无数据、文章详情和文章内应用入口。
- 搜索首屏、键盘/输入焦点、历史记录、加载更多、过滤开关。
- 更新首屏、卡片展开、变更日志、忽略一次/永久忽略和全部更新。
- 应用详情中的图片、视频、评论、版本信息和安装按钮。
- 安装方式、保存安装包、手动检查更新、更新历史和忽略更新的空态/非空态。
- 设备信息自定义输入、预设菜单、系统状态变化和返回行为。
- 启动说明弹窗、权限拒绝、网络失败、图片失败、下载失败和安装失败。

“功能有入口”不等于“和原版功能一致”；后续必须逐个记录原版和重构版的页面、点击、返回、网络状态和最终结果。

### 4.3 真机状态需要恢复

本次测试期间曾切换过设备信息来源和安装器选项。下一次继续测试前，应通过 UI 恢复：

- 设备信息来源：`从设备获取`。
- 安装器：标准安装（除非正在专门测试其他安装器）。

不要使用 `pm clear` 代替恢复操作，因为它会清除搜索历史、更新设置、设备参数和安装记录，破坏后续对比条件。

### 4.4 工程质量和自动化仍需补充

- 当前仍没有完整的网络 mock 测试或 Compose UI 自动化测试；已新增应用名称优化的 JUnit 单元测试作为纯逻辑测试起点。
- 当前主要验证方式是 Gradle 编译、ADB 安装、UIAutomator XML、真机截图和 logcat。
- 需要为 API 解析、搜索历史迁移、下载任务状态、设备字段映射和深链解析补充可重复测试。
- 旧 Java UI 原型已清理；后续新增 UI 不应重新引入另一套原生 View 页面。
- 需要检查所有 Kotlin/Java 文件是否符合项目约定的函数长度、职责分离和错误可见性要求。
- `./gradlew :app:lintDebug` 已通过；当前仍有 55 条以兼容性/非 SDK 访问/弃用为主的 warning，不能把 warning 当作未验证的功能完成证据。
- 2026-08-22 `:app:assembleRelease :app:lintRelease` 也已通过（包含详情紧凑顶栏和详情区块拆分）；发布变体使用同一 `org.hyper.market` applicationId，构建产物仍只留在被忽略的 `app/build/` 目录。
- 同日交付审计确认 Git 索引中没有 APK/AAB、构建目录、反编译目录或日志；Release 产物均被 `.gitignore` 排除。

## 5. 关键文件索引

### 应用入口和路由

- `app/src/main/kotlin/com/hyper/market/MainActivity.kt`
- `app/src/main/kotlin/com/hyper/market/HyperMarketApp.kt`
- `app/src/main/kotlin/com/hyper/market/NavigationComponents.kt`
- `app/src/main/kotlin/com/hyper/market/UiPrimitives.kt`

### 主页面

- `TodayPage.kt`
- `TodayArticlePage.kt`
- `UpdatesPage.kt`
- `UpdateCards.kt`
- `SearchPage.kt`
- `DetailPage.kt`
- `DetailExtras.kt`
- `DetailVideoPreview.kt`

### 设置和数据

- `SettingsPage.kt`
- `SettingsSubpage.kt`
- `DataSubpages.kt`
- `DeviceProfileFields.kt`
- `DeviceProfilePage.kt`
- `DeviceProfileSourceDialog.kt`
- `InstallerSettings.kt`
- `InstallerPicker.kt`
- `ManualUpdateSettings.kt`
- `SettingsAbout.kt`
- `AboutGradient.kt`
- `AppState.kt`
- `UpdateStore.kt`

### 网络、下载和安装

- `app/src/main/java/com/hyper/market/api/`
- `app/src/main/java/com/hyper/market/installer/`
- `DownloadService.kt`
- `DownloadCoordinator.kt`
- `SavedPackageInstaller.kt`
- `PackageInventory.java`

## 6. 开发环境

项目配置：

- Android Gradle Plugin：`8.11.1`
- Kotlin：`2.4.10`
- Compose compiler：`2.4.10`
- `compileSdk 37`
- `targetSdk 37`
- `minSdk 26`
- JDK：17
- UI 依赖：Compose Material 3、Miuix `0.9.3`
- 包体内保留 armv7/arm64 的 `libpatcherV3.so`

当前工作机使用的 Gradle 9.1.0 位于：

```text
/home/yokinanya/.gradle/wrapper/dists/gradle-9.1.0-all/2x09zxy9y9fz2e9j6blrf3xag/gradle-9.1.0/bin/gradle
```

仓库应使用 `./gradlew`，不要把上述本机绝对路径写进项目配置。测试手机当前序列号是 `5b04d78f`，换机器后以 `adb devices` 的实际序列号为准。

常用命令：

```bash
./gradlew :app:assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am start -n org.hyper.market/com.hyper.market.MainActivity
adb -s <serial> shell logcat -d -t 600 | rg 'FATAL EXCEPTION|AndroidRuntime'
```

安装包只在本机临时生成和安装，不要复制回仓库。需要原版时，从测试手机提取到仓库外的临时目录，再用 apktool/JADX 生成本机 `base-reverse/`。

## 7. 测试和排错约定

1. 每次安装和截图前确认 `topResumedActivity` 是 `org.hyper.market/com.hyper.market.MainActivity`。
2. 绝不通过启动 `com.xiaomi.market` 来验证重构版；原版对照使用 `com.hyper.market`。
3. 不要用 `pm clear` 清空测试数据；需要改变设置时通过真实 UI 操作。
4. 出现图片空白时先检查最终 URL、Coil 的 ErrorResult 和 logcat，不要添加静默占位图掩盖接口错误。
5. 出现安装失败时保留明确异常、下载任务和系统 logcat，分别确认权限、文件、split APK 和 PackageInstaller 状态。
6. UI 对齐需要同时看截图和 UIAutomator XML；截图负责视觉，XML 负责文字、边界、可点击状态和滚动位置。
7. 不要把 `/tmp` 下的截图、反编译缓存、设备 XML 或安装包移动进仓库。

## 8. Git 提交前检查

```bash
git status --short
git check-ignore -v base.apk base-reverse app/build app/build/outputs/apk/debug/app-debug.apk
find . -type f \( -name '*.apk' -o -name '*.aab' -o -name '*.apks' \) -print
./gradlew :app:assembleDebug
```

预期结果：

- `base.apk`、`base-reverse/`、`app/build/` 被忽略或不存在。
- `find` 不应列出待提交安装包。
- Gradle debug 构建成功后，APK 只出现在本地 `app/build/outputs/apk/debug/`，不会被 Git 纳入。
- `app/src/main/jniLibs/` 的 native 库仍然存在。

当前仓库已有 Git 提交和远程配置；不要提交 `base.apk`、`base-reverse/`、构建输出或测试截图。提交前以当前 `git status`、`git check-ignore` 和 Gradle 构建结果为准。
