package com.example.pnu_me.domain.hazard

interface PedestrianAlertChannel {
    val sourceLabel: String

    fun publish(alert: PedestrianSafetyAlert)

    fun subscribe(listener: (PedestrianSafetyAlert) -> Unit): PedestrianAlertSubscription
}

fun interface PedestrianAlertSubscription {
    fun cancel()
}

/** 네트워크·FCM을 사용하지 않는 앱 프로세스 내부 모바일 수신 시뮬레이션 채널 */
class InMemoryPedestrianAlertChannel : PedestrianAlertChannel {
    override val sourceLabel: String = "MOCK · 앱 내부 가상 모바일"

    private val listeners = mutableListOf<(PedestrianSafetyAlert) -> Unit>()

    override fun publish(alert: PedestrianSafetyAlert) {
        listeners.toList().forEach { it(alert) }
    }

    override fun subscribe(listener: (PedestrianSafetyAlert) -> Unit): PedestrianAlertSubscription {
        listeners += listener
        return PedestrianAlertSubscription { listeners -= listener }
    }
}

class PedestrianAlertInbox(
    private val policy: PedestrianAlertPolicy = PedestrianAlertPolicy(),
    private val minNotifyIntervalMillis: Long = 120_000L,
) {
    private val seenIds = mutableSetOf<String>()
    private var lastNotifiedAtMillis: Long? = null

    sealed interface Outcome {
        data class Show(
            val alert: PedestrianSafetyAlert,
            val distanceMeters: Int,
        ) : Outcome

        data class Suppressed(val reason: String) : Outcome
    }

    fun receive(
        alert: PedestrianSafetyAlert,
        receiver: PedestrianReceiverState,
        nowMillis: Long,
    ): Outcome {
        if (alert.alertId in seenIds) return Outcome.Suppressed("이미 받은 알림")

        return when (val relevance = policy.evaluate(alert, receiver, nowMillis)) {
            is PedestrianRelevanceResult.Ignore -> Outcome.Suppressed(ignoreText(relevance.reason))
            is PedestrianRelevanceResult.Notify -> {
                seenIds += alert.alertId
                val last = lastNotifiedAtMillis
                if ((last != null) && (nowMillis - last < minNotifyIntervalMillis)) {
                    Outcome.Suppressed("최근 알림 직후라 표시 안 함")
                } else {
                    lastNotifiedAtMillis = nowMillis
                    Outcome.Show(alert, relevance.distanceMeters)
                }
            }
        }
    }

    private fun ignoreText(reason: PedestrianIgnoreReason): String = when (reason) {
        PedestrianIgnoreReason.INVALID_ALERT -> "알림 데이터 오류"
        PedestrianIgnoreReason.EXPIRED -> "유효 시간 지남"
        PedestrianIgnoreReason.TOO_FAR -> "1km 범위 밖"
        PedestrianIgnoreReason.NOT_OPTED_IN -> "사용자가 알림 참여 안 함"
        PedestrianIgnoreReason.NOTIFICATIONS_DISABLED -> "모바일 알림 권한 없음"
        PedestrianIgnoreReason.STALE_RECEIVER_LOCATION -> "모바일 위치가 오래됨"
        PedestrianIgnoreReason.DEMO_MISMATCH -> "시연/실제 모드 불일치"
        PedestrianIgnoreReason.INVALID_POSITION -> "모바일 위치 오류"
    }
}
