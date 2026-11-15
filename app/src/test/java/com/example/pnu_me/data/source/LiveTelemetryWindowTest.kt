package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.DataQuality
import com.example.pnu_me.data.model.TelemetryOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTelemetryWindowTest {
    @Test
    fun samplesFasterThanMinimumInterval_areDropped() {
        val window = LiveTelemetryWindow()

        assertTrue(window.onSpeed(10_000, 10.0))
        assertFalse(window.onSpeed(10_100, 10.3))
        assertFalse(window.onSpeed(10_499, 10.3))
        assertTrue(window.onSpeed(10_500, 10.3))

        assertEquals(listOf(0L, 500L), window.snapshot().map { it.timestampMillis })
    }

    @Test
    fun samplesAreVehicleOriginWithRelativeTime() {
        val window = LiveTelemetryWindow()
        window.onSpeed(5_000, 12.0)

        val sample = window.snapshot().single()
        assertEquals(0L, sample.timestampMillis)
        assertEquals(TelemetryOrigin.VEHICLE, sample.origin)
        assertEquals(DataQuality.VALID, sample.quality)
    }

    @Test
    fun latestSteeringIsAttachedToNextSpeedSample() {
        val window = LiveTelemetryWindow()
        window.onSpeed(0, 10.0)
        window.onSteering(15.0)
        window.onSteering(-40.0)
        window.onSpeed(500, 10.0)

        val samples = window.snapshot()
        assertNull(samples[0].steeringDegrees)
        assertEquals(-40.0, samples[1].steeringDegrees!!, 0.0)
    }

    @Test
    fun nonFiniteSteering_isIgnored() {
        val window = LiveTelemetryWindow()
        window.onSteering(20.0)
        window.onSteering(Double.NaN)
        window.onSpeed(0, 10.0)

        assertEquals(20.0, window.snapshot().single().steeringDegrees!!, 0.0)
    }

    @Test
    fun missingSpeed_isKeptAsMissingInsteadOfInventingAValue() {
        val window = LiveTelemetryWindow()
        window.onSpeed(0, null)
        window.onSpeed(500, Double.POSITIVE_INFINITY)

        val samples = window.snapshot()
        assertTrue(samples.all { it.speedMps == null })
        assertTrue(samples.all { it.quality == DataQuality.MISSING })
    }

    @Test
    fun gapLongerThanMaximum_startsNewSegment() {
        val window = LiveTelemetryWindow()
        window.onSpeed(0, 10.0)
        window.onSpeed(500, 10.0)
        window.onSpeed(4_000, 10.0)

        assertEquals(listOf(4_000L), window.snapshot().map { it.timestampMillis })
    }

    @Test
    fun snapshotAfterMaximumGap_discardsStaleSamples() {
        val window = LiveTelemetryWindow(maxGapMillis = 3_000L)
        window.onSpeed(1_000L, 10.0)

        assertTrue(window.snapshot(4_000L).isNotEmpty())
        assertTrue(window.snapshot(4_001L).isEmpty())
    }

    @Test
    fun onlyRecentWindowIsKept() {
        val window = LiveTelemetryWindow(windowMillis = 2_000L)
        (0..10).forEach { window.onSpeed(it * 500L, 10.0) }

        assertEquals(
            listOf(3_000L, 3_500L, 4_000L, 4_500L, 5_000L),
            window.snapshot().map { it.timestampMillis },
        )
    }

    @Test
    fun clear_resetsTimeOriginAndSteering() {
        val window = LiveTelemetryWindow()
        window.onSteering(30.0)
        window.onSpeed(1_000, 10.0)
        window.clear()
        window.onSpeed(9_000, 10.0)

        val sample = window.snapshot().single()
        assertEquals(0L, sample.timestampMillis)
        assertNull(sample.steeringDegrees)
    }
}
