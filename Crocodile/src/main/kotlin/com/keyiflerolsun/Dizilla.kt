@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.nodes.Element
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Crocodile : MainAPI() {
    override var mainUrl = "https://dizilla.now"
    override var name = "Crocodile"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/yabanci-dizi-izle" to "Yabancı Diziler",
        "${mainUrl}/anime-izle" to "Animeler",
        "${mainUrl}/kdrama-izle" to "K-Drama",
        "${mainUrl}/arsiv" to "Arşiv & Keşfet"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) "${request.data}?page=${page}" else request.data
        val items = mutableListOf<SearchResponse>()
        try {
            val doc = app.get(pageUrl, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
            val script = doc.selectFirst("script#__NEXT_DATA__")?.html()
            if (script != null) {
                val json = JSONObject(script)
                val pageProps = json.optJSONObject("props")?.optJSONObject("pageProps")
                val encData = pageProps?.optString("initialSeries") ?: ""
                if (encData.isNotEmpty()) {
                    val decrypted = decryptAes(encData)
                    if (decrypted != null) {
                        val arr = JSONArray(decrypted)
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val title = item.optString("used_title").ifEmpty { item.optString("original_title") }
                            val slug = item.optString("used_slug").ifEmpty { item.optString("slug") }
                            if (title.isEmpty() || slug.isEmpty()) continue
                            val poster = fixUrlNull(item.optString("poster_url").ifEmpty { item.optString("face_url") })
                            val href = fixUrlNull("$mainUrl/$slug") ?: continue
                            items.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                                this.posterUrl = poster
                            })
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                val fallback = doc.select("a[href*='/dizi/'], a[href*='/film/']")
                    .filter { a ->
                        val href = a.attr("href")
                        !href.contains("/tur/") && !href.contains("/yil/") && !href.contains("/kategori/") && !href.contains("/sezon-")
                    }
                    .mapNotNull { it.toSearchResult() }
                    .distinctBy { it.url }
                items.addAll(fallback)
            }
        } catch (_: Exception) {}

        return newHomePageResponse(request.name, items.distinctBy { it.url })
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        val title = this.selectFirst("img")?.attr("alt")?.trim()
            ?: this.selectFirst("h2, h3, h4, span.text-white")?.text()?.trim()
            ?: this.attr("title").trim()
        if (title.isEmpty()) return null

        val posterUrl = fixUrlNull(this.selectFirst("img")?.let {
            it.attr("src").ifEmpty { it.attr("data-src") }
        })

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun decryptAes(cipherText: String): String? {
        return try {
            val sha = MessageDigest.getInstance("SHA-256")
            val keyHash = sha.digest("!!22xx!!90!!".toByteArray(Charsets.UTF_8))
            val keyBase64 = Base64.encodeToString(keyHash, Base64.NO_WRAP).substring(0, 32)
            val secretKey = SecretKeySpec(keyBase64.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decoded = Base64.decode(cipherText, Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("Crocodile", "decrypt error", e)
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()
        try {
            val res = app.post(
                "$mainUrl/api/bg/searchContent?searchterm=$query",
                data = mapOf(),
                headers = mapOf("Content-Type" to "application/json"),
                referer = "$mainUrl/"
            ).text

            val json = JSONObject(res)
            val cipher = json.optString("response")
            if (cipher.isNotEmpty()) {
                val decrypted = decryptAes(cipher)
                if (decrypted != null) {
                    val decObj = JSONObject(decrypted)
                    val arr = decObj.optJSONArray("result")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.getJSONObject(i)
                            val title = item.optString("object_name").ifEmpty { item.optString("object_alternative_name") }
                            val slug = item.optString("used_slug").ifEmpty { item.optString("slug") }
                            val poster = fixUrlNull(item.optString("object_poster_url").ifEmpty { item.optString("poster_url") })
                            val href = fixUrlNull("$mainUrl/$slug") ?: continue
                            val isSeries = item.optString("used_type") == "Series" || slug.startsWith("dizi/")

                            if (isSeries) {
                                results.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                                    this.posterUrl = poster
                                })
                            } else {
                                results.add(newMovieSearchResponse(title, href, TvType.Movie) {
                                    this.posterUrl = poster
                                })
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        if (results.isEmpty()) {
            try {
                val document = app.get("$mainUrl/diziler").document
                val fallback = document.select("a[href*='/dizi/'], a[href*='/film/']")
                    .filter { a ->
                        val href = a.attr("href")
                        !href.contains("/tur/") && !href.contains("/yil/") && !href.contains("/kategori/") && !href.contains("/sezon-")
                    }
                    .mapNotNull { it.toSearchResult() }
                    .distinctBy { it.url }
                    .take(10)
                results.addAll(fallback)
            } catch (e: Exception) {}
        }

        return results.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.html()
        val isTv = url.contains("/dizi/")

        if (script != null) {
            try {
                val json = JSONObject(script)
                val props = json.optJSONObject("props")?.optJSONObject("pageProps")
                val encSecure = props?.optString("secureData") ?: ""
                if (encSecure.isNotEmpty()) {
                    val decrypted = decryptAes(encSecure)
                    if (decrypted != null) {
                        val secObj = JSONObject(decrypted)
                        val contentItem = secObj.optJSONObject("contentItem")
                        val title = contentItem?.optString("used_title")?.ifEmpty { contentItem.optString("original_title") } ?: name
                        val poster = fixUrlNull(contentItem?.optString("poster_url")?.ifEmpty { contentItem.optString("face_url") })
                        val plot = contentItem?.optString("description")
                        val year = contentItem?.optInt("release_year")

                        val seasonEpisodesObj = secObj.optJSONObject("getSerieSeasonAndEpisodes")
                        val seasonsArr = seasonEpisodesObj?.optJSONArray("result")
                        val episodes = mutableListOf<Episode>()

                        if (seasonsArr != null) {
                            for (s in 0 until seasonsArr.length()) {
                                val sObj = seasonsArr.getJSONObject(s)
                                val sNum = sObj.optInt("season_no", s + 1)
                                val epArr = sObj.optJSONArray("episodes") ?: continue
                                for (e in 0 until epArr.length()) {
                                    val epObj = epArr.getJSONObject(e)
                                    val eNum = epObj.optInt("episode_no", e + 1)
                                    val epSlug = epObj.optString("used_slug")
                                    val epName = epObj.optString("episode_text").ifEmpty { "$sNum. Sezon $eNum. Bölüm" }
                                    val epUrl = fixUrlNull("$mainUrl/$epSlug") ?: "$url/sezon-$sNum/bolum-$eNum"

                                    episodes.add(newEpisode(epUrl) {
                                        this.name = epName
                                        this.season = sNum
                                        this.episode = eNum
                                    })
                                }
                            }
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
                }
            } catch (_: Exception) {}
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: name
        val poster = fixUrlNull(document.selectFirst("meta[property='og:image']")?.attr("content"))
            ?: fixUrlNull(document.selectFirst("div.poster img, img")?.attr("src"))
        val description = document.selectFirst("p.overview, p.description, div.summary")?.text()?.trim()

        if (isTv) {
            val episodes = mutableListOf<Episode>()
            val epElements = document.select("a[href*='/sezon-'][href*='/bolum-']")
            val epRegex = Regex("""sezon-(\d+)/bolum-(\d+)""")
            for (a in epElements) {
                val epHref = fixUrlNull(a.attr("href")) ?: continue
                val match = epRegex.find(epHref)
                val sNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val eNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1
                episodes.add(newEpisode(epHref) {
                    this.name = a.text().trim().ifEmpty { "$sNum. Sezon $eNum. Bölüm" }
                    this.season = sNum
                    this.episode = eNum
                })
            }
            if (episodes.isEmpty()) {
                episodes.add(newEpisode("$url/sezon-1/bolum-1") {
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
        try {
            val doc = app.get(data, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")).document
            val script = doc.selectFirst("script#__NEXT_DATA__")?.html()
            val iframes = mutableListOf<String>()

            if (script != null) {
                try {
                    val json = JSONObject(script)
                    val encSecure = json.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
                    if (encSecure.isNotEmpty()) {
                        val decrypted = decryptAes(encSecure)
                        if (decrypted != null) {
                            val secObj = JSONObject(decrypted)
                            val sourcesArr = secObj.optJSONObject("getEpisodeSources")?.optJSONArray("result")
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

            // Also check standard DOM iframes
            doc.select("iframe").mapNotNull { fixUrlNull(it.attr("src")) }.forEach { iframes.add(it) }

            for (iframe in iframes.distinct()) {
                try {
                    loadExtractor(iframe, data, subtitleCallback, callback)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        return true
    }
}
