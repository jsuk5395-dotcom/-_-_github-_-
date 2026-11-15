package com.example.pnu_me.ui.model

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.domain.report.CapabilityStatus
import com.example.pnu_me.domain.report.CueObservation
import com.example.pnu_me.domain.report.CueSource
import com.example.pnu_me.domain.report.ImpairedDrivingCue
import com.example.pnu_me.domain.report.LocationFetchResult
import com.example.pnu_me.domain.report.LocationStatus
import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReportHandoffResult
import com.example.pnu_me.domain.report.SuspicionLevel
import java.util.Locale

/**
 * 시연용 의심 단서 묶음. 주변 차량 자동 인식 API가 확인되지 않아 MOCK으로만 재현한다.
 */
enum class MockCuePreset(
    val displayName: String,
    val description: String,
    val cues: List<ImpairedDrivingCue>,
) {
    WEAVING_AND_SLOW(
        displayName = "지그재그 + 저속",
        description = "앞차가 차선을 넘나들며 제한속도보다 현저히 느리게 주행",
        cues = listOf(ImpairedDrivingCue.WEAVING_ACROSS_LANES, ImpairedDrivingCue.SLOW_SPEED),
    ),
    SUDDEN_ACCELERATION(
        displayName = "이유 없는 급가속",
        description = "주변 상황과 무관한 급가속 1회",
        cues = listOf(ImpairedDrivingCue.ACCELERATING_FOR_NO_REASON),
    ),
    DRIFT_AND_STOP(
        displayName = "표류 + 정지 이상",
        description = "서서히 차로를 벗어나고 정지선을 넘어 정지",
        cues = listOf(ImpairedDrivingCue.DRIFTING, ImpairedDrivingCue.STOPPING_PROBLEM),
    ),
    SINGLE_SLOW(
        displayName = "저속 주행 1회",
        description = "단서 1개뿐이므로 신고 여부를 묻지 않아야 함",
        cues = listOf(ImpairedDrivingCue.SLOW_SPEED),
    ),
    ;

    /** 최근 관찰처럼 2초 간격으로 배치한다. 출처는 항상 MOCK이다. */
    fun observations(nowMillis: Long): List<CueObservation> =
        cues.mapIndexed { index, cue ->
            CueObservation(
                cue = cue,
                timestampMillis = nowMillis - (cues.size - index) * 2_000L,
                source = CueSource.MOCK_SCENARIO,
                origin = TelemetryOrigin.MOCK,
            )
        }
}

fun suspicionLevelLabel(level: SuspicionLevel): String = when (level) {
    SuspicionLevel.NONE -> "의심 단서 없음"
    SuspicionLevel.WEAK -> "약한 단서 · 신고 질문 안 함"
    SuspicionLevel.MODERATE -> "의심 단서 있음"
    SuspicionLevel.STRONG -> "강한 의심 단서"
}

fun consentRemainingSeconds(askedAtMillis: Long, nowMillis: Long, timeoutMillis: Long): Int {
    val remaining = timeoutMillis - (nowMillis - askedAtMillis)
    if (remaining <= 0L) return 0
    return ((remaining + 999L) / 1_000L).toInt()
}

fun locationFetchMessage(result: LocationFetchResult?): String? = when (result) {
    null, is LocationFetchResult.Success -> null
    LocationFetchResult.PermissionDenied -> "위치 권한이 없어 위치 없이 초안을 만들었습니다."
    LocationFetchResult.LocationDisabled -> "기기 위치 기능이 꺼져 있습니다."
    LocationFetchResult.NoProvider -> "사용할 수 있는 위치 제공자가 없습니다."
    LocationFetchResult.Timeout -> "제한 시간 안에 위치를 얻지 못했습니다."
    is LocationFetchResult.Unusable -> "위치 값을 신고에 쓸 수 없습니다: ${result.reason}"
    is LocationFetchResult.Failed -> "위치 조회 오류: ${result.message}"
}

fun capabilityStatusText(status: CapabilityStatus): String = when (status) {
    CapabilityStatus.READY -> "사용 가능"
    CapabilityStatus.NEEDS_PERMISSION -> "권한 필요"
    CapabilityStatus.DISABLED -> "기능 꺼짐"
    CapabilityStatus.UNAVAILABLE -> "사용 불가"
    CapabilityStatus.DEMO_ONLY -> "시연 전용"
    CapabilityStatus.BLOCKED -> "공식 정책 검증 전"
}

fun handoffMessage(result: ReportHandoffResult): String = when (result) {
    is ReportHandoffResult.OpenedUnconfirmed ->
        "${result.channel.label} 앱을 열었습니다. 실제 전달 완료 여부는 외부 앱에서 확인해야 합니다."
    is ReportHandoffResult.Unavailable ->
        "${result.channel.label} 앱을 사용할 수 없습니다: ${result.reason}"
    is ReportHandoffResult.Failed ->
        "${result.channel.label} 앱을 열지 못했습니다: ${result.reason}"
}

fun draftRetentionRemainingSeconds(discardAtMillis: Long, nowMillis: Long): Int {
    val remaining = discardAtMillis - nowMillis
    if (remaining <= 0L) return 0
    val seconds = remaining / 1_000L + if (remaining % 1_000L == 0L) 0L else 1L
    return seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

fun draftLocationText(draft: PoliceReportDraft): String {
    val location = draft.location
    return when (draft.locationStatus) {
        LocationStatus.VALID -> if (location != null) {
            val originText = when (location.origin) {
                TelemetryOrigin.VEHICLE -> "기기 위치"
                TelemetryOrigin.MOCK -> "모의 위치"
            }
            String.format(
                Locale.US,
                "%.5f, %.5f · 오차 약 %.0fm · %s",
                location.latitude,
                location.longitude,
                location.accuracyMeters,
                originText,
            )
        } else {
            "위치 없음 · 통화로 위치를 설명하세요"
        }
        LocationStatus.UNAVAILABLE -> "위치 없음 · 통화로 위치를 설명하세요"
        LocationStatus.STALE -> "위치가 2분 이상 지나 사용하지 않음 · 통화로 위치를 설명하세요"
        LocationStatus.INVALID -> "위치 값이 올바르지 않아 사용하지 않음 · 통화로 위치를 설명하세요"
    }
}
