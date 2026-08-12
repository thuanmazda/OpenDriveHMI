package com.thuanmazda.opendrivehmi.data.ets2

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OkHttpSignalRTransport(
    private val config: Ets2ClientConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(config.requestTimeoutMillis, TimeUnit.MILLISECONDS)
        .build(),
) : Ets2RealtimeTransport {
    private val listenerRef = AtomicReference<Ets2RealtimeTransport.Listener>()
    private var websocket: WebSocket? = null
    private var connectionToken: String? = null

    override fun setListener(listener: Ets2RealtimeTransport.Listener) {
        listenerRef.set(listener)
    }

    override suspend fun connect() {
        val negotiate = negotiate()
        connectionToken = negotiate.connectionToken
        openWebSocket(negotiate)
    }

    override suspend fun disconnect() {
        websocket?.close(1000, "client disconnect")
        websocket = null
    }

    override suspend fun requestData() {
        val ws = websocket ?: return
        val payload = JSONObject()
            .put("H", "ets2TelemetryHub")
            .put("M", "RequestData")
            .put("A", JSONArray())
            .put("I", 0)
            .toString() + '\u001e'
        ws.send(payload)
    }

    private suspend fun negotiate(): SignalRNegotiateResult = withContext(Dispatchers.IO) {
        val connectionData = URLEncoder.encode("[{\"name\":\"ets2TelemetryHub\"}]", StandardCharsets.UTF_8.name())
        val url = buildBaseUrl() + "/signalr/negotiate?clientProtocol=1.5&connectionData=$connectionData&_=${System.currentTimeMillis()}"
        val response = client.newCall(Request.Builder().url(url).get().build()).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("SignalR negotiate failed with HTTP ${response.code}")
        }
        response.use {
            val body = it.body?.string() ?: throw IllegalStateException("SignalR negotiate returned empty body")
            val json = JSONObject(body)
            SignalRNegotiateResult(
                connectionToken = json.getString("ConnectionToken"),
                connectionId = json.optString("ConnectionId", ""),
            )
        }
    }

    private suspend fun openWebSocket(negotiate: SignalRNegotiateResult) {
        val connectionData = URLEncoder.encode("[{\"name\":\"ets2TelemetryHub\"}]", StandardCharsets.UTF_8.name())
        val url = buildWebSocketUrl(negotiate.connectionToken, connectionData)
        val openSignal = CompletableDeferred<Unit>()
        websocket = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openSignal.complete(Unit)
                    listenerRef.get()?.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listenerRef.get()?.onMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    openSignal.completeExceptionally(t)
                    listenerRef.get()?.onClosed(t)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listenerRef.get()?.onClosed(null)
                }
            },
        )
        openSignal.await()
    }

    private fun buildBaseUrl(): String {
        val scheme = if (config.protocol == Ets2Protocol.HTTPS) "https" else "http"
        return "$scheme://${config.host}:${config.port}"
    }

    private fun buildWebSocketUrl(connectionToken: String, connectionData: String): String {
        val scheme = if (config.protocol == Ets2Protocol.HTTPS) "wss" else "ws"
        return "$scheme://${config.host}:${config.port}${config.websocketPath}" +
            "?transport=webSockets&clientProtocol=1.5&connectionToken=${URLEncoder.encode(connectionToken, StandardCharsets.UTF_8.name())}&connectionData=$connectionData&_=${System.currentTimeMillis()}"
    }

    private data class SignalRNegotiateResult(
        val connectionToken: String,
        val connectionId: String,
    )

    private fun logDebug(message: String) {
        if (Build.TYPE != "user") {
            Log.d(TAG, message)
        }
    }

    private companion object {
        private const val TAG = "SignalRTransport"
    }
}