package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.ImpairedDrivingCue
import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReporterLocation
import com.example.pnu_me.domain.report.SuspicionLevel
import com.example.pnu_me.domain.report.SuspicionResult
import com.example.pnu_me.domain.report.WitnessDetails
import com.example.pnu_me.domain.report.WitnessDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardAlertTest {
    private val now = 1_000_000L
    private val strong = SuspicionResult(SuspicionLevel.STRONG, setOf(ImpairedDrivingCue.WEAVING), "t")
    private val location = ReporterLocation(35.231649, 129.082549, 5.0, now, headingDegrees = 90.0)
    private val draft = PoliceReportDraft(now, strong, location, LocationStatus.VALID, isDemo = true)

    @Test
    fun validDraft_createsAnonymousRoundedAlert() {
        val alert = HazardAlertFactory.fromDraft(draft, "id-1", now)!!
        assertEquals(35.232, alert.latitude, 1e-9)
        assertEquals(129.083, alert.longitude, 1e-9)
        assertNull(alert.headingDegrees)
        assertEquals(now + 90_000L, alert.expiresAtMillis)
        assertTrue(alert.isDemo)
    }

    @Test
    fun witnessedVehicleDirectionBecomesHeadingInsteadOfReporterDirection() {
        val withDirection = draft.copy(
            witnessDetails = WitnessDetails(
                observedAtMillis = now,
                lastSeenDirection = WitnessDirection.WEST,
            ),
        )
        val alert = HazardAlertFactory.fromDraft(withDirection, "id-direction", now)!!
        assertEquals(270.0, alert.headingDegrees!!, 0.0)
    }

    @Test
    fun draftWithoutUsableLocation_createsNoAlert() {
        assertNull(HazardAlertFactory.fromDraft(draft.copy(location = null, locationStatus = LocationStatus.UNAVAILABLE), "id", now))
        assertNull(HazardAlertFactory.fromDraft(draft.copy(locationStatus = LocationStatus.STALE), "id", now))
    }

    @Test
    fun onlyStrongSuspicionCreatesAlert() {
        val weak = SuspicionResult(SuspicionLevel.WEAK, setOf(ImpairedDrivingCue.SLOW_SPEED), "t")
        val moderate = SuspicionResult(
            SuspicionLevel.MODERATE,
            setOf(ImpairedDrivingCue.SLOW_SPEED, ImpairedDrivingCue.ACCELERATING_FOR_NO_REASON),
            "t",
        )
        assertNull(HazardAlertFactory.fromDraft(draft.copy(suspicion = weak), "id", now))
        assertNull(HazardAlertFactory.fromDraft(draft.copy(suspicion = moderate), "id", now))
    }

    @Test
    fun inaccurateOrStaleGpsAndInvalidIdCreateNoAlert() {
        assertNull(
            HazardAlertFactory.fromDraft(
                draft.copy(location = location.copy(accuracyMeters = 100.1)),
                "id",
                now,
            ),
        )
        assertNull(
            HazardAlertFactory.fromDraft(
                draft.copy(location = location.copy(timestampMillis = now - 120_001L)),
                "id",
                now,
            ),
        )
        assertNull(HazardAlertFactory.fromDraft(draft, "  ", now))
        assertNull(HazardAlertFactory.fromDraft(draft, "id", Long.MAX_VALUE - 10L))
    }

    @Test
    fun expiry_boundary() {
        val alert = HazardAlertFactory.fromDraft(draft, "id", now)!!
        assertFalse(alert.isExpired(now + HazardAlertFactory.TTL_MILLIS - 1))
        assertTrue(alert.isExpired(now + HazardAlertFactory.TTL_MILLIS))
    }
}
