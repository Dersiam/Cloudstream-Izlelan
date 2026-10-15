package com.cloudstream.izlelancloudstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class GoroseigoroseiPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Goroseigorosei())
    }
}
