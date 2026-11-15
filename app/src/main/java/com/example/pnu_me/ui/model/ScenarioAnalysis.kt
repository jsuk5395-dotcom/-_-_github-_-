package com.example.pnu_me.ui.model

import com.example.pnu_me.data.source.DrivingScenario
import com.example.pnu_me.data.source.TelemetrySource
import com.example.pnu_me.domain.risk.DataQualityReport
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RiskEngine
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.ValidationIssue

enum class ScenarioLoadStatus {
    READY,
    EMPTY,
    ERROR,
}

data class ScenarioAnalysisResult(
    val loadStatus: ScenarioLoadStatus,
    val assessment: RiskAssessment,
    val sourceBadgeText: String,
    val canRetry: Boolean,
)

fun analyzeScenario(
    source: TelemetrySource,
    engine: RiskEngine,
    scenario: DrivingScenario,
): ScenarioAnalysisResult {
    val samples = try {
        source.samplesFor(scenario)
    } catch (_: Exception) {
        return unavailableScenario(
            status = ScenarioLoadStatus.ERROR,
            issue = ValidationIssue.SOURCE_ERROR,
            message = "로컬 시나리오 데이터를 읽지 못했습니다. 다시 시도하세요.",
            sourceBadgeText = "데이터 오류 · " + source.displayName,
        )
    }

    if (samples.isEmpty()) {
        return unavailableScenario(
            status = ScenarioLoadStatus.EMPTY,
            issue = ValidationIssue.EMPTY_DATA,
            message = "로컬 시나리오 데이터가 비어 있습니다. 다른 시나리오를 선택하세요.",
            sourceBadgeText = "데이터 없음 · " + source.displayName,
        )
    }

    return ScenarioAnalysisResult(
        loadStatus = ScenarioLoadStatus.READY,
        assessment = engine.analyze(samples, source.origin),
        sourceBadgeText = "데이터 출처 · " + source.displayName,
        canRetry = false,
    )
}

private fun unavailableScenario(
    status: ScenarioLoadStatus,
    issue: ValidationIssue,
    message: String,
    sourceBadgeText: String,
): ScenarioAnalysisResult = ScenarioAnalysisResult(
    loadStatus = status,
    assessment = RiskAssessment(
        state = RiskState.UNAVAILABLE,
        origin = null,
        title = "측정 불가",
        guidance = message,
        quality = DataQualityReport(
            isUsable = false,
            issue = issue,
            message = message,
            validSampleCount = 0,
            totalSampleCount = 0,
        ),
        speedPoints = emptyList(),
        events = emptyList(),
        averageSpeedMps = null,
        maximumAbsoluteAccelerationMps2 = null,
    ),
    sourceBadgeText = sourceBadgeText,
    canRetry = true,
)
