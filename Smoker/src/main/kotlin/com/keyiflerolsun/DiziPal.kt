@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.keyiflerolsun

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Smoker : MainAPI() {
    override var mainUrl = "https://dizipal10.com.tr"
    override var name = "Smoker"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler" to "Popüler Diziler",
        "${mainUrl}/filmler" to "Popüler Filmler",
        "${mainUrl}/animeler" to "Animeler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("article.grid-card, article.type2 ul li, div.episode-item, div.grid-card").mapNotNull {
            it.toSearchResult()
        }.distinctBy { it.url }

        return newHomePageResponse(request.name, home, hasNext = false)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElem = if (this.tagName() == "a") this else this.selectFirst("a.grid-card-link, a") ?: return null
        val href = fixUrlNull(linkElem.attr("href")) ?: return null
        if (href == mainUrl || href == "$mainUrl/") return null

        val title = this.selectFirst("h3.grid-card-title, div.name, h2, h3, h4, span.title")?.text()?.trim()
            ?: linkElem.attr("title").ifEmpty { null }
            ?: this.selectFirst("img")?.attr("alt")?.trim()
            ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        })

        val badge = this.selectFirst("span.grid-card-badge, span.badge")?.text()?.lowercase() ?: ""
        val isTv = href.contains("/dizi/") || badge.contains("dizi") || href.contains("/sezon-")
        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val diziDoc = app.get("$mainUrl/diziler?kelime=$query").document
            val diziItems = diziDoc.select("article.grid-card, article.type2 ul li, div.grid-card").mapNotNull { it.toSearchResult() }
            results.addAll(diziItems)
        } catch (e: Exception) {}

        if (results.isEmpty()) {
            try {
                val filmDoc = app.get("$mainUrl/filmler?kelime=$query").document
                val filmItems = filmDoc.select("article.grid-card, article.type2 ul li, div.grid-card").mapNotNull { it.toSearchResult() }
                results.addAll(filmItems)
            } catch (e: Exception) {}
        }

        if (results.isEmpty()) {
            try {
                val mainDoc = app.get("$mainUrl/diziler").document
                val fallback = mainDoc.select("article.grid-card, article.type2 ul li, div.grid-card").mapNotNull { it.toSearchResult() }.take(10)
                results.addAll(fallback)
            } catch (e: Exception) {}
        }

        return results.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1, h2, div.cover h5, h3.grid-card-title")?.text()?.trim() ?: name
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("div.poster img, img")?.attr("src"))
        val description = document.selectFirst("div.summary p, p.overview, p.description, meta[name='description']")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim()

        val isTv = url.contains("/dizi/")
        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val epLinks = document.select("div.episode-item, a[href*='/sezon-'], a.episode-link")
            val epRegex = Regex("""(\\d+)[.-]sezon[.-](\\d+)""")

            for (it in epLinks) {
                val link = if (it.tagName() == "a") it else it.selectFirst("a") ?: continue
                val epHref = fixUrlNull(link.attr("href")) ?: continue
                val epName = it.selectFirst("div.name, .episode-title, h4")?.text()?.trim() ?: link.text().trim()
                val match = epRegex.find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

                episodes.add(newEpisode(epHref) {
                    this.name = epName.ifEmpty { "$sNum. Sezon $eNum. Bölüm" }
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
                this.plot = description
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
        val iframes = document.select("iframe").mapNotNull { fixUrlNull(it.attr("src")) }
        for (iframe in iframes) {
            loadExtractor(iframe, subtitleCallback, callback)
        }
        return true
    }
}
