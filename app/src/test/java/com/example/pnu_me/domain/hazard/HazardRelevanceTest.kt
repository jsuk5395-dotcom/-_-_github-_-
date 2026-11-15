package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.SuspicionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HazardRelevanceTest {
    private val now = 1_000_000L
    private val filter = HazardRelevanceFilter()
    private val alert = HazardAlert(
        alertId = "a",
        latitude = 35.0,
        longitude = 129.0,
        headingDegrees = 0.0,
        level = SuspicionLevel.STRONG,
        issuedAtMillis = now,
        expiresAtMillis = now + 90_000L,
        isDemo = true,
    )

    private fun receiverAt(
        bearing: Double,
        meters: Double,
        heading: Double?,
        demo: Boolean = true,
        accuracyMeters: Double = 10.0,
        updatedAtMillis: Long = now,
    ): ReceiverState {
        val (lat, lon) = GeoMath.destination(alert.latitude, alert.longitude, bearing, meters)
        return ReceiverState(
            latitude = lat,
            longitude = lon,
            headingDegrees = heading,
            accuracyMeters = accuracyMeters,
            locationUpdatedAtMillis = updatedAtMillis,
            isDemo = demo,
        )
    }

    @Test
    fun geoMath_destinationAndDistanceAreConsistent() {
        val (lat, lon) = GeoMath.destination(35.0, 129.0, 45.0, 1_000.0)
        assertEquals(1_000.0, GeoMath.distanceMeters(35.0, 129.0, lat, lon), 1.0)
    }

    @Test
    fun headingDifference_wrapsAround() {
        assertEquals(20.0, GeoMath.headingDifference(350.0, 10.0), 1e-9)
        assertEquals(180.0, GeoMath.headingDifference(0.0, 180.0), 1e-9)
        assertEquals(0.0, GeoMath.headingDifference(720.0, 0.0), 1e-9)
    }

    @Test
    fun nearSameDirection_notifiesWithRoundedDistance() {
        val result = filter.evaluate(alert, receiverAt(180.0, 320.0, 10.0), now)
        assertEquals(RelevanceResult.Notify(300), result)
    }

    @Test
    fun veryClose_reportsAtLeast100m() {
        assertEquals(RelevanceResult.Notify(100), filter.evaluate(alert, receiverAt(180.0, 10.0, 0.0), now))
    }

    @Test
    fun exactOneKilometerIsEligibleButBeyondIsTooFar() {
        assertTrue(filter.evaluate(alert, receiverAt(180.0, 1_000.0, 0.0), now) is RelevanceResult.Notify)
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.TOO_FAR),
            filter.evaluate(alert, receiverAt(180.0, 1_001.0, 0.0), now),
        )
    }

    @Test
    fun oppositeDirection_isIgnored_butBoundaryIsNotified() {
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.DIFFERENT_DIRECTION),
            filter.evaluate(alert, receiverAt(0.0, 400.0, 180.0), now),
        )
        assertTrue(filter.evaluate(alert, receiverAt(0.0, 400.0, 60.0), now) is RelevanceResult.Notify)
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.DIFFERENT_DIRECTION),
            filter.evaluate(alert, receiverAt(0.0, 400.0, 61.0), now),
        )
    }

    @Test
    fun missingHeading_skipsDirectionFilter() {
        assertTrue(filter.evaluate(alert, receiverAt(0.0, 400.0, null), now) is RelevanceResult.Notify)
        assertTrue(
            filter.evaluate(alert.copy(headingDegrees = null), receiverAt(0.0, 400.0, 180.0), now)
                is RelevanceResult.Notify,
        )
    }

    @Test
    fun expiredDemoMismatchAndInvalidAlertAreIgnored() {
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.EXPIRED),
            filter.evaluate(alert, receiverAt(180.0, 100.0, 0.0), now + 90_000L),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.DEMO_MISMATCH),
            filter.evaluate(alert, receiverAt(180.0, 100.0, 0.0, demo = false), now),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.INVALID_ALERT),
            filter.evaluate(alert.copy(latitude = Double.NaN), receiverAt(180.0, 100.0, 0.0), now),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.INVALID_ALERT),
            filter.evaluate(alert.copy(alertId = " "), receiverAt(180.0, 100.0, 0.0), now),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.INVALID_ALERT),
            filter.evaluate(alert.copy(expiresAtMillis = now + 90_001L), receiverAt(180.0, 100.0, 0.0), now),
        )
    }

    @Test
    fun staleInaccurateOrInvalidReceiverGpsIsIgnored() {
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.STALE_RECEIVER_LOCATION),
            filter.evaluate(alert, receiverAt(180.0, 100.0, 0.0, updatedAtMillis = now - 120_000L), now),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.INACCURATE_RECEIVER_LOCATION),
            filter.evaluate(alert, receiverAt(180.0, 100.0, 0.0, accuracyMeters = 100.1), now),
        )
        assertEquals(
            RelevanceResult.Ignore(IgnoreReason.INVALID_POSITION),
            filter.evaluate(
                alert,
                ReceiverState(
                    latitude = Double.NaN,
                    longitude = 129.0,
                    headingDegrees = 0.0,
                    accuracyMeters = 10.0,
                    locationUpdatedAtMillis = now,
                    isDemo = true,
                ),
                now,
            ),
        )
    }

    @Test
    fun inbox_deduplicatesAndRateLimits() {
        val inbox = HazardAlertInbox()
        val receiver = receiverAt(180.0, 300.0, 0.0)
        assertTrue(inbox.receive(alert, receiver, now) is HazardAlertInbox.Outcome.Show)
        assertTrue(inbox.receive(alert, receiver, now + 1) is HazardAlertInbox.Outcome.Suppressed)
        val second = alert.copy(alertId = "b")
        assertTrue(inbox.receive(second, receiver, now + 59_999L) is HazardAlertInbox.Outcome.Suppressed)
        val third = alert.copy(alertId = "c")
        assertTrue(inbox.receive(third, receiver, now + 60_000L) is HazardAlertInbox.Outcome.Show)
    }

    @Test
    fun ignoredAlertDoesNotConsumeRateLimitOrDuplicateId() {
        val inbox = HazardAlertInbox()
        inbox.receive(alert, receiverAt(180.0, 5_000.0, 0.0), now)
        assertTrue(inbox.receive(alert, receiverAt(180.0, 300.0, 0.0), now + 1) is HazardAlertInbox.Outcome.Show)
    }

    @Test
    fun inMemoryChannel_deliversToSubscribersUntilCancelled() {
        val channel = InMemoryHazardChannel()
        val received = mutableListOf<String>()
        val subscription = channel.subscribe { received += it.alertId }
        channel.publish(alert)
        subscription.cancel()
        channel.publish(alert.copy(alertId = "b"))
        assertEquals(listOf("a"), received)
    }
}
