package com.oneplus.app.player

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Variant(val height: Int, val url: String)

data class Resolved(
    val url: String,
    val live: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val drm: Drm? = null,
    val variants: List<Variant> = emptyList(),
    val defaultVariant: Int = 0,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

object Resolver {
    private val XHost = Regex("(^|\\.)(x|twitter)\\.com$")
    private val StatusId = Regex("/status(?:es)?/(\\d{1,25})")

    fun needsExtractor(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = u.host?.lowercase().orEmpty()
        return (host.let { XHost.containsMatchIn(it) } && StatusId.containsMatchIn(u.path.orEmpty()))
            || host.contains("wideiptv.top") || (host.isNotEmpty() && u.path.orEmpty().contains("/player/"))
    }

    suspend fun resolve(url: String): Resolved? = withContext(Dispatchers.IO) {
        runCatching {
            val u = url.lowercase()
            if (u.contains("wideiptv.top") || u.contains("/player/")) webPlayer(url)
            else x(url)
        }.getOrNull()
    }

    private fun webPlayer(url: String): Resolved? {
        val html = get(url, referer = "https://wideiptv.top/") ?: return null
        val match = Regex("""streamUrl:\s*["']([^"']+)["']""").find(html) ?: return null
        val raw = match.groupValues[1].replace("\\/", "/")
        if (!isAllowed(raw)) return null
        return Resolved(
            url = raw,
            live = true,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Referer" to "https://wideiptv.top/"
            )
        )
    }

    private fun x(url: String): Resolved? {
        val id = StatusId.find(Uri.parse(url).path.orEmpty())?.groupValues?.get(1) ?: return null
        val token = (1..10).map { "123456789abcdefghijklmnopqrstuvwxyz".random() }.joinToString("")
        val j = JSONObject(get("https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token=$token") ?: return null)
        val vars = j.optJSONArray("mediaDetails").objs().firstNotNullOfOrNull { it.optJSONObject("video_info") }
            ?.optJSONArray("variants").objs()
            .ifEmpty { j.optJSONObject("video")?.optJSONArray("variants").objs().map { JSONObject().put("content_type", it.optString("type")).put("url", it.optString("src")) } }
        fun JSONObject.link() = optString("url").takeIf { isAllowed(it) }

        vars.firstOrNull { it.optString("content_type").contains("mpegurl", true) }?.link()?.let { return Resolved(it) }

        val mp4 = vars.filter { it.optString("content_type") == "video/mp4" }
        val variants = mp4.mapNotNull { v ->
            val u = v.link() ?: return@mapNotNull null
            val h = Regex("/(\\d+)x(\\d+)/").find(u)?.groupValues?.get(2)?.toIntOrNull() ?: return@mapNotNull null
            Variant(h, u)
        }.distinctBy { it.height }.sortedByDescending { it.height }
        if (variants.size > 1) {
            val d = variants.indexOfFirst { it.height <= 1080 }.coerceAtLeast(0)
            return Resolved(variants[d].url, variants = variants, defaultVariant = d)
        }
        return mp4.maxByOrNull { it.optInt("bitrate") }?.link()?.let { Resolved(it) }
    }

    private fun get(u: String, referer: String? = null): String? {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            if (!referer.isNullOrBlank()) c.setRequestProperty("Referer", referer)
            if (c.responseCode in 300..399) {
                val loc = c.getHeaderField("Location") ?: return null
                return get(loc, referer)
            }
            if (c.responseCode != 200) return null
            return c.inputStream.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 1_000_000) return null
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
