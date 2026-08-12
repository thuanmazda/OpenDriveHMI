package com.thuanmazda.opendrivehmi.data.gps

import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.SpeedUnit
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleConnectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsLocationMapperTest {
    @Test
    fun mapsGpsSnapshotIntoVehicleState() {
        val mapper = GpsVehicleStateMapper()
        val state = mapper.map(
            GpsLocationSnapshot(
                latitude = 37.1234,
                longitude = -122.1234,
                altitudeMeters = 42.5,
                speedMetersPerSecond = 18.2f,
                bearingDegrees = 180.0f,
                accuracyMeters = 4.5f,
                timestampMillis = 1_700_000_000_000L,
            ),
        )

        assertEquals(37.1234, state.latitude.value ?: 0.0, 0.0001)
        assertEquals(DataSourceType.GPS, state.latitude.source)
        assertTrue(state.latitude.available)
        assertEquals(-122.1234, state.longitude.value ?: 0.0, 0.0001)
        assertEquals(42.5, state.altitude.value ?: 0.0, 0.0001)
        assertEquals(18.2, state.speed.value ?: 0.0, 0.0001)
        assertEquals(SpeedUnit.METERS_PER_SECOND, state.speedUnit.value)
        assertEquals(180.0, state.heading.value ?: 0.0, 0.0001)
        assertEquals(4.5, state.accuracy.value ?: 0.0, 0.0001)
        assertEquals(VehicleConnectionStatus.CONNECTED, state.vehicleConnectionStatus.value)
        assertFalse(state.gear.available)
    }
}