package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String, val day: Int = 0) // day: 0 today · 1 tomorrow
data class Movie(val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
                 val url: String)
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
            Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري روشن", "الرياضية 1"),
            Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "الرياضية 2"),
            Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "الرياضية 3"),
            Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "دوري روشن", "الرياضية 1"),
            Match(5, "21:00", false, "قريبًا", "يوفنتوس", "ميلان", "الدوري الإيطالي", "الرياضية 4"),
            Match(6, "22:45", false, "قريبًا", "بايرن ميونخ", "دورتموند", "الدوري الألماني", "الرياضية 5"),
            Match(7, "19:00", false, "قريبًا", "الشباب", "الفتح", "دوري روشن", "الرياضية 1", 1),
            Match(8, "21:30", false, "قريبًا", "أتلتيكو مدريد", "إشبيلية", "الدوري الإسباني", "الرياضية 2", 1),
            Match(9, "22:00", false, "قريبًا", "تشيلسي", "آرسنال", "الدوري الإنجليزي", "الرياضية 3", 1),
            Match(10, "23:00", false, "قريبًا", "إنتر", "نابولي", "الدوري الإيطالي", "الرياضية 4", 1),
            Match(11, "00:00", false, "قريبًا", "باريس سان جيرمان", "مارسيليا", "الدوري الفرنسي", "الرياضية 5", 1),
            Match(12, "20:00", false, "قريبًا", "التعاون", "الاتفاق", "دوري روشن", "الرياضية 1", 1),
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
        channels = listOf("الرياضة" to 9, "الأخبار" to 6, "الأفلام" to 7, "الوثائقية" to 5, "الأطفال" to 5, "المنوعات" to 6)
            .flatMap { (g, n) -> (1..n).map { g to it } }
            .mapIndexed { i, (g, n) -> Channel(i, "$g $n", TestStreamUrl, g, n) },
    ))
}
