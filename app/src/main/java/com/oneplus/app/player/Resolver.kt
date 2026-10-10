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

data class SubtitleTrack(val name: String, val lang: String, val url: String)

data class Resolved(
    val url: String,
    val live: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val drm: Drm? = null,
    val variants: List<Variant> = emptyList(),
    val defaultVariant: Int = 0,
    val subtitleUrl: String? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
)

object Resolver {
    private val XHost = Regex("(^|\\.)(x|twitter)\\.com$")
    private val Size = Regex("/(\\d+)x(\\d+)/")
    private val StatusId = Regex("/status(?:es)?/(\\d{1,25})")

    private const val UA = "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    fun needsExtractor(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = u.scheme?.lowercase().orEmpty()
        if (scheme == "cinema" || scheme == "wecima") return true
        val host = u.host?.lowercase().orEmpty()
        if (host.contains("aoneroom.com") || host.contains("wecima.bar") || host.contains("govid.live")) return true
        if (u.path.orEmpty().contains("api.php") && u.getQueryParameter("action") == "stream") return true
        return XHost.containsMatchIn(host) && StatusId.containsMatchIn(u.path.orEmpty())
    }

    suspend fun resolve(url: String): Resolved? = withContext(Dispatchers.IO) {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return@withContext null
        val scheme = u.scheme?.lowercase().orEmpty()
        val host = u.host?.lowercase().orEmpty()
        val path = u.path.orEmpty()

        if (scheme == "wecima" || host.contains("wecima.bar") || (path.contains("api.php") && u.getQueryParameter("action") == "stream")) {
            return@withContext resolveWeCima(u)
        }
        if (scheme == "cinema" || host.contains("aoneroom.com")) {
            return@withContext resolveUpstream(u)
        }
        runCatching { x(url) }.getOrNull()
    }

    private fun resolveWeCima(u: Uri): Resolved? {
        val realId = u.getQueryParameter("realid") ?: u.getQueryParameter("id") ?: ""
        if (realId.isBlank()) return null
        val streamApiUrl = "https://wecima.bar/api.php?action=stream&realid=" + Uri.encode(realId)
        val jsonStr = getWeCima(streamApiUrl) ?: return null
        val j = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return null
        val streamUrl = j.optString("url").trim()
        if (streamUrl.isBlank()) return null
        val ua = j.optString("user_agent", "okhttp/4.12.0")
        val headers = mapOf("User-Agent" to ua)
        return Resolved(
            url = streamUrl,
            headers = headers,
            live = false
        )
    }

    private fun resolveUpstream(u: Uri): Resolved? {
        val id = u.getQueryParameter("id") ?: u.getQueryParameter("subjectId") ?: ""
        if (id.isBlank()) return null
        val season = u.getQueryParameter("se")?.toIntOrNull() ?: u.getQueryParameter("season")?.toIntOrNull() ?: 1
        val episode = u.getQueryParameter("ep")?.toIntOrNull() ?: 1
        val isSeries = u.host == "stream" || u.getQueryParameter("se") != null || u.getQueryParameter("season") != null

        val allSubs = mutableListOf<SubtitleTrack>()
        val variants = mutableListOf<Variant>()
        var primaryUrl = ""
        var arSubtitle: String? = null

        if (isSeries) {
            val resJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/subject-resource?subjectId=" + Uri.encode(id) + "&page=1&perPage=100")
            if (!resJson.isNullOrBlank()) {
                val j = runCatching { JSONObject(resJson) }.getOrNull()
                val list = j?.optJSONObject("data")?.optJSONArray("list").objs()
                val targetEp = list.firstOrNull { it.optInt("se", 1) == season && it.optInt("ep", 1) == episode }
                    ?: list.firstOrNull { it.optInt("episode", 1) == episode }
                    ?: list.firstOrNull()

                if (targetEp != null) {
                    primaryUrl = targetEp.optString("resourceLink").ifBlank { targetEp.optString("sourceUrl") }
                    val epRes = targetEp.optInt("resolution", 0)
                    if (epRes > 0 && primaryUrl.isNotBlank()) {
                        variants.add(Variant(epRes, primaryUrl))
                    }
                    val caps = targetEp.optJSONArray("extCaptions").objs()
                    for (cap in caps) {
                        val lan = cap.optString("lan")
                        val lanName = cap.optString("lanName").ifBlank { lan }
                        val sUrl = cap.optString("url")
                        if (sUrl.isNotBlank()) {
                            val track = SubtitleTrack(cleanName(lanName), lan, sUrl)
                            allSubs.add(track)
                            if (lan.equals("ar", true) || lanName.contains("عرب")) {
                                arSubtitle = sUrl
                            }
                        }
                    }
                }
            }
        }

        if (primaryUrl.isBlank() || variants.isEmpty()) {
            val detailJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/subject_detail?subjectId=" + Uri.encode(id))
            if (!detailJson.isNullOrBlank()) {
                val j = runCatching { JSONObject(detailJson) }.getOrNull()
                val d = j?.optJSONObject("data")
                val detector = d?.optJSONArray("resourceDetectors").objs().firstOrNull()
                if (detector != null) {
                    val rList = detector.optJSONArray("resolutionList").objs()
                    for (r in rList) {
                        val res = r.optInt("resolution", 0)
                        val rLink = r.optString("resourceLink").ifBlank { r.optString("sourceUrl") }
                        if (res > 0 && rLink.isNotBlank()) {
                            variants.add(Variant(res, rLink))
                        }
                    }
                    if (primaryUrl.isBlank()) {
                        primaryUrl = detector.optString("downloadUrl").ifBlank {
                            rList.firstOrNull()?.optString("resourceLink") ?: ""
                        }
                    }
                    for (cap in detector.optJSONArray("extCaptions").objs()) {
                        val lan = cap.optString("lan")
                        val lanName = cap.optString("lanName").ifBlank { lan }
                        val sUrl = cap.optString("url")
                        if (sUrl.isNotBlank()) {
                            val track = SubtitleTrack(cleanName(lanName), lan, sUrl)
                            allSubs.add(track)
                            if (lan.equals("ar", true) || lanName.contains("عرب")) {
                                arSubtitle = sUrl
                            }
                        }
                    }
                }
            }
        }

        if (primaryUrl.isBlank() && variants.isEmpty()) return null

        val sortedVariants = variants.distinctBy { it.height }.sortedByDescending { it.height }
        val defVar = sortedVariants.indexOfFirst { it.height <= 1080 }.coerceAtLeast(0)
        val finalUrl = sortedVariants.getOrNull(defVar)?.url ?: primaryUrl

        return Resolved(
            url = finalUrl,
            headers = mapOf("User-Agent" to "OnePlus/1.0"),
            variants = sortedVariants,
            defaultVariant = defVar,
            subtitleUrl = arSubtitle,
            subtitles = allSubs.distinctBy { it.lang + it.name }
        )
    }

    private fun cleanName(name: String): String {
        return name.replace("اَلْعَرَبِيَّةُ", "العربية")
            .replace("ar", "العربية")
            .replace("en", "English")
            .trim()
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
            val h = Size.find(u)?.groupValues?.get(2)?.toIntOrNull() ?: return@mapNotNull null
            Variant(h, u)
        }.distinctBy { it.height }.sortedByDescending { it.height }
        if (variants.size > 1) {
            val d = variants.indexOfFirst { it.height <= 1080 }.coerceAtLeast(0)
            return Resolved(variants[d].url, variants = variants, defaultVariant = d)
        }
        return mp4.maxByOrNull { it.optInt("bitrate") }?.link()?.let { Resolved(it) }
    }

    private fun get(u: String): String? {
        val c = URL(u).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("x-tr-devtype", "h5")
            c.setRequestProperty("x-tr-region", "CN")
            c.setRequestProperty("x-md-global-color", "lane4")
            c.setRequestProperty("Accept", "application/json, text/plain, */*")
            if (c.responseCode != 200) return null
            return c.inputStream.use { s ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > 5_000_000) return null
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }
        } catch (_: Exception) {
            return null
        } finally { c.disconnect() }
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

    private fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
