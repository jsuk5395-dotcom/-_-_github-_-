package com.example.pnu_me.data.source

enum class DrivingScenario(
    val id: String,
    val displayName: String,
    val description: String,
    val assetFileName: String,
) {
    NORMAL("normal", "정상 주행", "일정한 속도와 완만한 변화", "normal.csv"),
    REPEATED_CHANGES("repeated_changes", "반복 가감속", "큰 가속과 감속이 여러 번 반복", "repeated_changes.csv"),
    SINGLE_BRAKE("single_brake", "단발 급제동", "한 번의 큰 감속 후 속도 유지", "single_brake.csv"),
    MISSING_DATA("missing_data", "데이터 누락", "중간 속도 표본이 비어 있음", "missing_data.csv"),
    REVERSED_TIME("reversed_time", "시간 역행", "표본 시각이 이전 값보다 작아짐", "reversed_time.csv"),
    ;

    companion object {
        fun fromId(id: String): DrivingScenario = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}
