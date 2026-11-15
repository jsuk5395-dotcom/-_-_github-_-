package com.example.pnu_me.domain.report

import org.junit.Assert.assertEquals
import org.junit.Test

class ReporterLocationValidatorTest {
    private val now = 1_000_000L
    private fun loc(
        lat: Double = 35.0,
        lon: Double = 129.0,
        acc: Double = 10.0,
        at: Long = now,
        heading: Double? = null,
    ) = ReporterLocation(lat, lon, acc, at, heading)

    @Test
    fun validLocation() = assertEquals(LocationStatus.VALID, ReporterLocationValidator.validate(loc(), now))

    @Test
    fun nullLocation_isUnavailable() =
        assertEquals(LocationStatus.UNAVAILABLE, ReporterLocationValidator.validate(null, now))

    @Test
    fun outOfRangeOrNonFinite_isInvalid() {
        assertEquals(LocationStatus.INVALID, ReporterLocationValidator.validate(loc(lat = 91.0), now))
        assertEquals(LocationStatus.INVALID, ReporterLocationValidator.validate(loc(lon = -180.1), now))
        assertEquals(LocationStatus.INVALID, ReporterLocationValidator.validate(loc(lat = Double.NaN), now))
        assertEquals(LocationStatus.INVALID, ReporterLocationValidator.validate(loc(acc = -1.0), now))
        assertEquals(
            LocationStatus.INVALID,
            ReporterLocationValidator.validate(loc(heading = Double.POSITIVE_INFINITY), now),
        )
        assertEquals(LocationStatus.INVALID, ReporterLocationValidator.validate(loc(at = now + 1), now))
    }

    @Test
    fun oldFix_isStale_boundaryIsValid() {
        val max = ReporterLocationValidator.MAX_AGE_MILLIS
        assertEquals(LocationStatus.VALID, ReporterLocationValidator.validate(loc(at = now - max), now))
        assertEquals(LocationStatus.STALE, ReporterLocationValidator.validate(loc(at = now - max - 1), now))
    }
}
