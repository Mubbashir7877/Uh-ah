package com.example.uh_ah.device

/** Pure matching logic kept separate so dialog identification can be unit-tested. */
internal object UninstallDialogMatcher {
    fun matches(packageName: String, appName: String, visibleStrings: List<String>): Boolean {
        val packageNeedle = packageName.lowercase()
        val appNeedle = appName.trim().lowercase()
        val normalizedStrings = visibleStrings.map { it.trim().lowercase() }

        // The app name is commonly the title of uninstall, update, permission,
        // and other Package Installer dialogs. Identity alone therefore isn't
        // enough: only block a window that also explicitly describes uninstalling.
        val identifiesTarget = normalizedStrings.any { text ->
            text.contains(packageNeedle) || text.containsWholePhrase(appNeedle)
        }
        val describesUninstall = normalizedStrings.any { text ->
            text.containsWholePhrase("uninstall") ||
                text.containsWholePhrase("remove app") ||
                (text.containsWholePhrase("remove") &&
                    (text.contains(packageNeedle) || text.containsWholePhrase(appNeedle)))
        }
        return identifiesTarget && describesUninstall
    }

    private fun String.containsWholePhrase(phrase: String): Boolean {
        if (phrase.isEmpty()) return false
        var start = indexOf(phrase)
        while (start >= 0) {
            val beforeIsBoundary = start == 0 || !this[start - 1].isLetterOrDigit()
            val end = start + phrase.length
            val afterIsBoundary = end == length || !this[end].isLetterOrDigit()
            if (beforeIsBoundary && afterIsBoundary) return true
            start = indexOf(phrase, start + 1)
        }
        return false
    }
}
