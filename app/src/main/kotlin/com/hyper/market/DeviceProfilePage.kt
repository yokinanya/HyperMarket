package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.DisplayMetrics
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction

private val PROFILE_CARD_HEIGHT = 55.dp
private val PROFILE_CORNER_RADIUS = 28.dp
private val PROFILE_BLUE = Color(0xFF347FF5)

@Composable
internal fun DeviceProfilePage(
    profile: MarketProfileSettings,
    onProfileChange: (MarketProfileSettings) -> Unit,
) {
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val values = remember(profile.source, profile.overrides, metrics) {
        effectiveDeviceProfile(profile, context, metrics)
    }
    var showTemplateDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 8.4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileSourceSelector(profile.source) { source ->
            onProfileChange(selectProfileSource(profile, source, values, context, metrics))
        }
        DEVICE_PROFILE_FIELDS.forEach { field ->
            ProfileInputCard(field, values[field.key].orEmpty()) { value ->
                val next = values.toMutableMap().apply { put(field.key, value) }
                onProfileChange(profile.copy(source = "custom", overrides = next, currentTemplate = ""))
            }
        }
        if (profile.source == "custom") {
            ProfileActions(
                onSave = { onProfileChange(profile.copy(source = "custom")) },
                onSaveTemplate = { showTemplateDialog = true },
            )
        }
    }
    if (showTemplateDialog) {
        SaveTemplateDialog(
            onDismiss = { showTemplateDialog = false },
            onSave = { name ->
                onProfileChange(
                    profile.copy(
                        source = "custom",
                        currentTemplate = name,
                        templates = profile.templates + (name to values),
                    ),
                )
                showTemplateDialog = false
            },
        )
    }
}

private fun selectProfileSource(
    profile: MarketProfileSettings,
    source: String,
    currentValues: Map<String, String>,
    context: android.content.Context,
    metrics: DisplayMetrics,
): MarketProfileSettings {
    if (source == "custom") {
        val values = if (profile.source == "custom") currentValues
        else effectiveDeviceProfile(profile, context, metrics)
        return profile.copy(source = source, overrides = values, currentTemplate = "")
    }
    return profile.copy(source = source, overrides = emptyMap(), currentTemplate = "")
}

@Composable
private fun ProfileSourceSelector(source: String, onSourceSelected: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    val label = when (source) {
        "custom" -> "自定义"
        "preset" -> "预设信息"
        else -> "从设备获取"
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().height(56.dp).clickable { showDialog = true },
            cornerRadius = 32.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("信息来源", fontSize = 17.sp, color = Color.Black, modifier = Modifier.offset(y = 4.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                Text(
                    label,
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.offset(x = 30.dp, y = 4.dp),
                )
                Box(
                    modifier = Modifier.size(48.dp).offset(x = 19.dp, y = 4.2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        DropdownArrowEndAction(actionColor = Color(0xFFAAAAAA))
                    }
                }
            }
        }
        if (showDialog) {
            ProfileSourceDialog(
                source = source,
                onDismiss = { showDialog = false },
                onSelected = {
                    onSourceSelected(it)
                    showDialog = false
                },
            )
        }
    }
}

@Composable
private fun ProfileInputCard(field: DeviceProfileField, value: String, onValueChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(PROFILE_CORNER_RADIUS)
    val cardModifier = Modifier
        .fillMaxWidth()
        .height(PROFILE_CARD_HEIGHT)
        .clip(shape)
        .background(Color(0xFFF1F1F1))
    val borderedModifier = if (focused) cardModifier.border(1.5.dp, PROFILE_BLUE, shape) else cardModifier
    Column(
        modifier = borderedModifier,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, lineHeight = 20.sp, color = Color.Black),
            modifier = Modifier.fillMaxWidth().height(PROFILE_CARD_HEIGHT).onFocusChanged {
                focused = it.isFocused
            },
            decorationBox = { innerTextField ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                ) {
                    Text(field.label, color = Color(0xFFAAAAAA), fontSize = 10.sp, lineHeight = 10.sp)
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ProfileActions(onSave: () -> Unit, onSaveTemplate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionPill("保存", onClick = onSave)
        ActionPill("另存为模板", onClick = onSaveTemplate)
    }
}

@Composable
private fun SaveTemplateDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("另存为模板") },
        text = {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
