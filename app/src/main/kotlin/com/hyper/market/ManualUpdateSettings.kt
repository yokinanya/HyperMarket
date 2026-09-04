package com.hyper.market

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class ManualUpdateResult(val message: String, val app: MarketAppInfo?)

@Composable
internal fun ManualUpdateCard(apiClient: XiaomiApiClient, onInstall: (MarketAppInfo) -> Unit) {
    var packageName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var updateApp by remember { mutableStateOf<MarketAppInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        ManualInput(packageName, { packageName = it }, "包名（packageName）")
        Spacer(Modifier.height(12.dp))
        ManualInput(versionCode, { versionCode = it }, "版本号（versionCode）")
        Spacer(Modifier.height(12.dp))
        FullWidthAction(if (loading) "请求中…" else "请求") {
            if (loading) return@FullWidthAction
            if (packageName.isBlank()) {
                result = "请输入包名"
                updateApp = null
                return@FullWidthAction
            }
            loading = true
            result = null
            updateApp = null
            scope.launch {
                val outcome = requestManualUpdate(apiClient, packageName, versionCode)
                result = outcome.message
                updateApp = outcome.app
                loading = false
            }
        }
        result?.let { message ->
            Text(
                message,
                color = if (updateApp != null) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.error
                },
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        updateApp?.let { app -> InstallActionPill(app, "下载并安装", onInstall) }
    }
}

private suspend fun requestManualUpdate(
    apiClient: XiaomiApiClient,
    packageName: String,
    versionCode: String,
): ManualUpdateResult = try {
    val requestedVersion = versionCode.trim().toLongOrNull()
        ?: throw IllegalArgumentException("请输入有效的版本号")
    val updates = withContext(Dispatchers.IO) {
        apiClient.loadManualUpdate(packageName.trim(), requestedVersion)
    }
    val update = updates.firstOrNull()
    ManualUpdateResult(
        update?.let { "发现更新：${it.app.versionName}（${it.app.packageName}）" }
            ?: "当前已是最新版本，或服务器未返回可用 APK",
        update?.app,
    )
} catch (exception: Exception) {
    ManualUpdateResult(exception.message ?: "检查更新失败", null)
}

@Composable
private fun ManualInput(value: String, onValueChange: (String) -> Unit, hint: String) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = hint,
        useLabelAsPlaceholder = true,
        singleLine = true,
    )
}

@Composable
private fun FullWidthAction(label: String, onClick: () -> Unit) {
    // miuix Button 默认规格：MinHeight 40dp、InsideMargin(horizontal 16, vertical 13)、圆角 16dp。
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) { Text(label, fontSize = 17.sp) }
}
