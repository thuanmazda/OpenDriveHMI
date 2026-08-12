package com.thuanmazda.opendrivehmi.data.gps

import android.location.Location
import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.SpeedUnit
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleConnectionStatus
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleValue
import java.time.Instant

internal fun Location.toGpsLocationSnapshot(): GpsLocationSnapshot {
    return GpsLocationSnapshot(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampMillis = time,
    )
}

class GpsVehicleStateMapper {
    fun map(snapshot: GpsLocationSnapshot): VehicleState {
        val timestamp = Instant.ofEpochMilli(snapshot.timestampMillis)
        return VehicleState(
            speed = VehicleValue(snapshot.speedMetersPerSecond?.toDouble(), DataSourceType.GPS, snapshot.speedMetersPerSecond != null, timestamp),
            speedUnit = VehicleValue(SpeedUnit.METERS_PER_SECOND, DataSourceType.GPS, true, timestamp),
            latitude = VehicleValue(snapshot.latitude, DataSourceType.GPS, true, timestamp),
            longitude = VehicleValue(snapshot.longitude, DataSourceType.GPS, true, timestamp),
            altitude = VehicleValue(snapshot.altitudeMeters, DataSourceType.GPS, snapshot.altitudeMeters != null, timestamp),
            accuracy = VehicleValue(snapshot.accuracyMeters?.toDouble(), DataSourceType.GPS, snapshot.accuracyMeters != null, timestamp),
            heading = VehicleValue(snapshot.bearingDegrees?.toDouble(), DataSourceType.GPS, snapshot.bearingDegrees != null, timestamp),
            gear = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            rpm = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            fuelLevel = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            fuelConsumption = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            engineTemperature = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            turnSignal = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            lights = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            parkingBrake = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            cruiseControl = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            cruiseControlSpeed = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            engineBrake = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            trailerAttached = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.CONNECTED, DataSourceType.GPS, true, timestamp),
        )
    }

    fun toUnavailableVehicleState(timestampMillis: Long): VehicleState {
        val timestamp = Instant.ofEpochMilli(timestampMillis)
        return VehicleState(
            speed = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            speedUnit = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            latitude = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            longitude = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            altitude = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            accuracy = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            heading = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            gear = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            rpm = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            fuelLevel = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            fuelConsumption = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            engineTemperature = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            turnSignal = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            lights = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            parkingBrake = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            cruiseControl = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            cruiseControlSpeed = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            engineBrake = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            trailerAttached = VehicleValue(null, DataSourceType.GPS, false, timestamp),
            vehicleConnectionStatus = VehicleValue(VehicleConnectionStatus.DISCONNECTED, DataSourceType.GPS, false, timestamp),
        )
    }
}