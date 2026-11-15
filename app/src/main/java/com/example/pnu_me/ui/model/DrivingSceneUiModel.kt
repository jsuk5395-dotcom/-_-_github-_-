package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.RiskState

data class DrivingSceneUiModel(
    val statusLabel: String,
    val detailLabel: String,
    val sourceNotice: String = "MOCK · 실제 차량 연결 아님",
    val needsAttention: Boolean,
)

fun drivingSceneUiModel(state: RiskState): DrivingSceneUiModel = when (state) {
    RiskState.NORMAL -> DrivingSceneUiModel(
        statusLabel = "모의 운행 중",
        detailLabel = "도로 위 주행 모습을 시각화한 로컬 시뮬레이션입니다.",
        needsAttention = false,
    )

    RiskState.REVIEW -> DrivingSceneUiModel(
        statusLabel = "모의 운행 · 상태 점검",
        detailLabel = "큰 속도 변화가 감지된 시뮬레이션입니다.",
        needsAttention = true,
    )

    RiskState.STOP_RECOMMENDED -> DrivingSceneUiModel(
        statusLabel = "모의 운행 · 안전 정차 권고",
        detailLabel = "안전한 곳에 정차한 뒤 상태를 확인하세요.",
        needsAttention = true,
    )

    RiskState.UNAVAILABLE -> DrivingSceneUiModel(
        statusLabel = "모의 운행 데이터 확인 불가",
        detailLabel = "로컬 시나리오 데이터를 다시 확인해 주세요.",
        needsAttention = true,
    )
}
