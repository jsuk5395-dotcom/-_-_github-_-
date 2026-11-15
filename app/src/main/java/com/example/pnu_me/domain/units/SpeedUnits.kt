package com.example.pnu_me.domain.units

/**
 * 분석 계층의 m/s 값을 사용자 표시용 km/h로 변환한다.
 *
 * 분석 입력과 임계값은 항상 m/s를 유지하며 이 변환값을 규칙 계산에 사용하지 않는다.
 */
object SpeedUnits {
    private const val KILOMETERS_PER_HOUR_PER_METER_PER_SECOND = 3.6

    fun metersPerSecondToKilometersPerHour(speedMps: Double): Double =
        speedMps * KILOMETERS_PER_HOUR_PER_METER_PER_SECOND
}
