package com.example.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryOptimizationHelperTest {

    @Test
    fun testGetStatusDescriptionWhenIgnored() {
        val (statusTitle, statusDesc) = BatteryOptimizationHelper.getStatusDescription(true)
        assertEquals("절전 예외 허용됨", statusTitle)
        assertTrue(statusDesc.contains("중단 없이"))
    }

    @Test
    fun testGetStatusDescriptionWhenNotIgnored() {
        val (statusTitle, statusDesc) = BatteryOptimizationHelper.getStatusDescription(false)
        assertEquals("절전 최적화 켜짐", statusTitle)
        assertTrue(statusDesc.contains("Doze"))
    }

    @Test
    fun testIsBatteryOptimizationIgnoredDoesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isIgnored = BatteryOptimizationHelper.isBatteryOptimizationIgnored(context)
        // Should evaluate safely without uncaught exception
        assertNotNull(isIgnored)
    }

    @Test
    fun testOpenBatteryOptimizationSettingsIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val opened = BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
        assertTrue(opened)
    }
}
