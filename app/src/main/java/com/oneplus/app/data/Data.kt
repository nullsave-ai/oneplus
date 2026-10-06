package com.oneplus.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Match(
    val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
    val away: String, val competition: String, val channel: String, val day: Int = 0 // day: 0 today · 1 tomorrow
)

data class Movie(
    val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
    val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
    val url: String,
    /** The second, landscape artwork (16:9 backdrop) used by "continue watching". Empty = the gradient placeholder. */
    val backdrop: String = ""
)

data class Channel(
    val id: Int, val name: String, val url: String, val group: String, val number: Int,
    /** The channel's logo (http/https). Empty = its first letter on the tile. */
    val logo: String = ""
)

data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

data class AppConfig(
    val minVersionCode: Int = 1,
    val latestVersionCode: Int = 2,
    val updateUrl: String = "https://github.com/nullsave-ai/oneplus/releases/latest",
    val updateTitle: String = "تحديث جديد متوفر",
    val updateMessage: String = "يرجى تنزيل الإصدار الأحدث من تطبيق ONE+ لمتابعة المشاهدة بأعلى دقة وثبات وبدون انقطاع.",
    val forceUpdate: Boolean = false
)

interface HomeRepository {
    val data: Flow<HomeData>
    val config: Flow<AppConfig>
}

/** Genres offered by the movies filter, in display order. */
val AllGenres = listOf("أكشن", "دراما", "جريمة", "إثارة", "رعب", "كوميديا", "خيال علمي", "مغامرة", "عائلي", "فانتازيا", "تاريخي")

