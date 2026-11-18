package com.isanabanana.aim

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Small asynchronous HTTPS JSON client for game data APIs.
 *
 * Use a protected backend/API key with limited permissions. Never put a database
 * password or a privileged service-role key in a game APK.
 */
class OnlineData(
    baseUrl: String,
    private val apiKey: String? = null,
    private val accessToken: String? = null,
    private val timeoutMillis: Int = 10_000
) {
    private val baseUrl: String

    init {
        val parsed = URL(baseUrl.trim().trimEnd('/') + "/")
        require(parsed.protocol.equals("https", ignoreCase = true)) {
            "OnlineData requires an HTTPS base URL."
        }
        require(parsed.host.isNotBlank()) { "A valid server URL is required." }
        require(timeoutMillis in 1_000..60_000) { "Timeout must be between 1000 and 60000 ms." }
        this.baseUrl = parsed.toExternalForm().trimEnd('/')
        require(apiKey == null || apiKey.isNotBlank()) { "API key cannot be blank." }
        require(accessToken == null || accessToken.isNotBlank()) { "Access token cannot be blank." }
    }

    /** Read JSON or text from an API endpoint. */
    fun get(path: String, onResult: (OnlineDataResult) -> Unit) =
        request("GET", path, null, onResult)

    /** Create data by sending a JSON body. */
    fun post(path: String, json: String, onResult: (OnlineDataResult) -> Unit) =
        request("POST", path, json, onResult)

    /** Update data by sending a JSON body. */
    fun put(path: String, json: String, onResult: (OnlineDataResult) -> Unit) =
        request("PUT", path, json, onResult)

    /** Delete data at an API endpoint. */
    fun delete(path: String, onResult: (OnlineDataResult) -> Unit) =
        request("DELETE", path, null, onResult)

    private fun request(
        method: String,
        path: String,
        json: String?,
        onResult: (OnlineDataResult) -> Unit
    ) {
        require(path.isNotBlank()) { "Endpoint path cannot be blank." }
        require(!path.startsWith("http://", true) && !path.startsWith("https://", true)) {
            "Pass an endpoint path, not a full URL."
        }
        val endpoint = URL("$baseUrl/${path.trimStart('/')}")
        require(endpoint.protocol.equals("https", ignoreCase = true) && endpoint.host == URL(baseUrl).host) {
            "Endpoint must stay on the configured HTTPS server."
        }

        executor.execute {
            val result = try {
                performRequest(method, endpoint, json)
            } catch (error: Exception) {
                OnlineDataResult(
                    success = false,
                    statusCode = 0,
                    data = "",
                    error = error.message ?: "Network request failed."
                )
            }
            mainHandler.post { onResult(result) }
        }
    }

    private fun performRequest(method: String, endpoint: URL, json: String?): OnlineDataResult {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            useCaches = false
            setRequestProperty("Accept", "application/json")
            apiKey?.let { setRequestProperty("apikey", it) }
            (accessToken ?: apiKey)?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (json != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        return try {
            if (json != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (status in 200..299) {
                OnlineDataResult(true, status, body, null)
            } else {
                OnlineDataResult(false, status, body, "Server returned HTTP $status.")
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private val executor = Executors.newCachedThreadPool()
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}

/** Result delivered on Android's main thread after an online request completes. */
data class OnlineDataResult(
    val success: Boolean,
    val statusCode: Int,
    val data: String,
    val error: String?
)
