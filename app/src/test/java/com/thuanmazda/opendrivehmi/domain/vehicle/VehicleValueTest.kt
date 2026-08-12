package com.thuanmazda.opendrivehmi.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VehicleValueTest {
    @Test
    fun availableValue_keepsValueSourceAndTimestamp() {
        val timestamp = Instant.parse("2026-08-11T12:34:56Z")

        val vehicleValue = VehicleValue(
            value = 86.0,
            source = DataSourceType.GPS,
            available = true,
            timestamp = timestamp,
        )

        assertEquals(86.0, vehicleValue.value)
        assertEquals(DataSourceType.GPS, vehicleValue.source)
        assertTrue(vehicleValue.available)
        assertEquals(timestamp, vehicleValue.timestamp)
    }

    @Test
    fun unavailableValue_allowsNullValueAndMarksUnavailable() {
        val timestamp = Instant.parse("2026-08-11T12:35:00Z")

        val vehicleValue = VehicleValue<Double>(
            value = null,
            source = DataSourceType.ETS2,
            available = false,
            timestamp = timestamp,
        )

        assertNull(vehicleValue.value)
        assertEquals(DataSourceType.ETS2, vehicleValue.source)
        assertFalse(vehicleValue.available)
        assertEquals(timestamp, vehicleValue.timestamp)
    }

    @Test
    fun sourceTracking_retainsSelectedSource() {
        val timestamp = Instant.parse("2026-08-11T12:35:10Z")

        val vehicleValue = VehicleValue(
            value = "D",
            source = DataSourceType.DEMO,
            available = true,
            timestamp = timestamp,
        )

        assertEquals(DataSourceType.DEMO, vehicleValue.source)
    }

    @Test
    fun timestampHandling_retainsExactInstant() {
        val timestamp = Instant.parse("2026-08-11T12:35:20Z")

        val vehicleValue = VehicleValue(
            value = 1200,
            source = DataSourceType.NONE,
            available = false,
            timestamp = timestamp,
        )

        assertEquals(timestamp, vehicleValue.timestamp)
    }
}