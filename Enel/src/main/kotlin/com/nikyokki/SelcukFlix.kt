@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
package com.nikyokki

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.json.JSONObject
import org.json.JSONArray
import org.jsoup.Jsoup
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class Enel : MainAPI() {
    override var mainUrl = "https://selcukflix.app"
    override var name = "Enel"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Yeni Eklenen Filmler",
        "49" to "Aile Film",
        "44" to "Animasyon Film",
        "59" to "Aksiyon Film",
        "66" to "Bilim Kurgu Film",
        "48" to "Dram Film",
        "61" to "Fantastik Film",
        "68" to "Gerilim Film",
        "51" to "Gizem Film",
        "63" to "Korku Film",
        "45" to "Komedi Film",
        "65" to "Romantik Film",
        "46" to "Suç Film",
        "69" to "Savaş Film",
        "78" to "Western Film",
        "" to "Yeni Eklenen Diziler",
        "15" to "Aile Dizi",
        "17" to "Animasyon Dizi",
        "9" to "Aksiyon Dizi",
        "5" to "Bilim Kurgu Dizi",
        "2" to "Dram Dizi",
        "12" to "Fantastik Dizi",
        "18" to "Gerilim Dizi",
        "3" to "Gizem Dizi",
        "8" to "Korku Dizi",
        "4" to "Komedi Dizi",
        "7" to "Romantik Dizi",
        "1" to "Suç Dizi",
        "26" to "Savaş Dizi",
        "11" to "Western Dizi",
    )

    private fun decryptAes(b64: String): String {
        return try {
            val keySpec = SecretKeySpec("9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI".toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(ByteArray(16))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decoded = Base64.decode(b64, Base64.DEFAULT)
            String(cipher.doFinal(decoded), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("Enel", "AES decrypt error", e)
            ""
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val yil = Calendar.getInstance().get(Calendar.YEAR)
        var url = "$mainUrl/api/bg/findMovies?releaseYearStart=1900&releaseYearEnd=$yil&imdbPointMin=1&imdbPointMax=10&categoryIdsComma=${request.data}&countryIdsComma=&orderType=date_desc&languageId=-1&currentPage=${page}&currentPageCount=12&queryStr=&categorySlugsComma=&countryCodesComma="
        val isDizi = request.name.contains("Dizi")
        if (isDizi) {
            url = url.replace("findMovies", "findSeries")
        }

        val res = app.post(
            url,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            ),
            referer = "$mainUrl/"
        )

        val json = JSONObject(res.text)
        val encB64 = json.optString("response")
        val decJsonStr = decryptAes(encB64)
        val homeItems = mutableListOf<SearchResponse>()

        if (decJsonStr.isNotEmpty()) {
            val decObj = JSONObject(decJsonStr)
            val resultArr = decObj.optJSONArray("result")
            if (resultArr != null) {
                for (i in 0 until resultArr.length()) {
                    val item = resultArr.getJSONObject(i)
                    val title = item.optString("original_title").ifEmpty { item.optString("culture_title") }
                    val slug = item.optString("used_slug")
                    val poster = item.optString("poster_url").replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
                    if (title.isEmpty() || slug.isEmpty()) continue
                    val fullUrl = fixUrl(slug)

                    if (isDizi || slug.contains("dizi/")) {
                        homeItems.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                            this.posterUrl = poster
                        })
                    } else {
                        homeItems.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
        }

        return newHomePageResponse(request.name, homeItems)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.post(
            "$mainUrl/api/bg/searchcontent?searchterm=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Accept" to "application/json, text/plain, */*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$mainUrl/"
            ),
            referer = "$mainUrl/"
        )

        val json = JSONObject(res.text)
        val encB64 = json.optString("response")
        val decJsonStr = decryptAes(encB64)
        val searchItems = mutableListOf<SearchResponse>()

        if (decJsonStr.isNotEmpty()) {
            val decObj = JSONObject(decJsonStr)
            val resultArr = decObj.optJSONArray("result")
            if (resultArr != null) {
                for (i in 0 until resultArr.length()) {
                    val item = resultArr.getJSONObject(i)
                    val title = item.optString("object_name").ifEmpty { item.optString("object_alternative_name") }
                    val slug = item.optString("used_slug")
                    val poster = item.optString("poster_url").replace("images-macellan-online.cdn.ampproject.org/i/s/", "")
                    val usedType = item.optString("used_type")
                    if (title.isEmpty() || slug.isEmpty()) continue
                    val fullUrl = fixUrl(slug)

                    if (usedType == "Series" || slug.contains("dizi/")) {
                        searchItems.add(newTvSeriesSearchResponse(title, fullUrl, TvType.TvSeries) {
                            this.posterUrl = poster
                        })
                    } else {
                        searchItems.add(newMovieSearchResponse(title, fullUrl, TvType.Movie) {
                            this.posterUrl = poster
                        })
                    }
                }
            }
        }

        return searchItems
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.html() ?: ""
        if (script.isEmpty()) return null

        val nextData = JSONObject(script)
        val secB64 = nextData.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
        val decJsonStr = decryptAes(secB64)
        if (decJsonStr.isEmpty()) return null

        val root = JSONObject(decJsonStr)
        val contentItem = root.optJSONObject("contentItem") ?: JSONObject()
        val orgTitle = contentItem.optString("original_title")
        val cultTitle = contentItem.optString("culture_title")
        val title = if (orgTitle.isNotEmpty()) orgTitle else if (cultTitle.isNotEmpty()) cultTitle else name
        val poster = fixUrlNull(contentItem.optString("poster_url").replace("images-macellan-online.cdn.ampproject.org/i/s/", ""))
        val description = contentItem.optString("series_description_content").ifEmpty { contentItem.optString("description") }
        val year = contentItem.optInt("release_year", 0).let { if (it > 0) it else null }

        val rel = root.optJSONObject("RelatedResults")
        val isDizi = url.contains("/dizi/")

        if (isDizi && rel != null) {
            val episodes = mutableListOf<com.lagradost.cloudstream3.Episode>()
            val seasonsArr = rel.optJSONObject("getSerieSeasonAndEpisodes")?.optJSONArray("result")
            if (seasonsArr != null) {
                for (s in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.getJSONObject(s)
                    val sNum = sObj.optInt("season_no", s + 1)
                    val epArr = sObj.optJSONArray("episodes") ?: continue
                    for (e in 0 until epArr.length()) {
                        val epObj = epArr.getJSONObject(e)
                        val epNum = epObj.optInt("episode_no", e + 1)
                        val epSlug = epObj.optString("used_slug")
                        val epText = epObj.optString("episode_text").ifEmpty { "$sNum. Sezon $epNum. Bölüm" }
                        val epUrl = fixUrl(epSlug)

                        episodes.add(newEpisode(epUrl) {
                            this.name = epText
                            this.season = sNum
                            this.episode = epNum
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
                this.year = year
                this.plot = description
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val script = document.selectFirst("script#__NEXT_DATA__")?.html() ?: ""
        var extractedAny = false

        if (script.isNotEmpty()) {
            val nextData = JSONObject(script)
            val secB64 = nextData.optJSONObject("props")?.optJSONObject("pageProps")?.optString("secureData") ?: ""
            val decJsonStr = decryptAes(secB64)

            if (decJsonStr.isNotEmpty()) {
                val root = JSONObject(decJsonStr)
                val rel = root.optJSONObject("RelatedResults")
                val iframeRegex = Regex("""src=["']([^"']+)["']""")

                // Check episode sources
                val epSources = rel?.optJSONObject("getEpisodeSources")?.optJSONArray("result")
                if (epSources != null) {
                    for (i in 0 until epSources.length()) {
                        val srcContent = epSources.getJSONObject(i).optString("source_content")
                        val iframeUrl = iframeRegex.find(srcContent)?.groupValues?.get(1)?.let { fixUrlNull(it) }
                        if (iframeUrl != null) {
                            loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
                            extractedAny = true
                        }
                    }
                }

                // Check movie parts
                val movieParts = rel?.optJSONObject("getMoviePartsById")?.optJSONArray("result")
                if (movieParts != null) {
                    for (p in 0 until movieParts.length()) {
                        val pId = movieParts.getJSONObject(p).optInt("id")
                        val partSources = rel.optJSONObject("getMoviePartSourcesById_$pId")?.optJSONArray("result")
                        if (partSources != null) {
                            for (s in 0 until partSources.length()) {
                                val srcContent = partSources.getJSONObject(s).optString("source_content")
                                val iframeUrl = iframeRegex.find(srcContent)?.groupValues?.get(1)?.let { fixUrlNull(it) }
                                if (iframeUrl != null) {
                                    loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
                                    extractedAny = true
                                }
                            }
                        }
                    }
                }
            }
        }

        // Static fallback
        val staticIframes = document.select("iframe").mapNotNull { fixUrlNull(it.attr("src")) }
        for (iframe in staticIframes) {
            loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
            extractedAny = true
        }

        return extractedAny
    }
}