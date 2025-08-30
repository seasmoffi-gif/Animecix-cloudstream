// ! Custom plugin for Cloudstream3
// ! Written for your Torrserve API

package com.yusiqo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class NotYusiqoPlugin : MainAPI() {
    override var mainUrl = "https://not.yusiqo.com"
    override var name = "Torrserve"
    override val hasMainPage = true
    override var lang = "tr"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private suspend fun fetchTorrents(): List<ApiTorrent> {
        val res = app.post(
            "$mainUrl/torrents",
            data = mapOf("action" to "list"),
            headers = mapOf("Content-Type" to "application/json")
        ).parsedSafe<List<ApiTorrent>>()
        return res ?: emptyList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = fetchTorrents()
        val items = list.map { it.toSearchResponse() }
        return newHomePageResponse("Torrents") {
            addAll(items)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchTorrents()
            .filter { it.title.contains(query, ignoreCase = true) }
            .map { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val linkData = parseJson<LinkData>(url)
        val torrent = fetchTorrents().find { it.hash == linkData.hash }
            ?: throw ErrorLoadingException("Torrent not found")

        return when {
            torrent.category == "movie" -> MovieLoadResponse(
                title = torrent.title,
                url = url,
                source = this.name,
                posterUrl = torrent.poster
            )
            else -> {
                val eps = torrent.toEpisodes()
                TvSeriesLoadResponse(
                    title = torrent.title,
                    url = url,
                    source = this.name,
                    episodes = eps,
                    posterUrl = torrent.poster
                )
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = parseJson<LinkData>(data)
        val torrent = fetchTorrents().find { it.hash == linkData.hash }
            ?: return false

        // Parçadan dosya yolunu alalım
        val files: List<TorrFile> = torrent.toFiles()
        for (file in files) {
            val encodedPath = URLEncoder.encode(file.path, "UTF-8")
            val streamUrl = "$mainUrl/stream/$encodedPath?link=${torrent.hash}&index=${file.id}&play"
            callback(
                ExtractorLink(
                    source = this.name,
                    name = file.path.substringAfterLast('/'),
                    url = streamUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        return true
    }

    // -------------------- Helper Data & Extensions --------------------

    data class ApiTorrent(
        val title: String,
        val category: String,
        val poster: String?,
        val data: String,
        val hash: String
    )

    data class TorrServer(val Files: List<TorrFile>)
    data class TorrFile(val id: Int, val path: String, val length: Long)
    data class LinkData(val hash: String)

    private fun ApiTorrent.toFiles(): List<TorrFile> {
        return try {
            parseJson<Map<String, TorrServer>>(this.data)["TorrServer"]?.Files ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun ApiTorrent.toEpisodes(): List<Episode> {
        return toFiles().map { file ->
            Episode(
                name = file.path.substringAfterLast('/'),
                url = LinkData(this.hash).toJson(),
                episode = file.id
            )
        }
    }

    private fun ApiTorrent.toSearchResponse(): SearchResponse {
        return if (category == "movie") {
            MovieSearchResponse(
                title = title,
                source = this@NotYusiqoPlugin.name,
                url = LinkData(hash).toJson(),
                type = TvType.Movie,
                poster = poster
            )
        } else {
            TvSeriesSearchResponse(
                title = title,
                source = this@NotYusiqoPlugin.name,
                url = LinkData(hash).toJson(),
                type = TvType.TvSeries,
                poster = poster,
                episodes = toEpisodes()
            )
        }
    }
}