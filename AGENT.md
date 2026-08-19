# HyperMarket 项目交接说明

最后更新：2026-08-19

这份文档用于在另一台机器继续开发和测试。当前功能对齐目标已按用户要求暂时暂停；本次整理只处理仓库结构、生成物和交接信息，没有把“视觉已经完全一致”误标为完成。

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
- 重构 debug 包固定为 `com.hyper.market.rebuilt`，原版包名 `com.hyper.market` 只用于原版对照。
- 深链支持 `market://`、`mimarket://` 以及部分 Xiaomi Web URL，并能解析包名、应用 ID 后进入详情。
- 已处理状态栏、导航栏、沉浸式内容区域和关于页的透明系统栏。
- 启动时会请求安装未知来源、已安装应用可见性和 Android 13 通知权限；权限拒绝会以明确状态暴露，不伪造成功。

### 3.2 小米市场数据层

主要文件：

- `app/src/main/java/com/hyper/market/api/XiaomiApiClient.java`
- `XiaomiApiSigner.java`
- `XiaomiDetailParser.java`
- `XiaomiDetailExtrasParser.java`
- `app/src/main/kotlin/com/hyper/market/RemoteImageLoader.kt`

已实现：

- 搜索、详情、今日内容、更新数据和下载元数据的请求封装。
- 原版 `_n`、`_s`、`_v` 请求签名逻辑的独立实现。
- 详情页额外数据、视频、评论和 promotion 数据的解析入口。
- 图片 URL 和缩略图字段处理。
- 已修复今日接口把无效缩略图传给接口导致 400、图片空白和文章无法打开的问题；当前会过滤明显无效的图片字段，并使用可用头像/图片字段。
- 网络、JSON 和服务端错误会显示到应用状态中，便于继续定位。

### 3.3 今日、更新、搜索和详情

- `TodayPage.kt`：今日主卡片、文章入口、精选应用卡片和加载状态。
- `TodayArticlePage.kt`：文章内容、图片、应用入口和返回流程；已验证首页今日卡片可以进入文章，再进入应用详情。
- `UpdatesPage.kt`、`UpdateCards.kt`：更新列表、卡片展开、变更日志、单个更新、全部更新和忽略更新入口。
- 更新卡片现在整个卡片和操作区域都能正确点击；变更日志不会在错误位置提前截断。
- `SearchPage.kt`：搜索提交、搜索历史、历史项加载、加载更多和推广/快应用/预约过滤。
- `SettingsStore.readSearchHistory()` 已兼容旧版 `String` 与 `Set` 存储形式，避免升级后历史记录失效。
- `DetailPage.kt`、`DetailExtras.kt`、`DetailVideoPreview.kt`：应用图标、版本信息、截图/视频、详情文字、安装和打开已安装应用的入口。

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
- 下载通知、安装完成通知和历史记录写入入口。
- 保存安装包、打开保存包、重新安装和删除确认流程。
- 安装未知来源权限不足时会跳转系统设置并显示明确提示。
- 已验证安装方式页、标准安装/第三方安装器条件 UI、保存安装包页和手动更新空包名错误提示。

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
- `InstallerSettings.kt` 已按原版调整安装器卡片、下载/保存选项、开关和第三方安装器条件内容。

### 3.7 关于页

- `SettingsAbout.kt` 包含应用图标、版本号、开源许可标题和 9 个 GitHub 项目链接。
- 链接可以打开外部浏览器。
- 初始滚动状态下顶部居中“关于”按原版保持隐藏，滚动后显示紧凑标题。
- `AboutGradient.kt` 已接入根据原版反编译结果实现的 RuntimeShader：包含 4 个渐变节点、节点动画、Perlin 噪声、亮度/饱和度参数和系统栏透明效果。

## 4. 当前未完成和已知差异

### 4.1 当前暂停点：关于页渐变仍未最终验收

用户已经明确指出重构版与原版仍有明显差异。最近一次真机截图显示：

- 关于页的整体结构、图标、文字位置和链接卡片边界已接近原版。
- 直接复用原版 4 节点着色器后，顶部中心蓝色和右下蓝色仍然偏弱。
- 当前 `AboutGradient.kt` 最后又加入了空间分布校正项；本次仓库整理后已经用根目录 Gradle 工程重新完成 debug 编译，但还没有把这一版安装回测试手机做真机截图验收。
- 因此不要把当前渐变实现标记为“已完成”，继续工作时第一步应先安装本次编译版并截图比较。

验收至少要同时比较：顶部系统栏、返回箭头、图标/标题/版本坐标、开放源码许可标题、链接卡片圆角/透明度、右侧箭头和滚动后紧凑标题。

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

- 当前没有完整的单元测试、网络 mock 测试或 Compose UI 自动化测试。
- 当前主要验证方式是 Gradle 编译、ADB 安装、UIAutomator XML、真机截图和 logcat。
- 需要为 API 解析、搜索历史迁移、下载任务状态、设备字段映射和深链解析补充可重复测试。
- 需要继续清理未使用的旧 Java UI 原型文件；在确认没有被 Manifest 或其他入口引用前不要直接删除。
- 需要检查所有 Kotlin/Java 文件是否符合项目约定的函数长度、职责分离和错误可见性要求。

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
adb -s <serial> shell am start -n com.hyper.market.rebuilt/com.hyper.market.MainActivity
adb -s <serial> shell logcat -d -t 600 | rg 'FATAL EXCEPTION|AndroidRuntime'
```

安装包只在本机临时生成和安装，不要复制回仓库。需要原版时，从测试手机提取到仓库外的临时目录，再用 apktool/JADX 生成本机 `base-reverse/`。

## 7. 测试和排错约定

1. 每次安装和截图前确认 `topResumedActivity` 是 `com.hyper.market.rebuilt/com.hyper.market.MainActivity`。
2. 绝不通过启动 `com.xiaomi.market` 来验证重构版；原版对照使用 `com.hyper.market`。
3. 不要用 `pm clear` 清空测试数据；需要改变设置时通过真实 UI 操作。
4. 出现图片空白时先检查最终 URL、HTTP 状态和 `RemoteImageLoader`，不要添加静默占位图掩盖接口错误。
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

当前仓库还没有提交记录，也没有远程仓库配置；提交和 GitHub 远程地址由用户后续操作决定。
