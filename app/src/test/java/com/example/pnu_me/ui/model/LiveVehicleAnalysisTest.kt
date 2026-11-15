package com.example.pnu_me.ui.model

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.RuleBasedRiskEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVehicleAnalysisTest {
    private val engine = RuleBasedRiskEngine()

    @Test
    fun notConnected_isUnavailableWithoutInventedData() {
        val result = analyzeLiveVehicle(emptyList(), connected = false, engine = engine)

        assertEquals(RiskState.UNAVAILABLE, result.assessment.state)
        assertTrue(result.assessment.speedPoints.isEmpty())
        assertTrue(result.sourceBadgeText.contains("연결 안 됨"))
    }

    @Test
    fun connectedWithoutSamples_isWaiting() {
        val result = analyzeLiveVehicle(emptyList(), connected = true, engine = engine)

        assertEquals(RiskState.UNAVAILABLE, result.assessment.state)
        assertTrue(result.sourceBadgeText.contains("수신 대기"))
    }

    @Test
    fun vehicleSamples_areAnalyzedWithVehicleOrigin() {
        val samples = (0..4).map {
            TelemetrySample(it * 1_000L, 11.0, origin = TelemetryOrigin.VEHICLE)
        }

        val result = analyzeLiveVehicle(samples, connected = true, engine = engine)

        assertEquals(ScenarioLoadStatus.READY, result.loadStatus)
        assertEquals(RiskState.NORMAL, result.assessment.state)
        assertEquals(TelemetryOrigin.VEHICLE, result.assessment.origin)
    }

    @Test
    fun mockSamplesInLivePath_areRejected() {
        val samples = (0..4).map {
            TelemetrySample(it * 1_000L, 11.0, origin = TelemetryOrigin.MOCK)
        }

        val result = analyzeLiveVehicle(samples, connected = true, engine = engine)

        assertEquals(RiskState.UNAVAILABLE, result.assessment.state)
    }
}
