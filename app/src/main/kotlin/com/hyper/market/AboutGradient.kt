package com.hyper.market

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AboutShader = """
    uniform float2 uResolution;
    uniform float uAnimTime;
    uniform float4 uBound;
    uniform float uTranslateY;
    uniform float3 uPoints[4];
    uniform float2 uPointsAnim[4];
    uniform float4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    float3 rgb2hsv(float3 c) {
        float4 K = float4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        float4 p = mix(float4(c.bg, K.wz), float4(c.gb, K.xy), step(c.b, c.g));
        float4 q = mix(float4(p.xyw, c.r), float4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return float3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    float3 hsv2rgb(float3 c) {
        float4 K = float4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        float3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(float2 p) {
        float3 p3 = fract(float3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(float2 x) {
        float2 i = floor(x);
        float2 f = fract(x);
        float a = hash(i);
        float b = hash(i + float2(1.0, 0.0));
        float c = hash(i + float2(0.0, 1.0));
        float d = hash(i + float2(1.0, 1.0));
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(float2 uv) {
        return fract(52.9829189 * fract(dot(uv, float2(0.06711056, 0.00583715))));
    }

    half4 main(float2 fragCoord) {
        float2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        float2 uv = vUv;
        uv -= float2(0.0, uTranslateY);
        uv -= uBound.xy;
        uv /= uBound.zw;

        float4 color = float4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + float2(-uAnimTime, -uAnimTime));
        for (int i = 0; i < 4; i++) {
            float4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            float2 point = uPointsAnim[i];
            float radius = uPoints[i].z * uPointRadiusMulti;
            float distanceToPoint = distance(uv, point);
            float percentage = smoothstep(radius, 0.0, distanceToPoint);
            color.rgb = mix(color.rgb, pointColor.rgb, percentage);
            color.a = mix(color.a, pointColor.a, percentage);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= color.a;
        float3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;
        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti;
        color += (10.0 / 255.0) * gradientNoise(fragCoord) - (5.0 / 255.0);
        return half4(color.rgb * color.a, color.a);
    }
"""

private val AboutPoints = floatArrayOf(
    0.8f, 0.2f, 1.0f,
    0.8f, 0.9f, 1.0f,
    0.2f, 0.9f, 1.0f,
    0.2f, 0.2f, 1.0f,
)

private val AboutColors = floatArrayOf(
    1.0f, 0.82f, 0.9f, 1.0f,
    0.86f, 0.82f, 1.0f, 1.0f,
    1.0f, 0.7f, 0.84f, 1.0f,
    0.56f, 0.64f, 1.0f, 1.0f,
)

private val AboutDarkColors = floatArrayOf(
    0.12f, 0.12f, 0.22f, 1.0f,
    0.22f, 0.12f, 0.22f, 1.0f,
    0.20f, 0.10f, 0.18f, 1.0f,
    0.08f, 0.14f, 0.24f, 1.0f,
)

private data class AboutShaderResources(
    val shader: RuntimeShader,
    val paint: Paint,
    val animatedPoints: FloatArray,
)

@Composable
internal fun AboutGradientBackground(modifier: Modifier) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AboutRuntimeGradient(modifier, isDark)
    } else {
        Canvas(modifier) {
            drawRect(
                Brush.verticalGradient(
                    if (isDark) ABOUT_DARK_FALLBACK_COLORS else ABOUT_LIGHT_FALLBACK_COLORS,
                ),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutRuntimeGradient(modifier: Modifier, isDark: Boolean) {
    val resources = remember(isDark) {
        AboutShaderResources(
            shader = RuntimeShader(AboutShader),
            paint = Paint(Paint.ANTI_ALIAS_FLAG),
            animatedPoints = FloatArray(ANIMATED_POINT_COMPONENTS),
        )
    }
    val animationsEnabled = systemAnimationsEnabled()
    var animationTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                animationTime = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier) {
        drawAboutShader(resources, animationTime, isDark)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun DrawScope.drawAboutShader(
    resources: AboutShaderResources,
    animationTime: Float,
    isDark: Boolean,
) {
    val shader = resources.shader
    val aspectRatio = size.height / size.width
    shader.setFloatUniform("uResolution", size.width, size.height)
    shader.setFloatUniform("uAnimTime", animationTime)
    shader.setFloatUniform("uBound", 0.0f, -0.667f, 1.0f, aspectRatio)
    shader.setFloatUniform("uTranslateY", 0.0f)
    shader.setFloatUniform("uPoints", AboutPoints)
    shader.setFloatUniform("uColors", if (isDark) AboutDarkColors else AboutColors)
    shader.setFloatUniform("uAlphaMulti", 1.0f)
    shader.setFloatUniform("uNoiseScale", 1.5f)
    shader.setFloatUniform("uPointRadiusMulti", 1.0f)
    shader.setFloatUniform("uSaturateOffset", 0.2f)
    shader.setFloatUniform("uLightOffset", 0.1f)
    for (index in 0 until 4) {
        val offset = index * 3
        val pointX = AboutPoints[offset]
        val pointY = AboutPoints[offset + 1]
        val animatedX = kotlin.math.sin(animationTime + pointY) * 0.2f + pointX
        val animatedY = kotlin.math.cos(animationTime + animatedX) * 0.2f + pointY
        resources.animatedPoints[index * 2] = animatedX
        resources.animatedPoints[index * 2 + 1] = animatedY
    }
    shader.setFloatUniform("uPointsAnim", resources.animatedPoints)
    resources.paint.shader = shader
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, resources.paint)
    }
}

private const val ANIMATED_POINT_COMPONENTS = 8

private val ABOUT_LIGHT_FALLBACK_COLORS = listOf(
    Color(0xFFECECFD), Color(0xFFFDE4EF), Color(0xFFF8F2FA),
)

private val ABOUT_DARK_FALLBACK_COLORS = listOf(
    Color(0xFF171823), Color(0xFF241925), Color(0xFF12131C),
)
