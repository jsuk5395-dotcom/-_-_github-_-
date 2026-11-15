package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.hazard.HazardAlert
import com.example.pnu_me.domain.hazard.HazardAlertInbox
import com.example.pnu_me.domain.hazard.InMemoryHazardChannel
import com.example.pnu_me.domain.report.SuspicionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyVehicleSimulationTest {
    private val now = 1_000_000L
    private fun alert(heading: Double?) = HazardAlert(
        alertId = "a",
        latitude = 35.232,
        longitude = 129.083,
        headingDegrees = heading,
        level = SuspicionLevel.STRONG,
        issuedAtMillis = now,
        expiresAtMillis = now + 90_000L,
        isDemo = true,
    )

    private fun results(heading: Double?) =
        runNearbySimulation(alert(heading), InMemoryHazardChannel(), now).associateBy { it.vehicle }

    private fun outcomes(heading: Double?) = results(heading).mapValues { it.value.outcome }

    @Test
    fun withHeading_onlyNearSameDirectionVehicleIsNotified() {
        val results = outcomes(heading = 90.0)
        assertEquals(3, results.size)
        assertEquals(
            HazardAlertInbox.Outcome.Show(alert(90.0), 300),
            results[SimulatedVehicle.SAME_DIRECTION_NEAR],
        )
        assertTrue(results[SimulatedVehicle.OPPOSITE_DIRECTION] is HazardAlertInbox.Outcome.Suppressed)
        assertTrue(results[SimulatedVehicle.SAME_DIRECTION_FAR] is HazardAlertInbox.Outcome.Suppressed)
    }

    @Test
    fun withoutHeading_directionFilterIsNotApplied() {
        val results = outcomes(heading = null)
        assertTrue(results[SimulatedVehicle.OPPOSITE_DIRECTION] is HazardAlertInbox.Outcome.Show)
        assertTrue(results[SimulatedVehicle.SAME_DIRECTION_FAR] is HazardAlertInbox.Outcome.Suppressed)
    }

    @Test
    fun onlyRelevantVehicleGetsLastReportedZoneMarker() {
        val results = results(heading = 90.0)
        val nearMarker = results.getValue(SimulatedVehicle.SAME_DIRECTION_NEAR).mapMarker
        assertEquals(300, nearMarker?.distanceMeters)
        assertTrue(results.getValue(SimulatedVehicle.OPPOSITE_DIRECTION).mapMarker == null)
        assertTrue(results.getValue(SimulatedVehicle.SAME_DIRECTION_FAR).mapMarker == null)
    }

    @Test
    fun bannerAndMapCopyAreNeutralAndDoNotClaimLiveTracking() {
        val banner = hazardBannerText(300)
        val mapStatus = hazardMapStatusText()
        assertTrue("300m" in banner)
        assertFalse("음주" in banner)
        assertTrue("마지막 제보 구역" in mapStatus)
        assertTrue("실시간 추적 아님" in mapStatus)
        assertTrue("90초" in mapStatus)
        assertFalse("음주" in mapStatus)
    }

    @Test
    fun simulation_unsubscribesAfterPublish() {
        val channel = InMemoryHazardChannel()
        runNearbySimulation(alert(0.0), channel, now)
        var delivered = 0
        channel.subscribe { delivered++ }
        channel.publish(alert(0.0))
        assertEquals(1, delivered)
    }
}
