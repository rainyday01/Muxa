package app.mtmux.core.data.diag

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * Upload diagnostic packages to LogHub (https://loginfo.xcodeuk.com).
 * Only the **upload** token belongs in the APK — never admin.
 */
object LogHubClient {
    const val BASE = "https://loginfo.xcodeuk.com"
    const val PROJECT_SLUG = "muxa"

    /**
     * Upload token (role=upload), injected at startup from the build
     * (`loghub.uploadToken` in local.properties or `MUXA_LOGHUB_UPLOAD_TOKEN`).
     * Never in source: the repository is public. Blank in community builds,
     * which then offer the GitHub issue path only.
     */
    @Volatile
    var uploadToken: String = ""

    val isConfigured: Boolean get() = uploadToken.isNotBlank()

    /** Cap log text so zip stays small (MinIO / reverse-proxy friendly). */
    private const val MAX_LOG_CHARS = 400_000

    data class UploadResult(
        val id: String,
        val status: String,
        val note: String?,
    )

    fun deviceId(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val raw = if (androidId.isNotBlank()) androidId else "unknown"
        return "muxa-" + raw.filter { it.isLetterOrDigit() }.take(16).ifEmpty { "dev" }
    }

    /**
     * Build zip: app.log + device.json + note.txt, then multipart POST.
     * [note] may be blank. Retries once on 502/503 (storage blips).
     */
    suspend fun uploadFeedback(
        context: Context,
        appVersion: String,
        note: String,
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured) { "LogHub upload token not configured" }
            val logText = cappedLog()
            val device = deviceId(context)
            val zipBytes = buildZip(
                logText = logText,
                deviceId = device,
                appVersion = appVersion,
                note = note,
            )
            var lastErr: Throwable? = null
            repeat(2) { attempt ->
                try {
                    return@runCatching postMultipart(
                        zipBytes = zipBytes,
                        deviceId = device,
                        appVersion = appVersion,
                        note = note.trim(),
                        osVersion = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "unknown",
                    )
                } catch (t: Throwable) {
                    lastErr = t
                    val msg = t.message.orEmpty()
                    val retryable = msg.contains("502") || msg.contains("503") ||
                        msg.contains("storage put", ignoreCase = true)
                    if (!retryable || attempt == 1) throw t
                    delay(800)
                }
            }
            throw lastErr ?: error("upload failed")
        }
    }

    /** The diagnostic log, trimmed from the front to [MAX_LOG_CHARS]. */
    fun cappedLog(): String {
        val rawLog = DiagLog.readAll()
        return if (rawLog.length > MAX_LOG_CHARS) {
            rawLog.takeLast(MAX_LOG_CHARS) + "\n…(truncated, last ${MAX_LOG_CHARS} chars)…\n"
        } else {
            rawLog
        }
    }

    /** app.log + device.json + note.txt, as uploaded to LogHub or saved for a GitHub issue. */
    fun buildZip(
        logText: String,
        deviceId: String,
        appVersion: String,
        note: String,
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(BufferedOutputStream(bos)).use { zos ->
            fun put(name: String, body: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(body.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("app.log", logText)
            put(
                "device.json",
                JSONObject()
                    .put("device_id", deviceId)
                    .put("app_version", appVersion)
                    .put("os", "android")
                    .put("os_version", Build.VERSION.RELEASE ?: "")
                    .put("sdk_int", Build.VERSION.SDK_INT)
                    .put("manufacturer", Build.MANUFACTURER ?: "")
                    .put("model", Build.MODEL ?: "")
                    .toString(2),
            )
            put("note.txt", note.ifBlank { "(no description)" })
        }
        return bos.toByteArray()
    }

    /**
     * Build full multipart body in memory with UTF-8 (Chinese notes must not use
     * [java.io.DataOutputStream.writeBytes], which corrupts non-ASCII and can break
     * the server parser / MinIO put).
     */
    private fun postMultipart(
        zipBytes: ByteArray,
        deviceId: String,
        appVersion: String,
        note: String,
        osVersion: String,
    ): UploadResult {
        val boundary = "----MuxaLogHub" + Random.nextLong().toULong().toString(16)
        val body = ByteArrayOutputStream()
        fun w(s: String) = body.write(s.toByteArray(Charsets.UTF_8))
        fun field(name: String, value: String) {
            w("--$boundary\r\n")
            w("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            w(value)
            w("\r\n")
        }
        field("device_id", deviceId)
        field("app_version", appVersion)
        field("os", "android")
        field("os_version", osVersion)
        if (note.isNotBlank()) field("note", note.take(2000))

        w("--$boundary\r\n")
        w("Content-Disposition: form-data; name=\"file\"; filename=\"muxa-diag.zip\"\r\n")
        w("Content-Type: application/zip\r\n\r\n")
        body.write(zipBytes)
        w("\r\n--$boundary--\r\n")
        val payload = body.toByteArray()

        val url = URL("$BASE/api/v1/projects/$PROJECT_SLUG/logs")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 90_000
            setRequestProperty("Authorization", "Bearer $uploadToken")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Content-Length", payload.size.toString())
            setFixedLengthStreamingMode(payload.size)
        }

        BufferedOutputStream(conn.outputStream).use { out ->
            out.write(payload)
            out.flush()
        }

        val code = conn.responseCode
        val respBody = runCatching {
            (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
        }.getOrDefault("")
        conn.disconnect()

        if (code !in 200..299) {
            error("LogHub HTTP $code: ${respBody.take(400)}")
        }
        val json = JSONObject(respBody)
        return UploadResult(
            id = json.optString("id", ""),
            status = json.optString("status", ""),
            note = if (json.has("note") && !json.isNull("note")) json.optString("note") else null,
        )
    }

    fun markUploaded(context: Context, id: String) {
        runCatching {
            File(context.filesDir, "diag/last-upload-id.txt").writeText(id)
        }
    }
}
