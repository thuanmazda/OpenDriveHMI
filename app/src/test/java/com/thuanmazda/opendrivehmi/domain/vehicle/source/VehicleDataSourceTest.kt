package com.thuanmazda.opendrivehmi.domain.vehicle.source

import com.thuanmazda.opendrivehmi.domain.vehicle.CruiseControlState
import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationManeuver
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationManeuverType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.ParkingBrakeState
import com.thuanmazda.opendrivehmi.domain.vehicle.RouteAvailability
import com.thuanmazda.opendrivehmi.domain.vehicle.RetarderState
import com.thuanmazda.opendrivehmi.domain.vehicle.SpeedUnit
import com.thuanmazda.opendrivehmi.domain.vehicle.TurnSignalState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleConnectionStatus
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleLightsState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class VehicleDataSourceTest {
    @Test
    fun fakeDataSource_startsDisconnectedWithNoTelemetry() {
        val dataSource = FakeVehicleDataSource()

        assertEquals(ConnectionState.DISCONNECTED, dataSource.connectionState.value)
        assertNull(dataSource.vehicleState.value)
        assertNull(dataSource.navigationState.value)
    }

    @Test
    fun fakeDataSource_canPublishVehicleAndNavigationState() {
        val dataSource = FakeVehicleDataSource()
        val timestamp = Instant.parse("2026-08-11T12:40:00Z")

        val vehicleState = sampleVehicleState(timestamp)
        val navigationState = sampleNavigationState(timestamp)

        dataSource.publishVehicleState(vehicleState)
        dataSource.publishNavigationState(navigationState)

        assertEquals(vehicleState, dataSource.vehicleState.value)
        assertEquals(navigationState, dataSource.navigationState.value)
        assertEquals(DataSourceType.DEMO, dataSource.type)
        assertEquals(DataSourceType.GPS, dataSource.vehicleState.value?.speed?.source)
        assertEquals(timestamp, dataSource.vehicleState.value?.speed?.timestamp)
        assertTrue(dataSource.vehicleState.value?.speed?.available == true)
        assertFalse(dataSource.vehicleState.value?.fuelLevel?.available ?: true)
    }

    @Test
    fun fakeDataSource_reconnectReturnsToConnectedState() = runBlocking {
        val dataSource = FakeVehicleDataSource()

        dataSource.connect()
        dataSource.disconnect()
        dataSource.reconnect()

        assertEquals(ConnectionState.CONNECTED, dataSource.connectionState.value)
        assertEquals(2, dataSource.connectCount)
        assertEquals(2, dataSource.disconnectCount)
        assertEquals(1, dataSource.reconnectCount)
    }

    private fun sampleVehicleState(timestamp: Instant): VehicleState {
        return VehicleState(
            speed = VehicleValue(86.0, DataSourceType.GPS, true, timestamp),
            speedUnit = VehicleValue(SpeedUnit.KILOMETERS_PER_HOUR, DataSourceType.GPS, true, timestamp),
            latitude = VehicleValue(37.1234, DataSourceType.GPS, true, timestamp),
            longitude = VehicleValue(-122.1234, DataSourceType.GPS, true, timestamp),
            altitude = VehicleValue(42.5, DataSourceType.GPS, true, timestamp),
            heading = VehicleValue(180.0, DataSourceType.GPS, true, timestamp),
            gear = VehicleValue("D", DataSourceType.ETS2, true, timestamp),
            rpm = VehicleValue(1800, DataSourceType.ETS2, true, timestamp),
            fuelLevel = VehicleValue(null, DataSourceType.NONE, false, timestamp),
            fuelConsumption = VehicleValue(7.2, DataSourceType.DEMO, true, timestamp),
            engineTemperature = VehicleValue(91.0, DataSourceType.ETS2, true, timestamp),
            turnSignal = VehicleValue(TurnSignalState.LEFT, DataSourceType.DEMO, true, timestamp),
            lights = VehicleValue(
                VehicleLightsState(
                    positionLights = true,
                    lowBeam = true,
                    highBeam = false,
                    fogLights = false,
                    hazardLights = false,
                    daytimeRunningLights = true,
                ),
                DataSourceType.DEMO,
                true,
                timestamp,
            ),
            parkingBrake = VehicleValue(ParkingBrakeState.RELEASED, DataSourceType.ETS2, true, timestamp),
            cruiseControl = VehicleValue(CruiseControlState.ACTIVE, DataSourceType.ETS2, true, timestamp),
            cruiseControlSpeed = VehicleValue(90.0, DataSourceType.ETS2, true, timestamp),
            engineBrake = VehicleValue(RetarderState(active = true, level = 2), DataSourceType.ETS2, true, timestamp),
            trailerAttached = VehicleValue(false, DataSourceType.DEMO, true, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.CONNECTED, DataSourceType.DEMO, true, timestamp),
        )
    }

    private fun sampleNavigationState(timestamp: Instant): NavigationState {
        return NavigationState(
            routeAvailability = VehicleValue(RouteAvailability.AVAILABLE, DataSourceType.GPS, true, timestamp),
            destination = VehicleValue("Portland", DataSourceType.DEMO, true, timestamp),
            distance = VehicleValue(12.7, DataSourceType.GPS, true, timestamp),
            estimatedTime = VehicleValue(Duration.ofMinutes(18), DataSourceType.GPS, true, timestamp),
            speedLimit = VehicleValue(88.0, DataSourceType.GPS, true, timestamp),
            nextManeuver = VehicleValue(
                NavigationManeuver(
                    type = NavigationManeuverType.RIGHT,
                    instruction = "Turn right onto Main St",
                ),
                DataSourceType.GPS,
                true,
                timestamp,
            ),
            navigationHeading = VehicleValue(182.0, DataSourceType.GPS, true, timestamp),
        )
    }
}

private class FakeVehicleDataSource(
    override val type: DataSourceType = DataSourceType.DEMO,
) : VehicleDataSource {
    override val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val vehicleState = MutableStateFlow<VehicleState?>(null)
    override val navigationState = MutableStateFlow<NavigationState?>(null)

    var connectCount: Int = 0
        private set

    var disconnectCount: Int = 0
        private set

    var reconnectCount: Int = 0
        private set

    override suspend fun connect() {
        connectCount += 1
        connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        disconnectCount += 1
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun reconnect() {
        reconnectCount += 1
        connectionState.value = ConnectionState.RECONNECTING
        disconnect()
        connect()
    }

    fun publishVehicleState(state: VehicleState) {
        vehicleState.value = state
    }

    fun publishNavigationState(state: NavigationState) {
        navigationState.value = state
    }
}