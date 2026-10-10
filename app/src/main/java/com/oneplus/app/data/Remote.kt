package com.oneplus.app.data

import com.oneplus.app.player.isAllowed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val ApiUrl = "https://apklive-default-rtdb.firebaseio.com/catalog.json"

private const val MaxBytes = 10_000_000
private const val MaxCached = 5_000_000

class RemoteRepository(private val url: String, private val store: Store) : HomeRepository {
    private var etag: String? = null

    override val data: Flow<HomeData> = flow {
        val old = runCatching { parse(JSONObject(store.page("home") ?: error("none"))) }.getOrNull()
        if (old != null) emit(old)
        val text = download(if (old != null) store.page("home.etag") else null)
        val fresh = text?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }
        if (text != null && fresh != null) {
            emit(fresh)
            if (text.length <= MaxCached) {
                store.putPage(Page("home", text))
                store.putPage(Page("home.etag", etag.orEmpty()))
            }
        } else if (old == null) emit(SampleRepository().data.first())
    }.flowOn(Dispatchers.IO)

    private fun download(known: String?): String? {
        if (!isAllowed(url)) return null
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            if (!known.isNullOrEmpty()) c.setRequestProperty("If-None-Match", known)
            if (c.responseCode != 200) return null
            etag = c.getHeaderField("ETag")
            val bytes = c.inputStream.use { it.readNBytesCapped(MaxBytes) } ?: return null
            return String(bytes, Charsets.UTF_8)
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readNBytesCapped(max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun parse(j: JSONObject): HomeData {
        val perGroup = HashMap<String, Int>()

        val seenMatchIds = HashSet<Int>()
        var nextMatchId = 1
        val matches = j.optJSONArray("matches").objs().mapIndexed { i, o ->
            val rawId = o.optLong("id", -1L)
            val candidateId = if (rawId in 1..2_000_000_000L) rawId.toInt() else null
            val id = if (candidateId != null && seenMatchIds.add(candidateId)) {
                candidateId
            } else {
                while (seenMatchIds.contains(nextMatchId)) nextMatchId++
                seenMatchIds.add(nextMatchId)
                nextMatchId
            }
            Match(
                id, o.optString("time"), o.optBoolean("live"), o.optString("status"), o.optString("home"),
                o.optString("away"), o.optString("competition"), o.optString("channel"), o.optInt("day")
            )
        }

        val seenMovieIds = HashSet<Int>()
        var nextMovieId = 1
        val movies = j.optJSONArray("movies").objs().mapIndexedNotNull { i, o ->
            val u = o.optString("url")
            val eps = o.optJSONArray("episodes").objs().mapNotNull { e ->
                e.optString("url").takeIf { playable(it) }?.let { Episode(e.optString("title"), it, e.optInt("season", 1).coerceAtLeast(1)) }
            }
            if (eps.isEmpty() && !playable(u)) return@mapIndexedNotNull null
            val rawId = o.optLong("id", -1L)
            val candidateId = if (rawId in 1..2_000_000_000L) rawId.toInt() else null
            val id = if (candidateId != null && seenMovieIds.add(candidateId)) {
                candidateId
            } else {
                while (seenMovieIds.contains(nextMovieId)) nextMovieId++
                seenMovieIds.add(nextMovieId)
                nextMovieId
            }
            Movie(
                id, o.optString("title"), o.optInt("year"), o.optDouble("rating", 0.0).toFloat(), o.optInt("duration"),
                o.optJSONArray("genres").strings(), o.optString("synopsis"), o.optString("director"), o.optJSONArray("cast").strings(),
                u, o.optString("backdrop"), Kind.entries.firstOrNull { it.name.equals(o.optString("kind"), true) } ?: Kind.Film, eps
            )
        }

        val seenChannelIds = HashSet<Int>()
        var nextChannelId = 1
        val channels = j.optJSONArray("channels").objs().mapIndexedNotNull { i, o ->
            val u = o.optString("url")
            if (!playable(u)) return@mapIndexedNotNull null
            val g = o.optString("group")
            val n = (perGroup[g] ?: 0) + 1
            perGroup[g] = n
            val rawId = o.optLong("id", -1L)
            val candidateId = if (rawId in 1..2_000_000_000L) rawId.toInt() else null
            val id = if (candidateId != null && seenChannelIds.add(candidateId)) {
                candidateId
            } else {
                while (seenChannelIds.contains(nextChannelId)) nextChannelId++
                seenChannelIds.add(nextChannelId)
                nextChannelId
            }
            Channel(id, o.optString("name"), u, g, n, o.optString("logo"))
        }

        return HomeData(matches = matches, movies = movies, channels = channels)
    }

    private fun playable(u: String) = isAllowed(u.substringBefore('|').trim()) || u.trimStart().startsWith("url", true)

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}
