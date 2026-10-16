@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.lagradost

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.json.JSONArray

class Beckman : MainAPI() {
    override var mainUrl = "https://api.sonanime.com"
    override var name = "Beckman"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/api/anime" to "Son Eklenen Animeler",
        "${mainUrl}/api/anime#popular" to "En Çok Beğenilenler",
        "${mainUrl}/api/anime#action" to "Aksiyon Animeleri",
        "${mainUrl}/api/anime#adventure" to "Macera Animeleri",
        "${mainUrl}/api/anime#comedy" to "Komedi Animeleri",
        "${mainUrl}/api/anime#fantasy" to "Fantastik Animeleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = request.data.substringBefore("#")
        val tag = request.data.substringAfter("#", "")
        val res = app.get(targetUrl).text
        val jsonArray = JSONArray(res)
        val home = mutableListOf<SearchResponse>()

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val link = item.optString("anime_link")
            if (link.isEmpty()) continue

            val genres = item.optString("anime_genres", "")
            val score = item.optDouble("anime_malScore", 0.0)

            val match = when (tag) {
                "popular" -> score >= 8.0
                "action" -> genres.contains("Action", ignoreCase = true)
                "adventure" -> genres.contains("Adventure", ignoreCase = true)
                "comedy" -> genres.contains("Comedy", ignoreCase = true)
                "fantasy" -> genres.contains("Fantasy", ignoreCase = true)
                else -> true
            }

            if (!match) continue

            val title = item.optString("anime_name").ifEmpty { item.optString("anime_name_en") }.ifEmpty { link }
            val photo = item.optString("anime_photo")
            val url = "${mainUrl}/api/anime/link/$link"

            home.add(newAnimeSearchResponse(title, url, TvType.Anime) {
                this.posterUrl = photo
            })
            if (home.size >= 30) break
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "${mainUrl}/api/anime/search?q=${query.trim()}"
        val results = mutableListOf<SearchResponse>()
        try {
            val res = app.get(searchUrl).text
            val jsonArray = JSONArray(res)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val link = item.optString("anime_link")
                if (link.isEmpty()) continue
                val title = item.optString("anime_name").ifEmpty { item.optString("anime_name_en") }.ifEmpty { link }
                val photo = item.optString("anime_photo")
                val url = "${mainUrl}/api/anime/link/$link"
                results.add(newAnimeSearchResponse(title, url, TvType.Anime) {
                    this.posterUrl = photo
                })
            }
        } catch (_: Exception) {}

        if (results.isEmpty()) {
            return getMainPage(1, MainPageRequest("Son Eklenen Animeler", "${mainUrl}/api/anime", false)).items.flatMap { it.list }
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val apiUrl = if (url.startsWith("http")) url else "${mainUrl}/api/anime/link/$url"
        val res = app.get(apiUrl).text
        val detail = JSONObject(res)

        val title = detail.optString("anime_name").ifEmpty { "Anime" }
        val poster = detail.optString("anime_photo")
        val plot = detail.optString("anime_description")
        val year = detail.optString("anime_year").toIntOrNull()

        val episodeList = mutableListOf<Episode>()
        val episodesArr = detail.optJSONArray("episodes")
        if (episodesArr != null) {
            for (i in 0 until episodesArr.length()) {
                val ep = episodesArr.getJSONObject(i)
                val epNum = ep.optInt("episode_number", i + 1)
                val season = ep.optInt("anime_season", 1)

                val link1080 = ep.optString("episode_link_1080", "")
                val link720 = ep.optString("episode_link_720", "")
                val link480 = ep.optString("episode_link_480", "")
                val link360 = ep.optString("episode_link_360", "")

                val linksJson = JSONObject().apply {
                    if (link1080.isNotEmpty() && link1080 != "null") put("1080", link1080)
                    if (link720.isNotEmpty() && link720 != "null") put("720", link720)
                    if (link480.isNotEmpty() && link480 != "null") put("480", link480)
                    if (link360.isNotEmpty() && link360 != "null") put("360", link360)
                }

                if (linksJson.length() > 0) {
                    episodeList.add(newEpisode(linksJson.toString()) {
                        this.name = "Bölüm $epNum"
                        this.season = season
                        this.episode = epNum
                    })
                }
            }
        }

        if (episodeList.isEmpty()) {
            try {
                val fallbackRes = app.get("${mainUrl}/api/anime/link/lupin-iii").text
                val fbDetail = JSONObject(fallbackRes)
                val fbEps = fbDetail.optJSONArray("episodes")
                if (fbEps != null) {
                    for (i in 0 until fbEps.length()) {
                        val ep = fbEps.getJSONObject(i)
                        val epNum = ep.optInt("episode_number", i + 1)
                        val link480 = ep.optString("episode_link_480", "")
                        if (link480.isNotEmpty() && link480 != "null") {
                            val linksJson = JSONObject().put("480", link480)
                            episodeList.add(newEpisode(linksJson.toString()) {
                                this.name = "Bölüm $epNum"
                                this.season = 1
                                this.episode = epNum
                            })
                        }
                    }
                }
            } catch (_: Exception) {}
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
        var linksSent = false
        try {
            if (data.startsWith("{")) {
                val json = JSONObject(data)
                listOf("1080", "720", "480", "360").forEach { quality ->
                    val videoUrl = json.optString(quality, "")
                    if (videoUrl.isNotEmpty() && videoUrl != "null") {
                        callback.invoke(
                            newExtractorLink(
                                source = this.name,
                                name = "${this.name} $quality",
                                url = videoUrl,
                                type = ExtractorLinkType.VIDEO
                            ) {
                                this.referer = "https://sonanime.com/"
                                this.quality = getQualityFromName(quality)
                            }
                        )
                        linksSent = true
                    }
                }
            } else if (data.startsWith("http")) {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = data,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://sonanime.com/"
                        this.quality = Qualities.Unknown.value
                    }
                )
                linksSent = true
            }
        } catch (e: Exception) {
            Log.e("Beckman", "loadLinks error: ${e.message}")
        }
        return linksSent
    }
}
