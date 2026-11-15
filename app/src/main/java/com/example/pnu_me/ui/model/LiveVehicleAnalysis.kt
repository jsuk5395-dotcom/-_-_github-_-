package com.example.pnu_me.ui.model

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample
import com.example.pnu_me.domain.risk.DataQualityReport
import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RiskEngine
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.risk.ValidationIssue

const val LIVE_VEHICLE_LABEL = "Android Automotive 차량 속도"

/**
 * 실차(VEHICLE) 표본을 MOCK과 같은 분석 엔진으로 판단한다.
 * 연결·표본이 없으면 값을 지어내지 않고 "측정 불가"를 반환한다.
 */
fun analyzeLiveVehicle(
    samples: List<TelemetrySample>,
    connected: Boolean,
    engine: RiskEngine,
): ScenarioAnalysisResult {
    if (!connected) {
        return unavailableLive(
            message = "차량 데이터에 연결되지 않았습니다. " +
                "Android Automotive 차량에서 차량 속도 권한을 허용하면 사용할 수 있습니다.",
            badge = "데이터 출처 · VEHICLE · Android Car API · 연결 안 됨",
        )
    }
    if (samples.isEmpty()) {
        return unavailableLive(
            message = "Android Car API 차량 속도를 받는 중입니다. 잠시 후 결과가 자동으로 갱신됩니다.",
            badge = "데이터 출처 · VEHICLE · Android Car API · 수신 대기",
        )
    }
    return ScenarioAnalysisResult(
        loadStatus = ScenarioLoadStatus.READY,
        assessment = engine.analyze(samples, TelemetryOrigin.VEHICLE),
        sourceBadgeText = "데이터 출처 · VEHICLE · $LIVE_VEHICLE_LABEL (최근 60초)",
        canRetry = false,
    )
}

private fun unavailableLive(message: String, badge: String) = ScenarioAnalysisResult(
    loadStatus = ScenarioLoadStatus.EMPTY,
    assessment = RiskAssessment(
        state = RiskState.UNAVAILABLE,
        origin = TelemetryOrigin.VEHICLE,
        title = "측정 불가",
        guidance = message,
        quality = DataQualityReport(
            isUsable = false,
            issue = ValidationIssue.EMPTY_DATA,
            message = message,
            validSampleCount = 0,
            totalSampleCount = 0,
        ),
        speedPoints = emptyList(),
        events = emptyList(),
        averageSpeedMps = null,
        maximumAbsoluteAccelerationMps2 = null,
    ),
    sourceBadgeText = badge,
    canRetry = false,
)
