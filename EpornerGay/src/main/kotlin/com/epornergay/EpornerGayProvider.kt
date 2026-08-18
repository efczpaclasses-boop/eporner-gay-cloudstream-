package com.epornergay

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class EpornerGayProvider : MainAPI() {
    override var mainUrl = "https://www.eporner.com"
    override var name = "Eporner Gay"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "amateur|latest" to "Amateur",
        "all|latest" to "Latest",
        "all|most-popular" to "Most Popular",
        "all|top-rated" to "Top Rated",
        "all|longest" to "Longest",
    )

    private fun apiUrl(query: String, page: Int, order: String): String {
        val encoded = URLEncoder.encode(query.ifBlank { "all" }, "UTF-8")
        return "$mainUrl/api/v2/video/search/?query=$encoded&per_page=40&page=$page" +
            "&thumbsize=big&order=$order&gay=2&lq=1&format=json"
    }

    private suspend fun getVideos(query: String, page: Int, order: String): EpornerSearchResponse {
        return app.get(apiUrl(query, page, order), headers = mapOf("Accept" to "application/json"))
            .parsedSafe<EpornerSearchResponse>() ?: EpornerSearchResponse()
    }

    private fun EpornerVideo.toSearchResponse(): SearchResponse? {
        if (id.isBlank() || title.isBlank() || url.isBlank()) return null
        return newMovieSearchResponse(title, url, TvType.NSFW) {
            posterUrl = defaultThumb?.src
            quality = SearchQuality.HD
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val (query, order) = request.data.split("|", limit = 2).let {
            (it.getOrNull(0) ?: "all") to (it.getOrNull(1) ?: "latest")
        }
        val response = getVideos(query, page, order)
        return newHomePageResponse(
            HomePageList(request.name, response.videos.mapNotNull { it.toSearchResponse() }, true),
            hasNext = page < response.totalPages,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> =
        getVideos(query, 1, "latest").videos.mapNotNull { it.toSearchResponse() }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" - EPORNER")?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return null
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val duration = document.selectFirst("meta[itemprop=duration]")?.attr("content")
            ?.let(::isoDurationSeconds)

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            posterUrl = poster
            plot = description
            this.duration = duration
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val page = app.get(data).text
        val videoId = Regex("(?:hd-porn|embed)/([A-Za-z0-9]+)").find(data)?.groupValues?.get(1)
            ?: Regex("video-([A-Za-z0-9]+)").find(data)?.groupValues?.get(1)
            ?: return false
        val rawHash = Regex("""hash\s*[:=]\s*[\"']([0-9a-fA-F]{32})""")
            .find(page)?.groupValues?.get(1) ?: return false
        val hash = rawHash.chunked(8).joinToString("") { it.toLong(16).toString(36) }
        val xhr = "$mainUrl/xhr/video/$videoId?hash=$hash&device=generic" +
            "&domain=www.eporner.com&fallback=false"
        val json = mapper.readTree(app.get(xhr, referer = data).text)
        val sources = json.path("sources")
        var emitted = false

        sources.fields().forEach { (kind, formats) ->
            if (!formats.isObject) return@forEach
            formats.fields().forEach inner@{ (label, format) ->
                val streamUrl = format.path("src").asText("")
                if (!streamUrl.startsWith("http")) return@inner
                val quality = Regex("(\\d+)[pP]").find(label)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Qualities.Unknown.value
                val type = if (kind.equals("hls", true) || streamUrl.contains(".m3u8")) {
                    ExtractorLinkType.M3U8
                } else {
                    ExtractorLinkType.VIDEO
                }
                callback(newExtractorLink(name, "$name $label", streamUrl, type) {
                    referer = "$mainUrl/"
                    this.quality = quality
                })
                emitted = true
            }
        }
        return emitted
    }

    private fun isoDurationSeconds(value: String): Int? {
        val match = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(value) ?: return null
        val (hours, minutes, seconds) = match.destructured
        return (hours.toIntOrNull() ?: 0) * 3600 +
            (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    }
}
