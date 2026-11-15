package com.example.pnu_me.data.source

import com.example.pnu_me.data.model.TelemetryOrigin
import com.example.pnu_me.data.model.TelemetrySample

/**
 * 콜백으로 들어오는 실차 속도·조향 값을 분석 엔진용 [TelemetrySample] 목록으로 바꾸는 순수 클래스.
 *
 * - 표본 간격을 [minIntervalMillis] 이상으로 맞춘다. 속도가 km/h 정수 단위로 빠르게 들어오므로
 *   0.1초 간격으로 가속도를 계산하면 1km/h 변화만으로도 임계값을 넘는다(SAFE DRIVE도 0.5초 사용).
 * - 조향각은 최신값을 다음 속도 표본에 함께 기록한다.
 * - 수신이 [maxGapMillis]보다 오래 끊기면 이전 구간을 버리고 새로 시작한다.
 * - 최근 [windowMillis] 표본만 유지한다.
 * - 값이 없으면 지어내지 않고 speed=null(MISSING) 표본으로 남겨 분석이 "측정 불가"가 되게 한다.
 *
 * 스레드 안전하지 않다. 호출 측(VehicleTelemetryHub)이 동기화한다.
 */
class LiveTelemetryWindow(
    private val windowMillis: Long = 60_000L,
    private val minIntervalMillis: Long = 500L,
    private val maxGapMillis: Long = 3_000L,
) {
    private val buffer = ArrayDeque<TelemetrySample>()
    private var originMillis: Long? = null
    private var lastReceivedMillis: Long? = null
    private var latestSteeringDegrees: Double? = null

    fun onSteering(degrees: Double) {
        if (degrees.isFinite()) latestSteeringDegrees = degrees
    }

    /**
     * @param nowMillis 단조 증가 시계(SystemClock.elapsedRealtime 등)
     * @param speedMps 속도(m/s). 차량이 값을 제공하지 못하면 null
     * @return 새 표본을 추가했으면 true
     */
    fun onSpeed(nowMillis: Long, speedMps: Double?): Boolean {
        val origin = originMillis ?: nowMillis.also { originMillis = it }
        val timestamp = nowMillis - origin
        if (timestamp < 0L || lastReceivedMillis?.let { nowMillis < it } == true) return false
        lastReceivedMillis = nowMillis

        buffer.lastOrNull()?.let { last ->
            val elapsed = timestamp - last.timestampMillis
            if (elapsed < minIntervalMillis) return false
            if (elapsed > maxGapMillis) buffer.clear()
        }

        buffer.addLast(
            TelemetrySample(
                timestampMillis = timestamp,
                speedMps = speedMps?.takeIf(Double::isFinite),
                steeringDegrees = latestSteeringDegrees,
                origin = TelemetryOrigin.VEHICLE,
            ),
        )
        while (buffer.first().timestampMillis < timestamp - windowMillis) {
            buffer.removeFirst()
        }
        return true
    }

    fun snapshot(): List<TelemetrySample> = buffer.toList()

    fun snapshot(nowMillis: Long): List<TelemetrySample> {
        val lastReceived = lastReceivedMillis
        if (lastReceived != null && nowMillis - lastReceived > maxGapMillis) {
            clear()
        }
        return snapshot()
    }

    fun clear() {
        buffer.clear()
        originMillis = null
        lastReceivedMillis = null
        latestSteeringDegrees = null
    }
}
