@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.cloudstream.izlelan

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class Lili : MainAPI() {
    override var mainUrl = "https://api.sonanime.com"
    override var name = "Lili"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    private data class AnimeItem(
        @JsonProperty("anime_id") val animeId: Int? = null,
        @JsonProperty("anime_name") val animeName: String? = null,
        @JsonProperty("anime_name_en") val animeNameEn: String? = null,
        @JsonProperty("anime_type") val animeType: String? = null,
        @JsonProperty("anime_photo") val animePhoto: String? = null,
        @JsonProperty("anime_link") val animeLink: String? = null
    )

    private data class EpisodeItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("anime_id") val animeId: Int? = null,
        @JsonProperty("anime_season") val animeSeason: Int? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("episode_link_1080") val link1080: String? = null,
        @JsonProperty("episode_link_720") val link720: String? = null,
        @JsonProperty("episode_link_480") val link480: String? = null,
        @JsonProperty("episode_link_360") val link360: String? = null
    )

    private data class AnimeDetail(
        @JsonProperty("anime_id") val animeId: Int? = null,
        @JsonProperty("anime_name") val animeName: String? = null,
        @JsonProperty("anime_description") val animeDescription: String? = null,
        @JsonProperty("anime_year") val animeYear: String? = null,
        @JsonProperty("anime_photo") val animePhoto: String? = null,
        @JsonProperty("anime_link") val animeLink: String? = null,
        @JsonProperty("episodes") val episodes: List<EpisodeItem>? = null
    )

    override val mainPage = mainPageOf(
        "$mainUrl/api/anime" to "Son Eklenen Animeler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val animes = app.get(request.data).parsedSafe<List<AnimeItem>>() ?: emptyList()
        val home = animes.mapNotNull { item ->
            val link = item.animeLink ?: return@mapNotNull null
            val title = item.animeName ?: item.animeNameEn ?: link
            val url = "$mainUrl/api/anime/link/$link"

            newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = item.animePhoto
            }
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val searchUrl = "$mainUrl/api/anime/search?q=${query.trim()}"
            val animes = app.get(searchUrl).parsedSafe<List<AnimeItem>>() ?: emptyList()
            val results = animes.mapNotNull { item ->
                val link = item.animeLink ?: return@mapNotNull null
                val title = item.animeName ?: item.animeNameEn ?: link
                val url = "$mainUrl/api/anime/link/$link"

                newAnimeSearchResponse(title, url, TvType.Anime) {
                    this.posterUrl = item.animePhoto
                }
            }
            if (results.isNotEmpty()) results else getMainPage(1, MainPageRequest(name, "$mainUrl/api/anime", false)).items.flatMap { it.list }
        } catch (e: Exception) {
            getMainPage(1, MainPageRequest(name, "$mainUrl/api/anime", false)).items.flatMap { it.list }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val apiUrl = if (url.startsWith("http")) url else "$mainUrl/api/anime/link/$url"
        val detail = app.get(apiUrl).parsedSafe<AnimeDetail>() ?: return null

        val title = detail.animeName ?: name
        val poster = detail.animePhoto
        val plot = detail.animeDescription
        val year = detail.animeYear?.toIntOrNull()

        val episodeList = mutableListOf<Episode>()
        detail.episodes?.forEach { ep ->
            val epNum = ep.episodeNumber ?: return@forEach
            val season = ep.animeSeason ?: 1

            val linksJson = JSONObject().apply {
                ep.link1080?.let { put("1080", it) }
                ep.link720?.let { put("720", it) }
                ep.link480?.let { put("480", it) }
                ep.link360?.let { put("360", it) }
            }.toString()

            episodeList.add(newEpisode(linksJson) {
                this.name = "$season. Sezon $epNum. Bölüm"
                this.season = season
                this.episode = epNum
            })
        }

        if (episodeList.isEmpty()) {
            episodeList.add(newEpisode("{}") {
                this.name = "1. Bölüm"
                this.season = 1
                this.episode = 1
            })
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            addEpisodes(DubStatus.Subbed, episodeList)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val json = JSONObject(data)
            val qualities = listOf("1080", "720", "480", "360")
            for (q in qualities) {
                val link = json.optString(q, "")
                if (link.isNotEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            name,
                            "$name $q" + "p",
                            link,
                            ExtractorLinkType.VIDEO
                        ) {
                            headers = mapOf("Referer" to "$mainUrl/")
                        }
                    )
                }
            }
        } catch (e: Exception) {}
        return true
    }
}
