package com.example.pnu_me.domain.cockpit

sealed interface CockpitAction {
    data class ShiftGear(val gear: Gear) : CockpitAction

    // 차체
    data class SetLocked(val locked: Boolean) : CockpitAction
    data class SetLights(val mode: LightMode) : CockpitAction
    data class SetWipers(val mode: WiperMode) : CockpitAction
    data class SetMirrorsFolded(val folded: Boolean) : CockpitAction
    data class SetWindowsVented(val vented: Boolean) : CockpitAction
    data class SetFrontStorageOpen(val open: Boolean) : CockpitAction
    data class SetTrunkOpen(val open: Boolean) : CockpitAction
    data class SetGloveboxOpen(val open: Boolean) : CockpitAction
    data class SetChildLock(val on: Boolean) : CockpitAction

    // 내비게이션
    data class StartGuidance(val destinationId: String) : CockpitAction
    data object CancelGuidance : CockpitAction

    // 보안
    data class SetParkingWatch(val on: Boolean) : CockpitAction
    data object SaveClip : CockpitAction
    data class SetPin(val pin: String) : CockpitAction
    data class ClearPin(val pin: String) : CockpitAction
    data class EnterPin(val pin: String) : CockpitAction
    data class SetValetMode(val on: Boolean, val pin: String? = null) : CockpitAction
    data class SetSpeedLimitMode(val on: Boolean) : CockpitAction
    data class AdjustSpeedLimit(val deltaKph: Int) : CockpitAction

    // 주행 보조 설정(표시 전용)
    data class AdjustFollowDistance(val delta: Int) : CockpitAction
    data class SetLaneAssist(val on: Boolean) : CockpitAction
    data class SetCollisionWarning(val timing: CollisionWarningTiming) : CockpitAction
    data class SetEmergencyBraking(val on: Boolean) : CockpitAction
    data class SetBlindSpotAlert(val on: Boolean) : CockpitAction
    data class SetSpeedLimitWarning(val on: Boolean) : CockpitAction

    // 주행 감각·디스플레이
    data class SetDriveFeel(val feel: DriveFeel) : CockpitAction
    data class SetSteeringFeel(val feel: SteeringFeel) : CockpitAction
    data class SetStopMode(val mode: StopMode) : CockpitAction
    data class SetDisplayTheme(val theme: DisplayTheme) : CockpitAction
    data class AdjustBrightness(val deltaPercent: Int) : CockpitAction

    // 주행 기록·정비·프로필
    data class ResetTrip(val trip: TripId) : CockpitAction
    data class SetServiceMode(val mode: ServiceMode) : CockpitAction
    data class SelectProfile(val index: Int) : CockpitAction
}

data class CockpitOutcome(
    val state: CockpitState,
    /** null이면 적용됨. 값이 있으면 상태를 바꾸지 않고 거절한 이유다. */
    val rejection: String? = null,
)
