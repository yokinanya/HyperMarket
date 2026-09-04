package com.hyper.market

import com.hyper.market.model.MarketAppInfo
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * miuix-nav 路由键（官方 miuix-nav NavDisplay 标准结构，见 miuix 指南 miuix-nav 章节）。
 * 主 Tab 页为栈底常驻场景；二级页携带自身载荷入栈，转场/返回由 NavDisplay 标准驱动。
 */
internal sealed interface MarketNav : NavKey {
    data object Tabs : MarketNav
    data class Detail(val app: MarketAppInfo) : MarketNav
    data class Article(val resourceId: String) : MarketNav
    data class Settings(val destination: SettingsDestination) : MarketNav
}
