package com.thuanmazda.opendrivehmi.data.ets2

import com.thuanmazda.opendrivehmi.domain.vehicle.source.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class Ets2ClientTest {
    @Test
    fun validTelemetry_updatesVehicleAndNavigationState() = runTest {
        val transport = FakeEts2Transport(
            messageToEmit = """
                {
                  "game": { "connected": true },
                  "truck": {
                    "speed": 52.5,
                    "displayedGear": 5,
                    "engineRpm": 1400.0,
                    "fuel": 300.0,
                    "fuelAverageConsumption": 8.1,
                    "blinkerLeftOn": false,
                    "blinkerRightOn": true,
                    "lightsParkingOn": true,
                    "lightsBeamLowOn": true,
                    "lightsBeamHighOn": false,
                    "parkBrakeOn": false,
                    "motorBrakeOn": true,
                    "retarderBrake": 1,
                    "cruiseControlSpeed": 88.0,
                    "cruiseControlOn": true
                  },
                  "trailer": { "attached": true },
                  "navigation": {
                    "estimatedTime": "0001-01-01T00:20:00Z",
                    "estimatedDistance": 1234,
                    "speedLimit": 90
                  }
                }
            """.trimIndent(),
        )
        val client = createClient(transport)

        client.connect()
        advanceUntilIdle()

        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        assertEquals(52.5, client.vehicleState.value?.speed?.value ?: 0.0, 0.0001)
        assertEquals("D5", client.vehicleState.value?.gear?.value)
        assertEquals(true, client.vehicleState.value?.trailerAttached?.value)
        assertEquals(1234.0, client.navigationState.value?.distance?.value ?: 0.0, 0.0001)
        assertEquals(90.0, client.navigationState.value?.speedLimit?.value ?: 0.0, 0.0001)
    }

    @Test
    fun disconnectedState_clearsTelemetry() = runTest {
        val transport = FakeEts2Transport()
        val client = createClient(transport)

        client.connect()
        advanceUntilIdle()

        client.disconnect()
        advanceUntilIdle()

        assertEquals(ConnectionState.DISCONNECTED, client.connectionState.value)
        assertNull(client.vehicleState.value)
        assertNull(client.navigationState.value)
    }

    @Test
    fun reconnect_retriesWithBoundedBackoff() = runTest {
        val transport = FakeEts2Transport(
            failConnectAttempts = 1,
            messageToEmit = """
                {
                  "game": { "connected": true },
                  "truck": { "speed": 40.0, "displayedGear": 3 },
                  "trailer": { "attached": false },
                  "navigation": { "estimatedDistance": 100, "speedLimit": 80 }
                }
            """.trimIndent(),
        )
        val client = createClient(transport)

        client.connect()
        advanceUntilIdle()

        assertEquals(1, transport.connectCalls)
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(2, transport.connectCalls)
        assertEquals(ConnectionState.CONNECTED, client.connectionState.value)
        assertEquals(40.0, client.vehicleState.value?.speed?.value ?: 0.0, 0.0001)
    }

    @Test
    fun malformedJson_marksFailure() = runTest {
        val transport = FakeEts2Transport(messageToEmit = "not-json")
        val client = createClient(transport)

        client.connect()
        advanceUntilIdle()

        assertEquals(ConnectionState.FAILED, client.connectionState.value)
    }

    private fun createClient(transport: FakeEts2Transport): DefaultEts2Client {
        return DefaultEts2Client(
            config = Ets2ClientConfig(
                host = "127.0.0.1",
                restFallbackEnabled = false,
                messageTimeoutMillis = 5_000,
                initialReconnectDelayMillis = 250,
                maxReconnectAttempts = 3,
            ),
            transport = transport,
            restClient = null,
            coroutineScope = backgroundScope,
            clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC),
        )
    }
}

private class FakeEts2Transport(
    var messageToEmit: String? = null,
    var failConnectAttempts: Int = 0,
) : Ets2RealtimeTransport {
    private var listener: Ets2RealtimeTransport.Listener? = null
    var connectCalls: Int = 0
        private set

    override fun setListener(listener: Ets2RealtimeTransport.Listener) {
        this.listener = listener
    }

    override suspend fun connect() {
        connectCalls += 1
        if (connectCalls <= failConnectAttempts) {
            throw IllegalStateException("connect failed")
        }
        listener?.onConnected()
    }

    override suspend fun disconnect() {
        listener?.onClosed(null)
    }

    override suspend fun requestData() {
        val message = messageToEmit ?: return
        messageToEmit = null
        listener?.onMessage(message)
    }
}