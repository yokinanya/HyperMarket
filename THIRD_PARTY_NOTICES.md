# Third-party references

这份清单区分“重构版实际运行依赖”和“为了保持原版关于页/兼容说明而保留的原版引用”。不把原 APK 的许可字符串误写成当前构建依赖。

## 当前构建实际使用

- JetBrains Compose Multiplatform / Material 3：Compose UI 与 Material 组件。
  https://github.com/JetBrains/compose-multiplatform
- compose-miuix-ui/miuix：Xiaomi 风格卡片、导航栏、开关、下拉和页面组件。
  https://github.com/compose-miuix-ui/miuix
- Kotlin Coroutines：网络任务、更新扫描和后台下载调度。
  https://github.com/Kotlin/kotlinx.coroutines
- Ktor 3.5.1（CIO engine）：小米市场 API 的 HTTP 客户端、超时、重定向和表单请求；engine 选择与原版一致。
  https://github.com/ktorio/ktor
- Coil 3.5.0：网络图片加载、内存/磁盘缓存、跨淡入和 Compose 图片渲染。
  https://github.com/coil-kt/coil
- AndroidX Media3 1.10.1：详情页视频播放与 PlayerView 渲染。
  https://github.com/androidx/media
- AndroidHiddenApiBypass 6.1：系统属性与安装器兼容逻辑的非 SDK API 访问。
  https://github.com/LSPosed/AndroidHiddenApiBypass
- HyperNotification focus-api 1.4：澎湃 OS 焦点通知/超级岛模板。由于该库以 Java 21 编译，重构版通过明确的反射适配层调用，以保留 Java 17 工程兼容性。
  https://github.com/xzakota/HyperNotification
- Shizuku：安装器保留 Shizuku Binder 协议兼容实现，并按原版注册 `rikka.shizuku.ShizukuProvider`；没有额外引入 Shizuku Maven API，避免与现有协议桥重复。
  https://github.com/RikkaApps/Shizuku

## 原版关于页保留引用的审计

`SettingsAbout.kt` 仍保留原版九项许可链接，这是原版关于页的一部分，不能因为重构版的实现方式不同而改变页面内容。当前实现结论如下：

- Koin：原版用于依赖注入；重构版使用构造参数注入，所有页面和服务的依赖已经显式传递，没有缺失用户功能，因此不添加无运行时收益的 Koin 依赖。
- ComposeMediaPlayer：原版是视频 Compose 封装；重构版已使用官方 AndroidX Media3/ExoPlayer，功能覆盖更直接且维护边界更清晰，因此不再重复引入另一套播放器封装。
- Shizuku、AndroidHiddenApiBypass、HyperNotification：原版确实依赖这些能力，重构版已经分别接入协议桥、6.1 和 focus-api 1.4，不能从审计中误删。

原版许可链接保留是为了 UI/归属对齐；Gradle 依赖是否存在以本文件“当前构建实际使用”章节和 `app/build.gradle` 为准。
