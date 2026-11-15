package com.example.pnu_me.domain.hazard

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 수신 차량의 현재 상태 */
data class ReceiverState(
    val latitude: Double,
    val longitude: Double,
    val headingDegrees: Double?,
    val accuracyMeters: Double,
    val locationUpdatedAtMillis: Long,
    val isDemo: Boolean,
)

sealed interface RelevanceResult {
    /** 알림 표시. [distanceMeters]는 100m 단위로 반올림한 값 */
    data class Notify(val distanceMeters: Int) : RelevanceResult

    data class Ignore(val reason: IgnoreReason) : RelevanceResult
}

enum class IgnoreReason {
    INVALID_ALERT,
    EXPIRED,
    TOO_FAR,
    DIFFERENT_DIRECTION,
    DEMO_MISMATCH,
    INVALID_POSITION,
    STALE_RECEIVER_LOCATION,
    INACCURATE_RECEIVER_LOCATION,
}

data class RelevanceCriteria(
    /** 이 거리 밖의 차량에는 알리지 않는다. */
    val maxDistanceMeters: Double = 1_000.0,
    /** 진행방향 차이가 이보다 크면 다른 도로/반대 차로로 보고 알리지 않는다. */
    val maxHeadingDifferenceDegrees: Double = 60.0,
)

/**
 * 수신 차량이 알림을 띄울지 결정한다. 과도한 알림은 그 자체로 주의 분산이므로
 * 가까이 있고 같은 방향으로 이동 중인 차량에만 알린다.
 * 방향 정보가 한쪽이라도 없으면 방향 필터는 적용하지 않는다(거리만 사용).
 */
class HazardRelevanceFilter(
    private val criteria: RelevanceCriteria = RelevanceCriteria(),
) {
    fun evaluate(alert: HazardAlert, receiver: ReceiverState, nowMillis: Long): RelevanceResult {
        if (!isValidAlert(alert, nowMillis)) return RelevanceResult.Ignore(IgnoreReason.INVALID_ALERT)
        if (alert.isExpired(nowMillis)) return RelevanceResult.Ignore(IgnoreReason.EXPIRED)
        if (alert.isDemo != receiver.isDemo) return RelevanceResult.Ignore(IgnoreReason.DEMO_MISMATCH)
        if (!isValidReceiver(receiver, nowMillis)) {
            return RelevanceResult.Ignore(IgnoreReason.INVALID_POSITION)
        }
        if (receiver.accuracyMeters > MAX_RECEIVER_ACCURACY_METERS) {
            return RelevanceResult.Ignore(IgnoreReason.INACCURATE_RECEIVER_LOCATION)
        }
        if ((nowMillis - receiver.locationUpdatedAtMillis) >= MAX_RECEIVER_LOCATION_AGE_MILLIS) {
            return RelevanceResult.Ignore(IgnoreReason.STALE_RECEIVER_LOCATION)
        }

        val distance = GeoMath.distanceMeters(
            alert.latitude, alert.longitude, receiver.latitude, receiver.longitude,
        )
        if (distance > criteria.maxDistanceMeters + DISTANCE_EPSILON_METERS) {
            return RelevanceResult.Ignore(IgnoreReason.TOO_FAR)
        }

        val alertHeading = alert.headingDegrees
        val receiverHeading = receiver.headingDegrees
        if (alertHeading != null && receiverHeading != null &&
            GeoMath.headingDifference(alertHeading, receiverHeading) > criteria.maxHeadingDifferenceDegrees
        ) {
            return RelevanceResult.Ignore(IgnoreReason.DIFFERENT_DIRECTION)
        }

        val rounded = ((distance / 100.0).roundToInt() * 100).coerceAtLeast(100)
        return RelevanceResult.Notify(rounded)
    }

    private fun isValidAlert(alert: HazardAlert, nowMillis: Long): Boolean =
        alert.alertId.isNotBlank() &&
            isValidPosition(alert.latitude, alert.longitude) &&
            (alert.headingDegrees?.isFinite() ?: true) &&
            alert.level == com.example.pnu_me.domain.report.SuspicionLevel.STRONG &&
            alert.issuedAtMillis in 0L..nowMillis &&
            alert.expiresAtMillis > alert.issuedAtMillis &&
            alert.expiresAtMillis - alert.issuedAtMillis <= HazardAlertFactory.TTL_MILLIS

    private fun isValidReceiver(receiver: ReceiverState, nowMillis: Long): Boolean =
        isValidPosition(receiver.latitude, receiver.longitude) &&
            (receiver.headingDegrees?.isFinite() ?: true) &&
            receiver.accuracyMeters.isFinite() &&
            receiver.accuracyMeters >= 0.0 &&
            receiver.locationUpdatedAtMillis in 0L..nowMillis

    private fun isValidPosition(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private companion object {
        const val MAX_RECEIVER_ACCURACY_METERS = 100.0
        const val MAX_RECEIVER_LOCATION_AGE_MILLIS = 120_000L
        const val DISTANCE_EPSILON_METERS = 0.000001
    }
}

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** 0~180도 */
    fun headingDifference(a: Double, b: Double): Double {
        val diff = abs(((a - b) % 360.0 + 360.0) % 360.0)
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /** 시작점에서 도착점을 바라보는 초기 방위각(0~360도, 북쪽 0도) */
    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val startLatitude = Math.toRadians(lat1)
        val endLatitude = Math.toRadians(lat2)
        val longitudeDelta = Math.toRadians(lon2 - lon1)
        val y = sin(longitudeDelta) * cos(endLatitude)
        val x = cos(startLatitude) * sin(endLatitude) -
            sin(startLatitude) * cos(endLatitude) * cos(longitudeDelta)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** 시작점에서 [bearingDegrees] 방향으로 [distanceMeters] 이동한 지점 (lat, lon) */
    fun destination(lat: Double, lon: Double, bearingDegrees: Double, distanceMeters: Double): Pair<Double, Double> {
        val angular = distanceMeters / EARTH_RADIUS_M
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angular) * cos(lat1),
            cos(angular) - sin(lat1) * sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}
