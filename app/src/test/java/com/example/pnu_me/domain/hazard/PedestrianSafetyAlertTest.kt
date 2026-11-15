package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.ImpairedDrivingCue
import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReporterLocation
import com.example.pnu_me.domain.report.SuspicionLevel
import com.example.pnu_me.domain.report.SuspicionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PedestrianSafetyAlertTest {
    private val now = 1_000_000L
    private val strong = SuspicionResult(
        level = SuspicionLevel.STRONG,
        distinctCues = setOf(ImpairedDrivingCue.WEAVING, ImpairedDrivingCue.SLOW_SPEED),
        reason = "test",
    )
    private val draft = PoliceReportDraft(
        createdAtMillis = now,
        suspicion = strong,
        location = ReporterLocation(
            latitude = 35.231649,
            longitude = 129.082549,
            accuracyMeters = 8.0,
            timestampMillis = now,
        ),
        locationStatus = LocationStatus.VALID,
        isDemo = true,
    )

    @Test
    fun strongDraftWithValidLocation_createsShortLivedCoarseAlert() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!

        assertEquals(35.232, alert.latitude, 1e-9)
        assertEquals(129.083, alert.longitude, 1e-9)
        assertEquals(now, alert.issuedAtMillis)
        assertEquals(now + 90_000L, alert.expiresAtMillis)
        assertTrue(alert.isDemo)
        assertFalse(alert.isExpired(now + 89_999L))
        assertTrue(alert.isExpired(now + 90_000L))
    }

    @Test
    fun moderateOrUnusableLocation_createsNoPublicAlert() {
        val moderate = strong.copy(level = SuspicionLevel.MODERATE)

        assertNull(PedestrianSafetyAlertFactory.fromDraft(draft.copy(suspicion = moderate), "p-1", now))
        assertNull(
            PedestrianSafetyAlertFactory.fromDraft(
                draft.copy(locationStatus = LocationStatus.STALE),
                "p-1",
                now,
            ),
        )
    }

    @Test
    fun sourceLocationWorseThanOneHundredMeters_createsNoPublicAlert() {
        val inaccurate = draft.copy(
            location = draft.location!!.copy(accuracyMeters = 100.1),
        )

        assertNull(PedestrianSafetyAlertFactory.fromDraft(inaccurate, "p-1", now))
    }

    @Test
    fun neutralNotificationText_neverClaimsAlcoholOrLeaksLocation() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val text = alert.notificationText()

        assertTrue("1km 이내" in text)
        assertTrue("좌우" in text)
        assertFalse("음주" in text)
        assertFalse("35." in text)
        assertFalse("129." in text)
        assertFalse("번호판" in text)
    }

    @Test
    fun optedInReceiverWithinBoundary_isNotified() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val atBoundary = receiverAt(alert, distanceMeters = 1_000.0)

        assertTrue(PedestrianAlertPolicy().evaluate(alert, atBoundary, now) is PedestrianRelevanceResult.Notify)
    }

    @Test
    fun receiverBeyondOneKilometer_isIgnored() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val outside = receiverAt(alert, distanceMeters = 1_001.0)

        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.TOO_FAR),
            PedestrianAlertPolicy().evaluate(alert, outside, now),
        )
    }

    @Test
    fun consentNotificationFreshnessAndMode_areRequired() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val receiver = receiverAt(alert, distanceMeters = 300.0)

        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.NOT_OPTED_IN),
            PedestrianAlertPolicy().evaluate(alert, receiver.copy(optedIn = false), now),
        )
        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.NOTIFICATIONS_DISABLED),
            PedestrianAlertPolicy().evaluate(alert, receiver.copy(notificationsAllowed = false), now),
        )
        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.STALE_RECEIVER_LOCATION),
            PedestrianAlertPolicy().evaluate(
                alert,
                receiver.copy(locationUpdatedAtMillis = now - 120_000L),
                now,
            ),
        )
        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.DEMO_MISMATCH),
            PedestrianAlertPolicy().evaluate(alert, receiver.copy(isDemo = false), now),
        )
    }

    @Test
    fun invalidReceiverPosition_isIgnored() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val invalid = receiverAt(alert, distanceMeters = 300.0).copy(latitude = Double.NaN)

        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.INVALID_POSITION),
            PedestrianAlertPolicy().evaluate(alert, invalid, now),
        )
    }

    @Test
    fun invalidOrFutureAlertPayload_isIgnored() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val receiver = receiverAt(alert, distanceMeters = 300.0)

        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.INVALID_ALERT),
            PedestrianAlertPolicy().evaluate(alert.copy(latitude = Double.NaN), receiver, now),
        )
        assertEquals(
            PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.INVALID_ALERT),
            PedestrianAlertPolicy().evaluate(alert.copy(issuedAtMillis = now + 1L), receiver, now),
        )
    }

    @Test
    fun inbox_deduplicatesAndRateLimitsEligibleAlerts() {
        val first = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val receiver = receiverAt(first, distanceMeters = 300.0)
        val inbox = PedestrianAlertInbox()

        assertTrue(inbox.receive(first, receiver, now) is PedestrianAlertInbox.Outcome.Show)
        assertEquals(
            PedestrianAlertInbox.Outcome.Suppressed("이미 받은 알림"),
            inbox.receive(first, receiver, now + 1L),
        )
        val second = first.copy(alertId = "p-2", issuedAtMillis = now + 60_000L, expiresAtMillis = now + 150_000L)
        assertEquals(
            PedestrianAlertInbox.Outcome.Suppressed("최근 알림 직후라 표시 안 함"),
            inbox.receive(second, receiver.copy(locationUpdatedAtMillis = now + 60_000L), now + 60_000L),
        )
        val third = first.copy(alertId = "p-3", issuedAtMillis = now + 120_000L, expiresAtMillis = now + 210_000L)
        assertTrue(
            inbox.receive(third, receiver.copy(locationUpdatedAtMillis = now + 120_000L), now + 120_000L)
                is PedestrianAlertInbox.Outcome.Show,
        )
    }

    @Test
    fun ignoredAlert_doesNotConsumeRateLimit() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val receiver = receiverAt(alert, distanceMeters = 300.0)
        val inbox = PedestrianAlertInbox()

        assertTrue(
            inbox.receive(alert, receiver.copy(optedIn = false), now) is PedestrianAlertInbox.Outcome.Suppressed,
        )
        assertTrue(inbox.receive(alert, receiver, now + 1L) is PedestrianAlertInbox.Outcome.Show)
    }

    @Test
    fun inMemoryChannel_deliversUntilSubscriptionIsCancelled() {
        val alert = PedestrianSafetyAlertFactory.fromDraft(draft, "p-1", now)!!
        val channel = InMemoryPedestrianAlertChannel()
        val received = mutableListOf<String>()
        val subscription = channel.subscribe { received += it.alertId }

        channel.publish(alert)
        subscription.cancel()
        channel.publish(alert.copy(alertId = "p-2"))

        assertEquals(listOf("p-1"), received)
    }

    private fun receiverAt(
        alert: PedestrianSafetyAlert,
        distanceMeters: Double,
    ): PedestrianReceiverState {
        val (latitude, longitude) = GeoMath.destination(
            alert.latitude,
            alert.longitude,
            bearingDegrees = 0.0,
            distanceMeters = distanceMeters,
        )
        return PedestrianReceiverState(
            latitude = latitude,
            longitude = longitude,
            locationUpdatedAtMillis = now,
            optedIn = true,
            notificationsAllowed = true,
            isDemo = true,
        )
    }
}
