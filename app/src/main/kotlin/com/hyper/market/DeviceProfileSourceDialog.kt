package com.hyper.market

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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

private val SOURCE_DIALOG_X_OFFSET = (-28.2).dp
private val SOURCE_DIALOG_Y_OFFSET = 156.5.dp
private val SOURCE_SELECTED_COLOR = Color(0xFF347FF5)
private const val SOURCE_DIALOG_DIM_AMOUNT = 0.3f
private val SOURCE_OPTIONS = listOf(
    "custom" to "自定义",
    "preset" to "预设信息",
    "device" to "从设备获取",
)

@Composable
internal fun ProfileSourceDialog(
    source: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(SOURCE_DIALOG_DIM_AMOUNT) }
        Box(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset(x = SOURCE_DIALOG_X_OFFSET, y = SOURCE_DIALOG_Y_OFFSET)
                    .width(200.dp),
                cornerRadius = 28.dp,
            ) {
                val colors = DropdownDefaults.dropdownColors(
                    selectedIndicatorColor = Color.Transparent,
                )
                Column {
                    SOURCE_OPTIONS.forEachIndexed { index, (key, label) ->
                        SourceOptionRow(
                            key = key,
                            label = label,
                            selected = key == source,
                            index = index,
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
private fun SourceOptionRow(
    key: String,
    label: String,
    selected: Boolean,
    index: Int,
    colors: DropdownColors,
    onSelected: (String) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(key) },
    ) {
        DropdownImpl(
            item = DropdownItem(text = label),
            optionSize = SOURCE_OPTIONS.size,
            isSelected = selected,
            index = index,
            dropdownColors = colors,
            isFirst = index == 0,
            isLast = index == SOURCE_OPTIONS.lastIndex,
            onSelectedIndexChange = { onSelected(key) },
        )
        if (selected) {
            Image(
                imageVector = MiuixIcons.Basic.Check,
                contentDescription = null,
                colorFilter = ColorFilter.tint(SOURCE_SELECTED_COLOR),
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(x = (-20.2).dp, y = (-3.8).dp)
                    .size(20.dp),
            )
        }
    }
}
