package com.example.uh_ah.playstore

import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** A single Play Store search hit. */
data class PlayStoreResult(
    val title: String,
    val packageName: String,
    val iconUrl: String?
)

sealed class PlayStoreSearchResult {
    /** [wasCorrected] is true when the best match's title isn't the query verbatim (fuzzy/typo correction). */
    data class Found(val result: PlayStoreResult, val wasCorrected: Boolean) : PlayStoreSearchResult()
    object NotFound : PlayStoreSearchResult()
    data class Error(val message: String) : PlayStoreSearchResult()
}

/**
 * Looks apps up by scraping the public Play Store search results page.
 *
 * There is no official public API for searching arbitrary Play Store listings
 * (the Google Play Developer API only manages apps you personally publish), so
 * this parses the same HTML a mobile browser would get. It's inherently
 * unofficial: Google can change the page markup at any time and break parsing.
 * Every failure mode here (network error, empty/changed markup, no matches)
 * degrades to [PlayStoreSearchResult.Error] / [PlayStoreSearchResult.NotFound]
 * rather than throwing, so the UI can fall back to manual entry.
 */
object PlayStoreSearchClient {

    private const val SEARCH_URL = "https://play.google.com/store/search?q=%s&c=apps&hl=en&gl=us"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    suspend fun search(query: String): PlayStoreSearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext PlayStoreSearchResult.NotFound
        try {
            val url = SEARCH_URL.format(URLEncoder.encode(trimmed, "UTF-8"))
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .get()

            val results = parseResults(doc)
            if (results.isEmpty()) return@withContext PlayStoreSearchResult.NotFound

            val best = results.maxBy { similarity(trimmed, it.title) }
            val wasCorrected = !best.title.equals(trimmed, ignoreCase = true)
            PlayStoreSearchResult.Found(best, wasCorrected)
        } catch (e: IOException) {
            PlayStoreSearchResult.Error(e.message ?: "Network error reaching the Play Store")
        } catch (e: Exception) {
            // Markup changed shape, parsing assumption broke, etc. Never crash the caller.
            PlayStoreSearchResult.Error(e.message ?: "Couldn't read the Play Store search results")
        }
    }

    /**
     * Search-result tiles are `<a href="/store/apps/details?id=...">` anchors carrying
     * the display name in `aria-label` (the anchor itself has no visible text/img
     * children); the icon `<img>` lives a couple of levels down inside the same tile,
     * as a sibling structure rather than inside the anchor. We key off aria-label +
     * the nearest play-lh.googleusercontent.com image instead of any specific class
     * name, since Google's class names are obfuscated and change across deploys.
     */
    private fun parseResults(doc: Document): List<PlayStoreResult> {
        val anchors = doc.select("a[href*=/store/apps/details?id=][aria-label]")
        val seen = LinkedHashMap<String, PlayStoreResult>()
        for (a in anchors) {
            val packageName = a.attr("href").substringAfter("id=").substringBefore("&")
            if (packageName.isBlank() || seen.containsKey(packageName)) continue
            val title = a.attr("aria-label").trim()
            if (title.isBlank()) continue
            val rawIcon = a.parent()
                ?.select("img[src^=https://play-lh.googleusercontent.com]")
                ?.firstOrNull()
                ?.attr("src")
            seen[packageName] = PlayStoreResult(
                title = title,
                packageName = packageName,
                iconUrl = rawIcon?.let { upscaleIcon(it) }
            )
        }
        return seen.values.toList()
    }

    /** Play Store icon URLs end in a size suffix like "=s64-rw"; ask for a crisper size for the app card. */
    private fun upscaleIcon(url: String): String =
        Regex("=s\\d+-rw$").replace(url, "=s256-rw")

    /** Normalized similarity in [0,1] from Levenshtein edit distance, used to pick/flag the best match. */
    private fun similarity(a: String, b: String): Double {
        val distance = levenshtein(a.lowercase(), b.lowercase())
        val maxLen = maxOf(a.length, b.length, 1)
        return 1.0 - distance.toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[a.length][b.length]
    }
}
