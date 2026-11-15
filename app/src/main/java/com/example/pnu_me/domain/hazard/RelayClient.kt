package com.example.pnu_me.domain.hazard

import com.example.pnu_me.domain.report.ReporterLocation
import java.io.IOException

data class RelayHttpResponse(val status: Int, val body: String)

/** HTTP 전송 추상화. 인증된 배포용 어댑터가 준비되기 전에는 앱 조립부에 연결하지 않는다. */
fun interface RelayTransport {
    @Throws(IOException::class)
    fun execute(method: String, path: String, body: String?): RelayHttpResponse
}

sealed interface RelayPublishResult {
    data object Accepted : RelayPublishResult

    data object RateLimited : RelayPublishResult

    data object Duplicate : RelayPublishResult

    data class Rejected(val status: Int) : RelayPublishResult

    data class Unreachable(val message: String) : RelayPublishResult
}

sealed interface RelayFetchResult<out T> {
    data class Success<T>(val alerts: List<T>) : RelayFetchResult<T>

    data class Rejected(val status: Int) : RelayFetchResult<Nothing>

    data class Unreachable(val message: String) : RelayFetchResult<Nothing>
}

/**
 * 중계 서버 호출 규칙(동기). 스레드 처리는 호출자가 맡는다.
 * 수신 조회에는 정밀 위치가 아니라 약 1km 격자 9칸만 보낸다.
 */
class HazardRelayClient(private val transport: RelayTransport) {
    fun publishVehicle(alert: HazardAlert): RelayPublishResult =
        publish(VEHICLE_PATH, RelayCodec.encodeVehicle(alert))

    fun publishPedestrian(alert: PedestrianSafetyAlert): RelayPublishResult =
        publish(PEDESTRIAN_PATH, RelayCodec.encodePedestrian(alert))

    fun fetchVehicle(latitude: Double, longitude: Double, nowMillis: Long): RelayFetchResult<HazardAlert> =
        fetch(VEHICLE_PATH, latitude, longitude) { RelayCodec.decodeVehicleList(it, nowMillis) }

    fun fetchPedestrian(
        latitude: Double,
        longitude: Double,
        nowMillis: Long,
    ): RelayFetchResult<PedestrianSafetyAlert> =
        fetch(PEDESTRIAN_PATH, latitude, longitude) { RelayCodec.decodePedestrianList(it, nowMillis) }

    private fun publish(path: String, body: String): RelayPublishResult = try {
        val response = transport.execute("POST", path, body)
        when (response.status) {
            201 -> RelayPublishResult.Accepted
            409 -> RelayPublishResult.Duplicate
            429 -> RelayPublishResult.RateLimited
            else -> RelayPublishResult.Rejected(response.status)
        }
    } catch (e: IOException) {
        RelayPublishResult.Unreachable(e.message ?: "서버에 연결할 수 없습니다")
    }

    private fun <T> fetch(
        path: String,
        latitude: Double,
        longitude: Double,
        decode: (String) -> List<T>,
    ): RelayFetchResult<T> = try {
        val cells = RelayCells.neighborhood(latitude, longitude).joinToString(",")
        val response = transport.execute("GET", "$path?cells=$cells", null)
        if (response.status != 200) {
            RelayFetchResult.Rejected(response.status)
        } else {
            try {
                RelayFetchResult.Success(decode(response.body))
            } catch (_: IllegalArgumentException) {
                RelayFetchResult.Rejected(response.status)
            }
        }
    } catch (e: IOException) {
        RelayFetchResult.Unreachable(e.message ?: "서버에 연결할 수 없습니다")
    }

    private companion object {
        const val VEHICLE_PATH = "/v1/vehicle-alerts"
        const val PEDESTRIAN_PATH = "/v1/pedestrian-alerts"
    }
}

/** 발행 결과 문구. 서버 접수를 "전달 완료"로 과장하지 않는다. */
fun RelayPublishResult.displayText(): String = when (this) {
    RelayPublishResult.Accepted -> "중계 서버 접수 완료 · 90초 동안 주변 기기가 조회해 각자 판단합니다"
    RelayPublishResult.RateLimited -> "중계 서버 거절 · 30초 안에 다시 보낼 수 없습니다"
    RelayPublishResult.Duplicate -> "이미 접수된 알림입니다"
    is RelayPublishResult.Rejected -> "중계 서버 거절(${status}) · 전송하지 않았습니다"
    is RelayPublishResult.Unreachable -> "중계 서버에 연결하지 못했습니다 · 앱 내부 시연 결과만 표시"
}

