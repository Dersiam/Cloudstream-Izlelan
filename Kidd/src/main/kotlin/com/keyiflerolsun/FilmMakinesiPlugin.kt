package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KiddPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kidd())
        registerExtractorAPI(CloseLoad())
        registerExtractorAPI(RapidExtractor())
    }
}