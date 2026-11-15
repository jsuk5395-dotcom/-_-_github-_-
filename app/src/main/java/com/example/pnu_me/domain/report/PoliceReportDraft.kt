package com.example.pnu_me.domain.report

import com.example.pnu_me.data.model.TelemetryOrigin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReporterLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val timestampMillis: Long,
    val headingDegrees: Double? = null,
    val origin: TelemetryOrigin = TelemetryOrigin.MOCK,
)

enum class LocationStatus {
    VALID,
    UNAVAILABLE,
    INVALID,
    STALE,
}

object ReporterLocationValidator {
    const val MAX_AGE_MILLIS: Long = 120_000L

    fun validate(location: ReporterLocation?, nowMillis: Long): LocationStatus {
        if (location == null) return LocationStatus.UNAVAILABLE
        val finite = location.latitude.isFinite() && location.longitude.isFinite() &&
            location.accuracyMeters.isFinite() &&
            (location.headingDegrees?.isFinite() ?: true)
        if (!finite ||
            location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0 ||
            location.accuracyMeters < 0.0 ||
            location.timestampMillis > nowMillis
        ) {
            return LocationStatus.INVALID
        }
        if (nowMillis - location.timestampMillis > MAX_AGE_MILLIS) return LocationStatus.STALE
        return LocationStatus.VALID
    }
}

data class PoliceReportDraft(
    val createdAtMillis: Long,
    val suspicion: SuspicionResult,
    val location: ReporterLocation?,
    val locationStatus: LocationStatus,
    val isDemo: Boolean,
    val witnessDetails: WitnessDetails? = null,
) {
    val hasUsableLocation: Boolean
        get() = locationStatus == LocationStatus.VALID && location != null

    fun withWitnessDetails(details: WitnessDetails): PoliceReportDraft =
        copy(witnessDetails = details.normalized())

    fun toSmsBody(timeZone: TimeZone = TimeZone.getDefault()): String = buildString {
        if (isDemo) append("[시연용 MOCK - 실제 신고 아님] ")
        append("이상 주행 차량 목격. ")
        append("관찰: ")
        append(suspicion.distinctCues.sortedBy { it.ordinal }.joinToString(", ") { it.label })
        append(". ")

        witnessDetails?.normalized()?.let { details ->
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).apply {
                this.timeZone = timeZone
            }
            append("목격 시각 ${formatter.format(Date(details.observedAtMillis))}. ")
            details.vehicleType?.let { append("차량 ${it.label}. ") }
            details.vehicleColor?.let { append("색상 ${it.label}. ") }
            details.lastSeenDirection?.let { append("마지막 진행방향 ${it.label}. ") }
            details.plateFragment?.let { append("번호판 일부 $it. ") }
            details.stillVisible?.let {
                append(if (it) "현재 시야에 있음. " else "현재 시야에서 벗어남. ")
            }
        }

        if (hasUsableLocation) {
            val loc = location!!
            append(
                String.format(
                    Locale.US,
                    "제보자 위치 %.5f, %.5f (오차 약 %.0fm)",
                    loc.latitude,
                    loc.longitude,
                    loc.accuracyMeters,
                ),
            )
            loc.headingDegrees?.let { append(String.format(Locale.US, ", 진행방향 %.0f도", it)) }
            append(".")
        } else {
            append("제보자 위치 확인 불가 - 통화로 위치 설명 필요.")
        }
    }

    fun toSafeVoiceSummary(): String {
        val locationText = if (hasUsableLocation) "신고자 위치가 초안에 포함되었습니다." else
            "신고자 위치가 없어 통화로 위치 설명이 필요합니다."
        return "이상 주행 차량 신고 초안이 준비되었습니다. $locationText " +
            "신고는 완료되지 않았으며, 112 연결과 발송은 사용자가 직접 확인해야 합니다."
    }

    companion object {
        const val POLICE_NUMBER = "112"
    }
}
