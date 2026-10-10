package com.oneplus.app.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MovieDetailData(
    val synopsis: String = "",
    val director: String = "",
    val cast: List<String> = emptyList(),
    val backdrop: String = "",
    val episodes: List<Episode> = emptyList()
)

object CinemaApi {
    private const val BASE = "https://h5.aoneroom.com/wefeed-h5-bff/mini"
    private const val UA = "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    private val BlockedWords = setOf(
        "porn", "xxx", "erotic", "sex", "adult", "hentai", "nude", "sensual", "naked", "blowjob", "hardcore"
    )

    private fun isFamilyFriendly(title: String, genre: String, desc: String): Boolean {
        val full = "$title $genre $desc".lowercase()
        return BlockedWords.none { it in full }
    }

    fun fetchList(kind: Kind, page: Int, limit: Int = 24): List<Movie> {
        val channelId = when (kind) {
            Kind.Film -> "1"
            Kind.Series -> "2"
            Kind.Anime -> "1"
        }
        val postParams = "channelId=$channelId&subjectType=$channelId&page=$page&perPage=$limit" +
                (if (kind == Kind.Anime) "&genre=animation" else "")
        val jsonStr = post("$BASE/subject-list", postParams) ?: return emptyList()
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val items = j.optJSONObject("data")?.optJSONArray("items").objs()
        return items.mapNotNull { mapItem(it, kind) }
    }

    fun search(keyword: String): List<Movie> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        val jsonStr = get("$BASE/search?keyword=" + Uri.encode(q)) ?: return emptyList()
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return emptyList()
        val items = j.optJSONObject("data")?.optJSONArray("items").objs()
        return items.mapNotNull { item ->
            val subType = item.optInt("subjectType", 1)
            val kind = if (subType == 2) Kind.Series else Kind.Film
            mapItem(item, kind)
        }
    }

    fun fetchDetails(subjectId: String, isSeries: Boolean): MovieDetailData? {
        val detailStr = get("$BASE/subject_detail?subjectId=" + Uri.encode(subjectId)) ?: return null
        val j = runCatching { JSONObject(detailStr) }.getOrNull() ?: return null
        val d = j.optJSONObject("data") ?: return null

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

        val director = when {
            directors.isNotEmpty() -> directors.joinToString(", ")
            else -> "إخراج عالمي"
        }

        val coverUrl = d.optJSONObject("cover")?.optString("url").orEmpty()
        val posters = d.optJSONArray("posters").objs()
        val backdrop = posters.firstOrNull()?.optString("url")?.ifBlank { coverUrl } ?: coverUrl

        val episodes = mutableListOf<Episode>()
        if (isSeries) {
            val epStr = get("$BASE/subject-resource?subjectId=" + Uri.encode(subjectId) + "&page=1&perPage=100")
            if (!epStr.isNullOrBlank()) {
                val epJ = runCatching { JSONObject(epStr) }.getOrNull()
                val list = epJ?.optJSONObject("data")?.optJSONArray("list").objs()
                for (ep in list) {
                    val se = maxOf(1, ep.optInt("se", 1))
                    val epNum = maxOf(1, ep.optInt("ep", 1))
                    val epTitle = "الموسم $se - الحلقة $epNum"
                    val epUrl = "cinema://stream?id=$subjectId&se=$se&ep=$epNum"
                    episodes.add(Episode(epTitle, epUrl, se))
                }
            }
        }

        return MovieDetailData(
            synopsis = synopsis,
            director = director,
            cast = cast.distinct().take(10),
            backdrop = backdrop,
            episodes = episodes
        )
    }

    private fun mapItem(item: JSONObject, kind: Kind): Movie? {
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
        val durationMin = if (durSec > 0) durSec / 60 else 110

        val rate = item.optDouble("imdbRatingValue", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: item.optDouble("imdbRate", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: item.optDouble("score", 0.0).toFloat()
            .takeIf { it > 0f }
            ?: 7.5f

        val genres = genre.split(",", "/", "-")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("دراما") }

        val cover = item.optJSONObject("cover")?.optString("url").orEmpty()
        val id = (sid.hashCode() and 0x7FFFFFFF)

        return Movie(
            id = id,
            title = title,
            year = year,
            rating = rate,
            durationMin = durationMin,
            genres = genres,
            synopsis = desc,
            director = "إخراج عالمي",
            cast = emptyList(),
            url = "cinema://detail?id=$sid",
            backdrop = cover,
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
