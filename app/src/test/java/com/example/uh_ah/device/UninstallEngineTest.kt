package com.example.uh_ah.device

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallEngineTest {
    @Test
    fun userInstalledPackageCanBeRemoved() {
        assertTrue(UninstallEngine.isRemovableInstalledApp(ApplicationInfo.FLAG_INSTALLED))
    }

    @Test
    fun preloadedSystemPackageIsNotRepeatedlyTargeted() {
        val flags = ApplicationInfo.FLAG_INSTALLED or ApplicationInfo.FLAG_SYSTEM
        assertFalse(UninstallEngine.isRemovableInstalledApp(flags))
    }

    @Test
    fun packageNotInstalledForCurrentUserIsNotTargeted() {
        assertFalse(UninstallEngine.isRemovableInstalledApp(0))
    }
}
