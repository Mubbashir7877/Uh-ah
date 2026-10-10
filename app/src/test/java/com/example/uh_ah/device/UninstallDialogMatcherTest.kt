package com.example.uh_ah.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallDialogMatcherTest {
    @Test
    fun matchesAppNamePresentedAsDialogTitle() {
        assertTrue(UninstallDialogMatcher.matches("com.example.notes", "Notes", listOf("Notes", "Uninstall this app?")))
    }

    @Test
    fun allowsUpdateDialogForProtectedApp() {
        assertFalse(UninstallDialogMatcher.matches("com.example.notes", "Notes", listOf("Notes", "Update this app?", "Cancel", "Update")))
    }

    @Test
    fun matchesDeviceAdminDeactivationForProtectedApp() {
        assertTrue(UninstallDialogMatcher.matches("com.example.uh_ah", "Uh-Ah", listOf("Uh-Ah", "Deactivate this device admin app?")))
    }

    @Test
    fun doesNotTreatUnrelatedDeactivationAsProtectedAppRemoval() {
        assertFalse(UninstallDialogMatcher.matches("com.example.uh_ah", "Uh-Ah", listOf("Other Admin", "Deactivate this device admin app?")))
    }

    @Test
    fun appNameAloneDoesNotIdentifyAnUninstallDialog() {
        assertFalse(UninstallDialogMatcher.matches("com.example.notes", "Notes", listOf("Notes", "Open by default")))
    }

    @Test
    fun matchesAppNameInsideUninstallQuestion() {
        assertTrue(UninstallDialogMatcher.matches("com.twitter.android", "X", listOf("Do you want to uninstall X?")))
    }

    @Test
    fun doesNotMatchShortNameAsPartOfUnrelatedText() {
        assertFalse(UninstallDialogMatcher.matches("com.twitter.android", "X", listOf("Uninstall Example Maps?")))
    }

    @Test
    fun matchesPackageNameWhenDeviceShowsIt() {
        assertTrue(UninstallDialogMatcher.matches("com.example.notes", "Notes", listOf("Remove com.example.notes?")))
    }

    @Test
    fun matchesRemoveAppWordingUsedBySomeInstallers() {
        assertTrue(UninstallDialogMatcher.matches("com.example.notes", "Notes", listOf("Remove app Notes?")))
    }
}
