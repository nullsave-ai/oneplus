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
        if (host.contains("aoneroom.com") || host.contains("wecima.bar") || host.contains("govid.live") || host.contains("hakunaymatata.com") || host.contains("shalltry.com")) return true
        if (u.path.orEmpty().contains("api.php") && (u.getQueryParameter("action") == "stream" || u.getQueryParameter("id") != null)) return true
        return XHost.containsMatchIn(host) && StatusId.containsMatchIn(u.path.orEmpty())
    }

    suspend fun resolve(url: String): Resolved? = withContext(Dispatchers.IO) {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return@withContext null
        val scheme = u.scheme?.lowercase().orEmpty()
        val host = u.host?.lowercase().orEmpty()
        val path = u.path.orEmpty()

        if (scheme == "wecima" || host.contains("wecima.bar") || (path.contains("api.php") && u.getQueryParameter("action") == "stream" && u.getQueryParameter("realid") != null)) {
            return@withContext resolveWeCima(u)
        }
        if (scheme == "cinema" || host.contains("aoneroom.com") || host.contains("hakunaymatata.com") || (path.contains("api.php") && u.getQueryParameter("action") == "stream")) {
            return@withContext resolveUpstream(u)
        }
        runCatching { x(url) }.getOrNull()
    }

    private fun resolveWeCima(u: Uri): Resolved? {
        val realId = u.getQueryParameter("realid") ?: u.getQueryParameter("id") ?: ""
        if (realId.isBlank()) return null
        val streamApiUrl = "https://wecima.bar/api.php?action=stream&realid=" + Uri.encode(realId)
        val jsonStr = getWeCima(streamApiUrl)
        val j = runCatching { jsonStr?.let { JSONObject(it) } }.getOrNull()
        var streamUrl = j?.optString("url")?.trim().orEmpty()
        if (streamUrl.isBlank()) {
            streamUrl = "https://govid.live/prem-$realId.m3u8"
        }
        val ua = j?.optString("user_agent")?.takeIf { it.isNotBlank() } ?: "okhttp/4.12.0"
        val headers = mapOf("User-Agent" to ua)
        return Resolved(
            url = streamUrl,
            headers = headers,
            live = false
        )
    }

    private fun resolveUpstream(u: Uri): Resolved? {
        var id = u.getQueryParameter("id") ?: u.getQueryParameter("subjectId") ?: ""
        var titleParam = u.getQueryParameter("title").orEmpty()
        if (id.isBlank() && titleParam.isNotBlank()) {
            val searchJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/search?keyword=" + Uri.encode(titleParam))
            val sj = runCatching { JSONObject(searchJson) }.getOrNull()
            id = sj?.optJSONObject("data")?.optJSONArray("items")?.objs()?.firstOrNull()?.optString("subjectId").orEmpty()
        }
        if (id.isBlank()) {
            // If already a direct link with no id, return direct link
            val rawStr = u.toString()
            if (rawStr.startsWith("http://") || rawStr.startsWith("https://")) {
                return Resolved(url = rawStr, headers = mapOf("User-Agent" to "okhttp/4.12.0"))
            }
            return null
        }
        val season = u.getQueryParameter("se")?.toIntOrNull() ?: u.getQueryParameter("season")?.toIntOrNull() ?: 1
        val episode = u.getQueryParameter("ep")?.toIntOrNull() ?: 1
        val isSeries = (u.host == "stream" && (u.getQueryParameter("se") != null || u.getQueryParameter("ep") != null)) ||
                       u.getQueryParameter("season") != null

        val allSubs = mutableListOf<SubtitleTrack>()
        val variants = mutableListOf<Variant>()
        var primaryUrl = ""
        var arSubtitle: String? = null

        fun checkAndAddSub(lan: String, lanName: String, sUrl: String) {
            if (sUrl.isBlank()) return
            val isAr = lan.equals("ar", true) || lan.equals("ara", true) ||
                       lanName.contains("عرب") || lanName.contains("اَلْعَرَبِيَّةُ") ||
                       lanName.equals("Arabic", true)
            val isEn = lan.equals("en", true) || lanName.equals("English", true)
            if (!isAr && !isEn) return // Only attach Arabic and English to avoid ExoPlayer subtitle stall
            val cleanLabel = if (isAr) "العربية [MovieBox]" else "English [MovieBox]"
            val track = SubtitleTrack(cleanLabel, if (isAr) "ar" else "en", sUrl)
            if (allSubs.none { it.url == sUrl }) {
                if (isAr) allSubs.add(0, track) else allSubs.add(track)
            }
            if (isAr && arSubtitle == null) {
                arSubtitle = sUrl
            }
        }

        if (isSeries) {
            val resJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/subject-resource?subjectId=" + Uri.encode(id) + "&page=1&perPage=100")
            if (!resJson.isNullOrBlank()) {
                val j = runCatching { JSONObject(resJson) }.getOrNull()
                val list = j?.optJSONObject("data")?.optJSONArray("list").objs()
                val targetEp = list.firstOrNull { it.optInt("se", 1) == season && it.optInt("ep", 1) == episode }
                    ?: list.firstOrNull { it.optInt("episode", 1) == episode }
                    ?: list.firstOrNull()

                if (targetEp != null) {
                    val candidate = targetEp.optString("resourceLink").ifBlank { targetEp.optString("sourceUrl") }
                    if (candidate.isNotBlank() && !candidate.contains("bcdnxw.hakunaymatata.com")) {
                        primaryUrl = candidate
                        val epRes = targetEp.optInt("resolution", 0)
                        if (epRes > 0) variants.add(Variant(epRes, candidate))
                    }
                    val caps = targetEp.optJSONArray("extCaptions").objs()
                    for (cap in caps) {
                        checkAndAddSub(cap.optString("lan"), cap.optString("lanName"), cap.optString("url"))
                    }
                }
            }
        }

        // Fetch subject_detail (which contains high-speed 1080p, 480p, 360p direct MP4 streams)
        val detailJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/subject_detail?subjectId=" + Uri.encode(id))
        if (!detailJson.isNullOrBlank()) {
            val j = runCatching { JSONObject(detailJson) }.getOrNull()
            val d = j?.optJSONObject("data")
            if (titleParam.isBlank() && d != null) {
                titleParam = d.optString("title").ifBlank { d.optString("postTitle") }
            }
            val detectors = d?.optJSONArray("resourceDetectors").objs()
            for (detector in detectors) {
                val dUrl = detector.optString("downloadUrl").trim()
                if (dUrl.isNotBlank() && !dUrl.contains("bcdnxw.hakunaymatata.com") && variants.none { it.url == dUrl }) {
                    variants.add(Variant(1080, dUrl))
                    if (primaryUrl.isBlank()) primaryUrl = dUrl
                }
                val rList = detector.optJSONArray("resolutionList").objs()
                for (r in rList) {
                    val res = r.optInt("resolution", 0)
                    val rLink = r.optString("resourceLink").ifBlank { r.optString("sourceUrl") }.trim()
                    if (res > 0 && rLink.isNotBlank() && !rLink.contains("bcdnxw.hakunaymatata.com") && variants.none { it.url == rLink }) {
                        variants.add(Variant(res, rLink))
                    }
                    for (cap in r.optJSONArray("extCaptions").objs()) {
                        checkAndAddSub(cap.optString("lan"), cap.optString("lanName"), cap.optString("url"))
                    }
                }
                if (primaryUrl.isBlank()) {
                    primaryUrl = rList.firstOrNull { !it.optString("resourceLink").contains("bcdnxw") }?.optString("resourceLink") ?: ""
                }
                for (cap in detector.optJSONArray("extCaptions").objs()) {
                    checkAndAddSub(cap.optString("lan"), cap.optString("lanName"), cap.optString("url"))
                }
            }
        }

        // For movies, also check subject-resource for any extra resolutions or captions
        if (!isSeries) {
            val resJson = get("https://h5.aoneroom.com/wefeed-h5-bff/mini/subject-resource?subjectId=" + Uri.encode(id) + "&page=1&perPage=50")
            if (!resJson.isNullOrBlank()) {
                val j = runCatching { JSONObject(resJson) }.getOrNull()
                val list = j?.optJSONObject("data")?.optJSONArray("list").objs()
                for (item in list) {
                    val res = item.optInt("resolution", 0)
                    val rLink = item.optString("resourceLink").ifBlank { item.optString("sourceUrl") }.trim()
                    if (res > 0 && rLink.isNotBlank() && !rLink.contains("bcdnxw.hakunaymatata.com") && variants.none { it.url == rLink }) {
                        variants.add(Variant(res, rLink))
                    }
                    for (cap in item.optJSONArray("extCaptions").objs()) {
                        checkAndAddSub(cap.optString("lan"), cap.optString("lanName"), cap.optString("url"))
                    }
                }
            }
        }

        // Series fallback or when MovieBox has no working direct video stream:
        if (primaryUrl.isBlank() || isSeries || primaryUrl.contains("bcdnxw.hakunaymatata.com")) {
            val fallbackStream = resolveWeCimaFallback(titleParam, season, episode, isSeries)
            if (!fallbackStream.isNullOrBlank()) {
                primaryUrl = fallbackStream
                variants.add(0, Variant(1080, fallbackStream))
            }
        }

        if (primaryUrl.isBlank() && variants.isEmpty()) {
            // Safety HLS stream fallback
            val fallbackUrl = "https://govid.live/prem-$id.m3u8"
            return Resolved(
                url = fallbackUrl,
                headers = mapOf("User-Agent" to "okhttp/4.12.0"),
                subtitleUrl = arSubtitle,
                subtitles = allSubs.distinctBy { it.url }
            )
        }

        val sortedVariants = variants.distinctBy { it.height }.sortedByDescending { it.height }
        val defVar = sortedVariants.indexOfFirst { it.height <= 1080 }.coerceAtLeast(0)
        val finalUrl = sortedVariants.getOrNull(defVar)?.url ?: primaryUrl

        val filteredSubs = (allSubs.filter { it.lang == "ar" } + allSubs.filter { it.lang == "en" }.take(1)).distinctBy { it.url }

        return Resolved(
            url = finalUrl,
            headers = mapOf("User-Agent" to "okhttp/4.12.0"),
            variants = sortedVariants,
            defaultVariant = defVar,
            subtitleUrl = arSubtitle,
            subtitles = filteredSubs
        )
    }

    private fun cleanTitleForSearch(raw: String): String {
        return raw.replace(Regex("""\[.*?\]|\(.*?\)|(?i)\b(season\s*\d+|part\s*\d+|الموسم\s*\d+)\b"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun resolveWeCimaFallback(rawTitle: String, targetSe: Int, targetEp: Int, isSeries: Boolean): String? {
        if (rawTitle.isBlank()) return null
        val cleanTitle = cleanTitleForSearch(rawTitle)
        if (cleanTitle.isBlank()) return null

        val queries = mutableListOf<String>()
        queries.add(cleanTitle)
        if (cleanTitle.contains(":")) queries.add(cleanTitle.substringBefore(":").trim())
        if (cleanTitle.contains("-")) queries.add(cleanTitle.substringBefore("-").trim())
        val words = cleanTitle.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size > 2) queries.add(words.take(2).joinToString(" "))

        var posts: List<JSONObject> = emptyList()
        for (q in queries.distinct()) {
            val res = getWeCima("https://wecima.bar/api.php?action=posts&search=" + Uri.encode(q) + "&page=1&per_page=20")
            posts = runCatching { res?.let { JSONObject(it).optJSONArray("posts").objs() } }.getOrNull().orEmpty()
            if (posts.isNotEmpty()) break
        }
        if (posts.isEmpty()) return null

        if (isSeries) {
            val seriesPosts = posts.filter { it.optString("series_slug").isNotBlank() }
            val seriesSlug = seriesPosts.firstOrNull()?.optString("series_slug") ?: posts.firstOrNull()?.optString("series_slug").orEmpty()

            var targetSeasonSlug: String? = null
            if (seriesSlug.isNotBlank()) {
                val sJsonStr = getWeCima("https://wecima.bar/api.php?action=seasons&series_slug=" + Uri.encode(seriesSlug))
                val seasons = runCatching {
                    sJsonStr?.let { if (it.startsWith("[")) JSONArray(it).objs() else JSONObject(it).optJSONArray("data").objs() }
                }.getOrNull().orEmpty()
                if (seasons.isNotEmpty()) {
                    val arabicOrdinals = listOf("الاول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر")
                    val targetOrd = if (targetSe in 1..arabicOrdinals.size) arabicOrdinals[targetSe - 1] else null

                    for (s in seasons) {
                        val sName = s.optString("name")
                        val sSlug = s.optString("slug")
                        if (targetOrd != null && (sName.contains(targetOrd) || sSlug.contains(targetOrd))) {
                            targetSeasonSlug = sSlug
                            break
                        }
                        if (sSlug.contains("الموسم-$targetSe") || sName.contains("الموسم $targetSe") || sSlug.contains("season-$targetSe", ignoreCase = true)) {
                            targetSeasonSlug = sSlug
                            break
                        }
                    }
                    if (targetSeasonSlug == null) {
                        val idx = (targetSe - 1).coerceIn(0, seasons.size - 1)
                        targetSeasonSlug = seasons[idx].optString("slug")
                    }
                } else {
                    targetSeasonSlug = seriesPosts.firstOrNull()?.optString("season_slug")
                }
            } else {
                targetSeasonSlug = posts.firstOrNull { it.optString("season_slug").isNotBlank() }?.optString("season_slug")
            }

            if (!targetSeasonSlug.isNullOrBlank()) {
                val epJsonStr = getWeCima("https://wecima.bar/api.php?action=posts&season_slug=" + Uri.encode(targetSeasonSlug) + "&page=1&per_page=100")
                val epPosts = runCatching { epJsonStr?.let { JSONObject(it).optJSONArray("posts").objs() } }.getOrNull().orEmpty()
                var matchedEp: JSONObject? = null
                for (ep in epPosts) {
                    val epTitle = ep.optString("title")
                    val num = Regex("""(?:الحلقة|حلقة|ep|episode)\s*(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epTitle)?.groupValues?.get(1)?.toIntOrNull()
                        ?: ep.optJSONObject("meta")?.optString("number")?.filter { it.isDigit() }?.toIntOrNull()
                    if (num == targetEp) {
                        matchedEp = ep
                        break
                    }
                }
                if (matchedEp == null && epPosts.isNotEmpty()) {
                    // WeCima lists episodes descending, so last is episode 1
                    matchedEp = if (targetEp == 1) epPosts.lastOrNull() else epPosts.firstOrNull()
                }

                if (matchedEp != null) {
                    val epReal = matchedEp.optString("realid").ifBlank { matchedEp.optString("id") }
                    val streamStr = getWeCima("https://wecima.bar/api.php?action=stream&realid=" + Uri.encode(epReal))
                    val streamUrl = runCatching { streamStr?.let { JSONObject(it).optString("url") } }.getOrNull()?.trim().orEmpty()
                    if (streamUrl.isNotBlank()) return streamUrl
                    return "https://govid.live/prem-$epReal.m3u8"
                }
            }

            for (p in posts) {
                val pTitle = p.optString("title")
                val num = Regex("""(?:الحلقة|حلقة|ep|episode)\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(pTitle)?.groupValues?.get(1)?.toIntOrNull()
                if (num == targetEp) {
                    val pReal = p.optString("realid").ifBlank { p.optString("id") }
                    val streamStr = getWeCima("https://wecima.bar/api.php?action=stream&realid=" + Uri.encode(pReal))
                    val streamUrl = runCatching { streamStr?.let { JSONObject(it).optString("url") } }.getOrNull()?.trim().orEmpty()
                    if (streamUrl.isNotBlank()) return streamUrl
                    return "https://govid.live/prem-$pReal.m3u8"
                }
            }
        } else {
            val targetPost = posts.firstOrNull { it.optString("series_slug").isBlank() } ?: posts.firstOrNull()
            if (targetPost != null) {
                val realId = targetPost.optString("realid").ifBlank { targetPost.optString("id") }
                val streamStr = getWeCima("https://wecima.bar/api.php?action=stream&realid=" + Uri.encode(realId))
                val streamUrl = runCatching { streamStr?.let { JSONObject(it).optString("url") } }.getOrNull()?.trim().orEmpty()
                if (streamUrl.isNotBlank()) return streamUrl
                return "https://govid.live/prem-$realId.m3u8"
            }
        }

        return null
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
