package com.cloudstream.izlelan
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SaboPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Sabo())
    }
}
