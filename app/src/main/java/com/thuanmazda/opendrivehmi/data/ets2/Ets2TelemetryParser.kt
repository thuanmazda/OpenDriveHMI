package com.thuanmazda.opendrivehmi.data.ets2

import org.json.JSONObject

class Ets2TelemetryParseException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class Ets2TelemetryParser {
    fun parse(rawJson: String): Ets2TelemetryDto {
        try {
            val root = JSONObject(rawJson)
            return Ets2TelemetryDto(
                game = root.optJSONObject("game")?.toGameDto(),
                truck = root.optJSONObject("truck")?.toTruckDto(),
                trailer = root.optJSONObject("trailer")?.toTrailerDto(),
                job = root.optJSONObject("job")?.toJobDto(),
                navigation = root.optJSONObject("navigation")?.toNavigationDto(),
            )
        } catch (throwable: Throwable) {
            throw Ets2TelemetryParseException("Malformed ETS2 telemetry JSON", throwable)
        }
    }

    private fun JSONObject.toGameDto(): Ets2GameDto {
        return Ets2GameDto(
            connected = optBooleanOrNull("connected"),
            paused = optBooleanOrNull("paused"),
            gameName = optStringOrNull("gameName"),
            time = optStringOrNull("time"),
            timeScale = optDoubleOrNull("timeScale"),
            nextRestStopTime = optStringOrNull("nextRestStopTime"),
            version = optStringOrNull("version"),
            telemetryPluginVersion = optStringOrNull("telemetryPluginVersion"),
        )
    }

    private fun JSONObject.toTruckDto(): Ets2TruckDto {
        return Ets2TruckDto(
            speed = optDoubleOrNull("speed"),
            cruiseControlSpeed = optDoubleOrNull("cruiseControlSpeed"),
            cruiseControlOn = optBooleanOrNull("cruiseControlOn"),
            gear = optIntOrNull("gear"),
            displayedGear = optIntOrNull("displayedGear"),
            engineRpm = optDoubleOrNull("engineRpm"),
            fuel = optDoubleOrNull("fuel"),
            fuelAverageConsumption = optDoubleOrNull("fuelAverageConsumption"),
            blinkerLeftOn = optBooleanOrNull("blinkerLeftOn"),
            blinkerRightOn = optBooleanOrNull("blinkerRightOn"),
            lightsParkingOn = optBooleanOrNull("lightsParkingOn"),
            lightsBeamLowOn = optBooleanOrNull("lightsBeamLowOn"),
            lightsBeamHighOn = optBooleanOrNull("lightsBeamHighOn"),
            parkBrakeOn = optBooleanOrNull("parkBrakeOn"),
            motorBrakeOn = optBooleanOrNull("motorBrakeOn"),
            retarderBrake = optIntOrNull("retarderBrake"),
            retarderStepCount = optIntOrNull("retarderStepCount"),
        )
    }

    private fun JSONObject.toTrailerDto(): Ets2TrailerDto {
        return Ets2TrailerDto(
            attached = optBooleanOrNull("attached"),
            id = optStringOrNull("id"),
            name = optStringOrNull("name"),
            mass = optDoubleOrNull("mass"),
            wear = optDoubleOrNull("wear"),
            placement = optJSONObject("placement")?.toPlacementDto(),
        )
    }

    private fun JSONObject.toJobDto(): Ets2JobDto {
        return Ets2JobDto(
            income = optIntOrNull("income"),
            deadlineTime = optStringOrNull("deadlineTime"),
            remainingTime = optStringOrNull("remainingTime"),
            sourceCity = optStringOrNull("sourceCity"),
            sourceCompany = optStringOrNull("sourceCompany"),
            destinationCity = optStringOrNull("destinationCity"),
            destinationCompany = optStringOrNull("destinationCompany"),
        )
    }

    private fun JSONObject.toNavigationDto(): Ets2NavigationDto {
        return Ets2NavigationDto(
            estimatedTime = optStringOrNull("estimatedTime"),
            estimatedDistance = optIntOrNull("estimatedDistance"),
            speedLimit = optIntOrNull("speedLimit"),
        )
    }

    private fun JSONObject.toPlacementDto(): Ets2PlacementDto {
        return Ets2PlacementDto(
            x = optDoubleOrNull("x"),
            y = optDoubleOrNull("y"),
            z = optDoubleOrNull("z"),
            heading = optDoubleOrNull("heading"),
            pitch = optDoubleOrNull("pitch"),
            roll = optDoubleOrNull("roll"),
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (has(name) && !isNull(name)) optString(name, null) else null
    }

    private fun JSONObject.optBooleanOrNull(name: String): Boolean? {
        return if (has(name) && !isNull(name)) optBoolean(name) else null
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        return if (has(name) && !isNull(name)) optDouble(name) else null
    }
}