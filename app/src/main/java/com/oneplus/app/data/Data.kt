package com.oneplus.app.data

import android.net.Uri
import com.oneplus.app.player.NetflyEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String, val day: Int = 0)
data class Movie(val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
                 val url: String,
                 val backdrop: String = "")
data class Channel(val id: Int, val name: String, val url: String, val group: String, val number: Int)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

interface HomeRepository { val data: Flow<HomeData> }

val AllGenres = listOf("أكشن", "دراما", "جريمة", "إثارة", "رعب", "كوميديا", "خيال علمي", "مغامرة", "عائلي")

private val SampleVod = listOf(
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
)
const val TestStreamUrl = "https://alkatlanhd.xmax1tv.com/live/2.m3u8"
private const val SampleSynopsis =
    "تدور الأحداث حول مجموعة من الأصدقاء تتغيّر حياتهم بعد حدث غير متوقع، لتبدأ رحلة مليئة بالتشويق والمفاجآت والقرارات الصعبة."

private fun nfChannel(id: Int, name: String, group: String, number: Int, fallbackUrl: String): Channel {
    val encoded = Uri.encode(fallbackUrl)
    return Channel(
        id = id,
        name = name,
        url = "turbo://channel/$id?id=$id&fallback=$encoded",
        group = group,
        number = number
    )
}

