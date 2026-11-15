package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.SpeedPoint
import com.example.pnu_me.domain.units.SpeedUnits
import kotlin.math.ceil

data class TelemetryChartScale(
    val startTimestampMillis: Long,
    val durationSeconds: Double,
    val maximumSpeedKph: Double,
    val yTicksKph: List<Double>,
    val xTicksSeconds: List<Double>,
)

object TelemetryChartScaleFactory {
    fun from(points: List<SpeedPoint>): TelemetryChartScale? {
        if (points.size < 2) return null

        val startTimestampMillis = points.first().timestampMillis
        val durationMillis = points.last().timestampMillis - startTimestampMillis
        if (durationMillis <= 0L) return null

        val rawMaximumKph = points.maxOf {
            SpeedUnits.metersPerSecondToKilometersPerHour(it.speedMps)
        }.coerceAtLeast(1.0)
        val axisMaximumKph = (ceil(rawMaximumKph / 10.0) * 10.0).coerceAtLeast(10.0)
        val durationSeconds = durationMillis / 1_000.0

        return TelemetryChartScale(
            startTimestampMillis = startTimestampMillis,
            durationSeconds = durationSeconds,
            maximumSpeedKph = axisMaximumKph,
            yTicksKph = listOf(axisMaximumKph, axisMaximumKph / 2.0, 0.0),
            xTicksSeconds = listOf(0.0, durationSeconds / 2.0, durationSeconds),
        )
    }
}
