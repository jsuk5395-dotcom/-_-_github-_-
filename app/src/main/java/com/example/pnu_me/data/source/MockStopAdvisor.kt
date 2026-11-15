package com.example.pnu_me.data.source

import com.example.pnu_me.domain.risk.RiskAssessment
import com.example.pnu_me.domain.risk.RiskState
import com.example.pnu_me.domain.stop.StopAdvice
import com.example.pnu_me.domain.stop.StopAdvisor
import com.example.pnu_me.domain.stop.StopCandidate
import com.example.pnu_me.domain.stop.StopGuidance

enum class MockStopAdvisorMode {
    AVAILABLE,
    NO_DATA,
    FAILURE,
}

/**
 * 외부 위치·내비 서비스 없이 정차 지원 흐름을 재현하는 결정론적 시연 구현.
 */
class MockStopAdvisor(
    private val mode: MockStopAdvisorMode = MockStopAdvisorMode.AVAILABLE,
) : StopAdvisor {
    override val sourceLabel: String = "MOCK · 시연용 가상 장소"

    private var activeCandidateIds: Set<String> = emptySet()
    private var guidanceActive: Boolean = false

    private val demoCandidates = listOf(
        StopCandidate(
            id = "demo-rest-area",
            label = "시연용 가상 휴게 공간",
            description = "실제 위치 검색이 아닌 정차 지원 흐름 확인용 후보입니다.",
            isDemo = true,
        ),
        StopCandidate(
            id = "demo-parking",
            label = "시연용 가상 주차 공간",
            description = "안전성·영업 여부·경로가 검증되지 않은 데모 후보입니다.",
            isDemo = true,
        ),
    )

    override fun advise(assessment: RiskAssessment): StopAdvice {
        val advice = when {
            mode == MockStopAdvisorMode.FAILURE ->
                StopAdvice.Failed("모의 정차 후보를 불러오지 못했습니다.")
            mode == MockStopAdvisorMode.NO_DATA ||
                assessment.state != RiskState.STOP_RECOMMENDED ->
                StopAdvice.NoCandidates
            else -> StopAdvice.Available(demoCandidates)
        }
        activeCandidateIds = when (advice) {
            is StopAdvice.Available -> advice.candidates.mapTo(mutableSetOf()) { it.id }
            is StopAdvice.Failed, StopAdvice.NoCandidates -> emptySet()
        }
        if (advice !is StopAdvice.Available) {
            guidanceActive = false
        }
        return advice
    }

    override fun requestGuidance(candidateId: String): StopGuidance {
        if (mode != MockStopAdvisorMode.AVAILABLE) {
            return StopGuidance.Failed("모의 정차 안내 요청을 처리하지 못했습니다.")
        }
        if (candidateId !in activeCandidateIds) {
            return StopGuidance.Failed("선택한 모의 정차 후보를 현재 사용할 수 없습니다.")
        }
        if (guidanceActive) {
            return StopGuidance.Failed("모의 정차 안내가 이미 준비되어 있습니다.")
        }

        val candidate = demoCandidates.first { it.id == candidateId }
        guidanceActive = true
        return StopGuidance.Ready(
            candidate = candidate,
            message = "시연용 안내가 준비되었습니다. 실제 경로 안내나 장소 검색은 수행하지 않습니다.",
        )
    }

    override fun cancelGuidance(): StopGuidance {
        if (!guidanceActive) {
            return StopGuidance.Failed("취소할 모의 정차 안내가 없습니다.")
        }
        guidanceActive = false
        activeCandidateIds = emptySet()
        return StopGuidance.Cancelled
    }
}
