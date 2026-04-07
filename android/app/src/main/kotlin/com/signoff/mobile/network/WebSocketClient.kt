package com.signoff.mobile.network

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * WebSocket client that communicates with the Spring Boot server using STOMP protocol.
 * Sends heartbeats with motion data every 2 seconds.
 */
class SignoffWebSocketClient(
    private val serverUrl: String,
    private val deviceId: String
) {

    companion object {
        private const val TAG = "SignoffWS"
        private const val HEARTBEAT_DESTINATION = "/app/heartbeat"
    }

    private val gson = Gson()
    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)     // Keep-alive
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var reconnectAttempts = 0
    private val maxReconnectDelay = 3000L

    // Callbacks
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun connect() {
        Log.d(TAG, "Connecting to $serverUrl...")
        
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected!")
                isConnected = true
                reconnectAttempts = 0

                // Send STOMP CONNECT frame
                val connectFrame = "CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000"
                webSocket.send(connectFrame)

                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: ${text.take(100)}")
                // Handle STOMP CONNECTED frame
                if (text.startsWith("CONNECTED")) {
                    Log.d(TAG, "STOMP session established")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                isConnected = false
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                isConnected = false
                onDisconnected?.invoke()
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                isConnected = false
                onError?.invoke(t.message ?: "Unknown error")
                onDisconnected?.invoke()
                scheduleReconnect()
            }
        })
    }

    /**
     * Send a heartbeat with current motion data via STOMP.
     */
    fun sendHeartbeat(moving: Boolean, acceleration: Float) {
        if (!isConnected) return

        val payload = gson.toJson(mapOf(
            "deviceId" to deviceId,
            "moving" to moving,
            "acceleration" to acceleration,
            "timestamp" to System.currentTimeMillis()
        ))

        // STOMP SEND frame
        val stompFrame = "SEND\ndestination:$HEARTBEAT_DESTINATION\ncontent-type:application/json\n\n$payload\u0000"
        
        val sent = webSocket?.send(stompFrame) ?: false
        if (!sent) {
            Log.w(TAG, "Failed to send heartbeat")
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting...")
        isConnected = false
        reconnectAttempts = Int.MAX_VALUE // Prevent reconnect

        // Send STOMP DISCONNECT
        webSocket?.send("DISCONNECT\n\n\u0000")
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= Int.MAX_VALUE) return // Manual disconnect

        reconnectAttempts++
        val delay = minOf(
            (1000L * (1 shl minOf(reconnectAttempts, 5))), // Exponential: 2s, 4s, 8s, 16s, 32s
            maxReconnectDelay
        )
        Log.d(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")

        Thread {
            try {
                Thread.sleep(delay)
                if (!isConnected) connect()
            } catch (e: InterruptedException) {
                // Cancelled
            }
        }.start()
    }

    fun isConnected() = isConnected
}
