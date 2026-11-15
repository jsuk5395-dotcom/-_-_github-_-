package com.example.pnu_me

/**
 * SavedState의 형식 버전을 관리한다.
 *
 * 버전 마커가 없는 이전 앱의 Bundle은 Compose가 레거시 Serializable을
 * 역직렬화하기 전에 폐기한다.
 */
internal object AppSavedStatePolicy {
    const val VERSION_KEY = "pnu_me.saved_state_format"
    const val CURRENT_VERSION = 2

    fun shouldRestore(savedVersion: Int?): Boolean = savedVersion == CURRENT_VERSION
}
