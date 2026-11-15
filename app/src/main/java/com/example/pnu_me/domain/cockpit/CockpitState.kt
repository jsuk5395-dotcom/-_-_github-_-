package com.example.pnu_me.domain.cockpit

import java.io.Serializable

/**
 * 시연용 MOCK 콕핏 상태. 실제 차량·Pleos Vehicle SDK와 연결되지 않으며,
 * 여기의 어떤 값도 실제 차량 상태나 안전 인증 값을 의미하지 않는다.
 */
data class CockpitState(
    val gear: Gear = Gear.PARK,
    val body: BodyState = BodyState(),
    val battery: BatteryState = BatteryState(),
    val navigation: NavigationState = NavigationState(),
    val security: SecurityState = SecurityState(),
    val assist: AssistSettings = AssistSettings(),
    val drive: DriveSettings = DriveSettings(),
    val trips: TripState = TripState(),
    val serviceMode: ServiceMode = ServiceMode.NONE,
    val profileIndex: Int = 0,
) : Serializable {
    val isParked: Boolean get() = gear == Gear.PARK
}

enum class Gear(val label: String) {
    PARK("P"),
    REVERSE("R"),
    NEUTRAL("N"),
    DRIVE("D"),
}

enum class LightMode(val label: String) {
    OFF("끔"),
    PARKING("미등"),
    ON("켬"),
    AUTO("자동"),
}

enum class WiperMode(val label: String) {
    OFF("끔"),
    AUTO("자동"),
    LOW("1단"),
    HIGH("2단"),
}

enum class DisplayTheme(val label: String) {
    AUTO("자동"),
    LIGHT("밝게"),
    DARK("어둡게"),
}

enum class DriveFeel(val label: String) {
    GENTLE("부드럽게"),
    STANDARD("표준"),
    SPORT("스포츠"),
}

enum class SteeringFeel(val label: String) {
    LIGHT("가볍게"),
    STANDARD("표준"),
    HEAVY("묵직하게"),
}

enum class StopMode(val label: String) {
    CREEP("크립"),
    ROLL("롤"),
    HOLD("홀드"),
}

enum class CollisionWarningTiming(val label: String) {
    EARLY("빠르게"),
    NORMAL("보통"),
    LATE("늦게"),
}

enum class ServiceMode(val label: String) {
    NONE("없음"),
    CAR_WASH("세차 모드"),
    TOW("견인 모드"),
    WIPER_SERVICE("와이퍼 정비 모드"),
}

enum class TripId(val label: String) {
    A("주행 A"),
    B("주행 B"),
}

data class BodyState(
    val locked: Boolean = true,
    val lights: LightMode = LightMode.AUTO,
    val wipers: WiperMode = WiperMode.AUTO,
    val mirrorsFolded: Boolean = false,
    val windowsVented: Boolean = false,
    val frontStorageOpen: Boolean = false,
    val trunkOpen: Boolean = false,
    val gloveboxOpen: Boolean = false,
    val childLock: Boolean = false,
) : Serializable

data class BatteryState(
    val percent: Int = 72,
) : Serializable

data class NavigationState(
    val destinationId: String? = null,
) : Serializable {
    val guiding: Boolean get() = destinationId != null
}

data class SecurityState(
    val parkingWatch: Boolean = false,
    val clipsSaved: Int = 0,
    val pin: String? = null,
    val driveUnlocked: Boolean = false,
    val valetMode: Boolean = false,
    val speedLimitMode: Boolean = false,
    val speedLimitKph: Int = 110,
) : Serializable

data class AssistSettings(
    val followDistance: Int = 4,
    val laneAssist: Boolean = true,
    val collisionWarning: CollisionWarningTiming = CollisionWarningTiming.NORMAL,
    val emergencyBraking: Boolean = true,
    val blindSpotAlert: Boolean = true,
    val speedLimitWarning: Boolean = true,
) : Serializable

data class DriveSettings(
    val driveFeel: DriveFeel = DriveFeel.STANDARD,
    val steeringFeel: SteeringFeel = SteeringFeel.STANDARD,
    val stopMode: StopMode = StopMode.HOLD,
    val displayTheme: DisplayTheme = DisplayTheme.AUTO,
    val brightnessPercent: Int = 70,
) : Serializable

data class TripState(
    val odometerKm: Double = 12_480.0,
    val tripAKm: Double = 42.5,
    val tripAWhPerKm: Int = 158,
    val tripBKm: Double = 318.2,
    val tripBWhPerKm: Int = 164,
) : Serializable

data class MockDestination(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val etaMinutes: Int,
)

data class MockProfile(
    val name: String,
    val driveFeel: DriveFeel,
)

/** 화면 시연용 가상 데이터. 실제 장소·사용자 정보가 아니다. */
object MockCatalog {
    val destinations: List<MockDestination> = listOf(
        MockDestination("home", "집 (가상)", 12.4, 24),
        MockDestination("work", "회사 (가상)", 8.1, 19),
        MockDestination("rest", "휴게소 (가상)", 46.0, 38),
        MockDestination("far", "장거리 목적지 (가상)", 380.0, 260),
    )

    val profiles: List<MockProfile> = listOf(
        MockProfile("운전자 1", DriveFeel.STANDARD),
        MockProfile("운전자 2", DriveFeel.GENTLE),
        MockProfile("게스트", DriveFeel.GENTLE),
    )

    fun destination(id: String?): MockDestination? = destinations.firstOrNull { it.id == id }
}

/** 시연용 단순 추정식. 실제 주행 가능 거리 예측이 아니다. */
object CockpitEstimates {
    const val DEMO_KM_PER_PERCENT = 4.5
    const val LOW_ARRIVAL_PERCENT = 10

    fun rangeKm(batteryPercent: Int): Int =
        Math.round(batteryPercent.coerceIn(0, 100) * DEMO_KM_PER_PERCENT).toInt()

    /** 도착 시 예상 잔량(%). 음수면 현재 잔량으로 도달 불가를 뜻한다. */
    fun arrivalPercent(batteryPercent: Int, distanceKm: Double): Int =
        batteryPercent - Math.ceil(distanceKm / DEMO_KM_PER_PERCENT).toInt()

    fun needsEnergyStop(batteryPercent: Int, distanceKm: Double): Boolean =
        arrivalPercent(batteryPercent, distanceKm) < LOW_ARRIVAL_PERCENT
}
