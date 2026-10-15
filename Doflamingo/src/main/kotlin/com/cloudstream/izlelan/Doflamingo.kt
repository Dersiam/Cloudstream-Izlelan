@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.cloudstream.izlelan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Doflamingo : MainAPI() {
    override var mainUrl = "https://asyawatch.com"
    override var name = "Doflamingo"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val document = app.get(mainUrl).document
        val items = document.select("div.movie, article, div.film, div.item, div.poster-box").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElem = if (this.tagName() == "a") this else this.selectFirst("a") ?: return null
        val href = fixUrlNull(linkElem.attr("href")) ?: return null
        if (href == mainUrl || href == "$mainUrl/") return null

        val title = this.selectFirst("div.title, h2, h3, h4, .title, a.title")?.text()?.trim()
            ?: linkElem.attr("title").ifEmpty { null }
            ?: return null

        val poster = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("data-src").ifEmpty { it.attr("data-original").ifEmpty { it.attr("src") } }
        })

        val isTv = href.contains("/dizi") || href.contains("/series") || href.contains("/sezon")
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        return document.select("div.result-item, div.movie, article, div.film, div.item, div.poster-box, li:has(a)").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: name
        val poster = fixUrlNull(document.selectFirst("div.poster img, img.poster, div.img img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        })
        val description = document.selectFirst("div.description, p.overview, div.summary, div.content p")?.text()?.trim()

        val episodeElements = document.select("a[href*='-sezon-'], a[href*='-bolum-'], a[href*='/bolum/'], a.episode-link")
        if (episodeElements.isNotEmpty()) {
            val eps = mutableListOf<Episode>()
            val epRegex = Regex("""(\d+)[.-]sezon[.-](\d+)[.-]bolum""")
            val added = mutableSetOf<String>()

            for (a in episodeElements) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                if (!added.add(epHref)) continue
                val match = epRegex.find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

                eps.add(newEpisode(epHref) {
                    this.name = a.text().trim().ifEmpty { "$sNum. Sezon $eNum. Bölüm" }
                    this.season = sNum
                    this.episode = eNum
                })
            }
            if (eps.isNotEmpty()) {
                return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                    this.posterUrl = poster
                    this.plot = description
                }
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Find iframes
        val iframes = document.select("iframe").mapNotNull { fixUrlNull(it.attr("data-src").ifEmpty { it.attr("src") }) }
        for (iframe in iframes) {
            loadExtractor(iframe, subtitleCallback, callback)
        }

        // Direct m3u8 / mp4 in script tags
        for (script in document.select("script")) {
            val scriptData = script.data()
            if (scriptData.contains("file:") || scriptData.contains("source") || scriptData.contains(".m3u8")) {
                val m3u8 = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""").find(scriptData)?.groupValues?.get(1)
                if (m3u8 != null) {
                    callback.invoke(
                        newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                            headers = mapOf("Referer" to "$mainUrl/")
                        }
                    )
                }
            }
        }
        return true
    }
}
