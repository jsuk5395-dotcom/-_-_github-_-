package com.example.pnu_me.domain.report

/**
 * 의심 단서 수준. "음주 확률"이나 "음주 판정"이 아니다.
 */
enum class SuspicionLevel {
    /** 단서 없음 또는 판단 불가 */
    NONE,

    /** 단발 단서 1개 — 신고 제안하지 않음 */
    WEAK,

    /** NHTSA 기준 p≥0.50 구간에 해당하는 패턴 — 신고 여부를 물어봄 */
    MODERATE,

    /** NHTSA 기준 p≥0.65 구간(지그재그 + 다른 단서) — 신고 여부를 물어봄 */
    STRONG,
}

data class SuspicionCriteria(
    /** 이 시간보다 오래된 관찰은 무시한다. */
    val observationWindowMillis: Long = 60_000L,
    /** MOCK과 탑승자 관찰을 섞어 판단하지 않는다. */
    val rejectMixedSources: Boolean = true,
)

data class SuspicionResult(
    val level: SuspicionLevel,
    val distinctCues: Set<ImpairedDrivingCue>,
    val reason: String,
) {
    val shouldAskOccupant: Boolean
        get() = level == SuspicionLevel.MODERATE || level == SuspicionLevel.STRONG
}

/**
 * 명확한 규칙 (NHTSA DOT HS 808 677 요약):
 * 1. 지그재그(WEAVING*) + 다른 단서 1개 이상           → STRONG  (원문: p ≥ .65)
 * 2. 지그재그 단독                                      → MODERATE (원문: p > .50)
 * 3. 지그재그가 아닌 서로 다른 단서 2개 이상            → MODERATE (원문: 대략 p ≥ .50)
 * 4. 단독으로 고확률인 단서(급조향·이유 없는 급가속·도로 외 주행) → MODERATE
 * 5. 그 외 단서 1개                                     → WEAK
 * 6. 관찰 없음 / 창 밖의 오래된 관찰 / 출처 혼합       → NONE
 *
 * 수치는 미국 경찰 관찰 연구의 참고값이며 국내 기준이나 앱의 판정 정확도가 아니다.
 */
class SuspicionEvaluator(
    private val criteria: SuspicionCriteria = SuspicionCriteria(),
) {
    fun evaluate(observations: List<CueObservation>, nowMillis: Long): SuspicionResult {
        if (observations.isEmpty()) {
            return SuspicionResult(SuspicionLevel.NONE, emptySet(), "관찰된 단서 없음")
        }
        val recent = observations.filter {
            it.timestampMillis <= nowMillis &&
                nowMillis - it.timestampMillis <= criteria.observationWindowMillis
        }
        if (recent.isEmpty()) {
            return SuspicionResult(SuspicionLevel.NONE, emptySet(), "최근 관찰 단서 없음")
        }
        if (criteria.rejectMixedSources && recent.map { it.source }.distinct().size > 1) {
            return SuspicionResult(SuspicionLevel.NONE, emptySet(), "관찰 출처 혼합으로 판단 불가")
        }

        val cues = recent.map { it.cue }.toSet()
        val hasWeaving = cues.any { it.isWeavingType }
        val nonWeaving = cues.filterNot { it.isWeavingType }

        return when {
            hasWeaving && nonWeaving.isNotEmpty() ->
                SuspicionResult(SuspicionLevel.STRONG, cues, "지그재그 주행과 다른 단서가 함께 관찰됨")
            hasWeaving ->
                SuspicionResult(SuspicionLevel.MODERATE, cues, "지그재그 주행 관찰됨")
            nonWeaving.size >= 2 ->
                SuspicionResult(SuspicionLevel.MODERATE, cues, "서로 다른 단서 ${nonWeaving.size}개 관찰됨")
            nonWeaving.any { it.isHighProbabilityAlone } ->
                SuspicionResult(SuspicionLevel.MODERATE, cues, "단독으로도 위험도가 높은 단서 관찰됨")
            else ->
                SuspicionResult(SuspicionLevel.WEAK, cues, "단서 1개만 관찰됨")
        }
    }
}
