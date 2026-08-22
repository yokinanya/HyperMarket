package com.hyper.market

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNameFormatterTest {
    @Test
    fun removesPromotionSuffixAfterSupportedSeparators() {
        assertEquals("哔哩哔哩", optimizedAppName("哔哩哔哩-弹幕视频", true))
        assertEquals("高德地图", optimizedAppName("高德地图－导航出行", true))
        assertEquals("QQ", optimizedAppName("QQ | 社交聊天", true))
    }

    @Test
    fun keepsOriginalNameWhenOptimizationIsDisabled() {
        val name = "应用-官方版本"
        assertEquals(name, optimizedAppName(name, false))
    }

    @Test
    fun keepsNameWhenNoPromotionSeparatorExists() {
        assertEquals("应用商店", optimizedAppName("应用商店", true))
    }
}
