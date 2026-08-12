package com.thuanmazda.opendrivehmi.data.ets2

import org.junit.Assert.assertEquals
import org.junit.Test

class Ets2TelemetryParserTest {
    @Test
    fun malformedJson_throwsParseException() {
        val parser = Ets2TelemetryParser()

        try {
            parser.parse("{ not valid json")
            throw AssertionError("Expected Ets2TelemetryParseException")
        } catch (exception: Ets2TelemetryParseException) {
            assertEquals("Malformed ETS2 telemetry JSON", exception.message)
        }
    }

    @Test
    fun parser_preservesNullAndMissingFields() {
        val parser = Ets2TelemetryParser()
        val dto = parser.parse(
            """
            {
              "game": { "connected": false },
              "truck": { "speed": 45.0, "gear": null, "engineRpm": null },
              "trailer": { "attached": null },
              "navigation": { "estimatedTime": null }
            }
            """.trimIndent(),
        )

        assertEquals(false, dto.game?.connected)
        assertEquals(45.0, dto.truck?.speed ?: 0.0, 0.0001)
        assertEquals(null, dto.truck?.gear)
        assertEquals(null, dto.truck?.engineRpm)
        assertEquals(null, dto.trailer?.attached)
        assertEquals(null, dto.navigation?.estimatedTime)
    }
}