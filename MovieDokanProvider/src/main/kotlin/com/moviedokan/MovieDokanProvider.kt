package com.moviedokan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document

class MovieDokanProvider : MainUrlPlugin() {
    override var mainUrl = "https://moviedokan.co"
    override var name = "MovieDokan"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "bn"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val homeItems = parseMoviesFromDoc(doc)
        return newHomePageResponse(request.name, homeItems)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val doc = app.get(searchUrl).document
        return parseMoviesFromDoc(doc)
    }

    private fun parseMoviesFromDoc(doc: Document): List<SearchResponse> {
        return doc.select("article, .post, .item, .movie-item").mapNotNull { element ->
            val titleElement = element.selectFirst(".title, .entry-title, h2, h3, a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            if (title.isEmpty()) return@mapNotNull null

            val linkElement = element.selectFirst("a") ?: return@mapNotNull null
            val href = linkElement.attr("abs:href")
            if (href.isEmpty()) return@mapNotNull null

            val imgElement = element.selectFirst("img")
            val poster = imgElement?.attr("abs:src") 
                ?: imgElement?.attr("data-src") 
                ?: imgElement?.attr("src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, .entry-title, .title")?.text()?.trim() ?: "MovieDokan Content"
        val poster = doc.selectFirst(".poster img, .featured-image img, article img")?.let {
            it.attr("abs:src").ifEmpty { it.attr("data-src") }
        }
        val description = doc.selectFirst(".entry-content p, .description, #description")?.text()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offset: Double,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframes = doc.select("iframe")

        for (iframe in iframes) {
            val src = iframe.attr("abs:src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty() && !src.startsWith("about:")) {
                loadExtractor(src, subtitleCallback, callback)
            }
        }
        return true
    }
}
