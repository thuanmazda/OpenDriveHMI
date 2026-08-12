package com.thuanmazda.opendrivehmi.data.ets2

import com.thuanmazda.opendrivehmi.domain.vehicle.CruiseControlState
import com.thuanmazda.opendrivehmi.domain.vehicle.DataSourceType
import com.thuanmazda.opendrivehmi.domain.vehicle.Ets2TelemetryState
import com.thuanmazda.opendrivehmi.domain.vehicle.GameState
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationManeuver
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationManeuverType
import com.thuanmazda.opendrivehmi.domain.vehicle.NavigationState
import com.thuanmazda.opendrivehmi.domain.vehicle.ParkingBrakeState
import com.thuanmazda.opendrivehmi.domain.vehicle.PlacementState
import com.thuanmazda.opendrivehmi.domain.vehicle.RouteAvailability
import com.thuanmazda.opendrivehmi.domain.vehicle.RetarderState
import com.thuanmazda.opendrivehmi.domain.vehicle.SpeedUnit
import com.thuanmazda.opendrivehmi.domain.vehicle.TurnSignalState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleConnectionStatus
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleLightsState
import com.thuanmazda.opendrivehmi.domain.vehicle.JobState
import com.thuanmazda.opendrivehmi.domain.vehicle.TrailerState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleState
import com.thuanmazda.opendrivehmi.domain.vehicle.VehicleValue
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Ets2TelemetryMapper {
    fun mapTelemetryState(dto: Ets2TelemetryDto, receivedAt: Instant): Ets2TelemetryState {
        return Ets2TelemetryState(
            game = mapGameState(dto, receivedAt),
            truck = mapVehicleState(dto, receivedAt),
            trailer = mapTrailerState(dto, receivedAt),
            job = mapJobState(dto, receivedAt),
            navigation = mapNavigationState(dto, receivedAt),
        )
    }

    fun mapGameState(dto: Ets2TelemetryDto, receivedAt: Instant): GameState {
        val game = dto.game
        return GameState(
            connected = VehicleValue(game?.connected, DataSourceType.ETS2, game?.connected != null, receivedAt),
            paused = VehicleValue(game?.paused, DataSourceType.ETS2, game?.paused != null, receivedAt),
            gameName = VehicleValue(game?.gameName, DataSourceType.ETS2, game?.gameName != null, receivedAt),
            time = VehicleValue(parseDuration(game?.time), DataSourceType.ETS2, game?.time != null, receivedAt),
            timeScale = VehicleValue(game?.timeScale, DataSourceType.ETS2, game?.timeScale != null, receivedAt),
            nextRestStopTime = VehicleValue(parseDuration(game?.nextRestStopTime), DataSourceType.ETS2, game?.nextRestStopTime != null, receivedAt),
            version = VehicleValue(game?.version, DataSourceType.ETS2, game?.version != null, receivedAt),
            telemetryPluginVersion = VehicleValue(game?.telemetryPluginVersion, DataSourceType.ETS2, game?.telemetryPluginVersion != null, receivedAt),
        )
    }

    fun mapVehicleState(dto: Ets2TelemetryDto, receivedAt: Instant): VehicleState {
        val truck = dto.truck
        val gameConnected = dto.game?.connected == true

        return VehicleState(
            speed = vehicleValue(truck?.speed, DataSourceType.ETS2, receivedAt),
            speedUnit = VehicleValue(SpeedUnit.KILOMETERS_PER_HOUR, DataSourceType.ETS2, truck?.speed != null, receivedAt),
            latitude = unavailable(DataSourceType.ETS2, receivedAt),
            longitude = unavailable(DataSourceType.ETS2, receivedAt),
            altitude = unavailable(DataSourceType.ETS2, receivedAt),
            accuracy = unavailable(DataSourceType.ETS2, receivedAt),
            heading = unavailable(DataSourceType.ETS2, receivedAt),
            gear = VehicleValue(formatGear(truck?.displayedGear ?: truck?.gear), DataSourceType.ETS2, truck?.displayedGear != null || truck?.gear != null, receivedAt),
            rpm = VehicleValue(truck?.engineRpm, DataSourceType.ETS2, truck?.engineRpm != null, receivedAt),
            fuelLevel = vehicleValue(truck?.fuel, DataSourceType.ETS2, receivedAt),
            fuelConsumption = vehicleValue(truck?.fuelAverageConsumption, DataSourceType.ETS2, receivedAt),
            engineTemperature = unavailable(DataSourceType.ETS2, receivedAt),
            turnSignal = VehicleValue(resolveTurnSignal(truck?.blinkerLeftOn, truck?.blinkerRightOn), DataSourceType.ETS2, truck?.blinkerLeftOn != null || truck?.blinkerRightOn != null, receivedAt),
            lights = VehicleValue(
                VehicleLightsState(
                    positionLights = truck?.lightsParkingOn ?: false,
                    lowBeam = truck?.lightsBeamLowOn ?: false,
                    highBeam = truck?.lightsBeamHighOn ?: false,
                    fogLights = false,
                    hazardLights = (truck?.blinkerLeftOn == true && truck?.blinkerRightOn == true),
                    daytimeRunningLights = false,
                ),
                DataSourceType.ETS2,
                truck?.lightsParkingOn != null || truck?.lightsBeamLowOn != null || truck?.lightsBeamHighOn != null,
                receivedAt,
            ),
            parkingBrake = VehicleValue(resolveParkingBrake(truck?.parkBrakeOn), DataSourceType.ETS2, truck?.parkBrakeOn != null, receivedAt),
            cruiseControl = VehicleValue(resolveCruiseControl(truck?.cruiseControlOn), DataSourceType.ETS2, truck?.cruiseControlOn != null, receivedAt),
            cruiseControlSpeed = vehicleValue(truck?.cruiseControlSpeed, DataSourceType.ETS2, receivedAt),
            engineBrake = VehicleValue(resolveRetarder(truck?.motorBrakeOn, truck?.retarderBrake, truck?.retarderStepCount), DataSourceType.ETS2, truck?.motorBrakeOn != null || truck?.retarderBrake != null || truck?.retarderStepCount != null, receivedAt),
            trailerAttached = VehicleValue(dto.trailer?.attached, DataSourceType.ETS2, dto.trailer?.attached != null, receivedAt),
            vehicleConnectionStatus = VehicleValue(if (gameConnected) VehicleConnectionStatus.CONNECTED else VehicleConnectionStatus.DISCONNECTED, DataSourceType.ETS2, dto.game?.connected != null, receivedAt),
        )
    }

    fun mapTrailerState(dto: Ets2TelemetryDto, receivedAt: Instant): TrailerState {
        val trailer = dto.trailer
        val placement = trailer?.placement
        return TrailerState(
            attached = VehicleValue(trailer?.attached, DataSourceType.ETS2, trailer?.attached != null, receivedAt),
            id = VehicleValue(trailer?.id, DataSourceType.ETS2, trailer?.id != null, receivedAt),
            name = VehicleValue(trailer?.name, DataSourceType.ETS2, trailer?.name != null, receivedAt),
            mass = VehicleValue(trailer?.mass, DataSourceType.ETS2, trailer?.mass != null, receivedAt),
            wear = VehicleValue(trailer?.wear, DataSourceType.ETS2, trailer?.wear != null, receivedAt),
            placement = PlacementState(
                x = vehicleValue(placement?.x, DataSourceType.ETS2, receivedAt),
                y = vehicleValue(placement?.y, DataSourceType.ETS2, receivedAt),
                z = vehicleValue(placement?.z, DataSourceType.ETS2, receivedAt),
                heading = vehicleValue(placement?.heading, DataSourceType.ETS2, receivedAt),
                pitch = vehicleValue(placement?.pitch, DataSourceType.ETS2, receivedAt),
                roll = vehicleValue(placement?.roll, DataSourceType.ETS2, receivedAt),
            ),
        )
    }

    fun mapJobState(dto: Ets2TelemetryDto, receivedAt: Instant): JobState {
        val job = dto.job
        return JobState(
            income = VehicleValue(job?.income, DataSourceType.ETS2, job?.income != null, receivedAt),
            deadlineTime = VehicleValue(parseDuration(job?.deadlineTime), DataSourceType.ETS2, job?.deadlineTime != null, receivedAt),
            remainingTime = VehicleValue(parseDuration(job?.remainingTime), DataSourceType.ETS2, job?.remainingTime != null, receivedAt),
            sourceCity = VehicleValue(job?.sourceCity, DataSourceType.ETS2, job?.sourceCity != null, receivedAt),
            sourceCompany = VehicleValue(job?.sourceCompany, DataSourceType.ETS2, job?.sourceCompany != null, receivedAt),
            destinationCity = VehicleValue(job?.destinationCity, DataSourceType.ETS2, job?.destinationCity != null, receivedAt),
            destinationCompany = VehicleValue(job?.destinationCompany, DataSourceType.ETS2, job?.destinationCompany != null, receivedAt),
        )
    }

    fun mapNavigationState(dto: Ets2TelemetryDto, receivedAt: Instant): NavigationState {
        val navigation = dto.navigation
        return NavigationState(
            routeAvailability = unavailable(DataSourceType.ETS2, receivedAt),
            destination = unavailable(DataSourceType.ETS2, receivedAt),
            distance = VehicleValue(navigation?.estimatedDistance?.toDouble(), DataSourceType.ETS2, navigation?.estimatedDistance != null, receivedAt),
            estimatedTime = VehicleValue(parseDuration(navigation?.estimatedTime), DataSourceType.ETS2, navigation?.estimatedTime != null, receivedAt),
            speedLimit = VehicleValue(navigation?.speedLimit?.toDouble(), DataSourceType.ETS2, navigation?.speedLimit != null, receivedAt),
            nextManeuver = unavailable(DataSourceType.ETS2, receivedAt),
            navigationHeading = unavailable(DataSourceType.ETS2, receivedAt),
        )
    }

    private fun vehicleValue(value: Double?, source: DataSourceType, timestamp: Instant): VehicleValue<Double> {
        return VehicleValue(value, source, value != null, timestamp)
    }

    private fun <T> unavailable(source: DataSourceType, timestamp: Instant): VehicleValue<T> {
        return VehicleValue(null, source, false, timestamp)
    }

    private fun formatGear(gear: Int?): String? {
        return when {
            gear == null -> null
            gear > 0 -> "D$gear"
            gear < 0 -> "R${kotlin.math.abs(gear)}"
            else -> "N"
        }
    }

    private fun resolveTurnSignal(left: Boolean?, right: Boolean?): TurnSignalState? {
        return when {
            left == null && right == null -> null
            left == true && right == true -> TurnSignalState.HAZARD
            left == true -> TurnSignalState.LEFT
            right == true -> TurnSignalState.RIGHT
            else -> TurnSignalState.OFF
        }
    }

    private fun resolveParkingBrake(value: Boolean?): ParkingBrakeState? {
        return when (value) {
            true -> ParkingBrakeState.ENGAGED
            false -> ParkingBrakeState.RELEASED
            null -> null
        }
    }

    private fun resolveCruiseControl(value: Boolean?): CruiseControlState? {
        return when (value) {
            true -> CruiseControlState.ACTIVE
            false -> CruiseControlState.OFF
            null -> null
        }
    }

    private fun resolveRetarder(motorBrakeOn: Boolean?, retarderBrake: Int?, retarderStepCount: Int?): RetarderState? {
        val active = motorBrakeOn == true || (retarderBrake != null && retarderBrake > 0)
        val level = when {
            retarderBrake != null -> retarderBrake
            retarderStepCount != null -> retarderStepCount
            else -> null
        }
        if (motorBrakeOn == null && retarderBrake == null && retarderStepCount == null) {
            return null
        }
        return RetarderState(active = active, level = level)
    }

    private fun parseDuration(value: String?): Duration? {
        if (value == null) return null
        return try {
            val parsed = OffsetDateTime.parse(value)
            Duration.between(OffsetDateTime.of(1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), parsed)
        } catch (_: Throwable) {
            null
        }
    }
}