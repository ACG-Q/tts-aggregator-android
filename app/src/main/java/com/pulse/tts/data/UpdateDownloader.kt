package com.pulse.tts.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * APK 流式下载器。
 *
 * - 先写 `.part` 临时文件，完整下载并通过 SHA-256 校验后再改名为正式文件
 * - [onProgress] 参数为 0..1，服务端未给出长度时为 null（UI 用不确定进度）
 * - 任何失败都会删除临时文件，不留下半成品
 */
class UpdateDownloader(
    private val http: OkHttpClient,
    private val dir: File,
) {

    suspend fun download(
        url: String,
        abi: String,
        expectedSha256: String?,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = File(dir, "Pulse-update-$abi.apk")
        val temp = File(dir, "${target.name}.part")
        temp.delete()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UpdateClient.USER_AGENT)
                .build()
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        copyWithProgress(input, output, total, onProgress)
                    }
                }
                if (total > 0 && temp.length() != total) {
                    throw IOException("下载不完整")
                }
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actual = sha256(temp)
                if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                    throw IOException("安装包校验失败")
                }
            }

            if (target.exists() && !target.delete()) throw IOException("无法清理旧安装包")
            if (!temp.renameTo(target)) throw IOException("无法保存安装包")
            target
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (Float?) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var readTotal = 0L
        var lastEmitAt = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            readTotal += read
            val now = System.currentTimeMillis()
            if (now - lastEmitAt >= PROGRESS_INTERVAL_MS) {
                lastEmitAt = now
                onProgress(if (total > 0) (readTotal.toFloat() / total).coerceIn(0f, 1f) else null)
            }
        }
        onProgress(if (total > 0) 1f else null)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_MS = 120L
    }
}
