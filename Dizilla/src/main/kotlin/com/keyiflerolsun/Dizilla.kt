// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Dizilla : MainAPI() {
    override var mainUrl              = "https://dizilla.nl"
    override var name                 = "Dizilla"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // Cloudflare Bypass
    override var sequentialMainPage   = true

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi-turu/aile"        to "Aile",
        "${mainUrl}/dizi-turu/aksiyon"     to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/romantik"    to "Romantik",
        "${mainUrl}/dizi-turu/komedi"      to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div.grid-cols-3 a, div.grid a").mapNotNull { it.diziler() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src")) ?: fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()

        val response = app.post(
            url = "$mainUrl/ajax/search",
            data = mapOf("term" to query),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<List<SearchData>>()

        response?.forEach { item ->
            searchResults.add(
                newTvSeriesSearchResponse(
                    item.title ?: return@forEach,
                    fixUrl("/dizi/${item.slug}"),
                    TvType.TvSeries
                ) { this.posterUrl = fixUrl(item.poster) }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h1.text-3xl")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.page-top img")?.attr("src"))
        val year       = document.selectXpath("//span[contains(text(), 'Yayın tarihi')]/following-sibling::span").text().trim().split(" ").last().toIntOrNull()
        val description = document.selectFirst("div.mv-det-p, div.w-full div.text-base")?.text()?.trim()
        val tags        = document.select("[href*='dizi-turu']").map { it.text() }
        val rating      = document.selectFirst("a[href*='imdb.com'] span")?.text()?.trim()?.toRatingInt()
        val duration    = document.select("div.gap-3 span.text-sm:contains(Dakika)").text().replace(Regex("[^0-9]"), "").toIntOrNull()
        val actors     = document.select("[href*='/oyuncu/']").map { Actor(it.text()) }

        val episodes = mutableListOf<Episode>()
        document.select("div.gap-2 a[href*='-sezon-']").forEach { seasonElement ->
            val seasonDoc = app.get(fixUrl(seasonElement.attr("href"))).document

            
            seasonDoc.select("div.episodes div.cursor-pointer").forEach { episode ->
                val epHref  = fixUrlNull(episode.selectFirst("a[href]")?.attr("href")) ?: return@forEach
                val epTitle = episode.selectFirst("a:last-child")?.text()?.trim() ?: "Bölüm ${episode.selectFirst("a.opacity-60")?.text()}"
                val epNo    = episode.selectFirst("a.opacity-60")?.text()?.toIntOrNull()
                val season  = seasonElement.attr("href").substringAfterLast("-sezon-").substringBefore("-").toIntOrNull()

                episodes.add(Eisode(epHref, epNo, season, epTitle))
            }

            
            seasonDoc.select("div.dub-episodes div.cursor-pointer").forEach { episode ->
                val epHref  = fixUrlNull(episode.selectFirst("a[href]")?.attr("href")) ?: return@forEach
                val epTitle = (episode.selectFirst("a:last-child")?.text()?.trim() ?: "Bölüm ${episode.selectFirst("a.opacity-60")?.text()}") + " (Dublaj)"
                val epNo    = episode.selectFirst("a.opacity-60")?.text()?.toIntOrNull()
                val season  = seasonElement.attr("href").substringAfterLast("-sezon-").substringBefore("-").toIntOrNull()

                episodes.add(Episode(epHref, epNo, season, epTitle))
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.duration = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        
        document.select("a[href*='/player/']").apmap { playerLink ->
            val playerDoc = app.get(fixUrl(playerLink.attr("href"))).document
            val iframeUrl = fixUrlNull(playerDoc.selectFirst("iframe")?.attr("src")) ?: return@apmap

            Log.d("DZL", "Video Iframe: $iframeUrl")
            loadExtractor(iframeUrl, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    private data class SearchData(
        val title: String?,
        val slug: String?,
        val poster: String?
    )
}
