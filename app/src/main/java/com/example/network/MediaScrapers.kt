package com.example.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.regex.Pattern

data class ScrapedGalleryResult(
    val title: String,
    val coverImage: String?,
    val images: List<String>
)

data class ScrapedTorrent(
    val title: String,
    val magnetUrl: String,
    val size: String,
    val seeders: Int,
    val leechers: Int,
    val siteName: String
)

object MediaScrapers {
    private const val TAG = "MediaScrapers"

    // 1. Photoset Scraper (AdultPhotoSets, PornBox, FreeOnes, direct image list)
    suspend fun scrapeGallery(url: String): ScrapedGalleryResult {
        return try {
            val html = NetworkClient.getHtml(url)
            val doc = Jsoup.parse(html)
            val title = doc.title().replace(" - AdultPhotoSets", "").replace(" - FreeOnes", "").trim()
            val images = mutableListOf<String>()

            // Extract all image links and fullsize img tags
            val elements = doc.select("a[href~=(?i)\\.(png|jpe?g|webp)], img[src~=(?i)\\.(png|jpe?g|webp)], img[data-src~=(?i)\\.(png|jpe?g|webp)]")
            for (el in elements) {
                var imgUrl = el.attr("href")
                if (imgUrl.isEmpty() || !imgUrl.matches(Regex(".*\\.(jpg|jpeg|png|webp).*", RegexOption.IGNORE_CASE))) {
                    imgUrl = el.attr("data-src")
                }
                if (imgUrl.isEmpty()) {
                    imgUrl = el.attr("src")
                }
                if (imgUrl.isNotEmpty() && !imgUrl.contains("logo") && !imgUrl.contains("banner") && !imgUrl.contains("icon")) {
                    val fullUrl = if (imgUrl.startsWith("//")) "https:$imgUrl"
                    else if (imgUrl.startsWith("/")) {
                        val base = url.substringBefore("/", url.removePrefix("https://"))
                        "https://$base$imgUrl"
                    } else imgUrl

                    if (!images.contains(fullUrl)) {
                        images.add(fullUrl)
                    }
                }
            }

            val cover = images.firstOrNull()
            ScrapedGalleryResult(title = title.ifEmpty { "Photoset Gallery" }, coverImage = cover, images = images)
        } catch (e: Exception) {
            Log.e(TAG, "scrapeGallery error", e)
            ScrapedGalleryResult("Photoset", null, emptyList())
        }
    }

    // 2. Sukebei / Torrent Scraper
    suspend fun scrapeSukebei(query: String): List<ScrapedTorrent> {
        val list = mutableListOf<ScrapedTorrent>()
        try {
            val searchUrl = "https://sukebei.nyaa.si/?f=0&c=0_0&q=${query.replace(" ", "+")}"
            val html = NetworkClient.getHtml(searchUrl)
            val doc = Jsoup.parse(html)
            val rows = doc.select("tr.default, tr.success, tr.danger")

            for (row in rows) {
                val titleEl = row.select("td:nth-child(2) a:not(.comments)").lastOrNull()
                val magnetEl = row.select("a[href^=magnet:]").firstOrNull()
                val sizeEl = row.select("td:nth-child(4)").firstOrNull()
                val seedersEl = row.select("td:nth-child(6)").firstOrNull()
                val leechersEl = row.select("td:nth-child(7)").firstOrNull()

                if (titleEl != null && magnetEl != null) {
                    list.add(
                        ScrapedTorrent(
                            title = titleEl.text().trim(),
                            magnetUrl = magnetEl.attr("href"),
                            size = sizeEl?.text()?.trim() ?: "Unknown",
                            seeders = seedersEl?.text()?.trim()?.toIntOrNull() ?: 0,
                            leechers = leechersEl?.text()?.trim()?.toIntOrNull() ?: 0,
                            siteName = "Sukebei Nyaa"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scrapeSukebei error", e)
        }
        return list
    }

    // 5. SubtitleCat Scraper
    suspend fun scrapeSubtitleCat(code: String): String? {
        return try {
            val url = "https://www.subtitlecat.com/index.php?search=${code.trim()}"
            val html = NetworkClient.getHtml(url)
            val doc = Jsoup.parse(html)
            val subLink = doc.select("a[href*=/subtitles/]").firstOrNull()?.attr("href")
            if (subLink != null) {
                val fullUrl = if (subLink.startsWith("http")) subLink else "https://www.subtitlecat.com$subLink"
                val pageHtml = NetworkClient.getHtml(fullUrl)
                val dlDoc = Jsoup.parse(pageHtml)
                val downloadLink = dlDoc.select("a[href*=/download/]").firstOrNull()?.attr("href")
                if (downloadLink != null) {
                    if (downloadLink.startsWith("http")) downloadLink else "https://www.subtitlecat.com$downloadLink"
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "scrapeSubtitleCat error", e)
            null
        }
    }
}
