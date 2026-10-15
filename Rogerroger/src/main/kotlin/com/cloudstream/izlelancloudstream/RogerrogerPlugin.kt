package com.cloudstream.izlelancloudstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class RogerrogerPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Rogerroger())
    }
}
