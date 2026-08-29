package com.hyper.market

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.PI
import kotlin.math.pow

/**
 * 动效标准：
 * - 页面过渡完全交给 miuix-nav 官方 NavDisplay（NavTransitions.MiuixDefault 深度栈转场），
 *   不再自研转场参数；
 * - 局部弹簧统一为 folme 风格临界阻尼（damping = 1.0，response 为自然周期秒）。
 */

/** folme 风格临界阻尼弹簧（dampingRatio = 1，stiffness 由 response 换算）。 */
internal inline fun <reified T> folmeSpring(response: Float): SpringSpec<T> =
    spring(dampingRatio = 1f, stiffness = (2.0 * PI / response).pow(2.0).toFloat())

/** 列表项重排弹簧（LazyColumn animateItem 用）。 */
internal inline fun <reified T> listSpring(): SpringSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 300f)