val SeedChannels = listOf(
    // === beIN SPORTS HD ===
    nfChannel(101, "beIN SPORTS NEWS HD", "beIN SPORTS HD", 1, "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8"),
    nfChannel(102, "beIN SPORTS 1 HD", "beIN SPORTS HD", 2, "https://bein-xtra-bein.amagi.tv/playlist.m3u8"),
    nfChannel(103, "beIN SPORTS 2 HD", "beIN SPORTS HD", 3, "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8"),
    nfChannel(104, "beIN SPORTS 3 HD", "beIN SPORTS HD", 4, "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8"),
    nfChannel(105, "beIN SPORTS 4 HD", "beIN SPORTS HD", 5, "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8"),
    nfChannel(106, "beIN SPORTS 5 HD", "beIN SPORTS HD", 6, "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8"),
    nfChannel(107, "beIN SPORTS 6 HD", "beIN SPORTS HD", 7, "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8"),
    nfChannel(108, "beIN SPORTS 7 HD", "beIN SPORTS HD", 8, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8"),
    nfChannel(109, "beIN SPORTS 8 HD", "beIN SPORTS HD", 9, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8"),
    nfChannel(110, "beIN SPORTS 9 HD", "beIN SPORTS HD", 10, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8"),
    nfChannel(111, "beIN SPORTS XTRA HD", "beIN SPORTS HD", 11, "https://bein-xtra-bein.amagi.tv/playlist.m3u8"),

    // === Alkass Sports HD ===
    nfChannel(201, "Alkass 1 HD", "Alkass Sports HD", 1, "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8"),
    nfChannel(202, "Alkass 2 HD", "Alkass Sports HD", 2, "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8"),
    nfChannel(203, "Alkass 4 HD", "Alkass Sports HD", 3, "https://bein-xtra-bein.amagi.tv/playlist.m3u8"),

    // === قنوات رياضية وإخبارية ===
    nfChannel(301, "الكويت الرياضية HD", "قنوات رياضية وإخبارية", 1, "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8"),
    nfChannel(302, "عمان الرياضية HD", "قنوات رياضية وإخبارية", 2, "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8"),
    nfChannel(303, "الجزيرة الإخبارية HD", "قنوات رياضية وإخبارية", 3, "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8"),
    nfChannel(304, "الجزيرة مباشر", "قنوات رياضية وإخبارية", 4, "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8"),
    nfChannel(305, "العربية HD", "قنوات رياضية وإخبارية", 5, "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8"),
    nfChannel(306, "سكاي نيوز عربية HD", "قنوات رياضية وإخبارية", 6, "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8"),

    // === شبكة MBC ===
    nfChannel(401, "MBC 1 HD", "شبكة قنوات MBC", 1, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8"),
    nfChannel(402, "MBC 4 HD", "شبكة قنوات MBC", 2, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-4/24f134f1cd63db9346439e96b86ca6ed/index.m3u8"),
    nfChannel(403, "MBC مصر HD", "شبكة قنوات MBC", 3, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8"),
    nfChannel(404, "MBC مصر 2 HD", "شبكة قنوات MBC", 4, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr-2/754931856515075b0aabf0e583495c68/index.m3u8"),
    nfChannel(405, "MBC دراما HD", "شبكة قنوات MBC", 5, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8"),
    nfChannel(406, "MBC 5 HD", "شبكة قنوات MBC", 6, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-5/ee6b000cee0629411b666ab26cb13e9b/index.m3u8"),
    nfChannel(407, "MBC العراق HD", "شبكة قنوات MBC", 7, "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-iraq/e38c44b1b43474e1c39cb5b90203691e/index.m3u8"),

    // === مسلسلات ومنوعات ===
    nfChannel(501, "أفلام أكشن Movies Action", "مسلسلات ومنوعات", 1, "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8"),
    nfChannel(502, "قناة أفلام Aflam HD", "مسلسلات ومنوعات", 2, "https://shd-amg-fast.edgenextcdn.net/tx001/playlist.m3u8"),
    nfChannel(503, "قناة باب الحارة HD", "مسلسلات ومنوعات", 3, "https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8"),
    nfChannel(504, "قناة مرايا HD", "مسلسلات ومنوعات", 4, "https://shd-amg-fast.edgenextcdn.net/tx008/playlist.m3u8"),
    nfChannel(505, "الشرق ديسكفري HD", "مسلسلات ومنوعات", 5, "https://svs.itworkscdn.net/asharqdiscoverylive/asharqd.smil/playlist.m3u8"),
    nfChannel(506, "الشرق الوثائقية HD", "مسلسلات ومنوعات", 6, "https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary/playlist.m3u8")
)

class SampleRepository : HomeRepository {
    override val data: Flow<HomeData> = flow {
        val defaultMatches = listOf(
            Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري أبطال آسيا", "beIN SPORTS 1 HD"),
            Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "beIN SPORTS 1 HD"),
            Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "beIN SPORTS 1 HD"),
            Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "كأس السوبر", "beIN SPORTS 2 HD"),
            Match(5, "21:00", false, "قريبًا", "يوفنتوس", "ميلان", "الدوري الإيطالي", "beIN SPORTS 2 HD"),
            Match(6, "22:45", false, "قريبًا", "بايرن ميونخ", "دورتموند", "الدوري الألماني", "beIN SPORTS 3 HD"),
            Match(7, "19:00", false, "قريبًا", "الشباب", "الفتح", "الدوري", "Alkass 1 HD", 1),
            Match(8, "21:30", false, "قريبًا", "أتلتيكو مدريد", "إشبيلية", "الدوري الإسباني", "beIN SPORTS 1 HD", 1),
            Match(9, "22:00", false, "قريبًا", "تشيلسي", "آرسنال", "الدوري الإنجليزي", "beIN SPORTS 2 HD", 1),
            Match(10, "23:00", false, "قريبًا", "إنتر", "نابولي", "الدوري الإيطالي", "beIN SPORTS 3 HD", 1),
            Match(11, "00:00", false, "قريبًا", "باريس سان جيرمان", "مارسيليا", "الدوري الفرنسي", "beIN SPORTS 4 HD", 1),
            Match(12, "20:00", false, "قريبًا", "التعاون", "الاتفاق", "الدوري", "Alkass 2 HD", 1),
        )
        val defaultMovies = List(24) { i ->
            val g1 = AllGenres[i % AllGenres.size]
            val g2 = AllGenres[(i * 4 + 3) % AllGenres.size].let { if (it == g1) AllGenres[(i + 1) % AllGenres.size] else it }
            Movie(
                id = i, title = "فيلم ${i + 1}", year = 2014 + (i * 7) % 12,
                rating = 5.5f + ((i * 37) % 40) / 10f, durationMin = 85 + (i * 11) % 60,
                genres = listOf(g1, g2), synopsis = SampleSynopsis,
                director = "المخرج ${i + 1}", cast = List(5) { "ممثل ${it + 1}" },
                url = SampleVod[i % SampleVod.size],
            )
        }

        var current = HomeData(defaultMatches, defaultMovies, SeedChannels)
        emit(current)

        val ctx = NetflyEngine.appContext
        if (ctx != null) {
            val netflyLive = withContext(Dispatchers.IO) {
                if (!NetflyEngine.isInitialized) {
                    NetflyEngine.init(ctx)
                }
                NetflyEngine.fetchLiveChannels(ctx)
            }
            if (!netflyLive.isNullOrEmpty()) {
                current = current.copy(channels = netflyLive)
                emit(current)
            }
        }
    }
}
