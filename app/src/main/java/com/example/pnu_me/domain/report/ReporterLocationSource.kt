package com.example.pnu_me.domain.report

import com.example.pnu_me.data.model.TelemetryOrigin

/**
 * 신고자(내 차량) 위치를 한 번 조회한다. 의심 차량 위치를 조회하는 기능은 없다.
 * 결과의 timestampMillis는 wall-clock(System.currentTimeMillis 기준)이다.
 */
interface ReporterLocationSource {
    val sourceLabel: String

    /** 결과는 정확히 한 번 [onResult]로 전달된다. */
    fun requestCurrentLocation(onResult: (LocationFetchResult) -> Unit)

    /** 진행 중 요청을 취소한다. 취소 후 콜백은 호출되지 않는다. */
    fun cancel()
}

sealed interface LocationFetchResult {
    data class Success(val location: ReporterLocation) : LocationFetchResult

    /** 위치 권한 미허용 */
    data object PermissionDenied : LocationFetchResult

    /** 기기 위치 기능 꺼짐 */
    data object LocationDisabled : LocationFetchResult

    /** 사용할 수 있는 위치 제공자 없음 */
    data object NoProvider : LocationFetchResult

    /** 제한 시간 내 위치를 얻지 못함 */
    data object Timeout : LocationFetchResult

    /** 받은 값이 신고에 쓸 수 없는 형태(정확도 미제공 등) */
    data class Unusable(val reason: String) : LocationFetchResult

    data class Failed(val message: String) : LocationFetchResult
}

/**
 * 플랫폼 Location 값을 [ReporterLocation]으로 바꾸는 순수 함수.
 * 정확도·방향이 없으면 임의 값을 넣지 않는다.
 * 정차/저속(< [MIN_SPEED_FOR_HEADING_MPS]) 또는 속도 미제공이면 진행방향은 의미가 없으므로 넣지 않는다.
 * 모의 위치 제공자(mock provider) 값은 VEHICLE이 아니라 MOCK으로 표시한다.
 */
object ReporterLocationMapper {
    const val MIN_SPEED_FOR_HEADING_MPS: Float = 1.0f

    fun fromRaw(
        latitude: Double,
        longitude: Double,
        hasAccuracy: Boolean,
        accuracyMeters: Float,
        timeMillis: Long,
        hasBearing: Boolean,
        bearingDegrees: Float,
        isMockProvider: Boolean,
        hasSpeed: Boolean = false,
        speedMps: Float = 0f,
    ): LocationFetchResult {
        if (!hasAccuracy) return LocationFetchResult.Unusable("정확도 미제공")
        val headingMeaningful = hasBearing && hasSpeed && speedMps >= MIN_SPEED_FOR_HEADING_MPS
        return LocationFetchResult.Success(
            ReporterLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters.toDouble(),
                timestampMillis = timeMillis,
                headingDegrees = if (headingMeaningful) bearingDegrees.toDouble() else null,
                origin = if (isMockProvider) TelemetryOrigin.MOCK else TelemetryOrigin.VEHICLE,
            ),
        )
    }
}

/** 시연·테스트용 고정 위치. 항상 MOCK으로 표시된다. */
class MockReporterLocationSource(
    private val clock: () -> Long = System::currentTimeMillis,
    private val result: ((Long) -> LocationFetchResult)? = null,
) : ReporterLocationSource {
    override val sourceLabel: String = "MOCK · 시연용 고정 위치"

    override fun requestCurrentLocation(onResult: (LocationFetchResult) -> Unit) {
        val now = clock()
        onResult(
            result?.invoke(now) ?: LocationFetchResult.Success(
                ReporterLocation(
                    latitude = 35.2316,
                    longitude = 129.0825,
                    accuracyMeters = 10.0,
                    timestampMillis = now,
                    headingDegrees = 90.0,
                    origin = TelemetryOrigin.MOCK,
                ),
            ),
        )
    }

    override fun cancel() = Unit
}
