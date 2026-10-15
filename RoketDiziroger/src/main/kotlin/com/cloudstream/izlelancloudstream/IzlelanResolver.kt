package com.cloudstream.izlelancloudstream

import com.lagradost.cloudstream3.app
import com.fasterxml.jackson.annotation.JsonProperty

data class IzlelanDomainsResponse(
    @JsonProperty("ok") val ok: Boolean? = null,
    @JsonProperty("data") val data: IzlelanData? = null
)

data class IzlelanData(
    @JsonProperty("domains") val domains: Map<String, String>? = null
)

object IzlelanResolver {
    private const val CDN_URL = "https://cdn.izlelan.com/domains.json"
    private var cachedDomains: MutableMap<String, String> = mutableMapOf()
    private var lastFetchTime: Long = 0
    private const val CACHE_DURATION_MS = 60 * 60 * 1000 // 1 hour

    // Hardcoded fallback domains decoded from izlelan.com
    private val fallbackDomains = mapOf(
        "imu" to "https://ha.vixolity.com",
        "joyboy" to "https://www.hdfilmizle.best",
        "enel" to "https://selcukflix.app",
        "crocodile" to "https://dizilla.now",
        "xebec" to "https://www.fullhdfilmizlesene.now",
        "kidd" to "https://filmmakinesi.to",
        "shiki" to "https://www.hdfilmcehennemi2.biz",
        "garp" to "https://siyahfilmizle.vip",
        "ryuma" to "https://lovefilmizle.net",
        "rouge" to "https://www.dizidad.com",
        "kalgara" to "https://dizibal.org",
        "sakazuki" to "https://dizipal2140.com",
        "shanks" to "https://filmekseni.vip",
        "kizaru" to "https://www.hdfilmcehennemi.nl",
        "smoker" to "https://dizipal1590.com",
        "zunesha" to "https://dizipal10.com.tr",
        "oden" to "https://yabanci-dizi.now",
        "fujitora" to "https://animecix.tv",
        "lili" to "https://openani.me",
        "sabo" to "https://api.anizium.co",
        "shamrock" to "https://animezer.com",
        "beckman" to "https://api.sonanime.com",
        "bogard" to "https://streamcorecdn.com",
        "sengoku" to "https://diziizle.wiki",
        "katakuri" to "https://dizipod.com",
        "mihawk" to "https://webdramaturkey2.com",
        "vegapunk" to "https://ydfvfdizipanel.ru",
        "clover" to "https://japierdolevid.com",
        "noland" to "https://dizi79.life",
        "doflamingo" to "https://asyawatch.com",
        "dragon" to "https://dizisol.com",
        "teach" to "https://ddizibox.com",
        "roger" to "https://roketdizi.life",
        "kuzan" to "https://filmizlehub.live",
        "garling" to "https://liderfilmizle.vip",
        "rayleigh" to "https://vixsrc.to",
        "hiriluk" to "https://www.showbox.media",
        "urouge" to "https://lmscript.xyz",
        "gaban" to "https://streamdata.vaplayer.ru",
        "ace" to "https://arc018.stream",
        "gorosei" to "https://api.wecollege.net",
        "toki" to "https://diziroom.com",
        "jinbe" to "https://nxsha.space"
    )

    suspend fun getDomain(providerId: String): String {
        val now = System.currentTimeMillis()
        if (cachedDomains.isEmpty() || (now - lastFetchTime) > CACHE_DURATION_MS) {
            try {
                val res = app.get(CDN_URL).parsedSafe<IzlelanDomainsResponse>()
                val fetched = res?.data?.domains
                if (!fetched.isNullOrEmpty()) {
                    cachedDomains.clear()
                    cachedDomains.putAll(fetched)
                    lastFetchTime = now
                }
            } catch (e: Exception) {
                // Ignore network errors and keep fallback
            }
        }
        return cachedDomains[providerId] ?: fallbackDomains[providerId] ?: "https://$providerId.com"
    }
}
