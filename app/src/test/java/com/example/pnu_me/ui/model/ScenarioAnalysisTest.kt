package com.example.pnu_me.ui.model

import com.example.pnu_me.data.source.DrivingScenario
import com.example.pnu_me.data.source.MockTelemetrySource
import com.example.pnu_me.data.source.ScenarioReader
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.RuleBasedRiskEngine
import com.example.pnu_me.domain.risk.ValidationIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.FileNotFoundException

class ScenarioAnalysisTest {
    private val engine = RuleBasedRiskEngine()

    @Test
    fun missingAsset_isAnExplicitRetryableError() {
        val source = MockTelemetrySource(
            ScenarioReader { throw FileNotFoundException("missing asset") },
        )

        val result = analyzeScenario(source, engine, DrivingScenario.NORMAL)

        assertEquals(ScenarioLoadStatus.ERROR, result.loadStatus)
        assertEquals(RiskState.UNAVAILABLE, result.assessment.state)
        assertEquals(ValidationIssue.SOURCE_ERROR, result.assessment.quality.issue)
        assertFalse("연결됨" in result.sourceBadgeText)
        assertEquals(true, result.canRetry)
    }

    @Test
    fun malformedCsv_isAnExplicitRetryableError() {
        val source = MockTelemetrySource(ScenarioReader { "bad,header" })

        val result = analyzeScenario(source, engine, DrivingScenario.NORMAL)

        assertEquals(ScenarioLoadStatus.ERROR, result.loadStatus)
        assertEquals(ValidationIssue.SOURCE_ERROR, result.assessment.quality.issue)
        assertFalse("연결됨" in result.sourceBadgeText)
    }

    @Test
    fun emptyCsv_isDistinguishedFromReadFailure() {
        val source = MockTelemetrySource(ScenarioReader { "" })

        val result = analyzeScenario(source, engine, DrivingScenario.NORMAL)

        assertEquals(ScenarioLoadStatus.EMPTY, result.loadStatus)
        assertEquals(ValidationIssue.EMPTY_DATA, result.assessment.quality.issue)
        assertFalse("연결됨" in result.sourceBadgeText)
    }

    @Test
    fun validCsv_isReadyWithoutClaimingVehicleConnection() {
        val source = MockTelemetrySource(
            ScenarioReader {
                """
                timestamp_ms,speed_mps,steering_deg,quality
                0,10.0,,VALID
                1000,10.2,,VALID
                2000,10.3,,VALID
                3000,10.4,,VALID
                """.trimIndent()
            },
        )

        val result = analyzeScenario(source, engine, DrivingScenario.NORMAL)

        assertEquals(ScenarioLoadStatus.READY, result.loadStatus)
        assertEquals(RiskState.NORMAL, result.assessment.state)
        assertFalse("VEHICLE 연결" in result.sourceBadgeText)
    }
}
