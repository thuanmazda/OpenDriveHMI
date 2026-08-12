package com.thuanmazda.opendrivehmi.data.ets2

import android.os.Build
import android.util.Log
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.source.ConnectionState
import com.thuanmazda.opendrivehmi.domain.vehicle.source.VehicleDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

enum class Ets2Protocol {
    HTTP,
    HTTPS,
}

data class Ets2ClientConfig(
    val host: String,
    val port: Int = 25555,
    val protocol: Ets2Protocol = Ets2Protocol.HTTP,
    val websocketPath: String = "/signalr",
    val telemetryPath: String = "/api/ets2/telemetry",
    val restFallbackEnabled: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val initialReconnectDelayMillis: Long = 250,
    val maxReconnectDelayMillis: Long = 4_000,
    val messageTimeoutMillis: Long = 10_000,
    val requestTimeoutMillis: Long = 3_000,
)

interface Ets2TelemetryRestClient {
    suspend fun fetchTelemetryJson(): String
}

interface Ets2RealtimeTransport {
    fun setListener(listener: Listener)
    suspend fun connect()
    suspend fun disconnect()
    suspend fun requestData()

    interface Listener {
        fun onConnected()
        fun onMessage(message: String)
        fun onClosed(cause: Throwable?)
    }
}

interface Ets2Client : VehicleDataSource {
    val config: Ets2ClientConfig
}

