package com.example.pnu_me.domain.report

import com.example.pnu_me.data.model.TelemetryOrigin

/**
 * 주변 차량에서 관찰된 "음주운전 의심 단서".
 *
 * 근거: NHTSA, The Visual Detection of DWI Motorists (DOT HS 808 677).
 * 이 단서들은 경찰 단속용 관찰 지표이며 음주 여부를 판정하지 않는다.
 * 앱은 이 단서를 이용해 "신고 여부를 물어볼지"만 결정한다.
 */
enum class ImpairedDrivingCue(
    val label: String,
    val group: CueGroup,
    /** NHTSA 지침에서 단독 관찰로도 높은 확률(>0.70)을 보인 단서 */
    val isHighProbabilityAlone: Boolean = false,
) {
    WEAVING("차로 안에서 좌우로 반복 흔들림", CueGroup.LANE_POSITION),
    WEAVING_ACROSS_LANES("차선을 넘나들며 지그재그 주행", CueGroup.LANE_POSITION),
    STRADDLING_LANE_LINE("차선을 밟고 계속 주행", CueGroup.LANE_POSITION),
    SWERVING("갑작스러운 급조향", CueGroup.LANE_POSITION, isHighProbabilityAlone = true),
    DRIFTING("서서히 차로를 벗어남", CueGroup.LANE_POSITION),
    ALMOST_STRIKING_OBJECT("물체·차량과 충돌할 뻔함", CueGroup.LANE_POSITION),
    WIDE_RADIUS_TURN("회전 반경이 비정상적으로 큼", CueGroup.LANE_POSITION),

    ACCELERATING_FOR_NO_REASON("이유 없는 급가속", CueGroup.SPEED_AND_BRAKING, isHighProbabilityAlone = true),
    VARYING_SPEED("속도가 불규칙하게 변함", CueGroup.SPEED_AND_BRAKING),
    SLOW_SPEED("제한속도보다 현저히 느림", CueGroup.SPEED_AND_BRAKING),
    STOPPING_PROBLEM("정지선 초과·불필요한 정지 등 정지 이상", CueGroup.SPEED_AND_BRAKING),

    DRIVING_OFF_ROADWAY("도로 외 구간(갓길·인도 등) 주행", CueGroup.VIGILANCE, isHighProbabilityAlone = true),
    WRONG_WAY("역주행", CueGroup.VIGILANCE),
    NO_HEADLIGHTS_AT_NIGHT("야간 전조등 미점등", CueGroup.VIGILANCE),
    SLOW_RESPONSE_TO_SIGNAL("신호 반응 지연", CueGroup.VIGILANCE),
    ;

    val isWeavingType: Boolean
        get() = this == WEAVING || this == WEAVING_ACROSS_LANES
}

enum class CueGroup {
    LANE_POSITION,
    SPEED_AND_BRAKING,
    VIGILANCE,
}

/**
 * 단서를 누가 관찰했는지. 현재 자동 인식 센서(전방 카메라·레이더 객체 정보)는
 * 공식 Pleos SDK에서 확인되지 않았으므로 MOCK 또는 탑승자 직접 관찰만 허용한다.
 */
enum class CueSource {
    MOCK_SCENARIO,
    OCCUPANT_OBSERVATION,
}

data class CueObservation(
    val cue: ImpairedDrivingCue,
    val timestampMillis: Long,
    val source: CueSource,
    val origin: TelemetryOrigin = TelemetryOrigin.MOCK,
)
