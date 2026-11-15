package com.example.pnu_me.domain.cockpit

/**
 * 공식 CarUxRestrictions가 연결되기 전 MOCK 콕핏의 보수적 조작 정책.
 *
 * 실제 Automotive 기기에서는 검증된 정책 어댑터가 없으므로 복잡 조작을 모두 막는다.
 * 일반 기기의 MOCK 데모에서만 가상 기어 P 상태를 정차로 취급한다.
 */
object CockpitInteractionPolicy {
    fun allowsComplexControls(
        state: CockpitState,
        isAutomotiveDevice: Boolean,
    ): Boolean = !isAutomotiveDevice && state.isParked
}