class DefaultEts2Client(
    override val config: Ets2ClientConfig,
    private val transport: Ets2RealtimeTransport,
    private val restClient: Ets2TelemetryRestClient?,
    private val parser: Ets2TelemetryParser = Ets2TelemetryParser(),
    private val mapper: Ets2TelemetryMapper = Ets2TelemetryMapper(),
    private val coroutineScope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) : Ets2Client {
    override val type = com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType.ETS2
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val vehicleState = MutableStateFlow<VehicleState?>(null)
    override val navigationState = MutableStateFlow<NavigationState?>(null)

    private val manualDisconnect = AtomicBoolean(false)
    private val suppressCloseCallback = AtomicBoolean(false)
    private var transportJob: Job? = null
    private var timeoutJob: Job? = null
    private var reconnectAttempt = 0

    init {
        transport.setListener(object : Ets2RealtimeTransport.Listener {
            override fun onConnected() {
                connectionState.value = ConnectionState.CONNECTED
                reconnectAttempt = 0
                restartTimeoutWatch()
                requestData()
                logDebug("ETS2 realtime connected")
            }

            override fun onMessage(message: String) {
                restartTimeoutWatch()
                handleTelemetryMessage(message)
                requestData()
            }

            override fun onClosed(cause: Throwable?) {
                if (suppressCloseCallback.getAndSet(false)) {
                    return
                }
                stopTimeoutWatch()
                if (manualDisconnect.get()) {
                    connectionState.value = ConnectionState.DISCONNECTED
                    logDebug("ETS2 realtime disconnected")
                } else {
                    connectionState.value = ConnectionState.RECONNECTING
                    logDebug("ETS2 realtime closed")
                    scheduleReconnect(cause)
                }
            }
        })
    }

    override suspend fun connect() {
        manualDisconnect.set(false)
        if (transportJob?.isActive == true) return
        transportJob = coroutineScope.launch(SupervisorJob()) {
            connectWithRetry()
        }
    }

    override suspend fun disconnect() {
        manualDisconnect.set(true)
        suppressCloseCallback.set(true)
        stopTimeoutWatch()
        disconnectTransport(updateConnectionState = true)
        transportJob?.cancel()
        transportJob = null
        connectionState.value = ConnectionState.DISCONNECTED
        logDebug("ETS2 client disconnected")
    }

    override suspend fun reconnect() {
        disconnect()
        connect()
    }

    private suspend fun connectWithRetry() {
        while (!manualDisconnect.get() && reconnectAttempt <= config.maxReconnectAttempts) {
            try {
                connectionState.value = if (reconnectAttempt == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
                transport.connect()
                return
            } catch (throwable: Throwable) {
                reconnectAttempt += 1
                logError("ETS2 connect failed (attempt $reconnectAttempt)", throwable)
                if (config.restFallbackEnabled) {
                    performRestFallback()
                }
                if (reconnectAttempt > config.maxReconnectAttempts) {
                    connectionState.value = ConnectionState.FAILED
                    return
                }
                delay(backoffMillis(reconnectAttempt))
            }
        }
    }

    private fun scheduleReconnect(cause: Throwable?) {
        coroutineScope.launch {
            reconnectAttempt += 1
            if (reconnectAttempt > config.maxReconnectAttempts) {
                connectionState.value = ConnectionState.FAILED
                return@launch
            }
            if (config.restFallbackEnabled) {
                performRestFallback()
            }
            delay(backoffMillis(reconnectAttempt))
            if (!manualDisconnect.get()) {
                connectWithRetry()
            }
        }
    }

    private suspend fun performRestFallback() {
        val rest = restClient ?: return
        try {
            val rawJson = rest.fetchTelemetryJson()
            val telemetry = parser.parse(rawJson)
            applyTelemetry(telemetry)
            connectionState.value = ConnectionState.DEGRADED
            logDebug("ETS2 REST fallback succeeded")
        } catch (throwable: Throwable) {
            logError("ETS2 REST fallback failed", throwable)
        }
    }

    private fun handleTelemetryMessage(message: String) {
        try {
            val telemetry = parseSignalRMessage(message)
            if (telemetry != null) {
                applyTelemetry(telemetry)
            }
        } catch (throwable: Throwable) {
            connectionState.value = ConnectionState.FAILED
            logError("ETS2 malformed telemetry message", throwable)
            suppressCloseCallback.set(true)
            transportJob?.cancel()
            stopTimeoutWatch()
            coroutineScope.launch {
                disconnectTransport(updateConnectionState = false)
            }
        }
    }

    private fun applyTelemetry(telemetry: Ets2TelemetryDto) {
        val receivedAt = Instant.now(clock)
        vehicleState.value = mapper.mapVehicleState(telemetry, receivedAt)
        navigationState.value = mapper.mapNavigationState(telemetry, receivedAt)
        if (telemetry.game?.connected == false) {
            connectionState.value = ConnectionState.DISCONNECTED
        } else if (connectionState.value != ConnectionState.DEGRADED) {
            connectionState.value = ConnectionState.CONNECTED
        }
    }

    private fun parseSignalRMessage(message: String): Ets2TelemetryDto? {
        val trimmed = message.trim().trim('')
        if (trimmed.startsWith("{")) {
            val json = org.json.JSONObject(trimmed)
            if (json.has("game") || json.has("truck") || json.has("trailer") || json.has("navigation")) {
                return parser.parse(trimmed)
            }
            val payload = extractTelemetryPayload(json)
            if (payload != null) {
                return parser.parse(payload)
            }
        }
        return parser.parse(trimmed)
    }

    private fun extractTelemetryPayload(signalRMessage: org.json.JSONObject): String? {
        val messages = signalRMessage.optJSONArray("M") ?: return signalRMessage.optString("R", null)
        for (index in 0 until messages.length()) {
            val item = messages.optJSONObject(index) ?: continue
            val methodName = item.optString("M", null) ?: continue
            if (methodName == "updateData") {
                val args = item.optJSONArray("A") ?: continue
                if (args.length() > 0) {
                    return args.optString(0, null)
                }
            }
        }
        return signalRMessage.optString("R", null)
    }

    private fun requestData() {
        coroutineScope.launch {
            try {
                transport.requestData()
                restartTimeoutWatch()
            } catch (throwable: Throwable) {
                logError("ETS2 requestData failed", throwable)
            }
        }
    }

    private fun restartTimeoutWatch() {
        stopTimeoutWatch()
        timeoutJob = coroutineScope.launch {
            delay(config.messageTimeoutMillis)
            if (!manualDisconnect.get()) {
                connectionState.value = ConnectionState.RECONNECTING
                suppressCloseCallback.set(true)
                disconnectTransport(updateConnectionState = false)
                scheduleReconnect(null)
            }
        }
    }

    private suspend fun disconnectTransport(updateConnectionState: Boolean) {
        transport.disconnect()
        if (!updateConnectionState && connectionState.value == ConnectionState.FAILED) {
            return
        }
        if (updateConnectionState) {
            connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private fun stopTimeoutWatch() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun backoffMillis(attempt: Int): Long {
        val baseDelay = config.initialReconnectDelayMillis shl (attempt - 1).coerceAtMost(10)
        return minOf(baseDelay, config.maxReconnectDelayMillis)
    }

    private fun logDebug(message: String) {
        if (Build.TYPE != "user") {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable) {
        if (Build.TYPE != "user") {
            Log.e(TAG, message, throwable)
        }
    }

    private companion object {
        private const val TAG = "Ets2Client"
    }
}