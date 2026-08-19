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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

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
        float2 screenUv = fragCoord / uResolution;
        float2 topCenterDistance = (screenUv - float2(0.5, 0.0)) / float2(0.58, 0.62);
        float topCenterBlue = exp(-dot(topCenterDistance, topCenterDistance));
        float2 topRightDistance = (screenUv - float2(1.0, 0.0)) / float2(0.52, 0.62);
        float topRightLight = exp(-dot(topRightDistance, topRightDistance));
        float2 lowerRightDistance = (screenUv - float2(1.08, 0.82)) / float2(0.46, 0.56);
        float lowerRightBlue = exp(-dot(lowerRightDistance, lowerRightDistance));
        float2 lowerCenterDistance = (screenUv - float2(0.5, 1.0)) / float2(0.52, 0.34);
        float lowerCenterLight = exp(-dot(lowerCenterDistance, lowerCenterDistance));
        color.rgb += topCenterBlue * float3(-0.075, -0.03, 0.025);
        color.rgb += topRightLight * float3(0.0, 0.025, 0.035);
        color.rgb += lowerRightBlue * float3(-0.18, -0.08, -0.01);
        color.rgb += lowerCenterLight * float3(-0.025, 0.05, 0.02);
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
    0.66f, 0.75f, 1.0f, 1.0f,
    1.0f, 0.86f, 0.91f, 1.0f,
    0.74f, 0.76f, 1.0f, 1.0f,
    0.97f, 0.77f, 0.84f, 1.0f,
)

@Composable
internal fun AboutGradientBackground(modifier: Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AboutRuntimeGradient(modifier)
    } else {
        Canvas(modifier) {
            drawRect(
                Brush.verticalGradient(
                    listOf(Color(0xFFECECFD), Color(0xFFFDE4EF), Color(0xFFF8F2FA)),
                ),
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun AboutRuntimeGradient(modifier: Modifier) {
    val shader = remember { RuntimeShader(AboutShader) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    var animationTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                animationTime = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }
    Canvas(modifier) {
        drawAboutShader(shader, paint, animationTime)
    }
}

private fun DrawScope.drawAboutShader(
    shader: RuntimeShader,
    paint: Paint,
    animationTime: Float,
) {
    val aspectRatio = size.height / size.width
    shader.setFloatUniform("uResolution", size.width, size.height)
    shader.setFloatUniform("uAnimTime", animationTime)
    shader.setFloatUniform("uBound", 0.0f, -0.224f, 1.0f, aspectRatio * 0.846f)
    shader.setFloatUniform("uTranslateY", 0.0f)
    shader.setFloatUniform("uPoints", AboutPoints)
    shader.setFloatUniform("uColors", AboutColors)
    shader.setFloatUniform("uAlphaMulti", 1.0f)
    shader.setFloatUniform("uNoiseScale", 1.5f)
    shader.setFloatUniform("uPointRadiusMulti", 1.0f)
    shader.setFloatUniform("uSaturateOffset", 0.2f)
    shader.setFloatUniform("uLightOffset", 0.1f)
    val pointsAnim = FloatArray(8)
    for (index in 0 until 4) {
        val offset = index * 3
        val pointX = AboutPoints[offset]
        val pointY = AboutPoints[offset + 1]
        val animatedX = kotlin.math.sin(animationTime + pointY) * 0.2f + pointX
        val animatedY = kotlin.math.cos(animationTime + animatedX) * 0.2f + pointY
        pointsAnim[index * 2] = animatedX
        pointsAnim[index * 2 + 1] = animatedY
    }
    shader.setFloatUniform("uPointsAnim", pointsAnim)
    paint.shader = shader
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
    }
}
