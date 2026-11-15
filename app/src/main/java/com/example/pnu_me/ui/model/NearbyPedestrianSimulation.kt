package com.example.pnu_me.ui.model

import com.example.pnu_me.domain.hazard.GeoMath
import com.example.pnu_me.domain.hazard.PedestrianAlertChannel
import com.example.pnu_me.domain.hazard.PedestrianAlertInbox
import com.example.pnu_me.domain.hazard.PedestrianReceiverState
import com.example.pnu_me.domain.hazard.PedestrianSafetyAlert

/** 실제 모바일·FCM이 아닌 결정론적 앱 내부 수신자 시뮬레이션 */
enum class SimulatedPedestrian(
    val displayName: String,
    val description: String,
    val bearingDegrees: Double,
    val distanceMeters: Double,
    val optedIn: Boolean,
    val notificationsAllowed: Boolean,
) {
    NEAR_OPTED_IN("모바일 A", "참여 중 · 약 300m", 0.0, 300.0, true, true),
    BOUNDARY_OPTED_IN("모바일 B", "참여 중 · 1km 경계", 90.0, 1_000.0, true, true),
    OUTSIDE_RADIUS("모바일 C", "참여 중 · 약 1.2km", 180.0, 1_200.0, true, true),
    OPTED_OUT("모바일 D", "안전 알림 참여 안 함 · 약 250m", 270.0, 250.0, false, true),
    NOTIFICATION_DENIED("모바일 E", "알림 권한 거부 · 약 200m", 315.0, 200.0, true, false),
}

data class NearbyPedestrianResult(
    val pedestrian: SimulatedPedestrian,
    val outcome: PedestrianAlertInbox.Outcome,
)

fun runNearbyPedestrianSimulation(
    alert: PedestrianSafetyAlert,
    channel: PedestrianAlertChannel,
    nowMillis: Long,
): List<NearbyPedestrianResult> {
    val results = mutableListOf<NearbyPedestrianResult>()
    val subscriptions = SimulatedPedestrian.entries.map { pedestrian ->
        val (latitude, longitude) = GeoMath.destination(
            lat = alert.latitude,
            lon = alert.longitude,
            bearingDegrees = pedestrian.bearingDegrees,
            distanceMeters = pedestrian.distanceMeters,
        )
        val receiver = PedestrianReceiverState(
            latitude = latitude,
            longitude = longitude,
            locationUpdatedAtMillis = nowMillis,
            optedIn = pedestrian.optedIn,
            notificationsAllowed = pedestrian.notificationsAllowed,
            isDemo = alert.isDemo,
        )
        val inbox = PedestrianAlertInbox()
        channel.subscribe { received ->
            results += NearbyPedestrianResult(
                pedestrian = pedestrian,
                outcome = inbox.receive(received, receiver, nowMillis),
            )
        }
    }
    try {
        channel.publish(alert)
    } finally {
        subscriptions.forEach { it.cancel() }
    }
    return results.sortedBy { it.pedestrian.ordinal }
}

fun pedestrianOutcomeText(outcome: PedestrianAlertInbox.Outcome): String = when (outcome) {
    is PedestrianAlertInbox.Outcome.Show -> "알림 표시: ${outcome.alert.notificationText()}"
    is PedestrianAlertInbox.Outcome.Suppressed -> "알림 없음 · ${outcome.reason}"
}
