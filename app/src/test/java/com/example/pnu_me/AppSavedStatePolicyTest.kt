package com.example.pnu_me

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSavedStatePolicyTest {
    @Test
    fun onlyCurrentFormatIsRestored() {
        assertFalse(AppSavedStatePolicy.shouldRestore(null))
        assertFalse(AppSavedStatePolicy.shouldRestore(1))
        assertTrue(AppSavedStatePolicy.shouldRestore(AppSavedStatePolicy.CURRENT_VERSION))
    }
}
