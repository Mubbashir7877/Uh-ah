package com.example.uh_ah.device

import java.util.Locale

internal object SamsungBrowserRules {
    const val STABLE = "com.sec.android.app.sbrowser"
    const val BETA = "com.sec.android.app.sbrowser.beta"
    const val PLAY = "com.android.vending"
    const val GALAXY = "com.sec.android.app.samsungapps"
    const val SETTINGS = "com.android.settings"
    val browsers = setOf(STABLE, BETA)
    val stores = setOf(PLAY, GALAXY)
    val settingsSearch = setOf(SETTINGS, "com.android.settings.intelligence", "com.google.android.settings.intelligence")
    val searchable = stores + settingsSearch
    val monitored = browsers + searchable

    private val titles = setOf(
        "samsung internet", "samsung internet browser", "samsung browser",
        "samsung internet beta", "samsung internet browser beta", "samsung browser beta"
    )
    private val listingUrls = listOf(
        Regex("""(?:market://details\?id=|https://play\.google\.com/store/apps/details\?id=)(com\.sec\.android\.app\.sbrowser(?:\.beta)?)(?:&[^\s]*)?"""),
        Regex("""(?:samsungapps://ProductDetail/|https://galaxystore\.samsung\.com/detail/)(com\.sec\.android\.app\.sbrowser(?:\.beta)?)(?:\?[^\s]*)?""")
    )
    private val actions = setOf("install", "enable", "open", "update", "get", "uninstall", "disable", "force stop")
    private val spaces = Regex("\\s+")

    fun normalize(value: String) = value.trim().replace(spaces, " ").lowercase(Locale.ROOT)
    fun isTitle(value: String) = normalize(value) in titles
    fun isSearch(value: String) = isTitle(value) || value.trim() in browsers
    fun targetId(value: String): String? {
        val text = value.trim()
        return text.removePrefix("package:").takeIf(browsers::contains)
            ?: listingUrls.firstNotNullOfOrNull { it.matchEntire(text)?.groupValues?.get(1) }
    }

    fun isPublisher(value: String): Boolean =
        normalize(value).replace(" ", "").replace(".", "").replace(",", "") == "samsungelectronicscoltd"

    fun isAction(value: String) = normalize(value) in actions
}

internal data class BrowserStoreCard(
    val targetTitle: Boolean = false,
    val targetId: Boolean = false,
    val publisher: Boolean = false,
    val appAction: Boolean = false,
    val clickable: Boolean = false
) {
    fun matches() = (targetId && (targetTitle || appAction)) ||
        (targetTitle && publisher && (appAction || clickable))
}

internal data class BrowserScreen(
    val targetedSearch: Boolean = false,
    val storeCard: Boolean = false,
    val settingsTargetId: Boolean = false,
    val settingsTargetTitle: String? = null
)

internal class SamsungBrowserGuard {
    private var attemptedPackage: String? = null

    val hasAttempted: Boolean get() = attemptedPackage != null

    fun hasAttempted(packageName: String) = attemptedPackage == packageName

    fun foregroundChanged(packageName: String) {
        if (packageName != attemptedPackage) reset()
    }

    fun reset() {
        attemptedPackage = null
    }

    fun handle(
        packageName: String,
        enabled: Boolean,
        authorized: () -> Boolean,
        screen: BrowserScreen = BrowserScreen(),
        settingsIdentityConfirmed: Boolean = false,
        goHome: () -> Unit
    ) {
        if (!enabled || hasAttempted(packageName)) return
        val match = when (packageName) {
            in SamsungBrowserRules.browsers -> true
            in SamsungBrowserRules.stores -> screen.targetedSearch || screen.storeCard
            in SamsungBrowserRules.settingsSearch -> screen.targetedSearch ||
                (packageName == SamsungBrowserRules.SETTINGS &&
                    (screen.settingsTargetId || screen.settingsTargetTitle != null && settingsIdentityConfirmed))
            else -> false
        }
        if (match && authorized()) {
            attemptedPackage = packageName
            goHome()
        }
    }
}
