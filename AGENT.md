# HyperMarket 进度记录

最后更新：2026-08-24

## 当前状态

- 包名：`org.hyper.market`；原版对照包名：`com.hyper.market`。
- 唯一真机：`5b04d78f`（`23013RK75C`、`mondrian`）。执行 ADB 操作前必须重新核对设备。
- 指定真机 `5b04d78f` 已安装并启动上一轮 Debug，当前没有下载服务、进行中通知或测试残留；本轮最后的搜索、专题页和 API 27 资源修正尚未重新安装到真机。
- 首页待更新已压成一行，只显示数量和“查看”，不显示应用图标。
- Release 使用仓库根目录 `key.properties` 签名；文件已忽略且未被 Git 跟踪。

## 已完成

- 主导航改为 Miuix `NavigationBar + HorizontalPager`，支持点击和左右滑动；子页面保留方向过渡。
- 根主题、深色语义色、系统动画开关和控件层级已统一；主要操作使用按钮，展开/忽略/清除等次要操作使用文本或图标按钮。
- 搜索页已按已安装版本号显示“打开/更新/安装”；清除关键词会取消请求、清空结果和分页状态，并用请求代次阻止迟到响应恢复旧列表；已移除重复的“取消”，保留搜索框内一个清除图标。
- 更新页无待更新项时不再显示“0 个应用待更新”；专题正文、关于页渐变、返回图标和安装进度文字已适配深色语义色。
- 已对照原版 `com.hyper.market` 的夜间资源：系统栏使用深色 `#101014`，导航栏 API 27+ 资源分 qualifier，深色手势导航显示白色沉浸小白条。
- Today、Updates、Search 列表已虚拟化；更新缓存每次进程只自动刷新一次，之后仅手动刷新。
- Xiaomi Market 真实接口、Ktor CIO、Coil、Media3、APK/split、差分、校验、断点续传、暂停/恢复/取消和安装结果通知均已接入。
- 支持 4 个 Range 分片（服务端支持 206 且文件大于 8 MB）；批量更新最多并行准备 2 个应用，安装按顺序执行。
- 通知职责已拆分：`4101` 普通进度、`4104` 开始/恢复时的动态岛、`4103` 结果；无任务或全部暂停时隐藏进行中通知。
- 已移除无界逐卡动画；详情展开保留有边界的 `animateContentSize`。`HyperMarketContent.kt` 已拆出路由渲染，`HyperMarketApp.kt` 低于 300 行。
- Root、Shizuku、标准安装分支均已完成至少一次真机验证；Release 已通过 `apksigner` v2 签名校验。
- 全页面深色模式：系统性排查代码层面未见遗漏。主要页面（Today/Updates/Search/Detail/Settings/About）均使用 `MiuixTheme.colorScheme` 语义色；硬编码颜色均为刻意设计：渐变卡片白字（`TodayPageCards`、`TodayArticlePage`、`UiPrimitives.GradientFeatureCard`）、视频黑背景（`DetailVideoPreview`）、`AboutGradient` 明暗双套、导航图标 stroke。无固定色用作页面/卡片背景或普通文字颜色的遗漏；系统栏/导航栏深色已在 `AppChrome.kt` 与 `values-night(-v27)` 处理。
- 对照原版 `com.hyper.market`（真机同装）改进搜索/详情交互：搜索历史 chips 由竖直列表改为**横向**（对齐原版横向布局，真机验证 qq/微信/QQ 同排）；详情页“应用介绍”的“更多/收起”改为**靠右**（对齐原版右下角位置）。
- 本次改动后 `compileDebugKotlin`、`assembleDebug` 通过并安装到 `5b04d78f`；`testDebugUnitTest` 6 个用例 0 失败；`lintRelease` 0 errors/11 warnings（与基线一致）。

## UI 还原清单（对照原版 `com.hyper.market`）

- 2026-08-24 起系统采集原版各页 UI 结构（`uiautomator dump`），目标：尽可能还原原版 UI。
- **今日页顶部待更新横幅**：保留现有**简化版**（“X 个应用待更新 + 查看”，无应用图标），**不做**原版带图标的完整还原（用户明确要求 2026-08-24）。
- 已采集原版基准：今日 / 更新 / 设置 / 搜索 / 详情页结构。

