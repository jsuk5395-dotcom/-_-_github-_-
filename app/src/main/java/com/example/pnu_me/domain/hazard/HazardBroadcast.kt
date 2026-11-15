package com.example.pnu_me.domain.hazard

/**
 * 주변 차량 알림 전송 경로. 공식 V2X API가 확인되지 않아 현재는 [InMemoryHazardChannel](MOCK)만 있다.
 * 향후 서버 중계(위치 기반 푸시)나 BLE 광고 같은 실제 전송은 이 인터페이스의 adapter로 추가한다.
 */
interface HazardBroadcastChannel {
    val sourceLabel: String

    fun publish(alert: HazardAlert)

    fun subscribe(listener: (HazardAlert) -> Unit): HazardSubscription
}

fun interface HazardSubscription {
    fun cancel()
}

/** 같은 앱 프로세스 안에서 가상 주변 차량에게 전달하는 시연용 채널 */
class InMemoryHazardChannel : HazardBroadcastChannel {
    override val sourceLabel: String = "MOCK · 앱 내부 가상 주변 차량"

    private val listeners = mutableListOf<(HazardAlert) -> Unit>()

    override fun publish(alert: HazardAlert) {
        listeners.toList().forEach { it(alert) }
    }

    override fun subscribe(listener: (HazardAlert) -> Unit): HazardSubscription {
        listeners += listener
        return HazardSubscription { listeners -= listener }
    }
}

/** 수신 차량 한 대의 알림함. 중복 제거와 알림 빈도 제한을 담당한다. */
class HazardAlertInbox(
    private val filter: HazardRelevanceFilter = HazardRelevanceFilter(),
    /** 이 시간 안에는 새 알림을 또 띄우지 않는다(주의 분산 방지). */
    private val minNotifyIntervalMillis: Long = 60_000L,
) {
    private val seenIds = mutableSetOf<String>()
    private var lastNotifiedAtMillis: Long? = null

    sealed interface Outcome {
        data class Show(val alert: HazardAlert, val distanceMeters: Int) : Outcome

        data class Suppressed(val reason: String) : Outcome
    }

    fun receive(alert: HazardAlert, receiver: ReceiverState, nowMillis: Long): Outcome {
        if (alert.alertId in seenIds) return Outcome.Suppressed("이미 받은 알림")
        return when (val relevance = filter.evaluate(alert, receiver, nowMillis)) {
            is RelevanceResult.Ignore -> Outcome.Suppressed(ignoreText(relevance.reason))
            is RelevanceResult.Notify -> {
                seenIds += alert.alertId
                val last = lastNotifiedAtMillis
                if (last != null && nowMillis - last < minNotifyIntervalMillis) {
                    Outcome.Suppressed("최근 알림 직후라 표시 안 함")
                } else {
                    lastNotifiedAtMillis = nowMillis
                    Outcome.Show(alert, relevance.distanceMeters)
                }
            }
        }
    }

    private fun ignoreText(reason: IgnoreReason): String = when (reason) {
        IgnoreReason.INVALID_ALERT -> "알림 데이터 오류"
        IgnoreReason.EXPIRED -> "유효 시간 지남"
        IgnoreReason.TOO_FAR -> "거리가 멀어 알리지 않음"
        IgnoreReason.DIFFERENT_DIRECTION -> "진행방향이 달라 알리지 않음"
        IgnoreReason.DEMO_MISMATCH -> "시연/실제 모드 불일치"
        IgnoreReason.INVALID_POSITION -> "수신 차량 위치 오류"
        IgnoreReason.STALE_RECEIVER_LOCATION -> "수신 차량 위치가 오래됨"
        IgnoreReason.INACCURATE_RECEIVER_LOCATION -> "수신 차량 위치 정확도 부족"
    }
}
