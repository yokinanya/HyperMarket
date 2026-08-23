package com.hyper.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.model.UpdateInfo
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    if (updates.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = TODAY_UPDATE_CARD_RADIUS,
    ) {
        UpdateCardContent(updates, onOpenUpdates)
    }
}

@Composable
private fun UpdateCardContent(
    updates: List<UpdateInfo>,
    onOpenUpdates: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "${updates.size} 个应用待更新",
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = todayUpdateTextStyle(20.sp, 24.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ActionPill("查看", onOpenUpdates)
    }
}


private fun todayUpdateTextStyle(fontSize: androidx.compose.ui.unit.TextUnit, lineHeight: androidx.compose.ui.unit.TextUnit) =
    TextStyle(
        fontSize = fontSize,
        lineHeight = lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

private val TODAY_UPDATE_CARD_RADIUS = 32.dp