### 已完成（2026-08-24）
- **搜索页**：输入关键词后，搜索框内保留“清除”图标，框外右侧增加**“取消”按钮**（对齐原版；点“取消”收起键盘、清空关键词、回到搜索历史列表）。真机验证。
- **今日页**：移除顶部**“应用商店”窗口标题栏**（`android:windowNoTitle=true` + `FEATURE_NO_TITLE`），今日大标题回到 `[68,241]`（对齐原版）。真机验证。
- **详情页**：修复“应用介绍”只显示一句的根因 —— `XiaomiDetailParser.parseDetails` 现在从 `detailTabList[0].data.detailTabBriefShow` 取 `introduction`（完整长文，优先于 `briefShow`），应用介绍显示多段完整长文；“更多/收起”靠右。真机验证 QQ 详情。
- **更新页**：与原版高度一致（标题/汇总“应用待更新+大小+全部更新”/卡片版本旧→新/日志“展开”均对齐），仅“展开”按钮略大（暂不动，避免影响全局 `InlineTextAction`）。

- **设置页/子页**：与原版主结构核对一致（应用详情/通用分组、各开关项与描述文案均相同）。细微差异：分组标题缩进差约 13dp（过小，不动）；我们多了"关于"分组（版本信息，属合理附加）；子页（更新历史等）为独立导航，结构与原版一致。
- **专题文章页**：应用卡片按钮由"查看"改为**"安装"**（对齐原版），已沿 `TodayArticlePage → ArticleLoadedPage → ArticleBody → ArticleAppCard` 接入 `onInstall`，`HyperMarketContent` 传入。真机验证。
- **详情页其余部分**：头部/统计/应用介绍/应用信息均已对齐原版；评论区/相关推荐原版为独立 tab（`native_market_comment/recommend`），我们内嵌 `OptionalDetailSections` 展示，属结构差异，保留（改动大）。
- **更新页**：`InlineTextAction` 的"展开/收起"按钮 padding 由 `vertical 10/horizontal 12` 降为 `vertical 2/horizontal 8`（更紧凑，对齐原版右侧文本）。

### 深水区核对（2026-08-24）
- **详情页评论/相关推荐**：原版详情页滚动到底只有 头部/统计/预览/应用介绍/应用信息（评论与推荐为独立 `native_market_comment/recommend` H5 tab，默认不显示）；我们的 `OptionalDetailSections` 默认也不显示（由“显示用户评论/显示同开发者应用/显示优惠活动”开关控制）——**行为一致，无需改动**。
- **搜索页“推广”标记**：已**完全移除**（2026-08-24，用户要求完全对齐原版）。`SearchResultCard` 不再显示“推广”角标，结果卡片结构对齐原版（图标/名称/发布者/版本大小/动作按钮）；“去除推广应用”设置仍按 `isAd()` 过滤（不显示标记）。真机验证 + 单测/lint 通过。
- **大字体 / RTL / IME**：按用户要求**不处理**。
- **横屏抽样验收**：临时强制横屏（`user_rotation=1`，已恢复 `accelerometer_rotation=1/rotation=0`），今日页布局正常：标题在左、待更新横幅全宽、底部导航 4 项均匀分布；无崩溃/严重错位。大字体/RTL/IME 尚未逐页验收。

### 尺寸对齐（2026-08-24）
用户反馈卡片/按钮/文本大小与原版不一致，基于 `uiautomator dump` 逐项对齐：
- **搜索页结果卡片**：图标 58dp→**52dp**、名称 20sp→**17sp**、发布者/版本 16sp→**14sp**、内边距 24dp→**16dp**；真机验证图标 `[74,604][211,741]` 137×137、名称高 60px，与原版一致。
- **搜索历史 chips**：18sp→**16sp**。
- **今日页待更新横幅**：18sp→**20sp**（高 70px 对齐原版）。
- **今日页卡片 footer 标签**（进行中的活动/金米奖）：16sp→**14sp**（`[74,1164][290,1212]` 高 48px，与原版一致）。
- **小节/分组标题 `SectionLabel`**：由 `SmallTitle`（约 16sp）改为自定义 14sp 文本，对齐设置页/详情页分组标题（原版 48px）。
- 按钮文本（打开/安装/更新 72×48）经对比与原版一致，未改动；`InstallPillWidth=88dp` 保留。
- 更新页卡片（17sp）与详情页头部（25sp/14sp）此前已对齐，未改动。

