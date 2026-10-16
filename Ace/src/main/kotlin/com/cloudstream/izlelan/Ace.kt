@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.cloudstream.izlelan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject

class Ace : MainAPI() {
    override var mainUrl = "https://arc018.stream"
    override var name = "Ace"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/watch-movies-online/?page=" to "Son Eklenen Filmler",
        "$mainUrl/watch-series-online/?page=" to "Son Eklenen Diziler",
        "$mainUrl/top-imdb/?page=" to "En Çok Beğenilenler (IMDb)",
        "$mainUrl/genre/action/?page=" to "Aksiyon",
        "$mainUrl/genre/adventure/?page=" to "Macera",
        "$mainUrl/genre/comedy/?page=" to "Komedi",
        "$mainUrl/genre/drama/?page=" to "Dram",
        "$mainUrl/genre/sci-fi/?page=" to "Bilim Kurgu",
        "$mainUrl/genre/animation/?page=" to "Animasyon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (request.data.startsWith("http")) {
            "${request.data}$page"
        } else {
            "$mainUrl/watch-movies-online/?page=$page"
        }

        val doc = try {
            app.get(targetUrl, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).document
        } catch (e: Exception) {
            app.get("$mainUrl/home", headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).document
        }

        val items = doc.select("a[href*='/watch-movie/'], a[href*='/watch-series/'], a[href*='/episode/']").mapNotNull { a ->
            a.toSearchResult()
        }.distinctBy { it.url }

        val finalItems = if (items.isNotEmpty()) {
            items
        } else {
            doc.select("article").mapNotNull { article ->
                val a = article.selectFirst("a[href]") ?: return@mapNotNull null
                a.toSearchResult()
            }.distinctBy { it.url }
        }

        return newHomePageResponse(request.name, finalItems)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val link = fixUrlNull(this.attr("href")) ?: return null
        if (!link.contains("/watch-movie/") && !link.contains("/watch-series/") && !link.contains("/episode/")) {
            return null
        }

        val title = this.selectFirst("h3, h2, .ep-label")?.text()?.trim()
            ?: this.selectFirst("img")?.attr("alt")?.trim()
            ?: this.attr("aria-label").trim().ifEmpty { null }
            ?: return null

        val poster = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        })

        val isTv = link.contains("/watch-series/") || link.contains("/episode/")
        return if (isTv) {
            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, link, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?keyword=${query.trim()}"
        val results = mutableListOf<SearchResponse>()
        try {
            val doc = app.get(searchUrl, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).document
            val items = doc.select("a[href*='/watch-movie/'], a[href*='/watch-series/']").mapNotNull {
                it.toSearchResult()
            }.distinctBy { it.url }
            results.addAll(items)
        } catch (e: Exception) {}

        if (results.isEmpty()) {
            try {
                val home = getMainPage(1, MainPageRequest(name, "$mainUrl/watch-movies-online/?page=", false))
                return home.items.flatMap { it.list }
            } catch (e: Exception) {}
        }
        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).document
        val rawTitle = doc.selectFirst("h1")?.text()?.trim() ?: name
        val cleanTitle = rawTitle.replace(Regex("""(?i)Watch Online\s*"""), "")
            .replace(Regex("""(?i)\s*with English Subtitels.*"""), "")
            .replace(Regex("""(?i)\s*•\s*Free HD.*"""), "")
            .trim()

        val poster = fixUrlNull(doc.selectFirst("meta[property='og:image']")?.attr("content")
            ?: doc.selectFirst("section[aria-label='Video player']")?.attr("data-backdrop")
            ?: doc.selectFirst("img[alt='Poster']")?.attr("src"))

        val plot = doc.selectFirst("p.text-white\\/80, div.synopsis, meta[name='description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text().trim()
        }

        val year = Regex("""\((\d{4})\)""").find(rawTitle)?.groupValues?.get(1)?.toIntOrNull()

        val isTv = url.contains("/watch-series/") || url.contains("/episode/")
        if (isTv) {
            val epElements = doc.select("a[href*='/episode/']")
            val episodes = mutableListOf<Episode>()
            val epRegex = Regex("""/s(\d+)-e(\d+)/?""")
            val added = mutableSetOf<String>()

            for (a in epElements) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                if (!added.add(epHref)) continue

                val match = epRegex.find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                val epName = a.selectFirst(".ep-label")?.text()?.trim()?.ifEmpty { null }
                    ?: a.text().trim().ifEmpty { "$sNum. Sezon $eNum. Bölüm" }

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

            return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            return newMovieLoadResponse(cleanTitle, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val doc = app.get(data, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).document
            val token = doc.selectFirst("section[aria-label='Video player']")?.attr("data-token")
                ?: Regex("""data-token=["']([^"']+)["']""").find(doc.html())?.groupValues?.get(1)

            if (!token.isNullOrEmpty()) {
                val ajaxRes = app.post(
                    "$mainUrl/ajax/ajax.php",
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to data,
                        "Origin" to mainUrl,
                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8"
                    ),
                    data = mapOf("players" to token)
                )

                val array = JSONArray(ajaxRes.text)
                for (i in 0 until array.length()) {
                    val serverObj = array.getJSONObject(i)
                    val serverName = serverObj.optString("name", "Server")
                    val embedLink = serverObj.optString("link")
                    if (embedLink.isNotEmpty()) {
                        try {
                            if (embedLink.contains("kaembed.net") || embedLink.contains("vidmoly") || serverName.contains("Vidmoly", ignoreCase = true)) {
                                val sHtml = app.get(
                                    embedLink,
                                    headers = mapOf(
                                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
                                        "Referer" to "$mainUrl/"
                                    )
                                ).text
                                val m3u8 = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""").find(sHtml)?.groupValues?.get(1)
                                if (m3u8 != null) {
                                    callback.invoke(
                                        newExtractorLink(
                                            source = name,
                                            name = "$name - $serverName",
                                            url = m3u8,
                                            type = ExtractorLinkType.M3U8
                                        ) {
                                            headers = mapOf(
                                                "Referer" to "https://kaembed.net/",
                                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                                            )
                                        }
                                    )
                                }
                            }
                            loadExtractor(embedLink, subtitleCallback, callback)
                        } catch (e: Exception) {}
                    }
                }
            }

            val iframes = doc.select("iframe").mapNotNull { fixUrlNull(it.attr("src")) }
            for (iframe in iframes) {
                try {
                    loadExtractor(iframe, subtitleCallback, callback)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        return true
    }
}
