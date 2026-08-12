package com.thuanmazda.opendrivehmi.data.ets2

data class Ets2TelemetryDto(
    val game: Ets2GameDto?,
    val truck: Ets2TruckDto?,
    val trailer: Ets2TrailerDto?,
    val job: Ets2JobDto?,
    val navigation: Ets2NavigationDto?,
)

data class Ets2GameDto(
    val connected: Boolean?,
    val paused: Boolean?,
    val gameName: String?,
    val time: String?,
    val timeScale: Double?,
    val nextRestStopTime: String?,
    val version: String?,
    val telemetryPluginVersion: String?,
)

data class Ets2TruckDto(
    val speed: Double?,
    val cruiseControlSpeed: Double?,
    val cruiseControlOn: Boolean?,
    val gear: Int?,
    val displayedGear: Int?,
    val engineRpm: Double?,
    val fuel: Double?,
    val fuelAverageConsumption: Double?,
    val blinkerLeftOn: Boolean?,
    val blinkerRightOn: Boolean?,
    val lightsParkingOn: Boolean?,
    val lightsBeamLowOn: Boolean?,
    val lightsBeamHighOn: Boolean?,
    val parkBrakeOn: Boolean?,
    val motorBrakeOn: Boolean?,
    val retarderBrake: Int?,
    val retarderStepCount: Int?,
)

data class Ets2TrailerDto(
    val attached: Boolean?,
    val id: String?,
    val name: String?,
    val mass: Double?,
    val wear: Double?,
    val placement: Ets2PlacementDto?,
)

data class Ets2JobDto(
    val income: Int?,
    val deadlineTime: String?,
    val remainingTime: String?,
    val sourceCity: String?,
    val sourceCompany: String?,
    val destinationCity: String?,
    val destinationCompany: String?,
)

data class Ets2PlacementDto(
    val x: Double?,
    val y: Double?,
    val z: Double?,
    val heading: Double?,
    val pitch: Double?,
    val roll: Double?,
)

data class Ets2NavigationDto(
    val estimatedTime: String?,
    val estimatedDistance: Int?,
    val speedLimit: Int?,
)