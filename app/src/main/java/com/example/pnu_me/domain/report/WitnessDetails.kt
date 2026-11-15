package com.example.pnu_me.domain.report

enum class WitnessVehicleType(val label: String) {
    SEDAN("승용차"),
    SUV("SUV"),
    VAN("승합차"),
    TRUCK("화물차"),
    BUS("버스"),
    MOTORCYCLE("이륜차"),
    OTHER("기타"),
}

enum class WitnessVehicleColor(val label: String) {
    WHITE("흰색"),
    BLACK("검은색"),
    SILVER("은색"),
    GRAY("회색"),
    RED("빨간색"),
    BLUE("파란색"),
    GREEN("초록색"),
    YELLOW("노란색"),
    OTHER("기타"),
}

enum class WitnessDirection(val label: String) {
    NORTH("북쪽"),
    NORTHEAST("북동쪽"),
    EAST("동쪽"),
    SOUTHEAST("남동쪽"),
    SOUTH("남쪽"),
    SOUTHWEST("남서쪽"),
    WEST("서쪽"),
    NORTHWEST("북서쪽"),
    UNKNOWN("방향 미확인"),
}

data class WitnessDetails(
    val observedAtMillis: Long,
    val vehicleType: WitnessVehicleType? = null,
    val vehicleColor: WitnessVehicleColor? = null,
    val lastSeenDirection: WitnessDirection? = null,
    val plateFragment: String? = null,
    val stillVisible: Boolean? = null,
) {
    fun normalized(): WitnessDetails = copy(
        plateFragment = plateFragment
            ?.filter { (it.isLetterOrDigit()) || (it == '-') }
            ?.take(MAX_PLATE_FRAGMENT_LENGTH)
            ?.ifBlank { null },
    )

    companion object {
        const val MAX_PLATE_FRAGMENT_LENGTH: Int = 13
    }
}
