package com.example.pnu_me.domain.risk

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataQualityValidatorTest {
    private val validator = DataQualityValidator()

    @Test
    fun negativeSpeed_isRejectedAsInvalidSpeed() {
        val report = validator.validate(
            validSamples().toMutableList().apply {
                this[2] = sample(time = 2_000, speed = -0.1)
            },
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.INVALID_SPEED, report.issue)
    }

    @Test
    fun nonFiniteSpeed_isRejectedAsInvalidSpeed() {
        val report = validator.validate(
            validSamples().toMutableList().apply {
                this[2] = sample(time = 2_000, speed = Double.NaN)
            },
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.INVALID_SPEED, report.issue)
    }

    @Test
    fun sampleGapAboveThreeSeconds_isRejected() {
        val report = validator.validate(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 4_001, speed = 10.0),
                sample(time = 5_000, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.EXCESSIVE_GAP, report.issue)
    }

    @Test
    fun duplicateTimestamp_isRejectedAsNonMonotonic() {
        val report = validator.validate(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 2_000, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.NON_MONOTONIC_TIME, report.issue)
    }

    @Test
    fun mixedOrigins_areRejected() {
        val report = validator.validate(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 2_000, speed = 10.0, origin = TelemetryOrigin.VEHICLE),
                sample(time = 3_000, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.ORIGIN_MISMATCH, report.issue)
    }

    @Test
    fun fewerThanFourSamples_areRejected() {
        val report = validator.validate(validSamples().take(3))

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.INSUFFICIENT_SAMPLES, report.issue)
    }

    @Test
    fun negativeRelativeTimestamp_isRejected() {
        val report = validator.validate(
            listOf(
                sample(time = -1, speed = 10.0),
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 2_000, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.NEGATIVE_TIMESTAMP, report.issue)
    }

    @Test
    fun longMinTimestamp_isRejectedBeforeSubtracting() {
        val report = validator.validate(
            listOf(
                sample(time = Long.MIN_VALUE, speed = 10.0),
                sample(time = 0, speed = 10.0),
                sample(time = 1_000, speed = 10.0),
                sample(time = 2_000, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.NEGATIVE_TIMESTAMP, report.issue)
    }

    @Test
    fun enormousPositiveGap_isRejectedWithoutOverflow() {
        val report = validator.validate(
            listOf(
                sample(time = 0, speed = 10.0),
                sample(time = Long.MAX_VALUE - 2_000, speed = 10.0),
                sample(time = Long.MAX_VALUE - 1_000, speed = 10.0),
                sample(time = Long.MAX_VALUE, speed = 10.0),
            ),
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.EXCESSIVE_GAP, report.issue)
    }

    @Test
    fun timestampsNearLongMax_withValidGapsRemainUsable() {
        val report = validator.validate(
            listOf(
                sample(time = Long.MAX_VALUE - 3_000, speed = 10.0),
                sample(time = Long.MAX_VALUE - 2_000, speed = 10.0),
                sample(time = Long.MAX_VALUE - 1_000, speed = 10.0),
                sample(time = Long.MAX_VALUE, speed = 10.0),
            ),
        )

        assertTrue(report.isUsable)
        assertEquals(ValidationIssue.NONE, report.issue)
    }

    @Test
    fun positiveInfinitySpeed_isRejectedAsInvalidSpeed() {
        val report = validator.validate(
            validSamples().toMutableList().apply {
                this[2] = sample(time = 2_000, speed = Double.POSITIVE_INFINITY)
            },
        )

        assertFalse(report.isUsable)
        assertEquals(ValidationIssue.INVALID_SPEED, report.issue)
    }

    private fun validSamples() = listOf(
        sample(time = 0, speed = 10.0),
        sample(time = 1_000, speed = 10.5),
        sample(time = 2_000, speed = 11.0),
        sample(time = 3_000, speed = 11.5),
    )

    private fun sample(
        time: Long,
        speed: Double,
        origin: TelemetryOrigin = TelemetryOrigin.MOCK,
    ) = TelemetrySample(
        timestampMillis = time,
        speedMps = speed,
        origin = origin,
    )
}
