package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.SpeedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TelemetryChartScaleFactoryTest {
    @Test
    fun chartScale_exposesRoundedKphAndSecondsAxes() {
        val scale = TelemetryChartScaleFactory.from(
            listOf(
                SpeedPoint(timestampMillis = 1_000, speedMps = 10.0),
                SpeedPoint(timestampMillis = 5_000, speedMps = 16.5),
                SpeedPoint(timestampMillis = 9_000, speedMps = 9.0),
            ),
        )

        assertNotNull(scale)
        requireNotNull(scale)
        assertEquals(60.0, scale.maximumSpeedKph, 0.0001)
        assertEquals(8.0, scale.durationSeconds, 0.0001)
        assertEquals(listOf(60.0, 30.0, 0.0), scale.yTicksKph)
        assertEquals(listOf(0.0, 4.0, 8.0), scale.xTicksSeconds)
    }

    @Test
    fun fewerThanTwoPoints_hasNoChartScale() {
        assertEquals(
            null,
            TelemetryChartScaleFactory.from(
                listOf(SpeedPoint(timestampMillis = 0, speedMps = 10.0)),
            ),
        )
    }
}
