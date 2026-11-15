package com.example.pnu_me.domain.hazard

/**
 * 수신 차량 지도에 잠시 표시할 익명 제보 구역.
 *
 * [reportedLatitude]/[reportedLongitude]은 약 100m 격자로 낮춘 마지막 제보 위치이며,
 * 의심 차량의 실시간 GPS나 이동 경로가 아니다.
 */
data class HazardMapMarker(
    val alertId: String,
    val reportedLatitude: Double,
    val reportedLongitude: Double,
    val distanceMeters: Int,
    val bearingFromReceiverDegrees: Double,
    val uncertaintyRadiusMeters: Int,
    val expiresAtMillis: Long,
    val isDemo: Boolean,
)

sealed interface HazardMapMarkerDecision {
    data class Show(val marker: HazardMapMarker) : HazardMapMarkerDecision

    data class Hidden(val reason: IgnoreReason) : HazardMapMarkerDecision
}

/** 경고 관련성 정책을 통과한 경우에만 수신 차량 기준 상대 지도 마커를 만든다. */
class HazardMapMarkerPolicy(
    private val relevanceFilter: HazardRelevanceFilter = HazardRelevanceFilter(),
) {
    fun evaluate(
        alert: HazardAlert,
        receiver: ReceiverState,
        nowMillis: Long,
    ): HazardMapMarkerDecision = when (val relevance = relevanceFilter.evaluate(alert, receiver, nowMillis)) {
        is RelevanceResult.Ignore -> HazardMapMarkerDecision.Hidden(relevance.reason)
        is RelevanceResult.Notify -> HazardMapMarkerDecision.Show(
            HazardMapMarker(
                alertId = alert.alertId,
                reportedLatitude = alert.latitude,
                reportedLongitude = alert.longitude,
                distanceMeters = relevance.distanceMeters,
                bearingFromReceiverDegrees = GeoMath.initialBearingDegrees(
                    receiver.latitude,
                    receiver.longitude,
                    alert.latitude,
                    alert.longitude,
                ),
                uncertaintyRadiusMeters = 100,
                expiresAtMillis = alert.expiresAtMillis,
                isDemo = alert.isDemo,
            ),
        )
    }
}
