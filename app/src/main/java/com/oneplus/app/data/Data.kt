package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String, val day: Int = 0) // day: 0 today · 1 tomorrow
data class Movie(val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
                 val url: String,
                 /** The second, landscape artwork (16:9 backdrop) used by "continue watching". Empty = the gradient placeholder. */
                 val backdrop: String = "")
data class Channel(val id: Int, val name: String, val url: String, val group: String, val number: Int)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

interface HomeRepository { val data: Flow<HomeData> }

/** Genres offered by the movies filter, in display order. */
val AllGenres = listOf("أكشن", "دراما", "جريمة", "إثارة", "رعب", "كوميديا", "خيال علمي", "مغامرة", "عائلي")

// Public test streams (HLS / DASH / MP4). Placeholders: replace with the real catalogue URLs.
private val SampleVod = listOf(
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
)
/** Public HLS test stream used by the sample channels (placeholder until the real catalogue). */
const val TestStreamUrl = "https://alkatlanhd.xmax1tv.com/live/2.m3u8"
private const val SampleSynopsis =
    "تدور الأحداث حول مجموعة من الأصدقاء تتغيّر حياتهم بعد حدث غير متوقع، لتبدأ رحلة مليئة بالتشويق والمفاجآت " +
    "والقرارات الصعبة. نص تجريبي يُستبدل بوصف الفيلم الحقيقي القادم من الخادم."