enum class RelayRole(val label: String) {
    OFF("꺼짐"),
    VEHICLE("차량 수신"),
    PEDESTRIAN("보행자 수신"),
}

data class RelayBanner(
    val alertId: String,
    val text: String,
    val shownAtMillis: Long,
)

sealed interface RelayPollResult {
    data object NoLocation : RelayPollResult

    data class Unreachable(val message: String) : RelayPollResult

    data class Rejected(val status: Int) : RelayPollResult

    /** 조회 성공. 표시할 알림이 있으면 [banner] */
    data class Checked(val received: Int, val banner: RelayBanner?) : RelayPollResult
}

/**
 * 한 기기의 수신 세션. 서버에서 받은 알림을 기존 수신 필터(거리·방향·TTL·중복·빈도)로 다시 판단한다.
 * 자기가 발행한 알림은 표시하지 않는다.
 */
class RelayReceiverSession(
    private val client: HazardRelayClient,
    private val isDemo: Boolean,
) {
    private val vehicleInbox = HazardAlertInbox()
    private val pedestrianInbox = PedestrianAlertInbox()
    private val ownAlertIds = mutableSetOf<String>()

    @Synchronized
    fun markOwn(alertId: String) {
        ownAlertIds += alertId
    }

    @Synchronized
    fun poll(role: RelayRole, location: ReporterLocation?, nowMillis: Long): RelayPollResult {
        if (role == RelayRole.OFF) return RelayPollResult.Checked(0, null)
        if (location == null) return RelayPollResult.NoLocation
        return when (role) {
            RelayRole.VEHICLE -> pollVehicle(location, nowMillis)
            RelayRole.PEDESTRIAN -> pollPedestrian(location, nowMillis)
            RelayRole.OFF -> RelayPollResult.Checked(0, null)
        }
    }

    private fun pollVehicle(location: ReporterLocation, nowMillis: Long): RelayPollResult =
        when (val result = client.fetchVehicle(location.latitude, location.longitude, nowMillis)) {
            is RelayFetchResult.Unreachable -> RelayPollResult.Unreachable(result.message)
            is RelayFetchResult.Rejected -> RelayPollResult.Rejected(result.status)
            is RelayFetchResult.Success -> {
                val receiver = ReceiverState(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    headingDegrees = location.headingDegrees,
                    accuracyMeters = location.accuracyMeters,
                    locationUpdatedAtMillis = location.timestampMillis,
                    isDemo = isDemo,
                )
                val banner = result.alerts
                    .filterNot { it.alertId in ownAlertIds }
                    .firstNotNullOfOrNull { alert ->
                        (vehicleInbox.receive(alert, receiver, nowMillis) as? HazardAlertInbox.Outcome.Show)
                            ?.let { RelayBanner(alert.alertId, vehicleBannerText(it.distanceMeters), nowMillis) }
                    }
                RelayPollResult.Checked(result.alerts.size, banner)
            }
        }

    private fun pollPedestrian(location: ReporterLocation, nowMillis: Long): RelayPollResult =
        when (val result = client.fetchPedestrian(location.latitude, location.longitude, nowMillis)) {
            is RelayFetchResult.Unreachable -> RelayPollResult.Unreachable(result.message)
            is RelayFetchResult.Rejected -> RelayPollResult.Rejected(result.status)
            is RelayFetchResult.Success -> {
                // 보행자 수신 모드를 켠 것 자체가 명시적 참여이며, 표시는 앱 내 배너로 한다.
                val receiver = PedestrianReceiverState(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    locationUpdatedAtMillis = location.timestampMillis,
                    optedIn = true,
                    notificationsAllowed = true,
                    isDemo = isDemo,
                )
                val banner = result.alerts
                    .filterNot { it.alertId in ownAlertIds }
                    .firstNotNullOfOrNull { alert ->
                        (pedestrianInbox.receive(alert, receiver, nowMillis) as? PedestrianAlertInbox.Outcome.Show)
                            ?.let { RelayBanner(alert.alertId, alert.notificationText(), nowMillis) }
                    }
                RelayPollResult.Checked(result.alerts.size, banner)
            }
        }

    companion object {
        /** "음주"라고 단정하지 않는다. */
        fun vehicleBannerText(distanceMeters: Int): String =
            "주의 · 약 ${distanceMeters}m 부근 이상 주행 차량 제보 · 차간거리를 유지하세요"
    }
}
