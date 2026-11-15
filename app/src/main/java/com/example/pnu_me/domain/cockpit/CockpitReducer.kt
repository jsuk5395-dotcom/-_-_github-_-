package com.example.pnu_me.domain.cockpit

import com.example.pnu_me.domain.cockpit.CockpitAction.*

/**
 * MOCK 콕핏 상태 전이 규칙. 순수 Kotlin이며 실제 차량을 제어하지 않는다.
 * 조건을 만족하지 않는 조작은 상태를 바꾸지 않고 거절 사유를 돌려준다.
 */
object CockpitReducer {
    const val MIN_SPEED_LIMIT_KPH = 80
    const val MAX_SPEED_LIMIT_KPH = 150
    const val MIN_FOLLOW_DISTANCE = 2
    const val MAX_FOLLOW_DISTANCE = 7
    const val MIN_STANDBY_BATTERY = 20
    const val MAX_CLIPS = 99
    const val PIN_LENGTH = 4

    const val REASON_PARKED_ONLY = "P(주차) 상태에서만 조작할 수 있습니다."
    const val REASON_PIN_REQUIRED = "주행 PIN을 먼저 입력하세요."
    const val REASON_PIN_MISMATCH = "PIN이 일치하지 않습니다."
    const val REASON_PIN_FORMAT = "PIN은 숫자 4자리여야 합니다."
    const val REASON_CLOSURE_OPEN = "열린 적재함을 먼저 닫으세요."
    const val REASON_SERVICE_MODE = "정비·세차·견인 모드에서는 사용할 수 없습니다."
    const val REASON_VALET = "대리 주차 모드에서는 잠겨 있습니다."
    const val REASON_LOW_BATTERY = "배터리 잔량이 20% 미만이라 사용할 수 없습니다."
    const val REASON_UNKNOWN_DESTINATION = "알 수 없는 목적지입니다."
    const val REASON_INVALID = "허용되지 않는 값입니다."

