package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {
    @Test
    fun accelerationUsesElapsedTimeAndMetersPerSecond() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = 2_000, speed = 16.0),
                sample(time = 4_000, speed = 16.0),
                sample(time = 6_000, speed = 16.0),
            ),
        )

        assertEquals(1, features.events.size)
        assertEquals(3.0, features.events.single().accelerationMps2, 0.0001)
        assertTrue(features.maximumAbsoluteAccelerationMps2 >= 3.0)
    }

    @Test
    fun accelerationImmediatelyBelowThreshold_isNotAnEvent() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 0.0),
                sample(time = 1_000, speed = 2.499),
            ),
        )

        assertEquals(0, features.events.size)
    }

    @Test
    fun accelerationExactlyAtThreshold_isAnEvent() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 0.0),
                sample(time = 1_000, speed = 2.5),
            ),
        )

        assertEquals(1, features.events.size)
    }

    @Test
    fun accelerationImmediatelyAboveThreshold_isAnEvent() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 0.0),
                sample(time = 1_000, speed = 2.501),
            ),
        )

        assertEquals(1, features.events.size)
    }

    @Test
    fun secondEventImmediatelyBeforeCooldownBoundary_isSuppressed() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 0.0),
                sample(time = 1_000, speed = 3.0),
                sample(time = 1_749, speed = 5.0),
            ),
        )

        assertEquals(1, features.events.size)
    }

    @Test
    fun secondEventExactlyAtCooldownBoundary_isAccepted() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 0.0),
                sample(time = 1_000, speed = 3.0),
                sample(time = 1_750, speed = 5.0),
            ),
        )

        assertEquals(2, features.events.size)
        assertEquals(1_750L, features.events.last().timestampMillis)
    }

    // --- SAFE DRIVE 조향 방향 반전 규칙 (8° 이상 변화, 8초 안 반전 2회, 10km/h 이상, 재감지 10초) ---

    @Test
    fun twoSteeringReversalsWithinWindow_isSteeringEvent() {
        val features = FeatureExtractor().extract(
            steeringSamples(0.0, 30.0, -30.0, 30.0),
        )

        val steering = features.events.filter { it.type == RiskEventType.STEERING_REVERSAL }
        assertEquals(1, steering.size)
        assertEquals(3_000L, steering.single().timestampMillis)
    }

    @Test
    fun singleSteeringReversal_isNotAnEvent() {
        val features = FeatureExtractor().extract(
            steeringSamples(0.0, 30.0, -30.0, -30.0),
        )

        assertTrue(features.events.none { it.type == RiskEventType.STEERING_REVERSAL })
    }

    @Test
    fun steeringChangesBelowMinimumDegrees_areIgnored() {
        val features = FeatureExtractor().extract(
            steeringSamples(0.0, 7.9, 0.0, 7.9, 0.0),
        )

        assertTrue(features.events.none { it.type == RiskEventType.STEERING_REVERSAL })
    }

    @Test
    fun steeringReversalsBelowMinimumSpeed_areIgnored() {
        val features = FeatureExtractor().extract(
            steeringSamples(0.0, 30.0, -30.0, 30.0, speedMps = 2.0),
        )

        assertTrue(features.events.none { it.type == RiskEventType.STEERING_REVERSAL })
    }

    @Test
    fun reversalsSpreadBeyondWindow_areNotAnEvent() {
        val features = FeatureExtractor().extract(
            // 첫 변화(5s)에서 구간 시작 → 반전 1(10s) → 15s는 8초 초과로 새 구간
            steeringSamples(0.0, 30.0, -30.0, 30.0, intervalMillis = 5_000L),
        )

        assertTrue(features.events.none { it.type == RiskEventType.STEERING_REVERSAL })
    }

    @Test
    fun repeatedSteeringEventInsideCooldown_isSuppressed() {
        val features = FeatureExtractor().extract(
            steeringSamples(0.0, 30.0, -30.0, 30.0, -30.0, 30.0, -30.0),
        )

        assertEquals(1, features.events.count { it.type == RiskEventType.STEERING_REVERSAL })
    }

    @Test
    fun samplesWithoutSteering_produceNoSteeringEvents() {
        val features = FeatureExtractor().extract(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 2_000, speed = 10.0),
                sample(time = 3_000, speed = 10.0),
            ),
        )

        assertTrue(features.events.isEmpty())
    }

    private fun sample(time: Long, speed: Double) = TelemetrySample(
        timestampMillis = time,
        speedMps = speed,
        origin = TelemetryOrigin.MOCK,
    )

    private fun steeringSamples(
        vararg steeringDegrees: Double,
        speedMps: Double = 11.0,
        intervalMillis: Long = 1_000L,
    ): List<TelemetrySample> = steeringDegrees.mapIndexed { index, degrees ->
        TelemetrySample(
            timestampMillis = index * intervalMillis,
            speedMps = speedMps,
            steeringDegrees = degrees,
            origin = TelemetryOrigin.VEHICLE,
        )
    }
}
