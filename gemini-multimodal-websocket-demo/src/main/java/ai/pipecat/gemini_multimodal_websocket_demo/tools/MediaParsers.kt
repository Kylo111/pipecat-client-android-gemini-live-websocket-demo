package ai.pipecat.gemini_multimodal_websocket_demo.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Common data model for any media type (Movie, Book, Music)
 */
data class MediaEntity(
    val title: String,
    val imageUrl: String?,
    val creator: String, // Director, Author, Artist
    val rating: String?, // 8.5/10
    val year: String?,
    val description: String? = null,
    val url: String
)

interface MediaParser {
    suspend fun parse(url: String): MediaEntity?
    fun isValidUrl(url: String): Boolean
}

/**
 * Parses Movies from IMDb (and potentially others in future)
 */
object MovieParser : MediaParser {
    
    override fun isValidUrl(url: String): Boolean {
        return url.contains("imdb.com/title/") || url.contains("themoviedb.org/movie/") || url.contains("filmweb.pl/film/")
    }

    override suspend fun parse(url: String): MediaEntity? = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(10000)
                .get()

            // Try Filmweb specific scraping if applicable
            if (url.contains("filmweb.pl")) {
                return@withContext parseFilmweb(doc, url)
            }
            
            // Try JSON-LD first (Standard)
            val jsonLd = extractJsonLd(doc)
            if (jsonLd != null) {
                return@withContext parseJsonLd(jsonLd, url)
            }
            
            // Fallback to OpenGraph meta tags
            parseOpenGraph(doc, url)
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractJsonLd(doc: Document): JSONObject? {
        val scriptElements = doc.select("script[type=application/ld+json]")
        for (element in scriptElements) {
            try {
                val jsonContent = element.data()
                val jsonTokener = org.json.JSONTokener(jsonContent)
                val root = jsonTokener.nextValue()

                if (root is JSONObject) {
                     if (isMovieType(root.optString("@type"))) return root
                }
            } catch (e: Exception) { }
        }
        return null
    }

    private fun isMovieType(type: String): Boolean {
        return type.contains("Movie", ignoreCase = true) || type.contains("TVSeries", ignoreCase = true)
    }

    private fun parseJsonLd(json: JSONObject, url: String): MediaEntity {
        val title = json.optString("name", "Unknown Title")
        val image = json.optString("image")
        
        // Handle Director (can be Person or list)
        var director = ""
        val directorObj = json.opt("director")
        if (directorObj is JSONObject) {
            director = directorObj.optString("name")
        } else if (directorObj is org.json.JSONArray && directorObj.length() > 0) {
            director = directorObj.getJSONObject(0).optString("name")
        }

        // Handle Rating
        var rating = ""
        val aggRating = json.optJSONObject("aggregateRating")
        if (aggRating != null) {
            val value = aggRating.optString("ratingValue")
            val best = aggRating.optString("bestRating", "10")
            if (value.isNotEmpty()) rating = "$value/$best"
        }

        val datePublished = json.optString("datePublished", "")
        val year = if (datePublished.length >= 4) datePublished.substring(0, 4) else ""
        val description = json.optString("description")

        return MediaEntity(title, image, director, rating, year, description, url)
    }

    private fun parseOpenGraph(doc: Document, url: String): MediaEntity {
        val title = doc.select("meta[property=og:title]").attr("content").replace(" - IMDb", "")
        val image = doc.select("meta[property=og:image]").attr("content")
        val description = doc.select("meta[property=og:description]").attr("content")
        
        // Try to extract Year from title "The Matrix (1999)"
        val yearRegex = "\\((\\d{4})\\)".toRegex()
        val yearMatch = yearRegex.find(title)
        val year = yearMatch?.groupValues?.get(1) ?: ""
        
        // Director is hard to get from meta only, simpler fallback
        return MediaEntity(
            title = title,
            imageUrl = image,
            creator = "Unknown",
            rating = null,
            year = year,
            description = description,
            url = url
        )
    }

    private fun parseFilmweb(doc: Document, url: String): MediaEntity {
        val title = doc.select("meta[property=og:title]").attr("content").split(" (")[0]
        val image = doc.select("meta[property=og:image]").attr("content")
        val rating = doc.select("span.filmRating__rateValue").text().trim()
        val description = doc.select("meta[property=og:description]").attr("content")
        
        // Better extraction for Director and Year
        val year = doc.select(".filmHeaderSection__year, .filmCoverSection__year").text().trim().replace("(", "").replace(")", "")
        val director = doc.select("a[itemprop=director], [data-type=director] a, .filmPosterSection__info--director a").first()?.text() ?: "Filmweb"
        
        return MediaEntity(
            title = title,
            imageUrl = image,
            creator = director,
            rating = if (rating.isNotEmpty()) "$rating/10" else null,
            year = if (year.isNotEmpty()) year else null,
            description = description,
            url = url
        )
    }
}
