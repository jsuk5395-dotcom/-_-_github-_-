package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.PoliceReportDraft
import com.example.pnu_me.domain.report.ReporterLocationValidator
import com.example.pnu_me.domain.report.SuspicionLevel
import kotlin.math.roundToInt

/**
 * 명시적으로 참여한 모바일 사용자에게 전달할 수 있는 최소 정보 안전 이벤트.
 * 실제 외부 전송 어댑터는 없으며 현재는 MOCK 채널에서만 사용한다.
 */
data class PedestrianSafetyAlert(
    val alertId: String,
    val latitude: Double,
    val longitude: Double,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val isDemo: Boolean,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun notificationText(): String =
        "보행 안전 주의 · 1km 이내 도로에 이상 주행 차량 제보가 있습니다. " +
            "횡단 전 좌우를 확인하고 차량에 접근하지 마세요."
}

object PedestrianSafetyAlertFactory {
    const val TTL_MILLIS: Long = 90_000L
    const val MAX_SOURCE_ACCURACY_METERS: Double = 100.0

    /**
     * 공개 범위 알림은 오경보 피해가 더 크므로 STRONG 수준과 현재도 유효한 위치를 모두 요구한다.
     */
    fun fromDraft(
        draft: PoliceReportDraft,
        alertId: String,
        nowMillis: Long,
    ): PedestrianSafetyAlert? {
        val location = draft.location ?: return null
        if (draft.suspicion.level != SuspicionLevel.STRONG) return null
        if (ReporterLocationValidator.validate(location, nowMillis) != draft.locationStatus) return null
        if (!draft.hasUsableLocation) return null
        if (location.accuracyMeters > MAX_SOURCE_ACCURACY_METERS) return null
        if (alertId.isBlank() || nowMillis > Long.MAX_VALUE - TTL_MILLIS) return null
        return PedestrianSafetyAlert(
            alertId = alertId,
            latitude = HazardAlertFactory.roundToGrid(location.latitude),
            longitude = HazardAlertFactory.roundToGrid(location.longitude),
            issuedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + TTL_MILLIS,
            isDemo = draft.isDemo,
        )
    }
}

data class PedestrianReceiverState(
    val latitude: Double,
    val longitude: Double,
    val locationUpdatedAtMillis: Long,
    val optedIn: Boolean,
    val notificationsAllowed: Boolean,
    val isDemo: Boolean,
)

sealed interface PedestrianRelevanceResult {
    data class Notify(val distanceMeters: Int) : PedestrianRelevanceResult

    data class Ignore(val reason: PedestrianIgnoreReason) : PedestrianRelevanceResult
}

enum class PedestrianIgnoreReason {
    INVALID_ALERT,
    EXPIRED,
    TOO_FAR,
    NOT_OPTED_IN,
    NOTIFICATIONS_DISABLED,
    STALE_RECEIVER_LOCATION,
    DEMO_MISMATCH,
    INVALID_POSITION,
}

data class PedestrianAlertCriteria(
    val maxDistanceMeters: Double = 1_000.0,
    val maxReceiverLocationAgeMillis: Long = 120_000L,
)

class PedestrianAlertPolicy(
    private val criteria: PedestrianAlertCriteria = PedestrianAlertCriteria(),
) {
    private val distanceCalculationEpsilonMeters = 0.000_001
    fun evaluate(
        alert: PedestrianSafetyAlert,
        receiver: PedestrianReceiverState,
        nowMillis: Long,
    ): PedestrianRelevanceResult {
        if (!isValidAlert(alert, nowMillis)) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.INVALID_ALERT)
        }
        if (alert.isExpired(nowMillis)) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.EXPIRED)
        }
        if (alert.isDemo != receiver.isDemo) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.DEMO_MISMATCH)
        }
        if (!receiver.optedIn) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.NOT_OPTED_IN)
        }
        if (!receiver.notificationsAllowed) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.NOTIFICATIONS_DISABLED)
        }
        if (!isValidPosition(receiver.latitude, receiver.longitude) ||
            receiver.locationUpdatedAtMillis > nowMillis
        ) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.INVALID_POSITION)
        }
        if (nowMillis - receiver.locationUpdatedAtMillis >= criteria.maxReceiverLocationAgeMillis) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.STALE_RECEIVER_LOCATION)
        }

        val distance = GeoMath.distanceMeters(
            alert.latitude,
            alert.longitude,
            receiver.latitude,
            receiver.longitude,
        )
        if (distance - criteria.maxDistanceMeters > distanceCalculationEpsilonMeters) {
            return PedestrianRelevanceResult.Ignore(PedestrianIgnoreReason.TOO_FAR)
        }

        val roundedDistance = ((distance / 100.0).roundToInt() * 100).coerceAtLeast(100)
        return PedestrianRelevanceResult.Notify(roundedDistance)
    }

    private fun isValidAlert(alert: PedestrianSafetyAlert, nowMillis: Long): Boolean =
        alert.alertId.isNotBlank() &&
            isValidPosition(alert.latitude, alert.longitude) &&
            alert.issuedAtMillis in 0L..nowMillis &&
            alert.expiresAtMillis > alert.issuedAtMillis &&
            alert.expiresAtMillis - alert.issuedAtMillis <= PedestrianSafetyAlertFactory.TTL_MILLIS

    private fun isValidPosition(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
