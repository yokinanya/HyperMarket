package com.hyper.market

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check

private val START_PAGE_OPTIONS = listOf("今日", "更新", "搜索")
private val START_PAGE_DIALOG_OFFSET_X = (-28.2).dp
private val START_PAGE_DIALOG_OFFSET_Y = (-153).dp
private const val START_PAGE_DIALOG_DIM_AMOUNT = 0.3f
private val START_PAGE_SELECTED_COLOR = Color(0xFF347FF5)

@Composable
internal fun StartPageDialog(
    selectedPage: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(START_PAGE_DIALOG_DIM_AMOUNT) }
        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = START_PAGE_DIALOG_OFFSET_X, y = START_PAGE_DIALOG_OFFSET_Y)
                    .width(200.dp),
                cornerRadius = 28.dp,
            ) {
                val colors = DropdownDefaults.dropdownColors(
                    selectedIndicatorColor = Color.Transparent,
                )
                Column {
                    START_PAGE_OPTIONS.forEachIndexed { index, label ->
                        StartPageOption(
                            label = label,
                            index = index,
                            selected = selectedPage == index,
                            colors = colors,
                            onSelected = onSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartPageOption(
    label: String,
    index: Int,
    selected: Boolean,
    colors: DropdownColors,
    onSelected: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(51.dp)
            .clickable { onSelected(index) },
    ) {
        DropdownImpl(
            item = DropdownItem(text = label),
            optionSize = START_PAGE_OPTIONS.size,
            isSelected = selected,
            index = index,
            dropdownColors = colors,
            isFirst = index == 0,
            isLast = index == START_PAGE_OPTIONS.lastIndex,
            onSelectedIndexChange = { onSelected(index) },
        )
        if (selected) {
            Image(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(START_PAGE_SELECTED_COLOR),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-20.2).dp, y = (-3.8).dp)
                    .size(20.dp),
            )
        }
    }
}
