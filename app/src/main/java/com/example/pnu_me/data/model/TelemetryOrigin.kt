package com.example.pnu_me.data.model

/**
 * 분석 입력의 실제 출처다. MOCK과 VEHICLE 값을 한 세션에서 섞지 않는다.
 */
enum class TelemetryOrigin {
    MOCK,
    VEHICLE,
}
