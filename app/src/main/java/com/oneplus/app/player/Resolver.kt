package com.oneplus.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A fixed-quality alternative (progressive streams that have no manifest to adapt over). */
data class Variant(val height: Int, val url: String, val audio: String?)
data class Sub(val lang: String, val url: String, val mime: String)

/** Everything the player needs to start: where to read from, with which headers, plus extras found by the extractor. */
data class Resolved(
    val url: String,
    val live: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val audio: String? = null,
    val variants: List<Variant> = emptyList(),
    val defaultVariant: Int = 0,
    val subs: List<Sub> = emptyList(),
)

/**
 * Turns a *page* link (YouTube, X, ok.ru, Facebook, Vimeo, ... about 1800 sites) into media URLs by running yt-dlp
 * ON THE DEVICE. It has to run here: CDNs such as YouTube's sign URLs for the IP that asked, so a server can't resolve for the phone.
 * Plain media links never come through here (the player reads them directly).
 */
object Resolver {
    // Hosts known to serve web pages, not media. Any other link is tried as media first and falls back to the extractor.
    private val PageHosts = Regex(
        "(^|\\.)(youtube\\.com|youtu\\.be|youtube-nocookie\\.com|x\\.com|twitter\\.com|ok\\.ru|facebook\\.com|fb\\.watch|" +
            "instagram\\.com|tiktok\\.com|vimeo\\.com|dailymotion\\.com|dai\\.ly|twitch\\.tv|kick\\.com|reddit\\.com|vk\\.com|" +
            "vkvideo\\.ru|streamable\\.com|soundcloud\\.com|bilibili\\.com|rumble\\.com|odysee\\.com|bitchute\\.com|t\\.me)$",
    )
    private val Http = setOf("http", "https")
    @Volatile private var ready = false

    fun needsExtractor(uri: Uri): Boolean = uri.host?.lowercase()?.let { PageHosts.containsMatchIn(it) } == true

    /** Blocking work (Python start-up + network): runs on IO. Returns null when nothing playable was found. */
    suspend fun resolve(app: Context, url: String): Resolved? = withContext(Dispatchers.IO) {
        runCatching {
            if (!ready) synchronized(this@Resolver) { if (!ready) { YoutubeDL.getInstance().init(app); ready = true } }
            // Fixed options only; the URL was validated as http(s) before it got here, so it can never be read as an option.
            val req = YoutubeDLRequest(url).apply {
                addOption("-J"); addOption("--no-playlist"); addOption("--no-warnings"); addOption("--socket-timeout", "10")
            }
            val out = YoutubeDL.getInstance().execute(req, null, null).out
            parse(JSONObject(out.substring(out.indexOf('{'))))
        }.getOrNull()
    }

    private fun parse(j: JSONObject): Resolved? {
        val fmts = j.optJSONArray("formats").objs()
        val live = j.optBoolean("is_live")
        val headers = j.optJSONObject("http_headers")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        val subs = subtitles(j)
        fun isHttp(f: JSONObject) = f.str("protocol") in Http && f.str("url") != null

        // 1) A real manifest wins: the player adapts quality by itself and exposes audio/subtitle tracks natively.
        fmts.firstNotNullOfOrNull { f -> if (f.str("protocol")?.startsWith("m3u8") == true) f.str("manifest_url") ?: f.str("url") else null }
            ?.let { return Resolved(it, live, headers, subs = subs) }
        fmts.firstNotNullOfOrNull { f -> if (f.str("protocol") == "http_dash_segments") f.str("manifest_url") else null }
            ?.let { return Resolved(it, live, headers, subs = subs) }

        // 2) Progressive streams: one entry per height (prefer H.264 and muxed), paired with the best separate audio.
        val audio = fmts.filter { isHttp(it) && it.str("vcodec") == null && it.str("acodec") != null }.maxByOrNull {
            (if (it.str("ext") == "m4a") 1e6 else 0.0) + (if (it.str("format_note")?.contains("default") == true) 5e5 else 0.0) + it.optDouble("tbr", 0.0)
        }
        val variants = fmts.filter { isHttp(it) && it.str("vcodec") != null && it.optInt("height") > 0 }
            .groupBy { it.optInt("height") }
            .mapValues { (_, l) ->
                l.maxByOrNull {
                    (if (it.str("vcodec")!!.startsWith("avc1")) 1e6 else 0.0) + (if (it.str("acodec") != null) 5e5 else 0.0) + it.optDouble("tbr", 0.0)
                }!!
            }
            .toSortedMap(reverseOrder())
            .map { (h, f) -> Variant(h, f.getString("url"), if (f.str("acodec") == null) audio?.getString("url") else null) }
        if (variants.isNotEmpty()) {
            val d = variants.indexOfFirst { it.height <= 1080 }.coerceAtLeast(0)
            return Resolved(variants[d].url, live, headers, variants[d].audio, variants, d, subs)
        }

        // 3) audio-only pages, then a plain direct link found by the generic extractor
        audio?.let { return Resolved(it.getString("url"), live, headers, subs = subs) }
        return j.str("url")?.let { Resolved(it, live, headers, subs = subs) }
    }

    /** Manual subtitles (Arabic/English first), plus automatic Arabic/English captions when no manual ones exist. */
    private fun subtitles(j: JSONObject): List<Sub> {
        fun pick(o: JSONObject?, only: Set<String>?): List<Sub> {
            if (o == null) return emptyList()
            return o.keys().asSequence().filter { only == null || it in only }.mapNotNull { lang ->
                val list = o.optJSONArray(lang).objs()
                val e = list.firstOrNull { it.optString("ext") == "vtt" } ?: list.firstOrNull { it.optString("ext") in setOf("srt", "ttml") }
                val url = e?.str("url") ?: return@mapNotNull null
                val mime = when (e.optString("ext")) { "srt" -> MimeTypes.APPLICATION_SUBRIP; "ttml" -> MimeTypes.APPLICATION_TTML; else -> MimeTypes.TEXT_VTT }
                Sub(lang, url, mime)
            }.toList()
        }
        val manual = pick(j.optJSONObject("subtitles"), null)
        val auto = pick(j.optJSONObject("automatic_captions"), setOf("ar", "en")).filter { a -> manual.none { it.lang == a.lang } }
        return (manual + auto).sortedBy { if (it.lang.startsWith("ar")) 0 else if (it.lang.startsWith("en")) 1 else 2 }.take(8)
    }

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONObject.str(k: String): String? = optString(k, "").takeIf { it.isNotBlank() && it != "none" && it != "null" }
}
