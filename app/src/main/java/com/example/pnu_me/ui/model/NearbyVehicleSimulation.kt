package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.hazard.GeoMath
import com.example.pnu_me.domain.hazard.HazardAlert
import com.example.pnu_me.domain.hazard.HazardAlertInbox
import com.example.pnu_me.domain.hazard.HazardBroadcastChannel
import com.example.pnu_me.domain.hazard.HazardMapMarker
import com.example.pnu_me.domain.hazard.HazardMapMarkerDecision
import com.example.pnu_me.domain.hazard.HazardMapMarkerPolicy
import com.example.pnu_me.domain.hazard.ReceiverState

/**
 * 시연용 가상 주변 차량. 위치는 신고 차량 기준 상대값으로 배치한다.
 * [bearingFromReporter]/[relativeHeading]은 신고 차량 진행방향 기준 각도(0 = 같은 방향)다.
 */
enum class SimulatedVehicle(
    val displayName: String,
    val description: String,
    val bearingFromReporter: Double,
    val distanceMeters: Double,
    val relativeHeading: Double,
) {
    SAME_DIRECTION_NEAR("차량 B", "같은 방향 · 약 300m 뒤", 180.0, 300.0, 0.0),
    OPPOSITE_DIRECTION("차량 C", "반대 방향 · 약 400m 앞", 0.0, 400.0, 180.0),
    SAME_DIRECTION_FAR("차량 D", "같은 방향 · 약 2.5km 뒤", 180.0, 2_500.0, 0.0),
}

data class NearbyResult(
    val vehicle: SimulatedVehicle,
    val receiverState: ReceiverState,
    val outcome: HazardAlertInbox.Outcome,
    val mapMarker: HazardMapMarker?,
)

/**
 * 가상 차량들이 [channel]을 구독하고, 알림을 발행해 각 차량의 수신 결과를 모은다.
 * 신고 차량 방향이 없으면 배치 기준을 북쪽(0도)으로 두되, 알림에는 방향이 없으므로 방향 필터는 적용되지 않는다.
 */
fun runNearbySimulation(
    alert: HazardAlert,
    channel: HazardBroadcastChannel,
    nowMillis: Long,
): List<NearbyResult> {
    val baseHeading = alert.headingDegrees ?: 0.0
    val results = mutableListOf<NearbyResult>()
    val markerPolicy = HazardMapMarkerPolicy()
    val subscriptions = SimulatedVehicle.entries.map { vehicle ->
        val (lat, lon) = GeoMath.destination(
            alert.latitude,
            alert.longitude,
            (baseHeading + vehicle.bearingFromReporter) % 360.0,
            vehicle.distanceMeters,
        )
        val receiver = ReceiverState(
            latitude = lat,
            longitude = lon,
            headingDegrees = (baseHeading + vehicle.relativeHeading) % 360.0,
            accuracyMeters = 10.0,
            locationUpdatedAtMillis = nowMillis,
            isDemo = alert.isDemo,
        )
        val inbox = HazardAlertInbox()
        channel.subscribe { received ->
            val outcome = inbox.receive(received, receiver, nowMillis)
            val marker = if (outcome is HazardAlertInbox.Outcome.Show) {
                when (val decision = markerPolicy.evaluate(received, receiver, nowMillis)) {
                    is HazardMapMarkerDecision.Show -> decision.marker
                    is HazardMapMarkerDecision.Hidden -> null
                }
            } else {
                null
            }
            results += NearbyResult(vehicle, receiver, outcome, marker)
        }
    }
    try {
        channel.publish(alert)
    } finally {
        subscriptions.forEach { it.cancel() }
    }
    return results.sortedBy { it.vehicle.ordinal }
}

/** 수신 차량에 짧게 표시할 문구. "음주"라고 단정하지 않는다. */
fun hazardBannerText(distanceMeters: Int): String =
    "주의 · 약 ${distanceMeters}m 부근 이상 주행 차량 보고 · 차간거리를 유지하세요"

fun hazardMapStatusText(): String =
    "MOCK 지도 · 마지막 제보 구역 · 실시간 추적 아님 · 90초 후 자동 삭제"

fun nearbyOutcomeText(outcome: HazardAlertInbox.Outcome): String = when (outcome) {
    is HazardAlertInbox.Outcome.Show -> "알림 표시: " + hazardBannerText(outcome.distanceMeters)
    is HazardAlertInbox.Outcome.Suppressed -> "알림 없음 · " + outcome.reason
}
