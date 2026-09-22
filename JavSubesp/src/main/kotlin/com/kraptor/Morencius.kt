package com.kraptor

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import android.util.Log

class Morencius : ExtractorApi() {
    override val name = "Morencius"
    override val mainUrl = "https://morencius.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val res = app.get(
                url,
                referer = referer ?: mainUrl,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
            )
            val html = res.text

            var m3u8 = Regex(
                """["']file["']\s*:\s*["']([^"']+\.m3u8[^"']*)["']""",
                RegexOption.IGNORE_CASE
            ).find(html)?.groupValues?.get(1)

            if (m3u8 == null) {
                val rel = Regex("""["'](/stream/[^"']+\.m3u8)["']""").find(html)?.groupValues?.get(1)
                if (rel != null) m3u8 = "$mainUrl$rel"
            }

            if (m3u8 == null) {
                m3u8 = Regex("""https?://[^"'\s]+/stream/[^"'\s]+\.m3u8[^"'\s]*""")
                    .find(html)?.value
            }

            if (m3u8 != null) {
                if (m3u8.startsWith("/")) m3u8 = "$mainUrl$m3u8"
                Log.d("Morencius", "Found: $m3u8")
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = m3u8,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.headers = mapOf(
                            "Origin" to mainUrl,
                            "Referer" to url
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("Morencius", "Error: ${e.message}")
        }
    }
}