package com.lecheeel.memento.network

import android.util.Log
import com.lecheeel.memento.crypto.CryptoManager
import com.lecheeel.memento.data.AppSettings
import com.lecheeel.memento.data.NotificationEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class UploadResult(
    val success: Boolean,
    val responseCode: Int,
    val message: String,
)

class NotificationUploader {
    companion object {
        private const val TAG = "MementoUpload"
    }

    fun upload(events: List<NotificationEvent>, settings: AppSettings): UploadResult {
        if (events.isEmpty()) {
            return UploadResult(true, 204, "empty")
        }
        val url = URL(settings.serverBaseUrl.trimEnd('/') + "/ingest")
        Log.d(TAG, "uploading batch size=${events.size} url=$url")
        val crypto = CryptoManager(settings.encryptionSecret)
        val payloadJson = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("eventCount", events.size)
            put("events", JSONArray().apply {
                events.forEach { put(it.toJson()) }
            })
        }.toString()
        val encrypted = crypto.encrypt(
            plainText = payloadJson,
            associatedData = settings.authToken
        )

        val requestBody = JSONObject().apply {
            put("deviceId", settings.authToken)
            put("timestamp", System.currentTimeMillis())
            put("iv", encrypted.ivBase64)
            put("ciphertext", encrypted.ciphertextBase64)
            put("signature", encrypted.hmacBase64)
        }.toString()

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Device-Token", settings.authToken)
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }
            val code = connection.responseCode
            val responseMessage = connection.responseMessage ?: "unknown"
            Log.d(TAG, "upload response code=$code message=$responseMessage")
            UploadResult(code in 200..299, code, responseMessage)
        } catch (t: Throwable) {
            Log.w(TAG, "upload error: ${t.message ?: t.javaClass.simpleName}")
            UploadResult(false, -1, t.message ?: t.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }
}