// Seed Channels for instant startup
private val SeedChannels = listOf(
    Channel(101, "beIN SPORTS 1 HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "beIN SPORTS HD", 1),
    Channel(102, "beIN SPORTS 2 HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "beIN SPORTS HD", 2),
    Channel(103, "beIN SPORTS 3 HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "beIN SPORTS HD", 3),
    Channel(104, "beIN SPORTS 4 HD", "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", "beIN SPORTS HD", 4),
    Channel(105, "beIN SPORTS 5 HD", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", "beIN SPORTS HD", 5),
    Channel(106, "beIN SPORTS 6 HD", "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", "beIN SPORTS HD", 6),
    Channel(107, "beIN SPORTS 7 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8", "beIN SPORTS HD", 7),
    Channel(108, "beIN SPORTS 8 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", "beIN SPORTS HD", 8),
    Channel(109, "beIN SPORTS 9 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8", "beIN SPORTS HD", 9),
    Channel(110, "beIN SPORTS XTRA HD", "https://bein-xtra-bein.amagi.tv/playlist.m3u8", "beIN SPORTS HD", 10),
    Channel(201, "Alkass 1 HD", "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", "Alkass Sports HD", 1),
    Channel(202, "Alkass 2 HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "Alkass Sports HD", 2),
    Channel(203, "Alkass 4 HD", "https://bein-xtra-bein.amagi.tv/playlist.m3u8", "Alkass Sports HD", 3),
    Channel(301, "الكويت الرياضية HD", "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", "قنوات رياضية وإخبارية", 1),
    Channel(302, "عمان الرياضية HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "قنوات رياضية وإخبارية", 2),
    Channel(303, "الجزيرة الإخبارية HD", "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8", "قنوات رياضية وإخبارية", 3),
    Channel(304, "الجزيرة مباشر", "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", "قنوات رياضية وإخبارية", 4),
    Channel(305, "العربية HD", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", "قنوات رياضية وإخبارية", 5),
    Channel(306, "سكاي نيوز عربية HD", "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", "قنوات رياضية وإخبارية", 6),
    Channel(401, "MBC 1 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8", "شبكة قنوات MBC", 1),
    Channel(402, "MBC 4 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-4/24f134f1cd63db9346439e96b86ca6ed/index.m3u8", "شبكة قنوات MBC", 2),
    Channel(403, "MBC مصر HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", "شبكة قنوات MBC", 3),
    Channel(404, "MBC مصر 2 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr-2/754931856515075b0aabf0e583495c68/index.m3u8", "شبكة قنوات MBC", 4),
    Channel(405, "MBC دراما HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8", "شبكة قنوات MBC", 5),
    Channel(406, "MBC 5 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-5/ee6b000cee0629411b666ab26cb13e9b/index.m3u8", "شبكة قنوات MBC", 6),
    Channel(407, "MBC العراق HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-iraq/e38c44b1b43474e1c39cb5b90203691e/index.m3u8", "شبكة قنوات MBC", 7),
    Channel(501, "أفلام أكشن Movies Action", "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8", "مسلسلات ومنوعات", 1),
    Channel(502, "قناة أفلام Aflam HD", "https://shd-amg-fast.edgenextcdn.net/tx001/playlist.m3u8", "مسلسلات ومنوعات", 2),
    Channel(503, "قناة باب الحارة HD", "https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8", "مسلسلات ومنوعات", 3),
    Channel(504, "قناة مرايا HD", "https://shd-amg-fast.edgenextcdn.net/tx008/playlist.m3u8", "مسلسلات ومنوعات", 4),
    Channel(505, "الشرق ديسكفري HD", "https://svs.itworkscdn.net/asharqdiscoverylive/asharqd.smil/playlist.m3u8", "مسلسلات ومنوعات", 5),
    Channel(506, "الشرق الوثائقية HD", "https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary/playlist.m3u8", "مسلسلات ومنوعات", 6)
)

private val SeedMatches = listOf(
    Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري أبطال آسيا", "beIN SPORTS 1 HD"),
    Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "beIN SPORTS 1 HD"),
    Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "beIN SPORTS 1 HD"),
    Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "كأس السوبر", "beIN SPORTS 2 HD"),
    Match(5, "21:00", false, "قريبًا", "يوفنتوس", "ميلان", "الدوري الإيطالي", "beIN SPORTS 2 HD"),
    Match(6, "22:45", false, "قريبًا", "بايرن ميونخ", "دورتموند", "الدوري الألماني", "beIN SPORTS 3 HD"),
    Match(7, "19:00", false, "قريبًا", "الشباب", "الفتح", "الدوري", "Alkass 1 HD", 1),
    Match(8, "21:30", false, "قريبًا", "أتلتيكو مدريد", "إشبيلية", "الدوري الإسباني", "beIN SPORTS 1 HD", 1),
    Match(9, "22:00", false, "قريبًا", "تشيلسي", "آرسنال", "الدوري الإنجليزي", "beIN SPORTS 2 HD", 1),
    Match(10, "23:00", false, "قريبًا", "إنتر", "نابولي", "الدوري الإيطالي", "beIN SPORTS 3 HD", 1)
)

private val SeedMovies = listOf(
    Movie(1, "Undisputed 3: Redemption", 2010, 7.3f, 96, listOf("أكشن", "جريمة", "دراما"), "Boyka is back fighting in the tournament.", "إخراج سينمائي", listOf("نجوم العمل"), "https://bcdn.hakunaymatata.com/extto/bc45770cf29ba799711f0595a29a186a.mp4", "https://pbcdnw.aoneroom.com/image/2026/01/27/492b8f0d20029b4b9b44c01b8587d6f8.jpg"),
    Movie(2, "Kingsman: The Secret Service", 2015, 7.7f, 129, listOf("أكشن", "مغامرة", "كوميديا"), "A spy organisation recruits an unrefined street kid into training.", "إخراج سينمائي", listOf("نجوم العمل"), "https://bcdn.hakunaymatata.com/resource/c49c7161dc3b7a598777e5e33d3bb7e5.mp4", "https://pbcdnw.aoneroom.com/image/2026/01/27/3f5509dd1732e6a9ce02cf75e81e3592.jpg"),
    Movie(3, "Green Book", 2018, 8.2f, 130, listOf("سيرة ذاتية", "كوميديا", "دراما"), "A working-class Italian-American bouncer becomes the driver of an African-American classical pianist.", "إخراج سينمائي", listOf("نجوم العمل"), "https://bcdn.hakunaymatata.com/resource/74ad32d78dfa9b6c00d5a4be46702e86.mp4", "https://pbcdnw.aoneroom.com/image/2026/01/27/c5453dd02b0c441f7158784d471569a9.jpg")
)

/**
 * Cloud Feed Repository: Seamlessly streams movies, channels, and matches from Firebase.
 */
class RemoteFeedRepository : HomeRepository {
    private val _config = MutableStateFlow(AppConfig())
    override val config: Flow<AppConfig> = _config.asStateFlow()

    override val data: Flow<HomeData> = flow {
        // 1. Emit instant baseline
        var current = HomeData(SeedMatches, SeedMovies, SeedChannels)
        emit(current)

        // 2. Fetch fresh catalog & config from Firebase Bridge
        withContext(Dispatchers.IO) {
            // Fetch App Config
            try {
                val cfgJson = httpGet("https://apklive-default-rtdb.firebaseio.com/app_config.json")
                if (!cfgJson.isNullOrBlank() && cfgJson != "null") {
                    val obj = JSONObject(cfgJson)
                    val parsedCfg = AppConfig(
                        minVersionCode = obj.optInt("min_version_code", 1),
                        latestVersionCode = obj.optInt("latest_version_code", 2),
                        updateUrl = obj.optString("update_url", "https://github.com/nullsave-ai/oneplus/releases/latest"),
                        updateTitle = obj.optString("update_title", "تحديث جديد متوفر"),
                        updateMessage = obj.optString("update_message", "يرجى تنزيل الإصدار الأحدث للاستمرار في المشاهدة."),
                        forceUpdate = obj.optBoolean("force_update", false)
                    )
                    _config.value = parsedCfg
                }
            } catch (_: Exception) {}

            // Fetch Catalog
            try {
                val catJson = httpGet("https://apklive-default-rtdb.firebaseio.com/catalog.json")
                if (!catJson.isNullOrBlank() && catJson != "null") {
                    val obj = JSONObject(catJson)

                    val movies = parseMovies(obj.optJSONArray("movies"))
                    val channels = parseChannels(obj.optJSONArray("channels"))
                    val matches = parseMatches(obj.optJSONArray("matches"))

                    if (movies.isNotEmpty() || channels.isNotEmpty()) {
                        current = HomeData(
                            matches = if (matches.isNotEmpty()) matches else current.matches,
                            movies = if (movies.isNotEmpty()) movies else current.movies,
                            channels = if (channels.isNotEmpty()) channels else current.channels
                        )
                        emit(current)
                    }
                }
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    private fun parseMovies(arr: JSONArray?): List<Movie> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Movie>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", i + 1)
            val title = obj.optString("title", "")
            val year = obj.optInt("year", 2024)
            val rating = obj.optDouble("rating", 7.0).toFloat()
            val durationMin = obj.optInt("durationMin", 100)
            val genresArr = obj.optJSONArray("genres")
            val genres = mutableListOf<String>()
            if (genresArr != null) {
                for (g in 0 until genresArr.length()) genres.add(genresArr.optString(g))
            }
            if (genres.isEmpty()) genres.add("أفلام")
            val synopsis = obj.optString("synopsis", "")
            val director = obj.optString("director", "إخراج سينمائي")
            val castArr = obj.optJSONArray("cast")
            val cast = mutableListOf<String>()
            if (castArr != null) {
                for (c in 0 until castArr.length()) cast.add(castArr.optString(c))
            }
            if (cast.isEmpty()) cast.add("نجوم العمل")
            val url = obj.optString("url", "")
            val backdrop = obj.optString("backdrop", "")
            if (title.isNotBlank() && url.isNotBlank()) {
                list.add(Movie(id, title, year, rating, durationMin, genres, synopsis, director, cast, url, backdrop))
            }
        }
        return list
    }

    private fun parseChannels(arr: JSONArray?): List<Channel> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Channel>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", i + 1)
            val name = obj.optString("name", "")
            val url = obj.optString("url", "")
            val group = obj.optString("group", "عام")
            val number = obj.optInt("number", i + 1)
            val logo = obj.optString("logo", "")
            if (name.isNotBlank() && url.isNotBlank()) {
                list.add(Channel(id, name, url, group, number, logo))
            }
        }
        return list
    }

    private fun parseMatches(arr: JSONArray?): List<Match> {
        if (arr == null) return emptyList()
        val list = mutableListOf<Match>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", i + 1)
            val time = obj.optString("time", "20:00")
            val live = obj.optBoolean("live", false)
            val status = obj.optString("status", if (live) "مباشر" else "قريبًا")
            val home = obj.optString("home", "")
            val away = obj.optString("away", "")
            val competition = obj.optString("competition", "مباراة رسمية")
            val channel = obj.optString("channel", "")
            val day = obj.optInt("day", 0)
            if (home.isNotBlank() && away.isNotBlank()) {
                list.add(Match(id, time, live, status, home, away, competition, channel, day))
            }
        }
        return list
    }

    private fun httpGet(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "OnePlusApp/2.0")
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