/** Placeholder data. Replace with the real API/cache layer behind HomeRepository. */
class SampleRepository : HomeRepository {
    override val data: Flow<HomeData> = flowOf(HomeData(
        matches = listOf(
            Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري روشن", "beIN SPORTS XTRA HD"),
            Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "الكويت الرياضية HD"),
            Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "عمان الرياضية HD"),
            Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "دوري روشن", "beIN SPORTS XTRA HD"),
            Match(5, "21:00", false, "قريبًا", "يوفنتوس", "ميلان", "الدوري الإيطالي", "الكويت الرياضية HD"),
            Match(6, "22:45", false, "قريبًا", "بايرن ميونخ", "دورتموند", "الدوري الألماني", "عمان الرياضية HD"),
            Match(7, "19:00", false, "قريبًا", "الشباب", "الفتح", "دوري روشن", "beIN SPORTS XTRA HD", 1),
            Match(8, "21:30", false, "قريبًا", "أتلتيكو مدريد", "إشبيلية", "الدوري الإسباني", "الكويت الرياضية HD", 1),
            Match(9, "22:00", false, "قريبًا", "تشيلسي", "آرسنال", "الدوري الإنجليزي", "عمان الرياضية HD", 1),
            Match(10, "23:00", false, "قريبًا", "إنتر", "نابولي", "الدوري الإيطالي", "الكويت الرياضية HD", 1),
            Match(11, "00:00", false, "قريبًا", "باريس سان جيرمان", "مارسيليا", "الدوري الفرنسي", "عمان الرياضية HD", 1),
            Match(12, "20:00", false, "قريبًا", "التعاون", "الاتفاق", "دوري روشن", "beIN SPORTS XTRA HD", 1),
        ),
        movies = List(24) { i ->
            val g1 = AllGenres[i % AllGenres.size]
            val g2 = AllGenres[(i * 4 + 3) % AllGenres.size].let { if (it == g1) AllGenres[(i + 1) % AllGenres.size] else it }
            Movie(
                id = i, title = "فيلم ${i + 1}", year = 2014 + (i * 7) % 12,
                rating = 5.5f + ((i * 37) % 40) / 10f, durationMin = 85 + (i * 11) % 60,
                genres = listOf(g1, g2), synopsis = SampleSynopsis,
                director = "المخرج ${i + 1}", cast = List(5) { "ممثل ${it + 1}" },
                url = SampleVod[i % SampleVod.size],
            )
        },
        channels = listOf(
            // قنوات الأخبار والبث المباشر (News & Live)
            Channel(101, "الجزيرة HD", "https://live-hls-apps-aja-fa.getaj.net/AJA/01.m3u8", "الأخبار", 1),
            Channel(102, "الجزيرة مباشر", "https://live-hls-apps-ajm-fa.getaj.net/AJM/index.m3u8", "الأخبار", 2),
            Channel(103, "العربية HD", "https://live.alarabiya.net/alarabiapublish/alarabiya.smil/playlist.m3u8", "الأخبار", 3),
            Channel(104, "العربية Business", "https://live.alarabiya.net/alarabiapublish/aswaaq.smil/playlist.m3u8", "الأخبار", 4),
            Channel(105, "سكاي نيوز عربية HD", "https://live-stream.skynewsarabia.com/c-horizontal-channel/horizontal-stream/index.m3u8", "الأخبار", 5),
            Channel(106, "الشرق للأخبار HD", "https://live-news.asharq.com/asharq.m3u8", "الأخبار", 6),
            Channel(107, "CNBC عربية HD", "https://cnbc-live.akamaized.net/cnbc/master.m3u8", "الأخبار", 7),

            // باقة MBC الترفيهية (MBC Network)
            Channel(201, "MBC 1 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-1/15cf99af5de54063fdabfefe66adc075/index.m3u8", "قنوات MBC", 1),
            Channel(202, "MBC 4 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-4/24f134f1cd63db9346439e96b86ca6ed/index.m3u8", "قنوات MBC", 2),
            Channel(203, "MBC مصر HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr/956eac069c78a35d47245db6cdbb1575/index.m3u8", "قنوات MBC", 3),
            Channel(204, "MBC مصر 2 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-masr-2/754931856515075b0aabf0e583495c68/index.m3u8", "قنوات MBC", 4),
            Channel(205, "MBC دراما HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-drama/2c28a458e2f3253e678b07ac7d13fe71/index.m3u8", "قنوات MBC", 5),
            Channel(206, "MBC 5 HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-5/ee6b000cee0629411b666ab26cb13e9b/index.m3u8", "قنوات MBC", 6),
            Channel(207, "MBC العراق HD", "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-mbc-iraq/e38c44b1b43474e1c39cb5b90203691e/index.m3u8", "قنوات MBC", 7),

            // الرياضة (Sports)
            Channel(301, "beIN SPORTS XTRA HD", "https://bein-xtra-bein.amagi.tv/playlist.m3u8", "الرياضة", 1),
            Channel(302, "الكويت الرياضية HD", "https://kwtspta.cdn.mangomolo.com/sp/smil:sp.stream.smil/chunklist.m3u8", "الرياضة", 2),
            Channel(303, "عمان الرياضية HD", "https://partneta.cdn.mgmlcdn.com/omsport/smil:omsport.stream.smil/chunklist.m3u8", "الرياضة", 3),

            // الأفلام والمسلسلات (Movies & Entertainment)
            Channel(401, "أفلام أكشن Movies Action", "https://shd-amg-fast.edgenextcdn.net/tx011/playlist.m3u8", "أفلام ومسلسلات", 1),
            Channel(402, "قناة أفلام Aflam HD", "https://shd-amg-fast.edgenextcdn.net/tx001/playlist.m3u8", "أفلام ومسلسلات", 2),
            Channel(403, "قناة باب الحارة HD", "https://shd-amg-fast.edgenextcdn.net/tx010/playlist.m3u8", "أفلام ومسلسلات", 3),
            Channel(404, "قناة مرايا HD", "https://shd-amg-fast.edgenextcdn.net/tx008/playlist.m3u8", "أفلام ومسلسلات", 4),

            // الوثائقيات والمنوعات (Documentary & General)
            Channel(501, "الشرق ديسكفري HD", "https://svs.itworkscdn.net/asharqdiscoverylive/asharqd.smil/playlist.m3u8", "الوثائقية والمنوعات", 1),
            Channel(502, "الشرق الوثائقية HD", "https://svs.itworkscdn.net/asharqdocumentarylive/asharqdocumentary/playlist.m3u8", "الوثائقية والمنوعات", 2),
            Channel(503, "تلفزيون الشارقة HD", "https://live.kwikmotion.com/smc1live/smc1tv.smil/playlist.m3u8", "الوثائقية والمنوعات", 3),
            Channel(504, "قناة دبي العالمية HD", "http://185.9.2.18/chid_139/index.m3u8", "الوثائقية والمنوعات", 4),
        )
    ))
}
