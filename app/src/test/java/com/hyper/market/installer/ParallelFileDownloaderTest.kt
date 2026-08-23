package com.hyper.market.installer

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParallelFileDownloaderTest {
    @Test
    fun downloadsFourRangesAndMergesThem() {
        val content = ByteArray(TEST_SIZE) { index -> (index % BYTE_PATTERN).toByte() }
        val server = RangeServer(content)
        val directory = Files.createTempDirectory("parallel-download").toFile()
        val target = directory.resolve("payload.part")
        try {
            val downloaded = ParallelFileDownloader(emptyMap()).tryDownload(
                target,
                "http://127.0.0.1:${server.port}/payload",
                content.size.toLong(),
                DownloadControl(),
                null,
            )
            assertEquals(true, downloaded)
            assertArrayEquals(content, target.readBytes())
            assertEquals(THREADS_PLUS_PROBE, server.ranges.size)
            assertEquals(0, directory.listFiles { file -> file.name.contains("segment-") }?.size)
        } finally {
            server.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun pausesAndResumesFourSegments() {
        val content = ByteArray(TEST_SIZE) { index -> (index % BYTE_PATTERN).toByte() }
        val server = RangeServer(content, TEST_CHUNK_SIZE, TEST_CHUNK_DELAY_MS)
        val directory = Files.createTempDirectory("parallel-resume").toFile()
        val target = directory.resolve("payload.part")
        val control = DownloadControl()
        val paused = CountDownLatch(1)
        val pauseOnce = AtomicBoolean()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val future = worker.submit<Boolean> {
                ParallelFileDownloader(emptyMap()).tryDownload(
                    target, "http://127.0.0.1:${server.port}/payload",
                    content.size.toLong(), control,
                ) { downloaded, _ ->
                    if (downloaded >= PAUSE_AFTER_BYTES && pauseOnce.compareAndSet(false, true)) {
                        control.pause()
                        paused.countDown()
                    }
                }
            }
            assertTrue(paused.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertTrue(segmentBytes(directory) in 1 until content.size.toLong())
            control.resume()
            assertTrue(future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertArrayEquals(content, target.readBytes())
            assertTrue(server.ranges.size > THREADS_PLUS_PROBE)
        } finally {
            control.cancel()
            worker.shutdownNow()
            server.close()
            directory.deleteRecursively()
        }
    }

    private class RangeServer(
        private val content: ByteArray,
        private val chunkSize: Int = Int.MAX_VALUE,
        private val chunkDelayMs: Long = 0,
    ) : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        private val workers = Executors.newCachedThreadPool()
        private val acceptThread = thread(name = "range-test-server") { acceptRequests() }
        val ranges: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val port: Int get() = socket.localPort

        private fun acceptRequests() {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    workers.submit { handle(client) }
                } catch (_: java.net.SocketException) {
                    return
                }
            }
        }

        private fun handle(client: Socket) {
            client.use { connection ->
                val range = readRange(connection)
                ranges += range
                val (start, end) = parseRange(range, content.lastIndex)
                val length = end - start + 1
                val header = "HTTP/1.1 206 Partial Content\r\n" +
                    "Content-Length: $length\r\n" +
                    "Content-Range: bytes $start-$end/${content.size}\r\n" +
                    "Connection: close\r\n\r\n"
                connection.getOutputStream().use { output ->
                    output.write(header.toByteArray(Charsets.US_ASCII))
                    var offset = start
                    while (offset <= end) {
                        val count = minOf(chunkSize, end - offset + 1)
                        output.write(content, offset, count)
                        output.flush()
                        offset += count
                        if (chunkDelayMs > 0) Thread.sleep(chunkDelayMs)
                    }
                }
            }
        }

        private fun readRange(client: Socket): String {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            while (true) {
                val line = reader.readLine() ?: error("请求头提前结束")
                if (line.isEmpty()) error("请求缺少 Range 头")
                if (line.startsWith("Range:", ignoreCase = true)) {
                    val range = line.substringAfter(':').trim()
                    while (reader.readLine().isNotEmpty()) { }
                    return range
                }
            }
        }

        override fun close() {
            socket.close()
            acceptThread.join(TEST_THREAD_JOIN_MS)
            workers.shutdownNow()
        }
    }

    private companion object {
        const val TEST_SIZE = 8 * 1024 * 1024 + 13
        const val BYTE_PATTERN = 251
        const val THREADS_PLUS_PROBE = 5
        const val TEST_THREAD_JOIN_MS = 1_000L
        const val TEST_CHUNK_SIZE = 32 * 1024
        const val TEST_CHUNK_DELAY_MS = 2L
        const val PAUSE_AFTER_BYTES = 512 * 1024L
        const val TEST_TIMEOUT_SECONDS = 10L

        fun segmentBytes(directory: java.io.File): Long =
            directory.listFiles { file -> file.name.contains("segment-") }
                ?.sumOf { it.length() } ?: 0

        fun parseRange(value: String, lastIndex: Int): Pair<Int, Int> {
            val bounds = value.removePrefix("bytes=").split("-", limit = 2)
            val start = bounds.first().toInt()
            val end = bounds.last().ifBlank { lastIndex.toString() }.toInt()
            return start to end
        }
    }
}
