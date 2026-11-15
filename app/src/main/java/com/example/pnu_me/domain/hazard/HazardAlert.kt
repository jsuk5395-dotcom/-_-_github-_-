package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReporterLocationValidator
import com.example.pnu_me.domain.report.SuspicionLevel
import com.example.pnu_me.domain.report.WitnessDirection
import kotlin.math.roundToLong

/**
 * 주변 차량에 보내는 익명 주의 알림.
 *
 * 개인정보·명예훼손 위험을 줄이기 위해 다음을 담지 않는다.
 * - 의심 차량 번호·차종·색상, 신고자 식별자
 * - "음주" 단정 표현 (수신 측 문구는 "이상 주행 차량")
 * 위치는 약 100m 격자로 반올림한 신고자 위치다.
 */
data class HazardAlert(
    val alertId: String,
    val latitude: Double,
    val longitude: Double,
    /** 목격자가 입력한 의심 차량의 마지막 진행방향. 미확인이면 null (방향 필터 미적용) */
    val headingDegrees: Double?,
    val level: SuspicionLevel,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val isDemo: Boolean,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

object HazardAlertFactory {
    /** 알림 유효 시간. 오래된 경고로 운전자를 혼란시키지 않는다. */
    const val TTL_MILLIS: Long = 90_000L
    const val MAX_SOURCE_ACCURACY_METERS: Double = 100.0

    /** 위도·경도 소수 셋째 자리 ≈ 약 100m */
    private const val GRID_SCALE = 1_000.0

    /**
     * 탑승자가 승인한 신고 초안에서만 알림을 만든다.
     * 위치가 유효하지 않거나 신고 질문 대상 수준이 아니면 null — 위치를 지어내지 않는다.
     */
    fun fromDraft(draft: PoliceReportDraft, alertId: String, nowMillis: Long): HazardAlert? {
        if (alertId.isBlank()) return null
        if (nowMillis < 0L) return null
        if (nowMillis > (Long.MAX_VALUE - TTL_MILLIS)) return null
        if (draft.suspicion.level != SuspicionLevel.STRONG) return null
        val location = draft.location ?: return null
        if (!draft.hasUsableLocation) return null
        if (ReporterLocationValidator.validate(location, nowMillis) != LocationStatus.VALID) return null
        if (location.accuracyMeters > MAX_SOURCE_ACCURACY_METERS) return null
        return HazardAlert(
            alertId = alertId,
            latitude = roundToGrid(location.latitude),
            longitude = roundToGrid(location.longitude),
            headingDegrees = witnessHeadingDegrees(draft.witnessDetails?.lastSeenDirection),
            level = draft.suspicion.level,
            issuedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + TTL_MILLIS,
            isDemo = draft.isDemo,
        )
    }

    internal fun roundToGrid(value: Double): Double = (value * GRID_SCALE).roundToLong() / GRID_SCALE

    private fun witnessHeadingDegrees(direction: WitnessDirection?): Double? = when (direction) {
        WitnessDirection.NORTH -> 0.0
        WitnessDirection.NORTHEAST -> 45.0
        WitnessDirection.EAST -> 90.0
        WitnessDirection.SOUTHEAST -> 135.0
        WitnessDirection.SOUTH -> 180.0
        WitnessDirection.SOUTHWEST -> 225.0
        WitnessDirection.WEST -> 270.0
        WitnessDirection.NORTHWEST -> 315.0
        WitnessDirection.UNKNOWN, null -> null
    }
}