    fun reduce(state: CockpitState, action: CockpitAction): CockpitOutcome = when (action) {
        is ShiftGear -> shift(state, action.gear)

        is SetLocked ->
            if (!action.locked && !state.isParked) reject(state, REASON_PARKED_ONLY)
            else body(state) { copy(locked = action.locked) }
        is SetLights -> body(state) { copy(lights = action.mode) }
        is SetWipers ->
            if (action.mode != WiperMode.OFF && state.serviceMode in WIPER_BLOCKING_MODES) {
                reject(state, REASON_SERVICE_MODE)
            } else {
                body(state) { copy(wipers = action.mode) }
            }
        is SetMirrorsFolded -> parkedOnly(state) { body(state) { copy(mirrorsFolded = action.folded) } }
        is SetWindowsVented ->
            if (action.vented && state.serviceMode == ServiceMode.CAR_WASH) {
                reject(state, REASON_SERVICE_MODE)
            } else {
                body(state) { copy(windowsVented = action.vented) }
            }
        is SetFrontStorageOpen -> openClosure(state, action.open, valetLocked = true) {
            copy(frontStorageOpen = action.open)
        }
        is SetTrunkOpen -> openClosure(state, action.open, valetLocked = false) {
            copy(trunkOpen = action.open)
        }
        is SetGloveboxOpen ->
            if (action.open && state.security.valetMode) reject(state, REASON_VALET)
            else body(state) { copy(gloveboxOpen = action.open) }
        is SetChildLock -> body(state) { copy(childLock = action.on) }

        is StartGuidance ->
            if (MockCatalog.destination(action.destinationId) == null) {
                reject(state, REASON_UNKNOWN_DESTINATION)
            } else {
                ok(state.copy(navigation = NavigationState(action.destinationId)))
            }
        CancelGuidance -> ok(state.copy(navigation = NavigationState()))

        is SetParkingWatch -> when {
            !action.on -> security(state) { copy(parkingWatch = false) }
            !state.isParked -> reject(state, REASON_PARKED_ONLY)
            state.serviceMode != ServiceMode.NONE -> reject(state, REASON_SERVICE_MODE)
            state.battery.percent < MIN_STANDBY_BATTERY -> reject(state, REASON_LOW_BATTERY)
            else -> security(state) { copy(parkingWatch = true) }
        }
        SaveClip -> security(state) { copy(clipsSaved = (clipsSaved + 1).coerceAtMost(MAX_CLIPS)) }
        is SetPin -> when {
            !state.isParked -> reject(state, REASON_PARKED_ONLY)
            !isValidPin(action.pin) -> reject(state, REASON_PIN_FORMAT)
            else -> security(state) { copy(pin = action.pin, driveUnlocked = false) }
        }
        is ClearPin -> when {
            !state.isParked -> reject(state, REASON_PARKED_ONLY)
            state.security.pin == null -> ok(state)
            state.security.pin != action.pin -> reject(state, REASON_PIN_MISMATCH)
            else -> security(state) { copy(pin = null, driveUnlocked = false) }
        }
        is EnterPin -> when {
            state.security.pin == null -> ok(state)
            state.security.pin != action.pin -> reject(state, REASON_PIN_MISMATCH)
            else -> security(state) { copy(driveUnlocked = true) }
        }
        is SetValetMode -> when {
            !state.isParked -> reject(state, REASON_PARKED_ONLY)
            action.on -> ok(
                state.copy(
                    body = state.body.copy(gloveboxOpen = false, frontStorageOpen = false),
                    drive = state.drive.copy(driveFeel = DriveFeel.GENTLE),
                    security = state.security.copy(valetMode = true, speedLimitMode = true),
                ),
            )
            state.security.pin != null && state.security.pin != action.pin ->
                reject(state, REASON_PIN_MISMATCH)
            else -> security(state) { copy(valetMode = false) }
        }
        is SetSpeedLimitMode ->
            if (!action.on && state.security.valetMode) reject(state, REASON_VALET)
            else security(state) { copy(speedLimitMode = action.on) }
        is AdjustSpeedLimit -> security(state) {
            copy(
                speedLimitKph = (speedLimitKph + action.deltaKph)
                    .coerceIn(MIN_SPEED_LIMIT_KPH, MAX_SPEED_LIMIT_KPH),
            )
        }

        is AdjustFollowDistance -> assist(state) {
            copy(
                followDistance = (followDistance + action.delta)
                    .coerceIn(MIN_FOLLOW_DISTANCE, MAX_FOLLOW_DISTANCE),
            )
        }
        is SetLaneAssist -> assist(state) { copy(laneAssist = action.on) }
        is SetCollisionWarning -> assist(state) { copy(collisionWarning = action.timing) }
        is SetEmergencyBraking -> parkedOnly(state) { assist(state) { copy(emergencyBraking = action.on) } }
        is SetBlindSpotAlert -> assist(state) { copy(blindSpotAlert = action.on) }
        is SetSpeedLimitWarning -> assist(state) { copy(speedLimitWarning = action.on) }

        is SetDriveFeel ->
            if (state.security.valetMode && action.feel != DriveFeel.GENTLE) reject(state, REASON_VALET)
            else drive(state) { copy(driveFeel = action.feel) }
        is SetSteeringFeel -> drive(state) { copy(steeringFeel = action.feel) }
        is SetStopMode -> parkedOnly(state) { drive(state) { copy(stopMode = action.mode) } }
        is SetDisplayTheme -> drive(state) { copy(displayTheme = action.theme) }
        is AdjustBrightness -> drive(state) {
            copy(brightnessPercent = (brightnessPercent + action.deltaPercent).coerceIn(10, 100))
        }

        is ResetTrip -> ok(
            state.copy(
                trips = when (action.trip) {
                    TripId.A -> state.trips.copy(tripAKm = 0.0, tripAWhPerKm = 0)
                    TripId.B -> state.trips.copy(tripBKm = 0.0, tripBWhPerKm = 0)
                },
            ),
        )
        is SetServiceMode -> serviceMode(state, action.mode)
        is SelectProfile -> {
            val profile = MockCatalog.profiles.getOrNull(action.index)
            when {
                profile == null -> reject(state, REASON_INVALID)
                !state.isParked -> reject(state, REASON_PARKED_ONLY)
                else -> ok(
                    state.copy(
                        profileIndex = action.index,
                        drive = state.drive.copy(
                            driveFeel = if (state.security.valetMode) DriveFeel.GENTLE else profile.driveFeel,
                        ),
                    ),
                )
            }
        }
    }

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    private val WIPER_BLOCKING_MODES = setOf(ServiceMode.CAR_WASH, ServiceMode.WIPER_SERVICE)

