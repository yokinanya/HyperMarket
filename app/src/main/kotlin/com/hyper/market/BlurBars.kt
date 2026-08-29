package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 栏级实时模糊（miuix-blur textureBlur，MGAide 同款配置）：
 * API 33+（RuntimeShader 可用）启用，低版本自动降级纯色底。
 *
 * 关键结构约束（MGAide 已踩坑）：模糊栏必须与 blurSource 内容盒互为【同级兄弟】——
 * 把模糊栏放进内容盒内部，textureBlur 采样自身所在 backdrop 会无限递归，
 * 在 SkiaDisplayList::prepareListAndChildren 触发 native 栈溢出闪退。
 */
internal class BarBlur internal constructor(
    val backdrop: LayerBackdrop?,
    val colors: BlurColors?,
) {
    val enabled: Boolean get() = backdrop != null && colors != null
}

@Composable
internal fun rememberBarBlur(): BarBlur {
    if (!isRuntimeShaderSupported()) return BarBlur(null, null)
    val surface = MiuixTheme.colorScheme.surface
    val backdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }
    val colors = BlurDefaults.blurColors(
        blendColors = listOf(BlendColorEntry(surface.copy(alpha = 0.7f), BlurBlendMode.SrcOver)),
        brightness = 0f,
        contrast = 1f,
        saturation = 1.2f,
    )
    return BarBlur(backdrop, colors)
}

/** 内容盒采样源：blur 可用时把当前节点注册进 backdrop（模糊栏的同级兄弟）。 */
internal fun Modifier.blurSource(blur: BarBlur): Modifier =
    if (blur.enabled) layerBackdrop(blur.backdrop!!) else this

/** 模糊栏材质：enabled 时挂 textureBlur，低版本降级纯色底。colorsOverride 可换混合配色（如 background 页）。 */
internal fun Modifier.barBlurMaterial(
    blur: BarBlur,
    fallback: Color,
    colorsOverride: BlurColors? = null,
): Modifier =
    blurMaterial(blur, RectangleShape, fallback, colorsOverride = colorsOverride)

/** 模糊材质（可指定形状/半径，卡片等毛玻璃用）：enabled 时挂 textureBlur，低版本降级纯色底。 */
internal fun Modifier.blurMaterial(
    blur: BarBlur,
    shape: Shape,
    fallback: Color,
    blurRadius: Float = 25f,
    colorsOverride: BlurColors? = null,
): Modifier =
    if (blur.enabled) {
        textureBlur(
            backdrop = blur.backdrop!!,
            shape = shape,
            blurRadius = blurRadius,
            colors = colorsOverride ?: blur.colors!!,
        )
    } else {
        background(fallback, shape)
    }
