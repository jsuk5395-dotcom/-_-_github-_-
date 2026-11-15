package com.example.pnu_me.domain.report

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WitnessDetailsTest {
    private val suspicion = SuspicionResult(
        level = SuspicionLevel.MODERATE,
        distinctCues = setOf(ImpairedDrivingCue.DRIFTING),
        reason = "test",
    )

    @Test
    fun normalized_removesUnsupportedPlateCharactersAndLimitsLength() {
        val details = WitnessDetails(
            observedAtMillis = 1_000L,
            plateFragment = " 12가-3456 !!! ABCDEFG ",
        )

        assertEquals("12가-3456ABCDE", details.normalized().plateFragment)
    }

    @Test
    fun smsBody_includesOnlyProvidedStructuredDetails() {
        val details = WitnessDetails(
            observedAtMillis = 60_000L,
            vehicleType = WitnessVehicleType.SUV,
            vehicleColor = WitnessVehicleColor.WHITE,
            lastSeenDirection = WitnessDirection.NORTH,
            plateFragment = "12가3456",
            stillVisible = true,
        )
        val draft = PoliceReportDraft(
            createdAtMillis = 60_000L,
            suspicion = suspicion,
            location = null,
            locationStatus = LocationStatus.UNAVAILABLE,
            isDemo = true,
            witnessDetails = details,
        )

        val body = draft.toSmsBody(timeZone = TimeZone.getTimeZone("UTC"))

        assertTrue(body.contains("목격 시각 1970-01-01 00:01"))
        assertTrue(body.contains("차량 SUV"))
        assertTrue(body.contains("색상 흰색"))
        assertTrue(body.contains("마지막 진행방향 북쪽"))
        assertTrue(body.contains("번호판 일부 12가3456"))
        assertTrue(body.contains("현재 시야에 있음"))
    }

    @Test
    fun smsBody_omitsBlankOptionalDetails() {
        val draft = PoliceReportDraft(
            createdAtMillis = 1_000L,
            suspicion = suspicion,
            location = null,
            locationStatus = LocationStatus.UNAVAILABLE,
            isDemo = true,
            witnessDetails = WitnessDetails(observedAtMillis = 1_000L, plateFragment = " !!! "),
        )

        val body = draft.toSmsBody(timeZone = TimeZone.getTimeZone("UTC"))

        assertFalse(body.contains("번호판 일부"))
        assertFalse(body.contains("차량 종류"))
    }
}
