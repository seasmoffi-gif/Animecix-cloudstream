package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId

// --- Basit TTL cache ---
data class CacheItem<T>(val value: T, val timestamp: Long)

object CacheTTL {
    private val map = mutableMapOf<String, CacheItem<Any>>()
    private const val TTL = 1000L * 60 * 5 // 5 dakika

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        val item = map[key] as? CacheItem<T> ?: return null
        return if (System.currentTimeMillis() - item.timestamp < TTL) item.value else null
    }

    fun set(key: String, value: Any) {
        map[key] = CacheItem(value, System.currentTimeMillis())
    }
}

class AnimeciX : MainAPI() {
    override var mainUrl              = "https://animecix.tv"
    override var name                 = "AnimeciX"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 200L
    override var sequentialMainPageScrollDelay = 200L

    override val mainPage = mainPageOf(
        "${mainUrl}/secure/last-episodes"                          to "Son Eklenen Bölümler",
        "${mainUrl}/secure/titles?type=series&onlyStreamable=true" to "Seriler",
        "${mainUrl}/secure/titles?type=movie&onlyStreamable=true"  to "Filmler",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val cacheKey = "mainPage_${request.data}_$page"
        CacheTTL.get<HomePageResponse>(cacheKey)?.let { return it }

        val home = if (request.data.contains("/last-episodes")) {
            val response = app.get(
                "${mainUrl}/secure/last-episodes?page=$page&perPage=10",
                headers = mapOf(
                    "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
                )
            ).parsedSafe<LastEpisodesResponse>()?.data ?: emptyList()

            response.map {
                val formattedTitle = "S${it.seasonNumber}B${it.episodeNumber} - ${it.titleName}"
                newAnimeSearchResponse(
                    formattedTitle,
                    "${mainUrl}/secure/titles/${it.titleId}?titleId=${it.titleId}",
                    TvType.Anime
                ) { this.posterUrl = fixUrlNull(it.titlePoster) }
            }
        } else {
            val response = app.get(
                "${request.data}&page=${page}&perPage=16",
                headers = mapOf(
                    "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
                )
            ).parsedSafe<Category>()

            response?.pagination?.data?.map { anime ->
                newAnimeSearchResponse(
                    anime.title,
                    "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                    TvType.Anime
                ) { this.posterUrl = fixUrlNull(anime.poster) }
            } ?: listOf()
        }

        val homePageResponse = newHomePageResponse(request.name, home)
        CacheTTL.set(cacheKey, homePageResponse)
        return homePageResponse
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val cacheKey = "search_$query"
        CacheTTL.get<List<SearchResponse>>(cacheKey)?.let { return it }

        val response = app.get("${mainUrl}/secure/search/${query}?limit=20").parsedSafe<Search>() ?: return listOf()

        val result = response.results.map { anime ->
            newAnimeSearchResponse(
                anime.title,
                "${mainUrl}/secure/titles/${anime.id}?titleId=${anime.id}",
                TvType.Anime
            ) { this.posterUrl = fixUrlNull(anime.poster) }
        }

        CacheTTL.set(cacheKey, result)
        return result
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        CacheTTL.get<LoadResponse>(url)?.let { return it }

        val response = app.get(
            url,
            headers = mapOf(
                "x-e-h" to "7Y2ozlO+QysR5w9Q6Tupmtvl9jJp7ThFH8SB+Lo7NvZjgjqRSqOgcT2v4ISM9sP10LmnlYI8WQ==.xrlyOBFS5BHjQ2Lk"
            )
        ).parsedSafe<Title>() ?: return null

        val episodes = mutableListOf<Episode>()
        val titleId  = url.substringAfter("?titleId=")

        if (response.title.titleType == "anime") {
            for (sezon in response.title.seasons) {
                val sezonResponse = app.get("${mainUrl}/secure/related-videos?episode=1&season=${sezon.number}&videoId=0&titleId=${titleId}").parsedSafe<TitleVideos>() ?: return null
                for (video in sezonResponse.videos) {
                    episodes.add(newEpisode("${mainUrl}/secure/episode-videos-points?episode=${video.episodeNum}&season=${video.seasonNum}&titleId=${titleId}") {
                        this.name = "${video.seasonNum}. Sezon ${video.episodeNum}. Bölüm"
                        this.season = video.seasonNum
                        this.episode = video.episodeNum
                    })
                }
            }
        } else {
            if (response.title.videos.isNotEmpty()) {
                episodes.add(newEpisode(response.title.videos.first().url) {
                    this.name    = "Filmi İzle"
                    this.season  = 1
                    this.episode = 1
                })
            }
        }

        val loadResponse = newTvSeriesLoadResponse(
            response.title.title,
            "${mainUrl}/secure/titles/${response.title.id}?titleId=${response.title.id}",
            TvType.Anime,
            episodes
        ) {
            this.posterUrl = fixUrlNull(response.title.poster)
            this.year      = response.title.year
            this.plot      = response.title.description
            this.tags      = response.title.tags.map { it.name }
            this.rating    = response.title.rating.toRatingInt()
            addActors(response.title.actors.map { Actor(it.name, fixUrlNull(it.poster)) })
            addTrailer(response.title.trailer)
            addMalId(response.title.malid)
        }

        CacheTTL.set(url, loadResponse)
        return loadResponse
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cacheKey = "links_$data"
        CacheTTL.get<Boolean>(cacheKey)?.let { return it }

        Log.d("ACX", "data » $data")

        val response = app.get(
            data,
            referer = "$mainUrl/"
        )

        val root = response.parsedSafe<Map<String, Any?>>() ?: return false
        val videos = root["videos"] as? List<Map<String, Any?>> ?: emptyList()

        for (video in videos) {
            val url = video["url"] as? String ?: continue
            if (url.isNotBlank()) {
                try {
                    loadExtractor(url, "$mainUrl/", subtitleCallback, callback)
                } catch (e: Exception) {
                    Log.e("ACX", "Extractor yüklenemedi: ${e.message}")
                }
            }
        }

        CacheTTL.set(cacheKey, true)
        return true
    }
}
