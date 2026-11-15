package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.SuspicionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardMapMarkerTest {
    private val now = 1_000_000L
    private val alert = HazardAlert(
        alertId = "zone-1",
        latitude = 35.0,
        longitude = 129.0,
        headingDegrees = 0.0,
        level = SuspicionLevel.STRONG,
        issuedAtMillis = now,
        expiresAtMillis = now + 90_000L,
        isDemo = true,
    )
    private val policy = HazardMapMarkerPolicy()

    private fun receiverAt(
        bearingFromAlert: Double,
        distanceMeters: Double,
        headingDegrees: Double? = 0.0,
        accuracyMeters: Double = 10.0,
        updatedAtMillis: Long = now,
    ): ReceiverState {
        val (latitude, longitude) = GeoMath.destination(
            alert.latitude,
            alert.longitude,
            bearingFromAlert,
            distanceMeters,
        )
        return ReceiverState(
            latitude = latitude,
            longitude = longitude,
            headingDegrees = headingDegrees,
            accuracyMeters = accuracyMeters,
            locationUpdatedAtMillis = updatedAtMillis,
            isDemo = true,
        )
    }

    @Test
    fun nearEligibleAlertCreatesLastReportedZoneNorthOfReceiver() {
        val decision = policy.evaluate(alert, receiverAt(180.0, 300.0), now)
        assertTrue(decision is HazardMapMarkerDecision.Show)
        val marker = (decision as HazardMapMarkerDecision.Show).marker
        assertEquals("zone-1", marker.alertId)
        assertEquals(300, marker.distanceMeters)
        assertEquals(0.0, marker.bearingFromReceiverDegrees, 0.5)
        assertEquals(100, marker.uncertaintyRadiusMeters)
        assertEquals(alert.expiresAtMillis, marker.expiresAtMillis)
        assertTrue(marker.isDemo)
    }

    @Test
    fun exactOneKilometerCreatesMarkerButBeyondDoesNot() {
        assertTrue(policy.evaluate(alert, receiverAt(180.0, 1_000.0), now) is HazardMapMarkerDecision.Show)
        assertEquals(
            HazardMapMarkerDecision.Hidden(IgnoreReason.TOO_FAR),
            policy.evaluate(alert, receiverAt(180.0, 1_001.0), now),
        )
    }

    @Test
    fun expiredOppositeDirectionOrBadGpsDoesNotCreateMarker() {
        assertEquals(
            HazardMapMarkerDecision.Hidden(IgnoreReason.EXPIRED),
            policy.evaluate(alert, receiverAt(180.0, 300.0), now + 90_000L),
        )
        assertEquals(
            HazardMapMarkerDecision.Hidden(IgnoreReason.DIFFERENT_DIRECTION),
            policy.evaluate(alert, receiverAt(180.0, 300.0, headingDegrees = 180.0), now),
        )
        assertEquals(
            HazardMapMarkerDecision.Hidden(IgnoreReason.INACCURATE_RECEIVER_LOCATION),
            policy.evaluate(alert, receiverAt(180.0, 300.0, accuracyMeters = 100.1), now),
        )
        assertEquals(
            HazardMapMarkerDecision.Hidden(IgnoreReason.STALE_RECEIVER_LOCATION),
            policy.evaluate(alert, receiverAt(180.0, 300.0, updatedAtMillis = now - 120_000L), now),
        )
    }

    @Test
    fun missingHeadingsUseDistanceOnly() {
        val noHeadingAlert = alert.copy(headingDegrees = null)
        assertTrue(
            policy.evaluate(
                noHeadingAlert,
                receiverAt(0.0, 400.0, headingDegrees = 180.0),
                now,
            ) is HazardMapMarkerDecision.Show,
        )
    }
}
