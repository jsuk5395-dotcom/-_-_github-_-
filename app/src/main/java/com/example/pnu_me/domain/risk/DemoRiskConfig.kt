package com.example.pnu_me.domain.risk

/**
 * 연구용 시연 임계값이다. 실도로 안전 인증값이 아니다.
 */
data class DemoRiskConfig(
    val minimumSampleCount: Int = 4,
    val minimumSpeedMps: Double = 0.0,
    val maximumSpeedMps: Double = 70.0,
    val maximumSampleGapMillis: Long = 3_000L,
    val abruptAccelerationThresholdMps2: Double = 2.5,
    val minimumEventSeparationMillis: Long = 750L,
    val stopRecommendedEventCount: Int = 3,

    // --- 조향 방향 반전 규칙: SAFE DRIVE에서 가져온 MOCK 시연용 값 ---
    // 공식 Pleos AVD·SDK·실차의 단위와 범위는 모두 미검증이다.
    // 검증 전까지 차량 연동 근거가 아닌 결정론적 테스트 데이터에만 사용한다.
    /** 이 속도 미만에서는 조향 분석을 초기화한다. (SAFE DRIVE: 10 km/h) */
    val steeringAnalysisMinimumSpeedMps: Double = 10.0 / 3.6,
    /** 연속 표본 사이 조향 변화가 이 값 미만이면 무시한다. (SAFE DRIVE: 8°) */
    val steeringMinimumChangeDegrees: Double = 8.0,
    /** 방향 반전을 세는 구간 길이. (SAFE DRIVE: 8초) */
    val steeringReversalWindowMillis: Long = 8_000L,
    /** 구간 안 반전 횟수가 이 값 이상이면 이벤트. (SAFE DRIVE: 2회) */
    val steeringReversalCount: Int = 2,
    /** 같은 조향 이벤트의 재감지 제한. (SAFE DRIVE: 10초) */
    val steeringEventCooldownMillis: Long = 10_000L,

    /**
     * 조향 반전 이벤트와 급가감속 이벤트가 이 시간 안에 함께 있으면 안전 정차를 권고한다.
     * (SAFE DRIVE 복합 위험 규칙: 30초)
     */
    val compositeEventWindowMillis: Long = 30_000L,
)
