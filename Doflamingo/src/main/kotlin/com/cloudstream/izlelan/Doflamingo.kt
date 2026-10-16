@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.cloudstream.izlelan

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.json.JSONArray

class Doflamingo : MainAPI() {
    override var mainUrl = "https://asyawatch.com"
    override var name = "Doflamingo"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        mainUrl to "Son Eklenenler",
        "$mainUrl/dizi-izle" to "Diziler",
        "$mainUrl/film-izle" to "Filmler",
        "$mainUrl/trend" to "Trendler"
    )

    private fun decodeBase64(input: String): String? {
        return try {
            val decoded = Base64.decode(input, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseItem(item: JSONObject): SearchResponse? {
        val title = item.optString("original_title").ifEmpty {
            item.optString("culture_title").ifEmpty {
                item.optString("used_title").ifEmpty { item.optString("name") }
            }
        }
        val slug = item.optString("used_slug").ifEmpty { item.optString("slug") }
        if (title.isEmpty() || slug.isEmpty()) return null

        val poster = fixUrlNull(item.optString("poster_url").ifEmpty {
            item.optString("face_url").ifEmpty { item.optString("brand_url") }
        })
        val href = fixUrlNull("$mainUrl/$slug") ?: return null
        val isTv = slug.startsWith("dizi/") || item.has("season_count") || item.optString("type_id") == "1"

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<SearchResponse>()
        try {
            val doc = app.get(
                request.data,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ).document

            val nextScript = doc.selectFirst("script#__NEXT_DATA__")?.html()
            if (nextScript != null) {
                val json = JSONObject(nextScript)
                val secStr = json.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
                if (secStr.isNotEmpty()) {
                    val dec = decodeBase64(secStr)
                    if (dec != null) {
                        val secObj = JSONObject(dec)

                        // 1. Check listItems (on /film-izle, /dizi-izle)
                        val listItems = secObj.optJSONArray("listItems")
                        if (listItems != null) {
                            for (i in 0 until listItems.length()) {
                                parseItem(listItems.getJSONObject(i))?.let { items.add(it) }
                            }
                        }

                        // 2. Check getLastSeriesAll / getLastMovies
                        val seriesArr = secObj.optJSONArray("getLastSeriesAll")
                        if (seriesArr != null) {
                            for (i in 0 until seriesArr.length()) {
                                parseItem(seriesArr.getJSONObject(i))?.let { items.add(it) }
                            }
                        }
                        val moviesArr = secObj.optJSONArray("getLastMovies")
                        if (moviesArr != null) {
                            for (i in 0 until moviesArr.length()) {
                                parseItem(moviesArr.getJSONObject(i))?.let { items.add(it) }
                            }
                        }

                        if (items.isEmpty()) {
                            val epBrand = secObj.optJSONArray("getEpisodesOnBrandAll")
                            if (epBrand != null) {
                                for (i in 0 until epBrand.length()) {
                                    parseItem(epBrand.getJSONObject(i))?.let { items.add(it) }
                                }
                            }
                        }
                    }
                }
            }

            // Fallback to DOM parsing if needed
            if (items.isEmpty()) {
                doc.select("a[href*='/dizi/'], a[href*='/film/']").forEach { a ->
                    val href = fixUrlNull(a.attr("href")) ?: return@forEach
                    if (href == mainUrl || href == "$mainUrl/" || href.contains("/kesfet") || href.contains("/trend")) return@forEach
                    val title = a.attr("title").ifEmpty { a.selectFirst("img")?.attr("alt") ?: a.text().trim() }
                    if (title.isEmpty()) return@forEach
                    val poster = fixUrlNull(a.selectFirst("img")?.let { it.attr("src").ifEmpty { it.attr("data-src") } })
                    val isTv = href.contains("/dizi/")
                    if (isTv) {
                        items.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster })
                    } else {
                        items.add(newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster })
                    }
                }
            }
        } catch (_: Exception) {}

        return newHomePageResponse(request.name, items.distinctBy { it.url })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        try {
            val res = app.post(
                "$mainUrl/api/bg/searchContent?searchterm=${query.trim()}",
                data = mapOf(),
                headers = mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ),
                referer = "$mainUrl/"
            ).text

            val json = JSONObject(res)
            val respCipher = json.optString("response")
            if (respCipher.isNotEmpty()) {
                val dec = decodeBase64(respCipher)
                if (dec != null) {
                    val decObj = JSONObject(dec)
                    val arr = decObj.optJSONArray("result")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val title = item.optString("object_name").ifEmpty { item.optString("object_alternative_name") }
                            val slug = item.optString("used_slug").ifEmpty { item.optString("slug") }
                            if (title.isEmpty() || slug.isEmpty()) continue
                            val poster = fixUrlNull(item.optString("object_poster_url").ifEmpty { item.optString("poster_url") })
                            val href = fixUrlNull("$mainUrl/$slug") ?: continue
                            val isTv = item.optString("used_type") == "Series" || slug.startsWith("dizi/")

                            if (isTv) {
                                searchResults.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                                    this.posterUrl = poster
                                })
                            } else {
                                searchResults.add(newMovieSearchResponse(title, href, TvType.Movie) {
                                    this.posterUrl = poster
                                })
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (searchResults.isEmpty()) {
            return getMainPage(1, MainPageRequest("Son Eklenenler", mainUrl, false)).items.flatMap { it.list }
        }
        return searchResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
        val isTv = url.contains("/dizi/")
        var title = doc.selectFirst("h1")?.text()?.trim() ?: name
        var poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content") ?: doc.selectFirst("img")?.attr("src"))
        var plot = doc.selectFirst("meta[property='og:description']")?.attr("content") ?: doc.selectFirst("p")?.text()?.trim()
        var year: Int? = null

        val script = doc.selectFirst("script#__NEXT_DATA__")?.html()
        if (script != null) {
            try {
                val json = JSONObject(script)
                val secStr = json.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
                if (secStr.isNotEmpty()) {
                    val dec = decodeBase64(secStr)
                    if (dec != null) {
                        val secObj = JSONObject(dec)
                        val ci = secObj.optJSONObject("contentItem")
                        if (ci != null) {
                            title = ci.optString("original_title").ifEmpty { ci.optString("culture_title").ifEmpty { title } }
                            poster = fixUrlNull(ci.optString("poster_url").ifEmpty { ci.optString("face_url") }) ?: poster
                            plot = ci.optString("description").ifEmpty { plot }
                            year = ci.optInt("release_year").takeIf { it > 0 }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val added = mutableSetOf<String>()
            val epRegex = Regex("""sezon-(\d+)/bolum-(\d+)""")

            doc.select("a[href*='bolum']").forEach { a ->
                val epHref = fixUrlNull(a.attr("href")) ?: return@forEach
                if (epHref == url || !added.add(epHref)) return@forEach
                val match = epRegex.find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: (episodes.size + 1)
                val epName = a.text().trim().ifEmpty { "$sNum. Sezon $eNum. Bölüm" }

                episodes.add(newEpisode(epHref) {
                    this.name = epName
                    this.season = sNum
                    this.episode = eNum
                })
            }

            if (episodes.isEmpty()) {
                episodes.add(newEpisode(url) {
                    this.name = "1. Sezon 1. Bölüm"
                    this.season = 1
                    this.episode = 1
                })
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val doc = app.get(data, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
            val iframes = mutableListOf<String>()

            val script = doc.selectFirst("script#__NEXT_DATA__")?.html()
            if (script != null) {
                try {
                    val json = JSONObject(script)
                    val secStr = json.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
                    if (secStr.isNotEmpty()) {
                        val dec = decodeBase64(secStr)
                        if (dec != null) {
                            val secObj = JSONObject(dec)
                            val relResults = secObj.optJSONObject("RelatedResults")
                            val sourcesArr = (relResults?.optJSONObject("getEpisodeSources") ?: secObj.optJSONObject("getEpisodeSources"))?.optJSONArray("result")
                            if (sourcesArr != null) {
                                for (i in 0 until sourcesArr.length()) {
                                    val srcItem = sourcesArr.getJSONObject(i)
                                    val contentHtml = srcItem.optString("source_content")
                                    val iframeSrc = Regex("""src=["']([^"']+)["']""").find(contentHtml)?.groupValues?.get(1)
                                    if (iframeSrc != null) {
                                        val fullIframe = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else fixUrl(iframeSrc)
                                        iframes.add(fullIframe)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            doc.select("iframe").mapNotNull { fixUrlNull(it.attr("src")) }.forEach { iframes.add(it) }

            for (iframe in iframes.distinct()) {
                if (iframe.contains("pichive.online") || iframe.contains("contentx.me") || iframe.contains("hotlinger.com") || iframe.contains("playru.net")) {
                    try {
                        val domain = if (iframe.contains("://")) {
                            val scheme = iframe.substringBefore("://")
                            val host = iframe.substringAfter("://").substringBefore("/")
                            "$scheme://$host"
                        } else {
                            "https://four.pichive.online"
                        }

                        val iSource = app.get(
                            iframe,
                            referer = data,
                            headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        ).text

                        val iExtract = Regex("""(?:window\.)?openPlayer\('([^']+)'""").find(iSource)?.groups?.get(1)?.value
                        if (iExtract != null) {
                            val vidSource = app.get(
                                "${domain}/source2.php?v=${iExtract}",
                                referer = iframe,
                                headers = mapOf(
                                    "X-Requested-With" to "XMLHttpRequest",
                                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                                )
                            ).text

                            val vidExtract = Regex("""file"\s*:\s*"([^"]+)""").find(vidSource)?.groups?.get(1)?.value
                            if (vidExtract != null) {
                                val m3uLink = vidExtract.replace("\\/", "/").replace("m.php", "master.m3u8")
                                callback.invoke(
                                    newExtractorLink(
                                        source = this.name,
                                        name = this.name,
                                        url = m3uLink,
                                        type = ExtractorLinkType.M3U8
                                    ) {
                                        this.referer = "$domain/"
                                        this.quality = Qualities.P1080.value
                                    }
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                try {
                    loadExtractor(iframe, data, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return true
    }
}
