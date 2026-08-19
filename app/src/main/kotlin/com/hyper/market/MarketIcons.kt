package com.hyper.market

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object MarketIcons {
    val Today: ImageVector by lazy {
        ImageVector.Builder(
            name = "Today",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            curveTo(10f, 0.8f, 9f, 1.3f, 9.2f, 5.5f)
            curveTo(7.2f, 3.1f, 4.4f, 2.7f, 3.5f, 4.3f)
            curveTo(3.6f, 10.5f, 6.2f, 14.5f, 12f, 15f)
            curveTo(17.8f, 14.5f, 20.4f, 10.5f, 20.5f, 4.3f)
            curveTo(19.6f, 2.7f, 16.8f, 3.1f, 14.8f, 5.5f)
            curveTo(15f, 1.3f, 14f, 0.8f, 12f, 3f)
            close()
            moveTo(12f, 15f)
            lineTo(12f, 20.5f)
            moveTo(12f, 18f)
            curveTo(9.1f, 16.2f, 7f, 16.7f, 5.4f, 19.1f)
            moveTo(12f, 18.5f)
            curveTo(14.9f, 16.7f, 17f, 17.2f, 18.6f, 19.6f)
        }.build()
    }
}
