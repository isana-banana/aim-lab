package com.isanabanana.aim

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small WebSocket connection for real-time multiplayer messages.
 * Messages are UTF-8 text (normally JSON). Callbacks are delivered on Android's main thread.
 */
class RealtimeConnection internal constructor(
    private val request: Request,
    private val listener: RealtimeListener,
    private val autoReconnect: Boolean,
    private val maxReconnectDelayMillis: Long,
    private val client: OkHttpClient,
    private val mainHandler: Handler
) {
    @Volatile private var socket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private val closedByUser = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private var reconnectScheduled = false

    init { openSocket() }

    val isConnected: Boolean get() = connected.get()

    /** Send one text message. Returns false if the socket is not ready or the queue is full. */
    fun send(message: String): Boolean = isConnected && socket?.send(message) == true

    /** Close permanently and stop automatic reconnection. */
    fun close() {
        closedByUser.set(true)
        connected.set(false)
        mainHandler.removeCallbacks(reconnectTask)
        socket?.close(1000, "Client closed")
    }

    /** Immediately cancel permanently and stop automatic reconnection. */
    fun cancel() {
        closedByUser.set(true)
        connected.set(false)
        mainHandler.removeCallbacks(reconnectTask)
        socket?.cancel()
    }

    private val reconnectTask = Runnable {
        synchronized(this) { reconnectScheduled = false }
        if (!closedByUser.get()) openSocket()
    }

    @Synchronized
    private fun openSocket() {
        if (closedByUser.get() || connected.get()) return
        val current = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (closedByUser.get()) {
                    webSocket.close(1000, "Client closed")
                    return
                }
                synchronized(this@RealtimeConnection) {
                    socket = webSocket
                    reconnectAttempt = 0
                }
                connected.set(true)
                mainHandler.post {
                    if (!closedByUser.get() && socket === webSocket) listener.onConnected?.invoke()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket === webSocket && !closedByUser.get()) {
                    mainHandler.post {
                        if (!closedByUser.get() && socket === webSocket) listener.onMessage?.invoke(text)
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket !== webSocket) return
                connected.set(false)
                mainHandler.post {
                    if (!closedByUser.get() && socket === webSocket) listener.onClosed?.invoke(code, reason)
                }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket != null && socket !== webSocket) return
                connected.set(false)
                val message = t.message ?: "WebSocket connection failed."
                mainHandler.post {
                    if (!closedByUser.get() && socket === webSocket) listener.onError?.invoke(message)
                }
                scheduleReconnect()
            }
        })
        socket = current
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (!autoReconnect || closedByUser.get() || reconnectScheduled) return
        reconnectAttempt += 1
        val exponent = (reconnectAttempt - 1).coerceAtMost(5)
        val delay = (1000L * (1L shl exponent)).coerceAtMost(maxReconnectDelayMillis)
        reconnectScheduled = true
        mainHandler.postDelayed(reconnectTask, delay)
    }
}

/** Callbacks for a RealtimeConnection. All callbacks run on Android's main thread. */
class RealtimeListener(
    val onConnected: (() -> Unit)? = null,
    val onMessage: ((String) -> Unit)? = null,
    val onClosed: ((code: Int, reason: String) -> Unit)? = null,
    val onError: ((message: String) -> Unit)? = null
)

/** Opens secure WebSocket connections and optionally retries after an unexpected disconnect. */
object Realtime {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(
        url: String,
        listener: RealtimeListener,
        accessToken: String? = null,
        autoReconnect: Boolean = false,
        maxReconnectDelayMillis: Long = 30_000L
    ): RealtimeConnection {
        val requestUrl = url.trim()
        require(requestUrl.startsWith("wss://", ignoreCase = true)) {
            "Realtime requires a secure wss:// URL."
        }
        require(accessToken == null || accessToken.isNotBlank()) { "Access token cannot be blank." }
        require(maxReconnectDelayMillis >= 1000L) { "Reconnect delay must be at least 1000ms." }
        val requestBuilder = Request.Builder().url(requestUrl)
        accessToken?.let { requestBuilder.header("Authorization", "Bearer $it") }
        return RealtimeConnection(
            requestBuilder.build(),
            listener,
            autoReconnect,
            maxReconnectDelayMillis,
            client,
            mainHandler
        )
    }
}

/**
 * A lightweight room wrapper using AIM's simple JSON room protocol.
 * The room join message is automatically resent after a successful reconnect.
 */
class RealtimeRoom internal constructor(
    private val connection: RealtimeConnection,
    val roomId: String
) {
    val isConnected: Boolean get() = connection.isConnected

    /** Send a named room event with an optional JSON object payload. */
    fun sendEvent(event: String, data: org.json.JSONObject = org.json.JSONObject()): Boolean {
        require(event.isNotBlank()) { "Event name cannot be blank." }
        val message = org.json.JSONObject()
            .put("type", "event")
            .put("roomId", roomId)
            .put("event", event)
            .put("data", data)
        return connection.send(message.toString())
    }

    /** Send a custom text message when the game uses its own protocol. */
    fun sendRaw(message: String): Boolean = connection.send(message)

    /** Leave this room and permanently close its WebSocket connection. */
    fun leave() {
        connection.send(
            org.json.JSONObject()
                .put("type", "leave")
                .put("roomId", roomId)
                .toString()
        )
        connection.close()
    }
}
