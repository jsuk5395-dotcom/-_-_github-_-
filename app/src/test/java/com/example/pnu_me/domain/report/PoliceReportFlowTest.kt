package com.example.pnu_me.domain.report

import com.example.pnu_me.domain.report.ImpairedDrivingCue.SLOW_SPEED
import com.example.pnu_me.domain.report.ImpairedDrivingCue.WEAVING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliceReportFlowTest {
    private val strong = SuspicionResult(SuspicionLevel.STRONG, setOf(WEAVING, SLOW_SPEED), "test")
    private val weak = SuspicionResult(SuspicionLevel.WEAK, setOf(SLOW_SPEED), "test")
    private val validLocation = ReporterLocation(35.2316, 129.0825, 8.0, 1_000L, headingDegrees = 90.0)

    @Test
    fun weakSuspicion_neverAsks() {
        val flow = PoliceReportFlow()
        assertEquals(ReportFlowState.Idle, flow.onSuspicion(weak, DrivingState.PARKED, 0L))
    }

    @Test
    fun strongSuspicion_asksOccupant_andNeverCreatesDraftByItself() {
        val flow = PoliceReportFlow()
        val state = flow.onSuspicion(strong, DrivingState.PARKED, 0L)
        assertTrue(state is ReportFlowState.AwaitingConsent)
        assertFalse((state as ReportFlowState.AwaitingConsent).restrictedUi)
    }

    @Test
    fun drivingOrUnknownState_usesRestrictedUi() {
        val driving = PoliceReportFlow().onSuspicion(strong, DrivingState.DRIVING, 0L)
        val unknown = PoliceReportFlow().onSuspicion(strong, DrivingState.UNKNOWN, 0L)
        assertTrue((driving as ReportFlowState.AwaitingConsent).restrictedUi)
        assertTrue((unknown as ReportFlowState.AwaitingConsent).restrictedUi)
    }

    @Test
    fun approval_createsDemoDraftWithReporterLocation() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        val state = flow.onOccupantApproved(validLocation, 5_000L)
        val draft = (state as ReportFlowState.DraftReady).draft
        assertTrue(draft.isDemo)
        assertTrue(draft.hasUsableLocation)
        assertTrue(draft.toSmsBody().startsWith("[시연용 MOCK"))
        assertTrue("35.23160, 129.08250" in draft.toSmsBody())
    }

    @Test
    fun approvalWithoutLocation_doesNotInventLocation() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.PARKED, 0L)
        val draft = (flow.onOccupantApproved(null, 1_000L) as ReportFlowState.DraftReady).draft
        assertEquals(LocationStatus.UNAVAILABLE, draft.locationStatus)
        assertFalse(draft.hasUsableLocation)
        assertTrue("위치 확인 불가" in draft.toSmsBody())
    }

    @Test
    fun noAnswerWithinTimeout_expiresWithoutReport() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        assertTrue(flow.tick(15_000L) is ReportFlowState.AwaitingConsent)
        assertTrue(flow.tick(15_001L) is ReportFlowState.Expired)
    }

    @Test
    fun lateApproval_afterTimeout_isExpiredNotDraft() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        assertTrue(flow.onOccupantApproved(validLocation, 20_000L) is ReportFlowState.Expired)
    }

    @Test
    fun decline_preventsRepromptDuringCooldown() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        flow.onOccupantDeclined(1_000L)
        flow.finish(1_000L)
        assertEquals(ReportFlowState.Idle, flow.onSuspicion(strong, DrivingState.DRIVING, 100_000L))
        assertTrue(flow.onSuspicion(strong, DrivingState.DRIVING, 301_000L) is ReportFlowState.AwaitingConsent)
    }

    @Test
    fun duplicateSuspicion_whileAwaiting_keepsOriginalQuestion() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        val again = flow.onSuspicion(strong, DrivingState.DRIVING, 2_000L)
        assertEquals(0L, (again as ReportFlowState.AwaitingConsent).askedAtMillis)
    }

    @Test
    fun leavingDuringQuestion_closesWithoutReport_andStartsCooldown() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        assertEquals(ReportFlowState.Idle, flow.finish(2_000L))
        assertEquals(ReportFlowState.Idle, flow.onSuspicion(strong, DrivingState.DRIVING, 10_000L))
        assertTrue(flow.onSuspicion(strong, DrivingState.DRIVING, 302_000L) is ReportFlowState.AwaitingConsent)
    }

    @Test
    fun approveOrDecline_withoutQuestion_isIgnored() {
        val flow = PoliceReportFlow()
        assertEquals(ReportFlowState.Idle, flow.onOccupantApproved(validLocation, 0L))
        assertEquals(ReportFlowState.Idle, flow.onOccupantDeclined(0L))
    }

    @Test
    fun drivingApproval_defersDraftUntilParked() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        flow.onOccupantApproved(nowMillis = 1_000L, deferUntilParked = true)

        val deferred = flow.onLocationResult(
            LocationFetchResult.Success(validLocation.copy(timestampMillis = 1_500L)),
            nowMillis = 2_000L,
        )
        assertTrue(deferred is ReportFlowState.DeferredUntilParked)
        assertTrue(flow.onParked(3_000L) is ReportFlowState.DraftReady)
    }

    @Test
    fun deferredDraft_expiresAtTenMinuteBoundary() {
        val flow = PoliceReportFlow(ReportFlowConfig(draftRetentionMillis = 600_000L))
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        flow.onOccupantApproved(nowMillis = 1_000L, deferUntilParked = true)
        flow.onLocationResult(LocationFetchResult.PermissionDenied, nowMillis = 2_000L)

        assertTrue(flow.tick(601_999L) is ReportFlowState.DeferredUntilParked)
        assertTrue(flow.tick(602_000L) is ReportFlowState.DraftDiscarded)
    }

    @Test
    fun explicitFinish_clearsDeferredSensitiveData() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        flow.onOccupantApproved(nowMillis = 1_000L, deferUntilParked = true)
        flow.onLocationResult(LocationFetchResult.PermissionDenied, nowMillis = 2_000L)

        assertEquals(ReportFlowState.Idle, flow.finish(3_000L))
    }

    @Test
    fun newSuspicion_doesNotReplaceDeferredDraft() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        flow.onOccupantApproved(nowMillis = 1_000L, deferUntilParked = true)
        flow.onLocationResult(LocationFetchResult.PermissionDenied, nowMillis = 2_000L)

        assertTrue(flow.onSuspicion(strong, DrivingState.DRIVING, 3_000L) is ReportFlowState.DeferredUntilParked)
    }
}
