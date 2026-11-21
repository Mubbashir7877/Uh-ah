package com.example.uh_ah

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.uh_ah.device.SamsungBrowserAccessibility
import com.example.uh_ah.device.SamsungBrowserRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SamsungBrowserAccessibilityTest {
    @Test
    fun checksOnlyFocusedSearchFieldsFromTheSameWindowAndPackage() {
        val host = SamsungBrowserRules.PLAY
        val search = AccessibilityNodeInfo().apply {
            packageName = host
            viewIdResourceName = "$host:id/search_box_text_input"
            isEditable = true
            isFocused = true
            isVisibleToUser = true
            text = "Samsung Internet"
        }

        assertTrue(SamsungBrowserAccessibility.read(search, host, search).targetedSearch)

        search.text = "internet"
        assertFalse(SamsungBrowserAccessibility.read(search, host, search).targetedSearch)

        search.text = "Samsung Browser Beta"
        search.isFocused = false
        assertFalse(SamsungBrowserAccessibility.read(search, host, search).targetedSearch)

        search.isFocused = true
        search.viewIdResourceName = "$host:id/review_input"
        assertFalse(SamsungBrowserAccessibility.read(search, host, search).targetedSearch)

        search.viewIdResourceName = "$host:id/search_box_text_input"
        search.packageName = "com.example.other"
        assertFalse(SamsungBrowserAccessibility.read(search, host, search).targetedSearch)
    }
}
