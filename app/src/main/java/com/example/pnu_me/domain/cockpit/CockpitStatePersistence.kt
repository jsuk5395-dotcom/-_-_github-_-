package com.example.pnu_me.domain.cockpit

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 콕핏의 임시 UI 상태를 원시 바이트로 저장한다.
 *
 * 저장 키 버전이 바뀌거나 직렬화 스키마가 호환되지 않으면 이전 값을 버리고
 * 기본 상태로 시작해 SavedState 복원 중 앱이 종료되지 않도록 한다.
 */
internal object CockpitStatePersistence {
    const val SAVE_KEY = "cockpit_state_v2"

    fun encode(state: CockpitState): ByteArray? = try {
        ByteArrayOutputStream().use { bytes ->
            ObjectOutputStream(bytes).use { output ->
                output.writeObject(state)
            }
            bytes.toByteArray()
        }
    } catch (_: Exception) {
        null
    }

    fun decode(payload: Any): CockpitState? {
        val bytes = (payload as? ByteArray) ?: return null
        return try {
            ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
                input.readObject() as? CockpitState
            }
        } catch (_: Exception) {
            null
        }
    }
}
