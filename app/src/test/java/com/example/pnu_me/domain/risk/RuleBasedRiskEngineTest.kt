package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.data.source.DrivingScenario
import com.example.pnu_me.data.source.MockTelemetrySource
import com.example.pnu_me.data.source.ScenarioReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedRiskEngineTest {
    private val engine = RuleBasedRiskEngine()

    @Test
    fun normalScenario_isNormal() {
        val result = analyze(
            """
            timestamp_ms,speed_mps,steering_deg,quality
            0,10.0,,VALID
            1000,10.4,,VALID
            2000,10.9,,VALID
            3000,10.7,,VALID
            4000,11.1,,VALID
            """.trimIndent(),
        )

        assertEquals(RiskState.NORMAL, result.state)
        assertEquals(0, result.events.size)
        assertEquals(TelemetryOrigin.MOCK, result.origin)
    }

    @Test
    fun repeatedChanges_recommendsStop() {
        val csv = """
            timestamp_ms,speed_mps,steering_deg,quality
            0,10.0,,VALID
            2000,16.0,,VALID
            4000,9.0,,VALID
            6000,16.5,,VALID
            8000,9.0,,VALID
            10000,17.0,,VALID
        """.trimIndent()

        val result = analyze(csv)

        assertEquals(RiskState.STOP_RECOMMENDED, result.state)
        assertTrue(result.events.size >= 3)
    }

    @Test
    fun singleBrake_requestsReviewButDoesNotRecommendStop() {
        val result = analyze(
            """
            timestamp_ms,speed_mps,steering_deg,quality
            0,15.0,,VALID
            1000,15.0,,VALID
            2000,8.0,,VALID
            3000,8.0,,VALID
            4000,8.0,,VALID
            """.trimIndent(),
        )

        assertEquals(RiskState.REVIEW, result.state)
        assertEquals(1, result.events.size)
    }

    @Test
    fun missingSpeed_isUnavailable() {
        val result = analyze(
            """
            timestamp_ms,speed_mps,steering_deg,quality
            0,10.0,,VALID
            1000,10.5,,VALID
            2000,,,MISSING
            3000,10.2,,VALID
            4000,10.0,,VALID
            """.trimIndent(),
        )

        assertEquals(RiskState.UNAVAILABLE, result.state)
        assertEquals(ValidationIssue.MISSING_SPEED, result.quality.issue)
    }

    @Test
    fun reversedTime_isUnavailable() {
        val result = analyze(
            """
            timestamp_ms,speed_mps,steering_deg,quality
            0,10.0,,VALID
            1000,10.5,,VALID
            500,11.0,,VALID
            2000,10.8,,VALID
            """.trimIndent(),
        )

        assertEquals(RiskState.UNAVAILABLE, result.state)
        assertEquals(ValidationIssue.NON_MONOTONIC_TIME, result.quality.issue)
    }

    @Test
    fun sameScenario_isDeterministic() {
        val csv = """
            timestamp_ms,speed_mps,steering_deg,quality
            0,10.0,,VALID
            2000,16.0,,VALID
            4000,9.0,,VALID
            6000,16.5,,VALID
            8000,9.0,,VALID
        """.trimIndent()

        assertEquals(analyze(csv), analyze(csv))
    }

    @Test
    fun twoSeparatedEvents_remainReview() {
        val result = engine.analyze(
            samples = listOf(
                TelemetrySample(0, 0.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(1_000, 3.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(2_000, 3.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(3_000, 6.0, origin = TelemetryOrigin.MOCK),
            ),
            origin = TelemetryOrigin.MOCK,
        )

        assertEquals(2, result.events.size)
        assertEquals(RiskState.REVIEW, result.state)
    }

    @Test
    fun exactlyThreeSeparatedEvents_recommendStop() {
        val result = engine.analyze(
            samples = listOf(
                TelemetrySample(0, 0.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(1_000, 3.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(2_000, 6.0, origin = TelemetryOrigin.MOCK),
                TelemetrySample(3_000, 9.0, origin = TelemetryOrigin.MOCK),
            ),
            origin = TelemetryOrigin.MOCK,
        )

        assertEquals(3, result.events.size)
        assertEquals(RiskState.STOP_RECOMMENDED, result.state)
    }

    @Test
    fun requestedOriginDifferentFromSamples_isUnavailable() {
        val vehicleSamples = listOf(
            TelemetrySample(0, 10.0, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(1_000, 10.5, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(2_000, 11.0, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(3_000, 11.5, origin = TelemetryOrigin.VEHICLE),
        )

        val result = engine.analyze(
            samples = vehicleSamples,
            origin = TelemetryOrigin.MOCK,
        )

        assertEquals(RiskState.UNAVAILABLE, result.state)
        assertEquals(ValidationIssue.ORIGIN_MISMATCH, result.quality.issue)
    }

    @Test
    fun requestedVehicleWithMockSamples_keepsObservedMockOrigin() {
        val mockSamples = listOf(
            TelemetrySample(0, 10.0, origin = TelemetryOrigin.MOCK),
            TelemetrySample(1_000, 10.5, origin = TelemetryOrigin.MOCK),
            TelemetrySample(2_000, 11.0, origin = TelemetryOrigin.MOCK),
            TelemetrySample(3_000, 11.5, origin = TelemetryOrigin.MOCK),
        )

        val result = engine.analyze(
            samples = mockSamples,
            origin = TelemetryOrigin.VEHICLE,
        )

        assertEquals(RiskState.UNAVAILABLE, result.state)
        assertEquals(ValidationIssue.ORIGIN_MISMATCH, result.quality.issue)
        assertEquals(TelemetryOrigin.MOCK, result.origin)
        assertTrue(result.speedPoints.isEmpty())
    }

    @Test
    fun requestedMockWithVehicleSamples_keepsObservedVehicleOrigin() {
        val vehicleSamples = listOf(
            TelemetrySample(0, 10.0, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(1_000, 10.5, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(2_000, 11.0, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(3_000, 11.5, origin = TelemetryOrigin.VEHICLE),
        )

        val result = engine.analyze(
            samples = vehicleSamples,
            origin = TelemetryOrigin.MOCK,
        )

        assertEquals(TelemetryOrigin.VEHICLE, result.origin)
    }

    @Test
    fun mixedOrigins_doNotClaimAnyVerifiedOrigin() {
        val samples = listOf(
            TelemetrySample(0, 10.0, origin = TelemetryOrigin.MOCK),
            TelemetrySample(1_000, 10.5, origin = TelemetryOrigin.VEHICLE),
            TelemetrySample(2_000, 11.0, origin = TelemetryOrigin.MOCK),
            TelemetrySample(3_000, 11.5, origin = TelemetryOrigin.VEHICLE),
        )

        val result = engine.analyze(
            samples = samples,
            origin = TelemetryOrigin.MOCK,
        )

        assertEquals(RiskState.UNAVAILABLE, result.state)
        assertEquals(ValidationIssue.ORIGIN_MISMATCH, result.quality.issue)
        assertEquals(null, result.origin)
        assertTrue(result.speedPoints.isEmpty())
    }

    @Test
    fun steeringReversalOnly_requestsReview() {
        val result = engine.analyze(
            samples = vehicleSamples(
                speeds = listOf(11.0, 11.0, 11.0, 11.0, 11.0),
                steering = listOf(0.0, 30.0, -30.0, 30.0, 30.0),
            ),
            origin = TelemetryOrigin.VEHICLE,
        )

        assertEquals(1, result.events.size)
        assertEquals(RiskEventType.STEERING_REVERSAL, result.events.single().type)
        assertEquals(RiskState.REVIEW, result.state)
    }

    @Test
    fun steeringReversalWithAbruptSpeedChangeWithinWindow_recommendsStop() {
        // SAFE DRIVE 복합 규칙: 30초 안에 불규칙 조향 + 급가감속
        val result = engine.analyze(
            samples = vehicleSamples(
                speeds = listOf(11.0, 11.0, 11.0, 11.0, 11.0, 15.0),
                steering = listOf(0.0, 30.0, -30.0, 30.0, 30.0, 30.0),
            ),
            origin = TelemetryOrigin.VEHICLE,
        )

        assertEquals(2, result.events.size)
        assertEquals(RiskState.STOP_RECOMMENDED, result.state)
        assertTrue(result.guidance.contains("조향"))
    }

    @Test
    fun steeringAndSpeedEventsFarApart_remainReview() {
        val speeds = MutableList(36) { 11.0 }.apply { this[35] = 15.0 }
        val steering = MutableList(36) { 30.0 }.apply {
            this[0] = 0.0
            this[2] = -30.0
        }

        val result = engine.analyze(
            samples = vehicleSamples(speeds = speeds, steering = steering),
            origin = TelemetryOrigin.VEHICLE,
        )

        assertEquals(2, result.events.size)
        assertEquals(RiskState.REVIEW, result.state)
    }

    private fun vehicleSamples(
        speeds: List<Double>,
        steering: List<Double>,
    ): List<TelemetrySample> = speeds.indices.map { index ->
        TelemetrySample(
            timestampMillis = index * 1_000L,
            speedMps = speeds[index],
            steeringDegrees = steering[index],
            origin = TelemetryOrigin.VEHICLE,
        )
    }

    private fun analyze(csv: String): RiskAssessment {
        val source = MockTelemetrySource(ScenarioReader { csv })
        return engine.analyze(
            samples = source.samplesFor(DrivingScenario.NORMAL),
            origin = source.origin,
        )
    }
}
