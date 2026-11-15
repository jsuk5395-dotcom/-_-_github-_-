package com.example.pnu_me.data.model

/**
 * 위험 패턴 분석에 전달되는 SDK 독립 데이터 모델.
 *
 * @param timestampMillis 단조 증가해야 하는 시나리오 상대 시각(ms)
 * @param speedMps 속도(m/s). 제공되지 않으면 null
 * @param steeringDegrees 선택 입력인 조향각(deg). 현재 MVP 규칙에는 사용하지 않음
 */
data class TelemetrySample(
    val timestampMillis: Long,
    val speedMps: Double?,
    val steeringDegrees: Double? = null,
    val origin: TelemetryOrigin,
    val quality: DataQuality = if (speedMps == null) DataQuality.MISSING else DataQuality.VALID,
)
