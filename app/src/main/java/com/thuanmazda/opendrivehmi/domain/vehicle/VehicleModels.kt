package com.thuanmazda.opendrivehmi.domain.vehicle

import java.time.Duration
import java.time.Instant

enum class DataSourceType {
    GPS,
    ETS2,
    DEMO,
    NONE,
}

enum class SpeedUnit {
    KILOMETERS_PER_HOUR,
    MILES_PER_HOUR,
    METERS_PER_SECOND,
}

enum class TurnSignalState {
    OFF,
    LEFT,
    RIGHT,
    HAZARD,
}

enum class ParkingBrakeState {
    RELEASED,
    ENGAGED,
    UNKNOWN,
}

enum class CruiseControlState {
    OFF,
    STANDBY,
    ACTIVE,
    PAUSED,
    UNKNOWN,
}

enum class VehicleConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    DEGRADED,
    UNKNOWN,
}

enum class RouteAvailability {
    AVAILABLE,
    UNAVAILABLE,
    UNKNOWN,
}

enum class NavigationManeuverType {
    STRAIGHT,
    LEFT,
    RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    U_TURN,
    EXIT,
    MERGE,
    ROUNDABOUT,
    ARRIVE,
    UNKNOWN,
}

data class VehicleValue<T>(
    val value: T?,
    val source: DataSourceType,
    val available: Boolean,
    val timestamp: Instant,
)

data class VectorState(
    val x: VehicleValue<Double>,
    val y: VehicleValue<Double>,
    val z: VehicleValue<Double>,
)

data class PlacementState(
    val x: VehicleValue<Double>,
    val y: VehicleValue<Double>,
    val z: VehicleValue<Double>,
    val heading: VehicleValue<Double>,
    val pitch: VehicleValue<Double>,
    val roll: VehicleValue<Double>,
)

data class GameState(
    val connected: VehicleValue<Boolean>,
    val paused: VehicleValue<Boolean>,
    val gameName: VehicleValue<String>,
    val time: VehicleValue<Duration>,
    val timeScale: VehicleValue<Double>,
    val nextRestStopTime: VehicleValue<Duration>,
    val version: VehicleValue<String>,
    val telemetryPluginVersion: VehicleValue<String>,
)

data class TrailerState(
    val attached: VehicleValue<Boolean>,
    val id: VehicleValue<String>,
    val name: VehicleValue<String>,
    val mass: VehicleValue<Double>,
    val wear: VehicleValue<Double>,
    val placement: PlacementState,
)

data class JobState(
    val income: VehicleValue<Int>,
    val deadlineTime: VehicleValue<Duration>,
    val remainingTime: VehicleValue<Duration>,
    val sourceCity: VehicleValue<String>,
    val sourceCompany: VehicleValue<String>,
    val destinationCity: VehicleValue<String>,
    val destinationCompany: VehicleValue<String>,
)

data class VehicleLightsState(
    val positionLights: Boolean,
    val lowBeam: Boolean,
    val highBeam: Boolean,
    val fogLights: Boolean,
    val hazardLights: Boolean,
    val daytimeRunningLights: Boolean,
)

data class RetarderState(
    val active: Boolean,
    val level: Int?,
)

data class NavigationManeuver(
    val type: NavigationManeuverType,
    val instruction: String?,
)

data class VehicleState(
    val speed: VehicleValue<Double>,
    val speedUnit: VehicleValue<SpeedUnit>,
    val latitude: VehicleValue<Double>,
    val longitude: VehicleValue<Double>,
    val altitude: VehicleValue<Double>,
    val accuracy: VehicleValue<Double> = VehicleValue(null, DataSourceType.NONE, false, Instant.EPOCH),
    val heading: VehicleValue<Double>,
    val gear: VehicleValue<String>,
    val rpm: VehicleValue<Double>,
    val fuelLevel: VehicleValue<Double>,
    val fuelConsumption: VehicleValue<Double>,
    val engineTemperature: VehicleValue<Double>,
    val turnSignal: VehicleValue<TurnSignalState>,
    val lights: VehicleValue<VehicleLightsState>,
    val parkingBrake: VehicleValue<ParkingBrakeState>,
    val cruiseControl: VehicleValue<CruiseControlState>,
    val cruiseControlSpeed: VehicleValue<Double>,
    val engineBrake: VehicleValue<RetarderState>,
    val trailerAttached: VehicleValue<Boolean>,
    val vehicleConnectionStatus: VehicleValue<VehicleConnectionStatus>,
)

data class Ets2TelemetryState(
    val game: GameState,
    val truck: VehicleState,
    val trailer: TrailerState,
    val job: JobState,
    val navigation: NavigationState,
)

data class NavigationState(
    val routeAvailability: VehicleValue<RouteAvailability>,
    val destination: VehicleValue<String>,
    val distance: VehicleValue<Double>,
    val estimatedTime: VehicleValue<Duration>,
    val speedLimit: VehicleValue<Double>,
    val nextManeuver: VehicleValue<NavigationManeuver>,
    val navigationHeading: VehicleValue<Double>,
)