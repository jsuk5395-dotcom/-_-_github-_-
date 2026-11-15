package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.risk.RiskState

data class CockpitMapUiModel(
    val title: String,
    val status: String,
    val sourceNotice: String = "MOCK 지도 · 실제 지도 아님",
    val needsAttention: Boolean,
)

fun cockpitMapUiModel(state: RiskState): CockpitMapUiModel = when (state) {
    RiskState.NORMAL -> CockpitMapUiModel(
        title = "주행 환경",
        status = "모의 경로 표시",
        needsAttention = false,
    )

    RiskState.REVIEW -> CockpitMapUiModel(
        title = "주행 상태 점검",
        status = "속도 변화 이벤트 확인",
        needsAttention = true,
    )

    RiskState.STOP_RECOMMENDED -> CockpitMapUiModel(
        title = "안전 정차 검토",
        status = "정차 후 상태 확인 권고",
        needsAttention = true,
    )

    RiskState.UNAVAILABLE -> CockpitMapUiModel(
        title = "주행 환경 확인 불가",
        status = "경로 없음 · 입력 데이터 확인",
        needsAttention = true,
    )
}
