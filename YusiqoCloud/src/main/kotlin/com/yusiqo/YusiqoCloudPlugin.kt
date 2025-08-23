package com.yusiqo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class YusiqoCloudPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(YusiqoCloud())
        registerExtractorAPI(TauVideo())
        registerExtractorAPI(SibNet())
    }
}
