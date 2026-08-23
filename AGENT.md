# HyperMarket 进度记录

最后更新：2026-08-24

## 当前状态

- 包名：`org.hyper.market`；原版对照包名：`com.hyper.market`。
- 唯一真机：`5b04d78f`（`23013RK75C`、`mondrian`）；启动组件 `org.hyper.market/com.hyper.market.MainActivity`。执行 ADB 前必须重新核对设备。
- 真机已安装最新 Debug（含 API 层拆分），今日页/搜索/详情冒烟通过；无下载服务、进行中通知或测试残留。
- 首页待更新横幅为**简化版**（数量 + “查看”，无应用图标），按用户要求不做原版带图标还原。
- Release 使用仓库根目录 `key.properties` 签名；文件已忽略且未被 Git 跟踪。

## 已完成

### 架构与重构

- 主导航：Miuix `NavigationBar + HorizontalPager`（点击 + 左右滑动）；子页面保留方向过渡。
- Today/Updates/Search 列表已虚拟化；更新缓存每次进程只自动刷新一次，之后仅手动刷新。
- `HyperMarketContent.kt` 已拆出路由渲染，`HyperMarketApp.kt` 低于 300 行；移除无界逐卡动画（详情展开保留有边界 `animateContentSize`）。
- **`XiaomiApiClient` 拆分完成（2026-08-24，1657 → 564 行）**：
  - `XiaomiApiSupport.java`（353 行）：网络请求（getJson/postJson/downloadHeaders）、签名（signedGet/signedPost）、参数构建（baseParameters/addClientParameters/addFeatureParameters/removeOfficialOnlyParameters/userAgent）、设备信息工具（systemProperty/oaId/hybridFramework/core 等）、instanceId 加载；client 委托调用。
  - `XiaomiResponseParsers.java`（913 行，无状态静态类）：全部响应解析（今日/金米奖/文章/更新/搜索/应用/下载 + firstText/firstUsableImage/normalizeImage/todayClickUrl/normalizeDownload/uniqueApps 等约 50 个方法）；复用 `XiaomiDetailParser.firstLong/parseScreenshotUrls`、`XiaomiApiSupport.isBlank/nonBlank/THUMBNAIL_BASE_URL/DOWNLOAD_API`。
  - `XiaomiApiClient` 仅保留 API 路由/请求组装/业务逻辑/oldApkHash 缓存。

### 下载·安装·通知

- Xiaomi Market 真实接口、Ktor CIO、Coil、Media3、APK/split、差分、校验、断点续传、暂停/恢复/取消、安装结果通知均已接入。
- 支持 4 个 Range 分片（服务端 206 且文件 > 8 MB）；批量更新最多并行准备 2 个应用，安装按顺序执行。
- 通知职责：`4101` 普通进度、`4104` 开始/恢复时动态岛、`4103` 结果；无任务或全部暂停时隐藏进行中通知。
- Root、Shizuku、标准安装分支均完成至少一次真机验证；Release 已通过 `apksigner` v2 签名校验。

### UI 与交互（对照原版 `com.hyper.market`）

- 根主题、深色语义色、系统动画开关、控件层级统一；主要操作使用按钮，展开/忽略/清除等次要操作使用文本或图标按钮。
- **搜索页**：按已安装版本显示“打开/更新/安装”；清除关键词会取消请求、清空结果和分页状态、用请求代次阻止迟到响应；“推广”标记**完全移除**（“去除推广应用”设置仍按 `isAd()` 过滤）；搜索框内保留清除图标 + 框外右侧“取消”按钮（收起键盘、清空关键词、回到搜索历史）；搜索历史 chips 改**横向**（真机验证 qq/微信/QQ 同排）。
- **详情页**：`XiaomiDetailParser.parseDetails` 从 `detailTabList[0].data.detailTabBriefShow` 取 `introduction`（完整长文，优先于 `briefShow`），应用介绍显示多段完整长文；“更多/收起”改**靠右**。真机验证 QQ 详情。
- **今日页**：移除顶部“应用商店”窗口标题栏（`android:windowNoTitle=true` + `FEATURE_NO_TITLE`），大标题回到 `[68,241]`。
- **更新页**：无待更新项不再显示“0 个应用待更新”；标题/汇总/卡片版本旧→新/日志“展开”对齐；“展开/收起”为 14sp 紧凑文本（无背景）。
- **专题文章页**：应用卡片按钮“查看”→“**安装**”，沿 `TodayArticlePage → ArticleLoadedPage → ArticleBody → ArticleAppCard` 接入 `onInstall`。
- **设置页/子页**：主体结构与原版一致；设备信息移除“信息来源”多余描述；忽略分组标签左对齐（x=32→74）；更新历史日期分组（yyyy年M月d日）+ 图标 + 名称 + “安装/更新 版本” + 时间；`UpdateHistoryEntry` 新增 `iconUrl`，旧记录用 `InstalledAppIcon`（PackageManager 取本地图标）兜底；**清空记录**移到右上角（与返回同行，`SettingsSubpage` header trailing IconButton）；保存的安装包空状态居中于页面中部偏下（`CenteredDataEmpty` 高度 620dp 居中，真机 y≈1157）。