### 全页面核对（2026-08-24）
逐页采集原版 vs 我们 `uiautomator dump` 对比（主页面 + 全部设置子页）：
- **一致**：设置页主结构、安装方式、手动检查更新、设备信息主体（键值列表）、保存的安装包（空状态文案）。
- **已修复**：
  - 设备信息子页：移除"信息来源"下多余描述"请求市场接口时使用的设备资料"（原版无）。
  - 忽略的更新：分组标签左对齐 `x=32→74`（`SectionLabel` 支持 modifier + padding start 16dp）。
  - 更新历史：条目对齐原版——日期分组（`yyyy年M月d日`）+ 图标 + 名称 + "安装/更新 版本" + 右侧时间；`UpdateHistoryEntry` 新增 `iconUrl` 字段并存储（旧数据图标为空显示占位）。真机验证。
  - 更新历史图标兜底：旧记录无 `iconUrl` 时改为 `InstalledAppIcon`（从 `PackageManager` 取已安装应用图标，已卸载显示"未安装"），不再显示"无图标"占位；新记录用网络图标。真机验证。
- **保留差异（需重构/受限）**：
  - 更新历史"清空记录"位置：原版在右上角（与返回同行 `[954,141]`），我们在内容顶部右侧 `[862,392]`；对齐需把清空状态提升到 `SettingsSubpage` header，暂未改。
  - 保存的安装包空状态位置：原版页面中部偏下 `y≈1361`，我们在 `y≈658`；`LazyColumn` 中难以垂直居中到该位置，暂未改。
  - 关于页（ABOUT）：原版设置页无"关于"入口，为我们附加功能，保留。

- **按钮大小对齐**（2026-08-24）：`InstallActionPill` 移除固定宽度 `InstallPillWidth=88dp`（改为自适应：文本+两侧 16dp padding），`ACTION_PILL_HEIGHT`/安装按钮 `minHeight` 32dp→**34dp**。更新页真机验证："全部更新" `[778,448][1006,537]` 228×89、"更新" `[850,696][1006,785]` 156×89，均与原版**完全一致**；搜索/详情安装按钮同步受益（自适应宽度）。
- **展开按钮**：已对齐——`InlineTextAction` 文本改为 **14sp**（原版日志右侧紧凑文本大小）、去掉 padding 与固定宽度，视觉上为无背景的紧凑文本（uiautomator 报的 126×126 为 Miuix 48dp 最小触达区，非视觉框）；"忽略本次/永久忽略"同步受益。

### 验证
- `compileDebugKotlin` / `assembleDebug` 通过并安装 `5b04d78f`；`testDebugUnitTest` 6 用例 0 失败；`lintRelease` 0 errors/11 warnings。

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

本轮在搜索代次、专题页图标和 API 27 系统栏资源修正后重新执行完整构建；首次 lint 暴露基础资源中的 `windowLightNavigationBar` API 26 兼容性错误，已拆到 `values-v27` 与 `values-night-v27`。第二次构建在 lint/R8 阶段按用户要求主动停止，不能据此宣称本轮最终构建通过。

## 仍需继续

- 受控 Tab 切换本次复测基线：174 帧、59 帧卡顿（33.91%），p50/p90/p95/p99 为 13/34/125/450 ms；Slow UI thread 58 帧、Slow bitmap uploads 0、GPU 50th=2ms → 卡顿来自 UI 线程（组合/布局/主线程工作），非图片上传；精确定位需 Perfetto/Macrobenchmark 专项。
- `testDebugUnitTest`、`lintRelease`、`assembleDebug`、**`assembleRelease`（2026-08-24 重跑通过，产出 `app-release.apk`）** 均已通过。
- 深色手势导航（`values-night-v27` `windowLightNavigationBar=false` + `AppChrome`）代码配置已确认；设备为手势导航+深色，视觉小白条仍需真机肉眼确认。
- 第三方安装器：`file_paths.xml` 已覆盖有效保存路径（`Download/HyperMarket/`、`downloads/`、`content://`），配置正确；真实闭环（下载→保存→第三方打开）需 GUI 多步+网络，未自动完成。
- `XiaomiApiClient.java` 约 1657 行：已完成职责分析（网络/签名、身份、参数、各响应解析、模型组装 146 个方法，深度耦合）；**未机械拆分**——需按网络/解析/路由/持久化先补回归测试再独立重构，避免回归。
- 横屏抽样正常；大字体/RTL/IME 按用户要求不处理；其余页面截图/语义验收未逐页完成。
- Release Lint 为 0 errors、11 warnings，剩余为必要的 `PrivateApi/DiscouragedPrivateApi` 反射和 Gradle 升级提示。

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
