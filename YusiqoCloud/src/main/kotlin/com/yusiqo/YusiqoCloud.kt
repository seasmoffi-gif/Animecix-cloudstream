// ! Custom plugin for Cloudstream3
// ! Written for your Node.js API

package com.yusiqo

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class YusiqoCloud : MainAPI() {
    override var mainUrl              = "http://localhost:3000"  // change to your deployed URL
    override var name                 = "MyAnimeAPI"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/titles"   to "Anime List",
        "${mainUrl}/search?q=naruto" to "Sample Search"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return if (request.data.contains("/titles")) {
            val response = app.get("${mainUrl}/titles").parsedSafe<List<Map<String, Any>>>() ?: emptyList()
            val home = response.map {
                val id = it["_id"].toString()
                val title = it["title_name"].toString()
                val poster = it["title_poster"]?.toString()

                newAnimeSearchResponse(title, "${mainUrl}/titles/$id", TvType.Anime) {
                    this.posterUrl = fixUrlNull(poster)
                }
            }
            newHomePageResponse(request.name, home)
        } else {
            newHomePageResponse(request.name, listOf())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("${mainUrl}/search?q=$query").parsedSafe<List<Map<String, Any>>>() ?: return listOf()

        return response.map {
            val id = it["_id"].toString()
            val title = it["title_name"].toString()
            val poster = it["title_poster"]?.toString()

            newAnimeSearchResponse(title, "${mainUrl}/titles/$id", TvType.Anime) {
                this.posterUrl = fixUrlNull(poster)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url).parsedSafe<Map<String, Any>>() ?: return null
        val title = response["title_name"].toString()
        val description = response["description"]?.toString()
        val poster = response["title_poster"]?.toString()
        val year = response["release_date"]?.toString()?.take(4)?.toIntOrNull()

        val episodesData = response["episodes"] as? List<Map<String, Any>> ?: emptyList()
        val episodes = episodesData.map {
            val epNum = it["episode_number"].toString().toIntOrNull() ?: 1
            val seasonNum = it["season_number"].toString().toIntOrNull() ?: 1
            val epName = "S$seasonNum E$epNum"

            newEpisode("$url/episodes/$epNum") {
                this.name = epName
                this.season = seasonNum
                this.episode = epNum
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = fixUrlNull(poster)
            this.year = year
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("MYAPI", "Loading links for $data")

        // Dummy implementation: replace with real API for video sources
        loadExtractor(url, "$mainUrl/", subtitleCallback, callback)
            
        return true
    }
}
