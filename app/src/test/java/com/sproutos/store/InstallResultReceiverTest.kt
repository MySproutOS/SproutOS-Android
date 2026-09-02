package com.sproutos.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallResultReceiverTest {
    @Test
    fun `user initiated confirmation starts directly only while activity remains resumed`() {
        assertTrue(shouldStartConfirmationDirectly(userInitiated = true, activityResumed = true))
        assertFalse(shouldStartConfirmationDirectly(userInitiated = true, activityResumed = false))
    }

    @Test
    fun `background automatic confirmation never starts directly`() {
        assertFalse(shouldStartConfirmationDirectly(userInitiated = false, activityResumed = true))
        assertFalse(shouldStartConfirmationDirectly(userInitiated = false, activityResumed = false))
    }
}
