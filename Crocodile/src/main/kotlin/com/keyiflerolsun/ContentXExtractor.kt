@file:Suppress("DEPRECATION", "DEPRECATION_ERROR", "UNCHECKED_CAST")
// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

open class ContentX : ExtractorApi() {
    override val name            = "ContentX"
    override val mainUrl         = "https://contentx.me"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val extRef = referer ?: ""
        val domain = if (url.contains("://")) {
            val scheme = url.substringBefore("://")
            val host = url.substringAfter("://").substringBefore("/")
            "$scheme://$host"
        } else {
            mainUrl
        }

        val iSource = try {
            app.get(url, referer = extRef, headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")).text
        } catch (_: Exception) { return }

        val iExtract = Regex("""(?:window\.)?openPlayer\('([^']+)'""").find(iSource)?.groups?.get(1)?.value ?: return

        val subUrls = mutableSetOf<String>()
        Regex(""""file":"([^"]+)","label":"([^"]+)"""").findAll(iSource).forEach {
            val (subUrl, subLang) = it.destructured
            if (subUrl in subUrls) return@forEach
            subUrls.add(subUrl)

            subtitleCallback.invoke(
                SubtitleFile(
                    lang = subLang.replace("\\u0131", "ı").replace("\\u0130", "İ").replace("\\u00fc", "ü").replace("\\u00e7", "ç"),
                    url = fixUrl(subUrl.replace("\\/", "/"))
                )
            )
        }

        try {
            val vidSource = app.get(
                "${domain}/source2.php?v=${iExtract}",
                referer = url,
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
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
        } catch (_: Exception) {}

        val iDublaj = Regex(""","([^']+)","Türkçe""").find(iSource)?.groups?.get(1)?.value
        if (iDublaj != null) {
            try {
                val dublajSource = app.get(
                    "${domain}/source2.php?v=${iDublaj}",
                    referer = url,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                    )
                ).text
                val dublajExtract = Regex("""file"\s*:\s*"([^"]+)""").find(dublajSource)?.groups?.get(1)?.value
                if (dublajExtract != null) {
                    val dublajLink = dublajExtract.replace("\\/", "/").replace("m.php", "master.m3u8")
                    callback.invoke(
                        newExtractorLink(
                            source = "${this.name} Türkçe Dublaj",
                            name = "${this.name} Türkçe Dublaj",
                            url = dublajLink,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "$domain/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                }
            } catch (_: Exception) {}
        }
    }
}