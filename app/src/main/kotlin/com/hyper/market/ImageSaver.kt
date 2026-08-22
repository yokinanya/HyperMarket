package com.hyper.market

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ImageSaver {
    suspend fun save(context: Context, url: String): String = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context).data(url).build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = if (result is SuccessResult) {
            result.image.toBitmap()
        } else {
            val cause = (result as? ErrorResult)?.throwable
            throw IllegalStateException("图片下载失败：$url", cause)
        }
        if (Build.VERSION.SDK_INT >= 29) saveMediaStore(context, bitmap) else saveLegacy(context, bitmap)
    }

    private fun saveMediaStore(context: Context, bitmap: Bitmap): String {
        val name = imageName()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HyperMarket")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("无法创建图片保存记录")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("图片编码失败")
                }
            } ?: throw IllegalStateException("无法打开图片保存目标")
            val published = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            if (resolver.update(uri, published, null, null) != 1) {
                throw IllegalStateException("无法发布保存的图片")
            }
            return uri.toString()
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    private fun saveLegacy(context: Context, bitmap: Bitmap): String {
        if (context.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Android 10 以下保存图片需要存储权限")
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "HyperMarket",
        )
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("无法创建 Pictures/HyperMarket 目录")
        }
        val file = File(directory, imageName())
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("图片编码失败")
            }
        }
        return file.absolutePath
    }

    private fun imageName(): String = "hypermarket-${System.currentTimeMillis()}.png"
}
