package com.hyper.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.DisplayMetrics
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun DeviceProfilePage(
    profile: MarketProfileSettings,
    onProfileChange: (MarketProfileSettings) -> Unit,
) {
    val context = LocalContext.current
    val metrics = LocalResources.current.displayMetrics
    var draftValues by remember(profile.source, profile.overrides, metrics) {
        mutableStateOf(effectiveDeviceProfile(profile, context, metrics))
    }
    var showTemplateDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.padding(top = 8.4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileSourceSelector(profile.source) { source ->
            onProfileChange(selectProfileSource(profile, source, draftValues, context, metrics))
        }
        DEVICE_PROFILE_FIELDS.forEach { field ->
            ProfileInputCard(field, draftValues[field.key].orEmpty()) { value ->
                draftValues = draftValues + (field.key to value)
            }
        }
        if (profile.source == "custom") {
            ProfileActions(
                onSave = {
                    onProfileChange(
                        profile.copy(source = "custom", overrides = draftValues, currentTemplate = ""),
                    )
                },
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
                        overrides = draftValues,
                        templates = profile.templates + (name to draftValues),
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
        ArrowPreference(
            title = "信息来源",
            endActions = { Text(label) },
            onClick = { showDialog = true },
        )
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
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = field.label,
        singleLine = true,
    )
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
    WindowDialog(
        show = true,
        title = "另存为模板",
        onDismissRequest = onDismiss,
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = "模板名称",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "保存",
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
