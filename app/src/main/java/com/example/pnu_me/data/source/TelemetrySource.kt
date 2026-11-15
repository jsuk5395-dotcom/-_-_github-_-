package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

/**
 * MOCK 입력과 향후 검증된 차량 입력을 분석 엔진에서 분리하는 계약.
 */
interface TelemetrySource {
    val origin: TelemetryOrigin
    val displayName: String

    fun samplesFor(scenario: DrivingScenario): List<TelemetrySample>
}
