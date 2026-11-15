package com.example.pnu_me.ui.model

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.domain.report.CueSource
import com.example.pnu_me.domain.report.LocationFetchResult
import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReporterLocation
import com.example.pnu_me.domain.report.SuspicionEvaluator
import com.example.pnu_me.domain.report.SuspicionLevel
import com.example.pnu_me.domain.report.SuspicionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuspectReportUiModelTest {
    private val now = 1_000_000L
    private val evaluator = SuspicionEvaluator()

    private fun levelOf(preset: MockCuePreset) = evaluator.evaluate(preset.observations(now), now).level

    @Test
    fun presets_produceDocumentedLevels() {
        assertEquals(SuspicionLevel.STRONG, levelOf(MockCuePreset.WEAVING_AND_SLOW))
        assertEquals(SuspicionLevel.MODERATE, levelOf(MockCuePreset.SUDDEN_ACCELERATION))
        assertEquals(SuspicionLevel.MODERATE, levelOf(MockCuePreset.DRIFT_AND_STOP))
        assertEquals(SuspicionLevel.WEAK, levelOf(MockCuePreset.SINGLE_SLOW))
    }

    @Test
    fun presetObservations_areAlwaysMockAndRecent() {
        MockCuePreset.entries.forEach { preset ->
            preset.observations(now).forEach {
                assertEquals(CueSource.MOCK_SCENARIO, it.source)
                assertEquals(TelemetryOrigin.MOCK, it.origin)
                assertTrue(it.timestampMillis < now)
            }
        }
    }

    @Test
    fun remainingSeconds_roundsUpAndClampsAtZero() {
        assertEquals(15, consentRemainingSeconds(0L, 0L, 15_000L))
        assertEquals(15, consentRemainingSeconds(0L, 1L, 15_000L))
        assertEquals(1, consentRemainingSeconds(0L, 14_999L, 15_000L))
        assertEquals(0, consentRemainingSeconds(0L, 15_000L, 15_000L))
        assertEquals(0, consentRemainingSeconds(0L, 20_000L, 15_000L))
    }

    @Test
    fun locationMessage_onlyForFailures() {
        assertNull(locationFetchMessage(null))
        assertNull(locationFetchMessage(LocationFetchResult.Success(ReporterLocation(35.0, 129.0, 5.0, now))))
        assertTrue("권한" in locationFetchMessage(LocationFetchResult.PermissionDenied)!!)
    }

    @Test
    fun draftLocationText_showsOriginAndNeverInventsLocation() {
        val suspicion = SuspicionResult(SuspicionLevel.STRONG, emptySet(), "t")
        val vehicle = ReporterLocation(35.1, 129.1, 7.0, now, origin = TelemetryOrigin.VEHICLE)
        val valid = PoliceReportDraft(now, suspicion, vehicle, LocationStatus.VALID, isDemo = true)
        assertTrue("기기 위치" in draftLocationText(valid))
        assertTrue("35.10000, 129.10000" in draftLocationText(valid))

        val stale = valid.copy(locationStatus = LocationStatus.STALE)
        assertFalse("35.1" in draftLocationText(stale))
        val none = valid.copy(location = null, locationStatus = LocationStatus.UNAVAILABLE)
        assertTrue("위치 없음" in draftLocationText(none))
    }

    @Test
    fun capabilityStatusText_distinguishesPermissionAndBlockedStates() {
        assertEquals(
            "권한 필요",
            capabilityStatusText(com.example.pnu_me.domain.report.CapabilityStatus.NEEDS_PERMISSION),
        )
        assertEquals(
            "공식 정책 검증 전",
            capabilityStatusText(com.example.pnu_me.domain.report.CapabilityStatus.BLOCKED),
        )
    }

    @Test
    fun handoffMessage_neverClaimsReportCompleted() {
        val message = handoffMessage(
            com.example.pnu_me.domain.report.ReportHandoffResult.OpenedUnconfirmed(
                com.example.pnu_me.domain.report.ReportChannel.DIALER,
            ),
        )

        assertTrue("완료 여부" in message)
        assertFalse("신고 완료" in message)
    }

    @Test
    fun retentionCountdown_roundsUpAndClampsAtZero() {
        assertEquals(10, draftRetentionRemainingSeconds(discardAtMillis = 10_000L, nowMillis = 0L))
        assertEquals(1, draftRetentionRemainingSeconds(discardAtMillis = 10_000L, nowMillis = 9_999L))
        assertEquals(0, draftRetentionRemainingSeconds(discardAtMillis = 10_000L, nowMillis = 10_000L))
        assertEquals(0, draftRetentionRemainingSeconds(discardAtMillis = 10_000L, nowMillis = 11_000L))
        assertEquals(
            Int.MAX_VALUE,
            draftRetentionRemainingSeconds(discardAtMillis = Long.MAX_VALUE, nowMillis = 0L),
        )
    }
}
