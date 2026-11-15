package com.example.pnu_me.domain.report

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.domain.report.ImpairedDrivingCue.SLOW_SPEED
import com.example.pnu_me.domain.report.ImpairedDrivingCue.WEAVING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReporterLocationSourceTest {
    private val strong = SuspicionResult(SuspicionLevel.STRONG, setOf(WEAVING, SLOW_SPEED), "test")

    private fun map(
        hasAccuracy: Boolean = true,
        hasBearing: Boolean = true,
        isMock: Boolean = false,
        hasSpeed: Boolean = true,
        speed: Float = 10f,
    ) =
        ReporterLocationMapper.fromRaw(
            latitude = 35.1,
            longitude = 129.1,
            hasAccuracy = hasAccuracy,
            accuracyMeters = 5f,
            timeMillis = 1_000L,
            hasBearing = hasBearing,
            bearingDegrees = 45f,
            isMockProvider = isMock,
            hasSpeed = hasSpeed,
            speedMps = speed,
        )

    @Test
    fun realProvider_mapsToVehicleOrigin() {
        val location = (map() as LocationFetchResult.Success).location
        assertEquals(TelemetryOrigin.VEHICLE, location.origin)
        assertEquals(5.0, location.accuracyMeters, 0.0)
        assertEquals(45.0, location.headingDegrees!!, 0.0)
    }

    @Test
    fun mockProvider_isMarkedMock() {
        assertEquals(TelemetryOrigin.MOCK, (map(isMock = true) as LocationFetchResult.Success).location.origin)
    }

    @Test
    fun missingBearing_isNullNotInvented() {
        assertNull((map(hasBearing = false) as LocationFetchResult.Success).location.headingDegrees)
    }

    @Test
    fun stoppedOrUnknownSpeed_dropsHeading() {
        assertNull((map(speed = 0f) as LocationFetchResult.Success).location.headingDegrees)
        assertNull((map(speed = 0.99f) as LocationFetchResult.Success).location.headingDegrees)
        assertNull((map(hasSpeed = false) as LocationFetchResult.Success).location.headingDegrees)
        assertEquals(45.0, (map(speed = 1f) as LocationFetchResult.Success).location.headingDegrees!!, 0.0)
    }

    @Test
    fun missingAccuracy_isUnusable() {
        assertTrue(map(hasAccuracy = false) is LocationFetchResult.Unusable)
    }

    @Test
    fun mockSource_returnsMockOriginWithCurrentTime() {
        var received: LocationFetchResult? = null
        MockReporterLocationSource(clock = { 42L }).requestCurrentLocation { received = it }
        val location = (received as LocationFetchResult.Success).location
        assertEquals(TelemetryOrigin.MOCK, location.origin)
        assertEquals(42L, location.timestampMillis)
    }

    @Test
    fun approvalThenSlowLocation_doesNotExpire() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.DRIVING, 0L)
        assertTrue(flow.onOccupantApproved(10_000L) is ReportFlowState.Locating)
        // 동의 제한시간(15초)이 지나도 위치 조회 중에는 만료되지 않는다.
        assertTrue(flow.tick(30_000L) is ReportFlowState.Locating)
        val location = ReporterLocation(35.0, 129.0, 8.0, 29_000L)
        val draft = (flow.onLocationResult(LocationFetchResult.Success(location), 30_000L) as ReportFlowState.DraftReady).draft
        assertTrue(draft.hasUsableLocation)
    }

    @Test
    fun locationFailure_createsDraftWithoutLocation() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.PARKED, 0L)
        flow.onOccupantApproved(1_000L)
        val draft = (flow.onLocationResult(LocationFetchResult.PermissionDenied, 2_000L) as ReportFlowState.DraftReady).draft
        assertEquals(LocationStatus.UNAVAILABLE, draft.locationStatus)
        assertFalse(draft.hasUsableLocation)
    }

    @Test
    fun locationResult_withoutApproval_isIgnored() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.PARKED, 0L)
        val state = flow.onLocationResult(LocationFetchResult.Timeout, 1_000L)
        assertTrue(state is ReportFlowState.AwaitingConsent)
    }

    @Test
    fun suspicionWhileLocating_doesNotRestartQuestion() {
        val flow = PoliceReportFlow()
        flow.onSuspicion(strong, DrivingState.PARKED, 0L)
        flow.onOccupantApproved(1_000L)
        assertTrue(flow.onSuspicion(strong, DrivingState.PARKED, 2_000L) is ReportFlowState.Locating)
    }
}