    private fun shift(state: CockpitState, target: Gear): CockpitOutcome {
        if (target == state.gear) return ok(state)
        if (target == Gear.PARK) {
            return ok(
                state.copy(
                    gear = Gear.PARK,
                    security = state.security.copy(driveUnlocked = false),
                ),
            )
        }
        if (state.security.pin != null && !state.security.driveUnlocked) {
            return reject(state, REASON_PIN_REQUIRED)
        }
        val blockedByService = when (state.serviceMode) {
            ServiceMode.NONE -> false
            ServiceMode.CAR_WASH -> target != Gear.NEUTRAL
            ServiceMode.TOW, ServiceMode.WIPER_SERVICE -> true
        }
        if (blockedByService) return reject(state, REASON_SERVICE_MODE)
        if (state.body.frontStorageOpen || state.body.trunkOpen) {
            return reject(state, REASON_CLOSURE_OPEN)
        }
        return ok(
            state.copy(
                gear = target,
                body = state.body.copy(locked = true),
                security = state.security.copy(parkingWatch = false),
            ),
        )
    }

    private fun serviceMode(state: CockpitState, mode: ServiceMode): CockpitOutcome {
        if (mode == state.serviceMode) return ok(state)
        if (!state.isParked) return reject(state, REASON_PARKED_ONLY)
        return when (mode) {
            ServiceMode.NONE, ServiceMode.TOW -> ok(state.copy(serviceMode = mode))
            ServiceMode.WIPER_SERVICE -> ok(
                state.copy(serviceMode = mode, body = state.body.copy(wipers = WiperMode.OFF)),
            )
            ServiceMode.CAR_WASH -> ok(
                state.copy(
                    serviceMode = mode,
                    body = state.body.copy(
                        wipers = WiperMode.OFF,
                        windowsVented = false,
                        mirrorsFolded = true,
                    ),
                    security = state.security.copy(parkingWatch = false),
                ),
            )
        }
    }

    private inline fun openClosure(
        state: CockpitState,
        open: Boolean,
        valetLocked: Boolean,
        change: BodyState.() -> BodyState,
    ): CockpitOutcome = when {
        !open -> body(state, change)
        !state.isParked -> reject(state, REASON_PARKED_ONLY)
        valetLocked && state.security.valetMode -> reject(state, REASON_VALET)
        else -> body(state, change)
    }

    private inline fun parkedOnly(state: CockpitState, block: () -> CockpitOutcome): CockpitOutcome =
        if (state.isParked) block() else reject(state, REASON_PARKED_ONLY)

    private inline fun body(state: CockpitState, change: BodyState.() -> BodyState) =
        ok(state.copy(body = state.body.change()))

    private inline fun security(state: CockpitState, change: SecurityState.() -> SecurityState) =
        ok(state.copy(security = state.security.change()))

    private inline fun assist(state: CockpitState, change: AssistSettings.() -> AssistSettings) =
        ok(state.copy(assist = state.assist.change()))

    private inline fun drive(state: CockpitState, change: DriveSettings.() -> DriveSettings) =
        ok(state.copy(drive = state.drive.change()))

    private fun ok(state: CockpitState) = CockpitOutcome(state)

    private fun reject(state: CockpitState, reason: String) = CockpitOutcome(state, reason)
}
