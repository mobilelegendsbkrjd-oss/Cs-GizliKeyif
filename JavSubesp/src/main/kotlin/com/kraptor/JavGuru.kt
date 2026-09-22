package com.kraptor

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import android.util.Log
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.Jsoup
import java.net.URI

class JavEnSpanish : MainAPI() {
    override var mainUrl = "https://javenspanish.com"
    override var name = "JavEnSpanish"
    override val hasMainPage = true
    override var lang = "es"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)

    private val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8",
        "Referer" to "$mainUrl/",
        "Cache-Control" to "max-age=0",
        "Upgrade-Insecure-Requests" to "1"
    )

    override val mainPage = mainPageOf(
        mainUrl to "Inicio",
        "$mainUrl/category/cornudos/" to "Cornudos",
        "$mainUrl/category/casadas/" to "Casadas",
        "$mainUrl/category/milf/" to "MILF",
        "$mainUrl/category/colegialas/" to "Colegialas",
        "$mainUrl/category/familia/" to "Familia",
        "$mainUrl/category/madrastra/" to "Madrastra",
        "$mainUrl/category/padrastro/" to "Padrastro",
        "$mainUrl/category/hermanastros/" to "Hermanastros",
        "$mainUrl/category/cunada/" to "Cuñada",
        "$mainUrl/category/jefes/" to "Jefes",
        "$mainUrl/category/anal/" to "Anal",
        "$mainUrl/category/interracial/" to "Interracial",
        "$mainUrl/category/live-action/" to "Live-Action",
        "$mainUrl/category/orgias/" to "Orgías",
        "$mainUrl/category/publico/" to "Público",
        "$mainUrl/category/profesores/" to "Profesores",
        "$mainUrl/category/novios/" to "Novios",
        "$mainUrl/category/desconocidos/" to "Desconocidos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            if (request.data == mainUrl) {
                "$mainUrl/page/$page/"
            } else {
                "${request.data.removeSuffix("/")}/page/$page/"
            }
        }

        Log.d("JavEnSpanish", "MainPage URL: $url")

        val document = app.get(url, headers = mainHeaders).document
        val items = document.select("article.cpg-card, .cpg-card")

        val home = items.mapNotNull { it.toSearchResponse() }
        val hasNext = document.select(".cpg-pagination a.next, .page-numbers.next, a.page-numbers:contains(Siguiente)").isNotEmpty()
                || home.size >= 12

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = hasNext
        )
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val linkElement = this.selectFirst("a")
        val href = fixUrlNull(linkElement?.attr("href")) ?: return null

        val title = this.selectFirst(".cpg-title")?.text()?.trim()
            ?: this.selectFirst("h3")?.text()?.trim()
            ?: linkElement?.attr("title")?.trim()
            ?: this.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        if (title.isBlank() || title.contains("Advanced search", ignoreCase = true)) return null

        val posterUrl = fixUrlNull(
            this.selectFirst("img")?.attr("src")
                ?: this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("source")?.attr("srcset")?.split(" ")?.firstOrNull()
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
            this.posterHeaders = mainHeaders
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) {
            "$mainUrl/?s=${query.replace(" ", "+")}"
        } else {
            "$mainUrl/page/$page/?s=${query.replace(" ", "+")}"
        }

        val document = app.get(url, headers = mainHeaders).document
        val items = document.select("article.cpg-card, .cpg-card")

        val results = items.mapNotNull { it.toSearchResponse() }
        val hasNext = results.isNotEmpty() && document.select(".cpg-pagination a.next, a.page-numbers:contains(Siguiente)").isNotEmpty()

        return newSearchResponseList(results, hasNext = hasNext)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mainHeaders).document

        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("title")?.text()?.substringBefore("–")?.trim()
            ?: "Unknown"

        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".cpg-thumb-wrap img, img.wp-post-image")?.attr("src")
        )

        val description = document.select("div.entry-content p, .post-content p, article p")
            .map { it.text().trim() }
            .filter { it.length > 40 && !it.contains("Telegram") && !it.contains("Copyright") }
            .joinToString("\n")
            .ifBlank { "JAV subtitulado en español – JavEnSpanish" }

        val year = Regex("""(20\d{2})""").find(
            document.selectFirst("time, .cpg-meta, .entry-date")?.text() ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()

        val tags = document.select("a[rel=tag], .cpg-badge, a[href*=/category/]")
            .mapNotNull { it.text().trim().takeIf { t -> t.isNotBlank() && t.length < 30 } }
            .distinct()

        val actors = document.select("a[href*=/actriz/], a[href*=/actress/], .actor a")
            .mapNotNull { it.text().trim().takeIf { n -> n.isNotBlank() } }
            .distinct()
            .map { Actor(it) }

        val recommendations = document.select("article.cpg-card, .related .cpg-card")
            .mapNotNull { it.toSearchResponse() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.posterHeaders = mainHeaders
            this.plot = description
            this.year = year
            this.tags = tags
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = app.get(data, headers = mainHeaders)
        val document = res.document
        val html = res.text

        val processed = mutableSetOf<String>()

        // 1) VIP / direct stream-delivery m3u8 (often in iframe src or data)
        val vipRegex = Regex(
            """(?:src|data-src)=["'](https?://jav\.stream-delivery\.net/player\.html\?src=([^"']+))["']""",
            RegexOption.IGNORE_CASE
        )
        vipRegex.findAll(html).forEach { match ->
            val fullPlayer = match.groupValues[1]
            val m3u8Encoded = match.groupValues[2]
            val m3u8 = try {
                java.net.URLDecoder.decode(m3u8Encoded, "UTF-8")
            } catch (e: Exception) {
                m3u8Encoded
            }
            if (m3u8.contains(".m3u8") && processed.add(m3u8)) {
                Log.d("JavEnSpanish", "VIP m3u8: $m3u8")
                callback.invoke(
                    newExtractorLink(
                        source = "$name VIP",
                        name = "VIP",
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://jav.stream-delivery.net/"
                        this.headers = mapOf(
                            "User-Agent" to mainHeaders["User-Agent"]!!,
                            "Origin" to "https://jav.stream-delivery.net"
                        )
                    }
                )
            }
        }

        // Also catch bare m3u8 from stream-delivery
        Regex("""https?://jav\.stream-delivery\.net/[^"'\s]+\.m3u8[^"'\s]*""")
            .findAll(html).forEach { m ->
                val m3u8 = m.value
                if (processed.add(m3u8)) {
                    callback.invoke(
                        newExtractorLink("$name VIP", "VIP", m3u8, ExtractorLinkType.M3U8) {
                            this.referer = "https://jav.stream-delivery.net/"
                        }
                    )
                }
            }

        // 2) All iframes (FM = Filemoon/Byse, VD = morencius, etc.)
        val iframeUrls = mutableListOf<Pair<String, String>>() // url to label

        document.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }
            if (src.isNotBlank() && !src.startsWith("about:") && !src.contains("ads")) {
                val label = when {
                    src.contains("byse") || src.contains("filemoon") || src.contains("moon") -> "FM"
                    src.contains("morencius") || src.contains("pixibay") -> "VD"
                    src.contains("stream-delivery") -> "VIP"
                    else -> "Source"
                }
                iframeUrls.add(src to label)
            }
        }

        // Also from tab buttons / data attributes if present
        Regex("""data-src=["'](https?://[^"']+)["']""").findAll(html).forEach { m ->
            val src = m.groupValues[1]
            if (src.contains("byse") || src.contains("morencius") || src.contains("embed")) {
                val label = if (src.contains("byse") || src.contains("filemoon")) "FM" else "VD"
                if (iframeUrls.none { it.first == src }) {
                    iframeUrls.add(src to label)
                }
            }
        }

        for ((embedUrl, label) in iframeUrls.distinctBy { it.first }) {
            try {
                Log.d("JavEnSpanish", "Processing embed [$label]: $embedUrl")

                if (embedUrl.contains("stream-delivery.net")) {
                    // already handled above
                    continue
                }

                if (embedUrl.contains("byse") || embedUrl.contains("filemoon") ||
                    embedUrl.contains("moonmov") || embedUrl.contains("kerapoxy") ||
                    Regex("""[a-z0-9]{8,}\.(sx|top|link|nl|wf|com|eu|art|pro|cc|xyz|org|fun|net|lol|online)""").containsMatchIn(embedUrl)
                ) {
                    // Filemoon / Byse family → use registered extractors
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                    continue
                }

                if (embedUrl.contains("morencius.com") || embedUrl.contains("pixibay.cc")) {
                    loadExtractor(embedUrl, data, subtitleCallback, callback)
                    // also try manual in case extractor misses
                    extractMorencius(embedUrl, label, callback)
                    continue
                }

                // Generic fallback
                loadExtractor(embedUrl, data, subtitleCallback, callback)

            } catch (e: Exception) {
                Log.e("JavEnSpanish", "Error on $embedUrl: ${e.message}")
            }
        }

        return true
    }

    private suspend fun extractMorencius(
        embedUrl: String,
        label: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(
                embedUrl,
                headers = mainHeaders + mapOf("Referer" to mainUrl)
            )
            val html = res.text

            // JWPlayer sources
            val fileRegex = Regex(
                """["']file["']\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
                RegexOption.IGNORE_CASE
            )
            var m3u8 = fileRegex.find(html)?.groupValues?.get(1)

            if (m3u8 == null) {
                // relative /stream/...
                val rel = Regex("""["'](/stream/[^"']+\.m3u8)["']""").find(html)?.groupValues?.get(1)
                if (rel != null) {
                    m3u8 = "https://morencius.com$rel"
                }
            }

            if (m3u8 == null) {
                // try playlist in JS
                m3u8 = Regex("""https?://[^"'\s]+/stream/[^"'\s]+\.m3u8[^"'\s]*""")
                    .find(html)?.value
            }

            if (m3u8 != null) {
                if (m3u8.startsWith("/")) {
                    m3u8 = "https://morencius.com$m3u8"
                }
                Log.d("JavEnSpanish", "Morencius m3u8: $m3u8")
                callback.invoke(
                    newExtractorLink(
                        source = "$name $label",
                        name = label,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://morencius.com/"
                        this.headers = mapOf(
                            "User-Agent" to mainHeaders["User-Agent"]!!,
                            "Origin" to "https://morencius.com",
                            "Referer" to embedUrl
                        )
                    }
                )
            } else {
                // last resort – pass to generic extractor
                loadExtractor(embedUrl, mainUrl, { }, callback)
            }
        } catch (e: Exception) {
            Log.e("JavEnSpanish", "Morencius error: ${e.message}")
        }
    }
}