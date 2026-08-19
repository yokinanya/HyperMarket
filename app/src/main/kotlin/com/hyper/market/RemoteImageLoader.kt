package com.hyper.market

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.delay

internal class RemoteImageLoader(context: Context) {
    private val cacheDirectory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }

    suspend fun load(url: String): BitmapLoadResult {
        val key = digest(url)
        memoryCache.get(key)?.let { return BitmapLoadResult.loaded(it) }
        readDisk(key)?.let {
            memoryCache.put(key, it)
            return BitmapLoadResult.loaded(it)
        }
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val bitmap = download(url)
                memoryCache.put(key, bitmap)
                writeDisk(key, bitmap)
                return BitmapLoadResult.loaded(bitmap)
            } catch (exception: IOException) {
                lastError = exception
                if (attempt + 1 < MAX_ATTEMPTS) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return BitmapLoadResult.failed(lastError?.message ?: "图片下载失败")
    }

    private fun readDisk(key: String): Bitmap? {
        val file = File(cacheDirectory, key)
        if (!file.isFile) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun writeDisk(key: String, bitmap: Bitmap) {
        val target = File(cacheDirectory, key)
        val temporary = File(cacheDirectory, "$key.part")
        try {
            FileOutputStream(temporary).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (!temporary.renameTo(target)) temporary.delete()
        } catch (_: IOException) {
            temporary.delete()
        }
    }

    private fun download(url: String): Bitmap {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", ACCEPT_HEADER)
        return try {
            if (connection.responseCode !in HTTP_SUCCESS) {
                throw IOException("图片请求失败：HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input) ?: throw IOException("图片内容无法解码")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString(HEX_SEPARATOR) { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val CACHE_DIRECTORY = "remote-images"
        const val MAX_MEMORY_BYTES = 20 * 1024 * 1024
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 350L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "Dalvik/2.1.0 (Linux; Android 16)"
        const val ACCEPT_HEADER = "image/avif,image/webp,image/*,*/*;q=0.8"
        const val HEX_SEPARATOR = ""
        val HTTP_SUCCESS = 200..299
        val memoryCache = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }
}

internal data class BitmapLoadResult(val bitmap: Bitmap?, val status: String) {
    companion object {
        fun loading() = BitmapLoadResult(null, "加载中")
        fun loaded(bitmap: Bitmap) = BitmapLoadResult(bitmap, "")
        fun failed(message: String) = BitmapLoadResult(null, "加载失败：$message")
    }
}
