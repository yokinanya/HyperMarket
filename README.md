# HyperMarket

这是一个面向 Android 的 HyperMarket 重构工程。目标是根据原版应用的实际行为、界面和交互逐项恢复功能；源码使用清晰命名，并优先使用 Compose、Miuix 等已有组件。

仓库根目录就是 Gradle Android 工程：

- `app/`：应用源码、资源和运行所需 native 库。
- `AGENT.md`：当前实现状态、已知差异、测试约定和后续交接说明。
- `THIRD_PARTY_NOTICES.md`：界面中列出的开源项目及本工程使用的第三方项目。
- `base-reverse/`、`base.apk`：本地逆向和原版提取资料，不属于仓库内容；已加入忽略规则。原始 APK 不应提交到 GitHub。

## 构建

推荐使用仓库内 Gradle Wrapper：

```bash
./gradlew :app:assembleDebug
```

debug/release 变体统一使用 `org.hyper.market`，可以和原版 `com.hyper.market` 并行安装，不需要在完成复刻后再改包名。

测试时应明确使用重构包名 `org.hyper.market`，避免把原版 `com.hyper.market` 与重构版混淆。安装包和 `app/build/` 均为生成物，不提交到仓库。