### 尺寸对齐（uiautomator 实测）

- **搜索页结果卡片**：图标 58→52dp、名称 20→17sp、发布者/版本 16→14sp、内边距 24→16dp；验证图标 `[74,604][211,741]` 137×137、名称高 60px。
- **搜索历史 chips**：18→16sp。
- **今日待更新横幅**：18→20sp（高 70px）。
- **今日卡片 footer 标签**（进行中的活动/金米奖）：16→14sp（`[74,1164][290,1212]` 高 48px）。
- **`SectionLabel`**：由 `SmallTitle`（约 16sp）改为 14sp 文本（对齐设置/详情分组标题 48px）。
- **`InstallActionPill`**：移除固定宽 88dp（改为自适应：文本 + 两侧 16dp padding），`ACTION_PILL_HEIGHT`/minHeight 32→34dp；“全部更新” `[778,448][1006,537]` 228×89、“更新” `[850,696][1006,785]` 156×89，均与原版**完全一致**。
- **`InlineTextAction`**：文本 14sp、去 padding 与固定宽度（视觉无背景紧凑文本；uiautomator 的 126×126 为 Miuix 48dp 最小触达区，非视觉框）；“忽略本次/永久忽略”同步受益。
- 更新页卡片 17sp、详情页头部 25sp/14sp 此前已对齐。

### 深色模式与系统栏

- 全页面深色：Today/Updates/Search/Detail/Settings/About 均使用 `MiuixTheme.colorScheme` 语义色；硬编码颜色均为刻意设计——渐变卡片白字（`TodayPageCards`、`TodayArticlePage`、`UiPrimitives.GradientFeatureCard`）、视频黑背景（`DetailVideoPreview`）、`AboutGradient` 明暗双套、导航图标 stroke；无固定色用作页面/卡片背景或普通文字。
- 系统栏深色 `#101014`；`windowLightNavigationBar` 拆到 `values-v27` 与 `values-night-v27`（修复 API 26 兼容错误）；深色手势导航白色沉浸小白条由 `AppChrome.kt` + 资源处理。
- 专题正文、关于页渐变、返回图标、安装进度文字已适配深色语义色。

### 验证与构建

- `compileDebugKotlin` / `assembleDebug` / `assembleRelease`（R8）均通过；`testDebugUnitTest` 6 用例 0 失败；`lintRelease` 0 errors/11 warnings（剩余为必要的 `PrivateApi/DiscouragedPrivateApi` 反射与 Gradle 升级提示）。
- 真机冒烟：今日页（夏日出行攻略/马蜂窝/金米奖）、搜索 qq（QQ/腾讯/QQ阅读）、QQ 详情页（下载次数 + 完整介绍）均正常。

## UI 还原说明

- 目标：尽可能还原原版 `com.hyper.market`（2026-08-24 起用 `uiautomator dump` 逐页采集对比：今日/更新/设置/搜索/详情）。
- **明确不做**（用户要求）：今日页待更新横幅完整版（保留简化版）；大字体 / RTL / IME。
- **保留差异**：设置页“关于”分组（原版无入口，合理附加）；详情页评论区/相关推荐为内嵌 `OptionalDetailSections`（原版为独立 `native_market_comment/recommend` tab，默认均不显示——行为一致）；评论区默认开关控制。
- 横屏抽样正常（标题在左/待更新横幅全宽/底部导航均匀分布，无崩溃错位）；其余页面截图/语义验收未逐页完成。

## 疑难问题与证据

### Xiaomi API 签名与身份

重构版曾把 signed-key 白名单外的参数加入 `_s`，导致服务端把搜索判为违规。现在只对原版允许的键解码、排序和签名，`marketVersion`、`x-version-code` 等字段保持原版的分离处理；2026-08-22 真机搜索 `qq` 返回 `code=0`、18 个应用、`hasMore=true`。

本地没有 `dctx` 时先请求 `/apm/expId`，并缓存 `server_dctx/exp_id`；读取顺序为 `system_dctx → server_dctx → legacy dctx`。TrustZone 凭据服务受系统签名权限保护时必须省略 `tzNonce/tzSign`，不能生成伪造值。

### 分片并发、暂停与安装边界

回环测试验证了能力探测、4 个 Range 请求、合并、校验和清理；暂停测试验证分片长度保留及恢复后重新发 Range。真机同时观察到 QQ、微信各自的 `segment-0` 至 `segment-3`，证明应用级和文件级并发都启用。

