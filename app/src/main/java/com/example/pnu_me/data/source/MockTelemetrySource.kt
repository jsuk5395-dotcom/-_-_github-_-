package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

/**
 * 로컬 CSV를 결정론적으로 읽는 시연용 데이터 소스.
 */
class MockTelemetrySource(
    private val scenarioReader: ScenarioReader,
) : TelemetrySource {
    override val origin: TelemetryOrigin = TelemetryOrigin.MOCK
    override val displayName: String = "MOCK · 로컬 시나리오"

    override fun samplesFor(scenario: DrivingScenario): List<TelemetrySample> =
        ScenarioCsvParser.parse(
            csv = scenarioReader.read(scenario),
            origin = origin,
        )
}
