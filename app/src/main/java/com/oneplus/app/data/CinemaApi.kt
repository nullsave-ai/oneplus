package com.oneplus.app.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class MovieDetailData(
    val synopsis: String = "",
    val director: String = "",
    val cast: List<String> = emptyList(),
    val backdrop: String = "",
    val episodes: List<Episode> = emptyList()
)

object CinemaApi {
    private const val BASE = "https://h5.aoneroom.com/wefeed-h5-bff/mini"
    private const val WECIMA_BASE = "https://wecima.bar/api.php"
    private const val UA = "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private val BlockedWords = setOf(
        "porn", "xxx", "erotic", "sex", "adult", "hentai", "nude", "sensual", "naked", "blowjob", "hardcore"
    )

    private fun isFamilyFriendly(title: String, genre: String, desc: String): Boolean {
        val full = "$title $genre $desc".lowercase()
        return BlockedWords.none { it in full }
    }

    fun fetchList(kind: Kind, page: Int, limit: Int = 20): List<Movie> {
        val safePerPage = limit.coerceIn(1, 24)
        return when (kind) {
            Kind.ArabicMovies -> {
                fetchWeCimaPosts("افلام عربي", page, safePerPage, kind)
            }
            Kind.ArabicSeries -> {
                if (page == 1) {
                    val ramadan = fetchWeCimaPosts("مسلسلات رمضان 2026", 1, 10, kind)
                    val series = fetchWeCimaPosts("مسلسلات عربي", 1, 10, kind)
                    (ramadan + series).distinctBy { it.id }
                } else {
                    fetchWeCimaPosts("مسلسلات عربي", page, safePerPage, kind)
                }
            }
            Kind.Trending -> {
                fetchTrending(page, safePerPage)
            }
            Kind.Top100 -> {
                fetchUpstream("1", "&sort=score", page, safePerPage, kind)
            }
            Kind.NewUpdates -> {
                fetchUpstream("1", "&sort=new", page, safePerPage, kind)
            }
            Kind.WesternSeries -> {
                fetchUpstream("2", "&sort=hot", page, safePerPage, kind)
            }
            Kind.KidsAnimation -> {
                fetchUpstream("1", "&genre=animation", page, safePerPage, kind)
            }
            Kind.Film -> {
                fetchUpstream("1", "", page, safePerPage, kind)
            }
            Kind.Series -> {
                fetchUpstream("2", "", page, safePerPage, kind)
            }
            Kind.Anime -> {
                fetchUpstream("1", "&genre=Anime", page, safePerPage, kind)
            }
        }
    }

    private fun fetchTrending(page: Int, limit: Int): List<Movie> {
        val postParams = "page=$page&perPage=$limit"
        val jsonStr = post("$BASE/trending", postParams) ?: return emptyList()
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val items = j.optJSONObject("data")?.optJSONArray("items").objs()
        return items.mapNotNull { mapUpstreamItem(it, Kind.Trending) }
    }

    private fun fetchUpstream(channelId: String, extraParams: String, page: Int, limit: Int, kind: Kind): List<Movie> {
        val postParams = "channelId=$channelId&subjectType=$channelId&page=$page&perPage=$limit$extraParams"
        val jsonStr = post("$BASE/subject-list", postParams) ?: return emptyList()
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val items = j.optJSONObject("data")?.optJSONArray("items").objs()
        return items.mapNotNull { mapUpstreamItem(it, kind) }
    }

    private fun fetchWeCimaPosts(category: String, page: Int, perPage: Int, kind: Kind): List<Movie> {
        val encodedCat = runCatching { URLEncoder.encode(category, "UTF-8") }.getOrDefault(category)
        val url = "$WECIMA_BASE?action=posts&category=$encodedCat&page=$page&per_page=$perPage"
        val jsonStr = getWeCima(url) ?: return emptyList()
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val posts = j.optJSONArray("posts").objs()
        return posts.mapNotNull { mapWeCimaItem(it, kind) }
    }

    fun search(keyword: String): List<Movie> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()

        val isArabic = q.any { it in '\u0600'..'\u06FF' }

        val upstreamResults = runCatching {
            val jsonStr = get("$BASE/search?keyword=" + Uri.encode(q))
            val j = jsonStr?.let { JSONObject(it) }
            val items = j?.optJSONObject("data")?.optJSONArray("items").objs()
            items.mapNotNull { item ->
                val subType = item.optInt("subjectType", 1)
                val kind = if (subType == 2) Kind.Series else Kind.Film
                mapUpstreamItem(item, kind)
            }
        }.getOrDefault(emptyList())

        val weCimaResults = runCatching {
            val encQ = URLEncoder.encode(q, "UTF-8")
            val jsonStr = getWeCima("$WECIMA_BASE?action=posts&search=$encQ&page=1&per_page=20")
            val j = jsonStr?.let { JSONObject(it) }
            val posts = j?.optJSONArray("posts").objs()
            posts.mapNotNull { item ->
                val isSeries = !item.optString("series_slug").isNullOrBlank() || !item.optString("season_slug").isNullOrBlank()
                mapWeCimaItem(item, if (isSeries) Kind.ArabicSeries else Kind.ArabicMovies)
            }
        }.getOrDefault(emptyList())

        return if (isArabic) {
            (weCimaResults + upstreamResults).distinctBy { it.title }
        } else {
            (upstreamResults + weCimaResults).distinctBy { it.title }
        }
    }

    fun fetchDetails(urlOrId: String, isSeriesHint: Boolean = false): MovieDetailData? {
        val clean = urlOrId.trim()
        if (clean.isBlank()) return null

        val isWeCima = clean.startsWith("wecima://") || clean.contains("wecima.bar") || clean.contains("realid=")

        return if (isWeCima) {
            fetchWeCimaDetails(clean, isSeriesHint)
        } else {
            val sid = if (clean.startsWith("cinema://")) {
                Uri.parse(clean).getQueryParameter("id") ?: clean.substringAfter("id=").substringBefore("&")
            } else clean
            fetchUpstreamDetails(sid, isSeriesHint)
        }
    }

    private fun fetchWeCimaDetails(urlStr: String, isSeriesHint: Boolean): MovieDetailData? {
        val u = runCatching { Uri.parse(urlStr) }.getOrNull()
        val id = u?.getQueryParameter("id") ?: urlStr.substringAfter("id=").substringBefore("&")
        val realId = u?.getQueryParameter("realid")?.takeIf { it.isNotBlank() } ?: id
        var seriesSlug = u?.getQueryParameter("series_slug").orEmpty()
        var seasonSlug = u?.getQueryParameter("season_slug").orEmpty()

        val postJsonStr = getWeCima("$WECIMA_BASE?action=posts&id=$id")
        val postJ = runCatching { postJsonStr?.let { JSONObject(it) } }.getOrNull()
        val posts = postJ?.optJSONArray("posts")
        val d = if (posts != null && posts.length() > 0) posts.getJSONObject(0) else postJ?.optJSONObject("data") ?: postJ

        val synopsis = d?.optJSONObject("meta")?.optString("story").orEmpty().trim()
        val thumb = d?.optString("thumbnail").orEmpty().ifBlank { d?.optString("poster").orEmpty() }

        if (seriesSlug.isBlank() && d != null) {
            seriesSlug = d.optString("series_slug").ifBlank { d.optJSONObject("series_info")?.optString("slug").orEmpty() }
        }
        if (seasonSlug.isBlank() && d != null) {
            seasonSlug = d.optString("season_slug").ifBlank { d.optJSONObject("season_info")?.optString("slug").orEmpty() }
        }

        val directors = mutableListOf<String>()
        val cast = mutableListOf<String>()
        val terms = d?.optJSONObject("terms")
        terms?.optJSONArray("director")?.objs()?.forEach {
            val n = it.optString("name").trim()
            if (n.isNotBlank()) directors.add(n)
        }
        terms?.optJSONArray("actor")?.objs()?.forEach {
            val n = it.optString("name").trim()
            if (n.isNotBlank()) cast.add(n)
        }

        val isSeries = isSeriesHint || seriesSlug.isNotBlank() || seasonSlug.isNotBlank()
        val episodes = mutableListOf<Episode>()

        if (isSeries) {
            var targetSeason = seasonSlug
            if (targetSeason.isBlank() && seriesSlug.isNotBlank()) {
                val sJsonStr = getWeCima("$WECIMA_BASE?action=seasons&series_slug=" + URLEncoder.encode(seriesSlug, "UTF-8"))
                val sJ = runCatching { sJsonStr?.let { if (it.startsWith("[")) JSONArray(it) else JSONObject(it).optJSONArray("data") } }.getOrNull()
                val firstSeason = sJ?.objs()?.firstOrNull()
                targetSeason = firstSeason?.optString("slug").orEmpty()
            }

            if (targetSeason.isNotBlank()) {
                val epJsonStr = getWeCima("$WECIMA_BASE?action=posts&season_slug=" + URLEncoder.encode(targetSeason, "UTF-8") + "&page=1&per_page=100")
                val epJ = runCatching { epJsonStr?.let { JSONObject(it) } }.getOrNull()
                val epPosts = epJ?.optJSONArray("posts").objs()
                for ((idx, ep) in epPosts.withIndex()) {
                    val epId = ep.optString("id")
                    val epReal = ep.optString("realid", epId).ifBlank { epId }
                    val epTitle = ep.optString("title", "الحلقة ${idx + 1}")
                    val matchNum = Regex("""(?:الحلقة|حلقة|ep|episode)\s*(\d+)""", RegexOption.IGNORE_CASE).find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                    val num = matchNum ?: ep.optJSONObject("meta")?.optString("number")?.filter { it.isDigit() }?.toIntOrNull() ?: (idx + 1)
                    val title = "الحلقة $num"
                    val epUrl = "wecima://stream?realid=$epReal&id=$epId"
                    episodes.add(Episode(title, epUrl, 1))
                }
                episodes.sortBy { it.title.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 1 }
            } else if (realId.isNotBlank()) {
                episodes.add(Episode("الحلقة 1", "wecima://stream?realid=$realId&id=$id", 1))
            }
        }

        return MovieDetailData(
            synopsis = synopsis.ifBlank { "مشاهدة وتحميل بجودة عالية." },
            director = if (directors.isNotEmpty()) directors.joinToString(", ") else "إخراج عربي",
            cast = cast.distinct().take(10),
            backdrop = thumb,
            episodes = episodes
        )
    }

    private fun fetchUpstreamDetails(subjectId: String, isSeriesHint: Boolean): MovieDetailData? {
        val detailStr = get("$BASE/subject_detail?subjectId=" + Uri.encode(subjectId)) ?: return null
        val j = runCatching { JSONObject(detailStr) }.getOrNull() ?: return null
        val d = j.optJSONObject("data") ?: return null

        val subType = d.optInt("subjectType", 1)
        val isSeries = isSeriesHint || subType == 2

        val synopsis = d.optString("description")
            .ifBlank { d.optString("postTitle") }
            .trim()

        val staff = d.optJSONArray("staffList").objs()
        val directors = mutableListOf<String>()
        val cast = mutableListOf<String>()

        for (st in staff) {
            val name = st.optString("name").trim()
            if (name.isBlank()) continue
            when (st.optInt("staffType", 0)) {
                2 -> directors.add(name)
                1 -> cast.add(name)
                3 -> if (directors.isEmpty()) directors.add(name)
            }
        }

        val coverUrl = d.optJSONObject("cover")?.optString("url").orEmpty()
        val stillObj = d.optJSONObject("stills")
        val stillArr = d.optJSONArray("stills")
        val stillsUrl = stillObj?.optString("url")
            ?: stillArr?.optJSONObject(0)?.optString("url")
            ?: ""
        val backdrop = stillsUrl.ifBlank { coverUrl }

        val episodes = mutableListOf<Episode>()
        if (isSeries) {
            val epStr = get("$BASE/subject-resource?subjectId=" + Uri.encode(subjectId) + "&page=1&perPage=100")
            if (!epStr.isNullOrBlank()) {
                val epJ = runCatching { JSONObject(epStr) }.getOrNull()
                val list = epJ?.optJSONObject("data")?.optJSONArray("list").objs()
                for (ep in list) {
                    val se = maxOf(1, ep.optInt("se", 1))
                    val epNum = maxOf(1, ep.optInt("ep", 1))
                    val rawTitle = ep.optString("title").trim()
                    val epTitle = if (rawTitle.isNotBlank() && !rawTitle.equals("null", true)) {
                        if (rawTitle.contains("حلقة", ignoreCase = true) || rawTitle.contains("episode", ignoreCase = true)) rawTitle
                        else "الموسم $se - الحلقة $epNum: $rawTitle"
                    } else {
                        "الموسم $se - الحلقة $epNum"
                    }
                    val epUrl = "cinema://stream?id=$subjectId&se=$se&ep=$epNum"
                    episodes.add(Episode(epTitle, epUrl, se))
                }
            }
        }

        return MovieDetailData(
            synopsis = synopsis,
            director = if (directors.isNotEmpty()) directors.joinToString(", ") else "إخراج سينمائي",
            cast = cast.distinct().take(10),
            backdrop = backdrop,
            episodes = episodes
        )
    }

    private fun mapUpstreamItem(item: JSONObject, kind: Kind): Movie? {
        val sid = item.optString("subjectId").trim()
        if (sid.isBlank()) return null
        val title = item.optString("title").trim()
        if (title.isBlank()) return null
        val desc = item.optString("description").trim()
        val genre = item.optString("genre").trim()
        if (!isFamilyFriendly(title, genre, desc)) return null

        val releaseDate = item.optString("releaseDate")
        val year = releaseDate.take(4).toIntOrNull() ?: 2024
        val durSec = item.optInt("durationSeconds", 0)
        val durationMin = if (durSec > 0) durSec / 60 else 115

        val rate = item.optDouble("imdbRatingValue", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: item.optDouble("imdbRate", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: item.optDouble("score", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: 7.6f

        val genres = genre.split(",", "/", "-")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("سينما") }

        val cover = item.optJSONObject("cover")?.optString("url").orEmpty()
        val id = ((sid + ":" + kind.name).hashCode() and 0x7FFFFFFF)

        return Movie(
            id = id,
            title = title,
            year = year,
            rating = rate,
            durationMin = durationMin,
            genres = genres,
            synopsis = desc,
            director = "إخراج سينمائي",
            cast = emptyList(),
            url = "cinema://detail?id=$sid",
            backdrop = cover,
            kind = kind
        )
    }

    private fun mapWeCimaItem(item: JSONObject, kind: Kind): Movie? {
        val id = item.optString("id").trim()
        if (id.isBlank()) return null
        val realId = item.optString("realid", id).trim().ifBlank { id }
        var title = item.optString("title").trim()
        if (title.isBlank()) return null
        val thumb = item.optString("thumbnail").trim().ifBlank { item.optString("poster").trim() }
        val meta = item.optJSONObject("meta")
        val story = meta?.optString("story").orEmpty().trim()
        val terms = item.optJSONObject("terms")

        val yearFromTitle = Regex("""\b(19\d\d|20\d\d)\b""").find(title)?.value?.toIntOrNull()
        var year = yearFromTitle ?: 0
        if (year <= 1900 && terms != null) {
            val yArr = terms.optJSONArray("release-year")
            if (yArr != null && yArr.length() > 0) {
                year = yArr.getJSONObject(0).optString("name").filter { it.isDigit() }.toIntOrNull() ?: 2025
            }
        }
        if (year <= 1900) year = 2025

        val seriesSlug = item.optString("series_slug").ifBlank { item.optJSONObject("series_info")?.optString("slug").orEmpty() }
        val seasonSlug = item.optString("season_slug").ifBlank { item.optJSONObject("season_info")?.optString("slug").orEmpty() }
        val isSeries = kind == Kind.ArabicSeries || seriesSlug.isNotBlank() || seasonSlug.isNotBlank()

        if (isSeries) {
            title = title.replace(Regex("""\s*الحلقة\s*\d+.*$"""), "").trim()
        }

        val genresList = mutableListOf<String>()
        terms?.optJSONArray("genre")?.objs()?.forEach {
            val n = it.optString("name").trim()
            if (n.isNotBlank()) genresList.add(n)
        }
        val genres = if (genresList.isNotEmpty()) genresList else listOf(if (isSeries) "مسلسلات عربية" else "سينما عربية")

        val actors = mutableListOf<String>()
        terms?.optJSONArray("actor")?.objs()?.forEach {
            val n = it.optString("name").trim()
            if (n.isNotBlank()) actors.add(n)
        }

        val directors = mutableListOf<String>()
        terms?.optJSONArray("director")?.objs()?.forEach {
            val n = it.optString("name").trim()
            if (n.isNotBlank()) directors.add(n)
        }
        val director = if (directors.isNotEmpty()) directors.joinToString(", ") else "إخراج عربي"

        val movieUrl = if (isSeries) {
            "wecima://detail?id=$id&realid=$realId&series_slug=" + Uri.encode(seriesSlug) + "&season_slug=" + Uri.encode(seasonSlug)
        } else {
            "wecima://stream?id=$id&realid=$realId"
        }

        val intId = ((realId + ":" + kind.name).hashCode() and 0x7FFFFFFF)
        return Movie(
            id = intId,
            title = title,
            year = year,
            rating = 8.0f,
            durationMin = if (isSeries) 45 else 115,
            genres = genres,
            synopsis = story.ifBlank { "مشاهدة وتحميل $title بجودة عالية." },
            director = director,
            cast = actors.distinct().take(10),
            url = movieUrl,
            backdrop = thumb,
            kind = kind
        )
    }

    private fun get(urlStr: String): String? {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("x-tr-devtype", "h5")
            c.setRequestProperty("x-tr-region", "CN")
            c.setRequestProperty("x-md-global-color", "lane4")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun getWeCima(urlStr: String): String? {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 12_000
            c.setRequestProperty("User-Agent", "okhttp/4.12.0")
            c.setRequestProperty("X-App-Key", "sv_x9k2m7p4q8n3r6t1w5y0z")
            c.setRequestProperty("X-App-Sig", "0de08ec6cd0526dcc699041ed8e6dfb3")
            c.setRequestProperty("X-App-Chk", "cebe2159")
            c.setRequestProperty("X-Device-ID", "3b29c9ef4e872d8a")
            c.setRequestProperty("X-Device-Name", "Samsung SM-S901B")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun post(urlStr: String, body: String): String? {
        val c = URL(urlStr).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000
            c.readTimeout = 10_000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("x-tr-devtype", "h5")
            c.setRequestProperty("x-tr-region", "CN")
            c.setRequestProperty("x-md-global-color", "lane4")
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        } finally {
            c.disconnect()
        }
    }

    private fun JSONArray?.objs(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