暂停必须区分下载和安装：尾部进度回调不能把 `PAUSED` 重新发布为进行中，安装前还要再次等待共享控制器；取消后不能继续安装已准备文件。直接向 `exported=false` 接收器发 ADB 广播不算真实暂停证据，必须使用应用 UI 或通知操作。

搜狗输入法小米版是系统应用，普通安装失败属于系统限制；只有 Root/Shizuku 伪装安装来源为 `com.xiaomi.market` 才在本项目范围内尝试。

### 小米动态岛

横向反复展开的根因是每次百分比更新都重新附加动态岛参数。现在 `4101` 只承载普通进度，`4104` 使用稳定文案并只在开始/恢复时发送；暂停、取消和终态会移除动态岛通知。最终展开形态仍须以真实 SystemUI 观察为准，不能只看通知日志推断。

### 安装来源与第三方安装器

真机 `su -c id` 为 root，Shizuku 服务正在运行。Root 命令 `pm install -r --user 0 -i com.xiaomi.market` 返回 `Success`，包管理器显示 `installer=com.xiaomi.market`；Shizuku 通过真实 UI 重新下载保存包，PackageInstaller session 为 applied，来源同样为 `com.xiaomi.market`。

标准安装真实闭环已完成：设备将 InstallerX 注册为 `ACTION_INSTALL_PACKAGE` 处理器，系统确认页显示“来自应用商店”的请求，确认后版本更新成功，来源为调用方 `org.hyper.market`。第三方安装器尚未完成有效保存包的完整闭环；一次测试使用了不在 FileProvider 配置根目录内的伪造绝对路径，真实错误为：`Failed to find configured root that contains .../files/hypermarket-debug.apk`。实际保存包使用 `content://` 或已覆盖的 `Download/HyperMarket`、`downloads` 路径，因此该错误不能直接推断真实保存流程失败。

### 更新取消异常

页面离开 Compose 时的 `CancellationException` 曾被误显示成网络错误。现在取消异常继续传播并释放自动刷新门闩，返回页面后可重新执行尚未完成的首次自动刷新。

### 构建超时

Wrapper 曾卡在下载 `gradle-9.3.1-bin.zip`，沙箱随后报 `Unable to establish loopback connection`，未进入源码任务。使用代理 `127.0.0.1:7890` 和本机缓存的 Gradle 9.3.1 分发包后，`testDebugUnitTest`、`lintRelease`、`assembleDebug`、`assembleRelease` 在 21 秒内成功；这是 Wrapper/沙箱网络问题，不是测试失败。

首次 lint 曾暴露基础资源中的 `windowLightNavigationBar` API 26 兼容性错误，已拆到 `values-v27` 与 `values-night-v27`；2026-08-24 完整重跑 `assembleRelease`（含 R8）已通过，产出 `app-release.apk`。

## 仍需继续

- **Tab 切换性能定位**：受控切换复测基线 174 帧、59 帧卡顿（33.91%），p50/p90/p95/p99 = 13/34/125/450 ms；Slow UI thread 58 帧、Slow bitmap uploads 0、GPU 50th=2ms → 卡顿来自 UI 线程（组合/布局/主线程工作），非图片上传；精确定位需 Perfetto/Macrobenchmark 专项。
- **深色手势导航小白条**：代码配置已就绪（`values-night-v27` `windowLightNavigationBar=false` + `AppChrome`），设备为手势导航+深色；视觉小白条仍需真机肉眼确认。
- **第三方安装器真实闭环**：`file_paths.xml` 已覆盖有效保存路径（`Download/HyperMarket/`、`downloads/`、`content://`），配置正确；真实闭环（下载→保存→第三方打开）需 GUI 多步+网络，未自动完成。
- 其余页面截图/语义验收未逐页完成（横屏抽样正常；大字体/RTL/IME 按用户要求不处理）。

## 验证命令

```text
:app:testDebugUnitTest
:app:lintRelease
:app:assembleDebug
:app:assembleRelease
```

```bash
adb devices -l
adb -s 5b04d78f install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 5b04d78f shell am start -n org.hyper.market/com.hyper.market.MainActivity
adb -s 5b04d78f shell logcat -d -t 600 | rg 'FATAL EXCEPTION|AndroidRuntime'
```

## 提交约定

- 不提交 `base.apk`、`base-reverse/`、Gradle/build 输出、安装包、截图、日志、本地 agent 元数据和 `key.properties`；`app/src/main/jniLibs/` 的 native 库必须保留。
- 不使用 `pm clear` 清空真机数据；安装或截图前确认 `topResumedActivity` 为 `org.hyper.market/com.hyper.market.MainActivity`。
- 图片空白或安装失败时保留真实 URL、异常、任务文件和 logcat，先修根因，不添加占位成功或静默 fallback。
