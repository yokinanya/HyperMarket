package com.hyper.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyper.market.api.XiaomiApiClient
import com.hyper.market.model.MarketAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults

internal data class ManualUpdateResult(val message: String, val app: MarketAppInfo?)

@Composable
internal fun ManualUpdateCard(apiClient: XiaomiApiClient, onInstall: (MarketAppInfo) -> Unit) {
    var packageName by remember { mutableStateOf("") }
    var versionCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var updateApp by remember { mutableStateOf<MarketAppInfo?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.5.dp)) {
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
                color = if (updateApp != null) AccentBlue else Color(0xFFD14343),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFFF1F1F1)),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 18.sp, color = Color(0xFF777777)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            decorationBox = { field ->
                if (value.isEmpty()) Text(hint, color = Color(0xFFAAAAAA), fontSize = 18.sp)
                field()
            },
        )
    }
}

@Composable
private fun FullWidthAction(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(43.dp),
        cornerRadius = 32.dp,
        minWidth = 0.dp,
        minHeight = 43.dp,
        colors = ButtonDefaults.buttonColorsPrimary(),
        insideMargin = PaddingValues(0.dp),
    ) { Text(label, color = Color.White, fontSize = 18.sp) }
}
