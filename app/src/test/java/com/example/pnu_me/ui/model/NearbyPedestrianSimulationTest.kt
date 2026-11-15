package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.hazard.InMemoryPedestrianAlertChannel
import com.example.pnu_me.domain.hazard.PedestrianAlertInbox
import com.example.pnu_me.domain.hazard.PedestrianSafetyAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPedestrianSimulationTest {
    private val now = 1_000_000L
    private val alert = PedestrianSafetyAlert(
        alertId = "p-1",
        latitude = 35.232,
        longitude = 129.083,
        issuedAtMillis = now,
        expiresAtMillis = now + 90_000L,
        isDemo = true,
    )

    @Test
    fun simulation_notifiesOnlyEligibleRecipientsWithinOneKilometer() {
        val results = runNearbyPedestrianSimulation(
            alert = alert,
            channel = InMemoryPedestrianAlertChannel(),
            nowMillis = now,
        ).associateBy { it.pedestrian }

        assertTrue(results.getValue(SimulatedPedestrian.NEAR_OPTED_IN).outcome is PedestrianAlertInbox.Outcome.Show)
        assertTrue(results.getValue(SimulatedPedestrian.BOUNDARY_OPTED_IN).outcome is PedestrianAlertInbox.Outcome.Show)
        assertEquals(
            PedestrianAlertInbox.Outcome.Suppressed("1km 범위 밖"),
            results.getValue(SimulatedPedestrian.OUTSIDE_RADIUS).outcome,
        )
        assertEquals(
            PedestrianAlertInbox.Outcome.Suppressed("사용자가 알림 참여 안 함"),
            results.getValue(SimulatedPedestrian.OPTED_OUT).outcome,
        )
        assertEquals(
            PedestrianAlertInbox.Outcome.Suppressed("모바일 알림 권한 없음"),
            results.getValue(SimulatedPedestrian.NOTIFICATION_DENIED).outcome,
        )
    }

    @Test
    fun outcomeText_isNeutralAndActionable() {
        val result = runNearbyPedestrianSimulation(
            alert = alert,
            channel = InMemoryPedestrianAlertChannel(),
            nowMillis = now,
        ).first { it.pedestrian == SimulatedPedestrian.NEAR_OPTED_IN }
        val text = pedestrianOutcomeText(result.outcome)

        assertTrue("좌우" in text)
        assertTrue("접근하지 마세요" in text)
        assertFalse("음주" in text)
        assertFalse("35." in text)
        assertFalse("129." in text)
    }
}
