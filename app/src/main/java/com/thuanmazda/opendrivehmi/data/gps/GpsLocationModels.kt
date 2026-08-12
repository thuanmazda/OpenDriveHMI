package com.thuanmazda.opendrivehmi.data.gps

sealed interface GpsLocationEvent {
    data class Fix(val snapshot: GpsLocationSnapshot) : GpsLocationEvent

    data object LocationDisabled : GpsLocationEvent

    data object ProviderUnavailable : GpsLocationEvent
}

enum class LocationPermissionState {
    GRANTED,
    DENIED,
    REVOKED,
}

data class GpsLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
)

interface GpsLocationClient {
    fun observeLocationUpdates(): kotlinx.coroutines.flow.Flow<GpsLocationEvent>

    fun isLocationEnabled(): Boolean
}