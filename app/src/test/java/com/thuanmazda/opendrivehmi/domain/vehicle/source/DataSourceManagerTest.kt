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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class DataSourceManagerTest {
    @Test
    fun gpsMode_usesGpsTelemetry() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.GPS))

        val timestamp = Instant.parse("2026-08-11T12:45:00Z")
        gps.publishVehicleState(gpsVehicleState(timestamp))
        gps.publishNavigationState(gpsNavigationState(timestamp))
        gps.connect()

        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(DataSourceType.GPS, manager.activeSourceType.value)
        assertEquals(86.0, manager.vehicleState.value?.speed?.value)
        assertEquals(DataSourceType.GPS, manager.vehicleState.value?.speed?.source)
        assertNotNull(manager.navigationState.value)
    }

    @Test
    fun ets2Mode_usesEts2Telemetry() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.ETS2))

        val timestamp = Instant.parse("2026-08-11T12:46:00Z")
        ets2.publishVehicleState(ets2VehicleState(timestamp))
        ets2.publishNavigationState(ets2NavigationState(timestamp))
        ets2.connect()

        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(DataSourceType.ETS2, manager.activeSourceType.value)
        assertEquals("D", manager.vehicleState.value?.gear?.value)
        assertEquals(DataSourceType.ETS2, manager.vehicleState.value?.gear?.source)
    }

    @Test
    fun demoMode_usesDeterministicDemoTelemetry() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.DEMO))

        val timestamp = Instant.parse("2026-08-11T12:47:00Z")
        demo.publishVehicleState(demoVehicleState(timestamp))
        demo.publishNavigationState(demoNavigationState(timestamp))
        demo.connect()

        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(DataSourceType.DEMO, manager.activeSourceType.value)
        assertEquals(54.0, manager.vehicleState.value?.speed?.value)
        assertEquals(DataSourceType.DEMO, manager.vehicleState.value?.speed?.source)
    }

    @Test
    fun autoMode_fallsBackFromEts2ToGps() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.AUTO))

        val timestamp = Instant.parse("2026-08-11T12:48:00Z")
        gps.publishVehicleState(gpsVehicleState(timestamp))
        gps.publishNavigationState(gpsNavigationState(timestamp))
        ets2.publishVehicleState(ets2UnavailableVehicleState(timestamp))
        ets2.publishNavigationState(ets2NavigationState(timestamp))
        demo.publishVehicleState(demoVehicleState(timestamp))
        gps.connect()
        ets2.connect()
        ets2.disconnect()
        demo.connect()

        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(DataSourceType.GPS, manager.activeSourceType.value)
        assertEquals(86.0, manager.vehicleState.value?.speed?.value)
        assertEquals(DataSourceType.GPS, manager.vehicleState.value?.speed?.source)
        assertEquals("N", manager.vehicleState.value?.gear?.value)
    }

    @Test
    fun hybridMode_selectsConfiguredSourcePerField() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val configuration = VehicleSourceConfiguration(
            mode = DataSourceMode.HYBRID,
            vehicleFieldSources = VehicleFieldSourceConfiguration(
                speed = DataSourceType.GPS,
                speedUnit = DataSourceType.GPS,
                latitude = DataSourceType.GPS,
                longitude = DataSourceType.GPS,
                heading = DataSourceType.GPS,
                gear = DataSourceType.ETS2,
                rpm = DataSourceType.ETS2,
                fuelLevel = DataSourceType.ETS2,
                turnSignal = DataSourceType.ETS2,
            ),
            navigationSource = DataSourceType.GPS,
        )
        val manager = createManager(gps, ets2, demo, configuration)

        val timestamp = Instant.parse("2026-08-11T12:49:00Z")
        gps.publishVehicleState(gpsVehicleState(timestamp))
        gps.publishNavigationState(gpsNavigationState(timestamp))
        ets2.publishVehicleState(ets2VehicleState(timestamp))
        ets2.publishNavigationState(ets2NavigationState(timestamp))
        gps.connect()
        ets2.connect()

        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(86.0, manager.vehicleState.value?.speed?.value)
        assertEquals(DataSourceType.GPS, manager.vehicleState.value?.speed?.source)
        assertEquals("D", manager.vehicleState.value?.gear?.value)
        assertEquals(DataSourceType.ETS2, manager.vehicleState.value?.gear?.source)
        assertEquals(1800, manager.vehicleState.value?.rpm?.value)
    }

    @Test
    fun sourceUnavailable_returnsUnavailableVehicleState() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.GPS))

        gps.publishVehicleState(gpsVehicleState(Instant.parse("2026-08-11T12:50:00Z")))
        gps.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState.value)
        assertNotNull(manager.vehicleState.value)
        assertEquals(false, manager.vehicleState.value?.speed?.available)
    }

    @Test
    fun sourceReconnection_restoresUnifiedVehicleState() = runBlocking {
        val gps = ManagerFakeVehicleDataSource(DataSourceType.GPS)
        val ets2 = ManagerFakeVehicleDataSource(DataSourceType.ETS2)
        val demo = ManagerFakeVehicleDataSource(DataSourceType.DEMO)
        val manager = createManager(gps, ets2, demo, VehicleSourceConfiguration(mode = DataSourceMode.GPS))

        val timestamp = Instant.parse("2026-08-11T12:51:00Z")
        gps.publishVehicleState(gpsVehicleState(timestamp))
        gps.publishNavigationState(gpsNavigationState(timestamp))
        gps.connect()

        assertNotNull(manager.vehicleState.value)

        gps.disconnect()
        assertNotNull(manager.vehicleState.value)
        assertEquals(false, manager.vehicleState.value?.speed?.available)
        assertEquals(ConnectionState.DISCONNECTED, manager.connectionState.value)

        gps.reconnect()
        assertNotNull(manager.vehicleState.value)
        assertEquals(ConnectionState.CONNECTED, manager.connectionState.value)
        assertEquals(true, manager.vehicleState.value?.speed?.available)
        assertEquals(DataSourceType.GPS, manager.vehicleState.value?.speed?.source)
    }

    private fun createManager(
        gps: ManagerFakeVehicleDataSource,
        ets2: ManagerFakeVehicleDataSource,
        demo: ManagerFakeVehicleDataSource,
        configuration: VehicleSourceConfiguration,
    ): DefaultDataSourceManager {
        return DefaultDataSourceManager(
            sources = mapOf(
                DataSourceType.GPS to gps,
                DataSourceType.ETS2 to ets2,
                DataSourceType.DEMO to demo,
            ),
            initialConfiguration = configuration,
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )
    }

    private fun gpsVehicleState(timestamp: Instant): VehicleState {
        return VehicleState(
            speed = VehicleValue(86.0, DataSourceType.GPS, true, timestamp),
            speedUnit = VehicleValue(SpeedUnit.KILOMETERS_PER_HOUR, DataSourceType.GPS, true, timestamp),
            latitude = VehicleValue(37.1234, DataSourceType.GPS, true, timestamp),
            longitude = VehicleValue(-122.1234, DataSourceType.GPS, true, timestamp),
            altitude = VehicleValue(42.5, DataSourceType.GPS, true, timestamp),
            heading = VehicleValue(180.0, DataSourceType.GPS, true, timestamp),
            gear = VehicleValue("N", DataSourceType.GPS, true, timestamp),
            rpm = VehicleValue(1200, DataSourceType.GPS, true, timestamp),
            fuelLevel = VehicleValue(70.0, DataSourceType.GPS, true, timestamp),
            fuelConsumption = VehicleValue(7.2, DataSourceType.GPS, true, timestamp),
            engineTemperature = VehicleValue(88.0, DataSourceType.GPS, true, timestamp),
            turnSignal = VehicleValue(TurnSignalState.LEFT, DataSourceType.GPS, true, timestamp),
            lights = VehicleValue(
                VehicleLightsState(true, true, false, false, false, true),
                DataSourceType.GPS,
                true,
                timestamp,
            ),
            parkingBrake = VehicleValue(ParkingBrakeState.RELEASED, DataSourceType.GPS, true, timestamp),
            cruiseControl = VehicleValue(CruiseControlState.ACTIVE, DataSourceType.GPS, true, timestamp),
            cruiseControlSpeed = VehicleValue(90.0, DataSourceType.GPS, true, timestamp),
            engineBrake = VehicleValue(RetarderState(true, 1), DataSourceType.GPS, true, timestamp),
            trailerAttached = VehicleValue(false, DataSourceType.GPS, true, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.CONNECTED, DataSourceType.GPS, true, timestamp),
        )
    }

    private fun ets2VehicleState(timestamp: Instant): VehicleState {
        return VehicleState(
            speed = VehicleValue(82.0, DataSourceType.ETS2, true, timestamp),
            speedUnit = VehicleValue(SpeedUnit.KILOMETERS_PER_HOUR, DataSourceType.ETS2, true, timestamp),
            latitude = VehicleValue(37.0, DataSourceType.ETS2, true, timestamp),
            longitude = VehicleValue(-122.0, DataSourceType.ETS2, true, timestamp),
            altitude = VehicleValue(40.0, DataSourceType.ETS2, true, timestamp),
            heading = VehicleValue(181.0, DataSourceType.ETS2, true, timestamp),
            gear = VehicleValue("D", DataSourceType.ETS2, true, timestamp),
            rpm = VehicleValue(1800, DataSourceType.ETS2, true, timestamp),
            fuelLevel = VehicleValue(54.0, DataSourceType.ETS2, true, timestamp),
            fuelConsumption = VehicleValue(6.8, DataSourceType.ETS2, true, timestamp),
            engineTemperature = VehicleValue(91.0, DataSourceType.ETS2, true, timestamp),
            turnSignal = VehicleValue(TurnSignalState.RIGHT, DataSourceType.ETS2, true, timestamp),
            lights = VehicleValue(
                VehicleLightsState(true, true, false, false, false, true),
                DataSourceType.ETS2,
                true,
                timestamp,
            ),
            parkingBrake = VehicleValue(ParkingBrakeState.RELEASED, DataSourceType.ETS2, true, timestamp),
            cruiseControl = VehicleValue(CruiseControlState.ACTIVE, DataSourceType.ETS2, true, timestamp),
            cruiseControlSpeed = VehicleValue(88.0, DataSourceType.ETS2, true, timestamp),
            engineBrake = VehicleValue(RetarderState(true, 2), DataSourceType.ETS2, true, timestamp),
            trailerAttached = VehicleValue(true, DataSourceType.ETS2, true, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.CONNECTED, DataSourceType.ETS2, true, timestamp),
        )
    }

    private fun ets2UnavailableVehicleState(timestamp: Instant): VehicleState {
        return ets2VehicleState(timestamp).copy(
            speed = VehicleValue(null, DataSourceType.ETS2, false, timestamp),
        )
    }

    private fun demoVehicleState(timestamp: Instant): VehicleState {
        return VehicleState(
            speed = VehicleValue(54.0, DataSourceType.DEMO, true, timestamp),
            speedUnit = VehicleValue(SpeedUnit.KILOMETERS_PER_HOUR, DataSourceType.DEMO, true, timestamp),
            latitude = VehicleValue(35.0, DataSourceType.DEMO, true, timestamp),
            longitude = VehicleValue(-120.0, DataSourceType.DEMO, true, timestamp),
            altitude = VehicleValue(20.0, DataSourceType.DEMO, true, timestamp),
            heading = VehicleValue(90.0, DataSourceType.DEMO, true, timestamp),
            gear = VehicleValue("P", DataSourceType.DEMO, true, timestamp),
            rpm = VehicleValue(900, DataSourceType.DEMO, true, timestamp),
            fuelLevel = VehicleValue(80.0, DataSourceType.DEMO, true, timestamp),
            fuelConsumption = VehicleValue(5.4, DataSourceType.DEMO, true, timestamp),
            engineTemperature = VehicleValue(75.0, DataSourceType.DEMO, true, timestamp),
            turnSignal = VehicleValue(TurnSignalState.OFF, DataSourceType.DEMO, true, timestamp),
            lights = VehicleValue(
                VehicleLightsState(true, false, false, false, false, true),
                DataSourceType.DEMO,
                true,
                timestamp,
            ),
            parkingBrake = VehicleValue(ParkingBrakeState.ENGAGED, DataSourceType.DEMO, true, timestamp),
            cruiseControl = VehicleValue(CruiseControlState.OFF, DataSourceType.DEMO, true, timestamp),
            cruiseControlSpeed = VehicleValue(0.0, DataSourceType.DEMO, true, timestamp),
            engineBrake = VehicleValue(RetarderState(false, null), DataSourceType.DEMO, true, timestamp),
            trailerAttached = VehicleValue(false, DataSourceType.DEMO, true, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.CONNECTED, DataSourceType.DEMO, true, timestamp),
        )
    }

    private fun gpsNavigationState(timestamp: Instant): NavigationState {
        return NavigationState(
            routeAvailability = VehicleValue(RouteAvailability.AVAILABLE, DataSourceType.GPS, true, timestamp),
            destination = VehicleValue("Portland", DataSourceType.GPS, true, timestamp),
            distance = VehicleValue(12.7, DataSourceType.GPS, true, timestamp),
            estimatedTime = VehicleValue(Duration.ofMinutes(18), DataSourceType.GPS, true, timestamp),
            speedLimit = VehicleValue(88.0, DataSourceType.GPS, true, timestamp),
            nextManeuver = VehicleValue(
                NavigationManeuver(NavigationManeuverType.RIGHT, "Turn right onto Main St"),
                DataSourceType.GPS,
                true,
                timestamp,
            ),
            navigationHeading = VehicleValue(182.0, DataSourceType.GPS, true, timestamp),
        )
    }

    private fun ets2NavigationState(timestamp: Instant): NavigationState {
        return NavigationState(
            routeAvailability = VehicleValue(RouteAvailability.AVAILABLE, DataSourceType.ETS2, true, timestamp),
            destination = VehicleValue("Seattle", DataSourceType.ETS2, true, timestamp),
            distance = VehicleValue(10.0, DataSourceType.ETS2, true, timestamp),
            estimatedTime = VehicleValue(Duration.ofMinutes(15), DataSourceType.ETS2, true, timestamp),
            speedLimit = VehicleValue(80.0, DataSourceType.ETS2, true, timestamp),
            nextManeuver = VehicleValue(
                NavigationManeuver(NavigationManeuverType.LEFT, "Turn left in 200 m"),
                DataSourceType.ETS2,
                true,
                timestamp,
            ),
            navigationHeading = VehicleValue(170.0, DataSourceType.ETS2, true, timestamp),
        )
    }

    private fun demoNavigationState(timestamp: Instant): NavigationState {
        return NavigationState(
            routeAvailability = VehicleValue(RouteAvailability.AVAILABLE, DataSourceType.DEMO, true, timestamp),
            destination = VehicleValue("Demo City", DataSourceType.DEMO, true, timestamp),
            distance = VehicleValue(8.0, DataSourceType.DEMO, true, timestamp),
            estimatedTime = VehicleValue(Duration.ofMinutes(11), DataSourceType.DEMO, true, timestamp),
            speedLimit = VehicleValue(65.0, DataSourceType.DEMO, true, timestamp),
            nextManeuver = VehicleValue(
                NavigationManeuver(NavigationManeuverType.STRAIGHT, "Continue straight"),
                DataSourceType.DEMO,
                true,
                timestamp,
            ),
            navigationHeading = VehicleValue(95.0, DataSourceType.DEMO, true, timestamp),
        )
    }
}

private class ManagerFakeVehicleDataSource(
    override val type: DataSourceType,
) : VehicleDataSource {
    override val connectionState = kotlinx.coroutines.flow.MutableStateFlow(ConnectionState.DISCONNECTED)
    override val vehicleState = kotlinx.coroutines.flow.MutableStateFlow<VehicleState?>(null)
    override val navigationState = kotlinx.coroutines.flow.MutableStateFlow<NavigationState?>(null)

    override suspend fun connect() {
        connectionState.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun reconnect() {
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