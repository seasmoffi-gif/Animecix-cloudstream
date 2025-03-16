// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class IzleAI : MainAPI() {
    override var mainUrl              = "https://selcukflix.com"
    override var name                 = "720PizleAI"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/film-izle"   to "Filmler",
        "${mainUrl}/dizi-izle" to "Diziler",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div.grid a.relative").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text() ?: return null
        val href      = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.relative.border img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            title ?: return null,
            "${mainUrl}/${slug}",
            TvType.Movie,
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mainReq  = app.get(mainUrl)
        val mainPage = mainReq.document
        val cKey     = mainPage.selectFirst("input[name='cKey']")?.attr("value") ?: return emptyList()
        val cValue   = mainPage.selectFirst("input[name='cValue']")?.attr("value") ?: return emptyList()

        val veriler   = mutableListOf<SearchResponse>()

        val searchReq = app.post(
            "${mainUrl}/bg/searchcontent",
            data = mapOf(
                "cKey"       to cKey,
                "cValue"     to cValue,
                "searchterm" to query
            ),
            headers = mapOf(
                "Accept"           to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            cookies = mapOf(
                "showAllDaFull"   to "true",
                "PHPSESSID"       to mainReq.cookies["PHPSESSID"].toString(),
            )
        ).parsedSafe<SearchResult>()

        if (searchReq?.data?.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }

        searchReq.data.result?.forEach { searchItem ->
            val title = searchItem.title ?: return@forEach
            if (title.endsWith("Serisi") || title.endsWith("Series")) {
                return@forEach
            }

            veriler.add(searchItem.toSearchResponse() ?: return@forEach)
        }

        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
    
       

    
       
        if (url.contains("film", ignoreCase = true)) {
            // Filmle ilgili özel işlemler
            val title = document.selectFirst("div.hidden h1")?.text() ?: return null
            val poster = fixUrlNull(document.selectFirst("div.hidden img")?.attr("src"))
            val year = document.selectFirst("div.justify-between div.space-x-2 span:nth-of-type(7)")?.text()?.toIntOrNull()
            val description = document.selectFirst("div.my-10 p")?.text()?.trim() ?: document.selectFirst("div.w-full div.text-base")?.text()?.trim()
            val tags = document.select("div.flex.flex-wrap a").map { it.text() }
            val rating = document.selectFirst("div.flex.items-center div.text-2xl")?.text()?.trim().toRatingInt()
            val duration = document.selectFirst("div.justify-between div.space-x-2 span:nth-of-type(3)")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
            //val trailer = document.selectFirst("iframe[data-src*='youtube.com/embed/']")?.attr("data-src")
            //val actors = document.selectFirst("div.list span.justify-center:nth-of-type(2)")?.nextSibling()?.toString()?.trim()?.split(", ")?.map { Actor(it) }
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                // addTrailer(trailer)
                //addActors(actors)
            }
        }
    
        if (url.contains("dizi", ignoreCase = true)) {
          
            val title = document.selectFirst("span.flex.items-start h2")?.text() ?: return null
            val poster = fixUrlNull(document.selectFirst("div.poster.hidden a.flex img")?.attr("src"))
            val year = document.selectFirst("div.w-fit span.opacity-60:nth-of-type(2)")?.text()?.toIntOrNull()
            val description = document.selectFirst("div.mv-det-p")?.text()?.trim() ?: document.selectFirst("div.w-full div.text-base")?.text()?.trim()
            val tags = document.select("span.block h3").map { it.text() }
            val rating = document.selectFirst("span.flex-col span:first-child")?.text()?.trim().toRatingInt()
            val duration = document.selectXpath("//span[contains(text(), ' dk.')]").text().trim().split(" ").first().toIntOrNull()
            val trailer = document.selectFirst("iframe[data-src*='youtube.com/embed/']")?.attr("data-src")
            val actors = document.select("div.flex.overflow-auto [href*='oyuncu']").map {
                Actor(it.selectFirst("span span")!!.text(), it.selectFirst("img")?.attr("data-srcset")?.split(" ")?.first())
            }
    
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                addTrailer(trailer)
                addActors(actors)
            }
        }
    
        
        return null
    }
    

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("IAI", "data » $data")
        val document = app.get(data).document
        val iframe   = fixUrlNull(document.selectFirst("div.player iframe")?.attr("src")) ?: return false
        Log.d("IAI", "iframe » $iframe")

        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)

        return true
    }
}
