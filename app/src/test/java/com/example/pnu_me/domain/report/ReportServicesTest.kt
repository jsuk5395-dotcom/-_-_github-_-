package com.example.pnu_me.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportServicesTest {
    @Test
    fun readiness_onlyExposesReadyChannels() {
        val readiness = ReportReadiness(
            location = CapabilityStatus.READY,
            dialer = CapabilityStatus.READY,
            sms = CapabilityStatus.UNAVAILABLE,
            voiceGuidance = CapabilityStatus.READY,
            voiceResponse = CapabilityStatus.BLOCKED,
            isDemo = true,
        )

        assertEquals(setOf(ReportChannel.DIALER), readiness.availableChannels)
        assertFalse(readiness.canUseSms)
        assertTrue(readiness.canReadDraftAloud)
        assertFalse(readiness.canAcceptVoiceResponse)
    }

    @Test
    fun openedExternalApp_isNeverReportedAsCompleted() {
        val outcome = ReportHandoffResult.OpenedUnconfirmed(ReportChannel.SMS)

        assertEquals(ReportChannel.SMS, outcome.channel)
        assertFalse(outcome.reportConfirmed)
    }

    @Test
    fun readinessFactory_mapsPermissionAndHandlerFailures() {
        val readiness = ReportReadinessFactory.create(
            hasLocationPermission = false,
            locationEnabled = false,
            dialerAvailable = false,
            smsAvailable = true,
            voiceGuidanceAvailable = false,
            isDemo = true,
        )

        assertEquals(CapabilityStatus.NEEDS_PERMISSION, readiness.location)
        assertEquals(CapabilityStatus.UNAVAILABLE, readiness.dialer)
        assertEquals(CapabilityStatus.READY, readiness.sms)
        assertEquals(CapabilityStatus.UNAVAILABLE, readiness.voiceGuidance)
        assertEquals(CapabilityStatus.BLOCKED, readiness.voiceResponse)
    }

    @Test
    fun readinessFactory_requiresEnabledLocationAfterPermission() {
        val readiness = ReportReadinessFactory.create(
            hasLocationPermission = true,
            locationEnabled = false,
            dialerAvailable = true,
            smsAvailable = true,
            voiceGuidanceAvailable = true,
            isDemo = false,
        )

        assertEquals(CapabilityStatus.DISABLED, readiness.location)
        assertEquals(setOf(ReportChannel.DIALER, ReportChannel.SMS), readiness.availableChannels)
    }

    @Test
    fun safeVoiceSummary_omitsSensitiveValuesAndCompletionClaim() {
        val suspicion = SuspicionResult(
            level = SuspicionLevel.STRONG,
            distinctCues = setOf(ImpairedDrivingCue.WEAVING),
            reason = "test",
        )
        val draft = PoliceReportDraft(
            createdAtMillis = 1_000L,
            suspicion = suspicion,
            location = ReporterLocation(35.12345, 129.54321, 5.0, 1_000L),
            locationStatus = LocationStatus.VALID,
            isDemo = true,
            witnessDetails = WitnessDetails(
                observedAtMillis = 900L,
                plateFragment = "12가3456",
            ),
        )

        val spoken = draft.toSafeVoiceSummary()

        assertFalse(spoken.contains("35.12345"))
        assertFalse(spoken.contains("129.54321"))
        assertFalse(spoken.contains("12가3456"))
        assertFalse(spoken.contains("신고 완료"))
        assertTrue(spoken.contains("초안"))
        assertTrue(spoken.contains("사용자가 직접"))
    }
}
