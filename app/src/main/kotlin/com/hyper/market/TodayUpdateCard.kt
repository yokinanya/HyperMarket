package com.hyper.market

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card

internal sealed interface TodayUpdatesState {
    data object Loading : TodayUpdatesState
    data class Loaded(val updates: List<UpdateInfo>) : TodayUpdatesState
    data class Failed(val message: String) : TodayUpdatesState
}

@Composable
internal fun TodayUpdateCard(
    state: TodayUpdatesState,
    onOpenUpdates: () -> Unit,
) {
    val updates = (state as? TodayUpdatesState.Loaded)?.updates.orEmpty()
    AnimatedVisibility(
        visible = updates.isNotEmpty(),
        enter = expandVertically(tween(420), expandFrom = Alignment.Top) +
            slideInVertically(tween(420)) { -it / 5 } + fadeIn(tween(260)),
        exit = shrinkVertically(tween(260), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().height(TODAY_UPDATE_CARD_HEIGHT),
            cornerRadius = TODAY_UPDATE_CARD_RADIUS,
        ) {
            UpdateCardContent(updates, onOpenUpdates)
        }
    }
}

@Composable
private fun UpdateCardContent(updates: List<UpdateInfo>, onOpenUpdates: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${updates.size} 个应用待更新",
                color = Color(0xFF202020),
                style = todayUpdateTextStyle(18.sp, 22.sp),
            )
            ActionPill("查看", onOpenUpdates)
        }
        UpdateIconGrid(updates)
    }
}

@Composable
private fun UpdateIconGrid(updates: List<UpdateInfo>) {
    val rows = updates.withIndex().partition { it.index % 2 == 0 }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        MarqueeIconRow(rows.first.map { it.value }, reverse = false)
        MarqueeIconRow(rows.second.map { it.value }, reverse = true)
    }
}

@Composable
private fun MarqueeIconRow(updates: List<UpdateInfo>, reverse: Boolean) {
    if (updates.isEmpty()) return
    val stride = TODAY_UPDATE_ICON_SIZE + TODAY_UPDATE_ICON_SPACING
    val cycleWidth = with(LocalDensity.current) { (stride * updates.size).toPx() }
    val duration = updates.size * MILLIS_PER_ICON
    val progress = rememberInfiniteTransition(label = "today-update-marquee").animateFloat(
        initialValue = 0f,
        targetValue = cycleWidth,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "today-update-offset",
    ).value
    val x = if (reverse) progress - cycleWidth else -progress
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().height(TODAY_UPDATE_ICON_SIZE).clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .offset { IntOffset(x.toInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(TODAY_UPDATE_ICON_SPACING),
        ) {
            (updates + updates).forEach { update ->
                RemoteAppIcon(
                    url = update.app.iconUrl,
                    label = update.app.displayName,
                    modifier = Modifier.size(TODAY_UPDATE_ICON_SIZE),
                )
            }
        }
    }
}

private fun todayUpdateTextStyle(fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =
    TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

private val TODAY_UPDATE_CARD_HEIGHT = 235.dp
private val TODAY_UPDATE_CARD_RADIUS = 32.dp
private val TODAY_UPDATE_ICON_SIZE = 64.dp
private val TODAY_UPDATE_ICON_SPACING = 16.dp
private const val MILLIS_PER_ICON = 2_000
