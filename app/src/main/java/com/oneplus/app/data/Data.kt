package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String)
data class Movie(val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
                 val url: String)
data class Channel(val id: Int, val name: String, val url: String)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

interface HomeRepository { val data: Flow<HomeData> }

/** Genres offered by the movies filter, in display order. */
val AllGenres = listOf("أكشن", "دراما", "جريمة", "إثارة", "رعب", "كوميديا", "خيال علمي", "مغامرة", "عائلي")

// Public test streams (HLS / DASH / MP4). Placeholders: replace with the real catalogue URLs.
// A url may carry player options after "|" (see PlaySource.parse), e.g.
//   https://host/live.m3u8|User-Agent=VLC/3.0|Referer=https://site/|Origin=https://site|Cookie=a%3Db
//   https://host/manifest.mpd|drm=widevine|license_key=https%3A%2F%2Flic.example%2Fgetlicense
//   https://host/manifest.mpd|drm=clearkey|license_key=<kid hex>:<key hex>
//   https://host/movie.mp4|sub=https%3A%2F%2Fhost%2Far.vtt|sub=https%3A%2F%2Fhost%2Fen.srt
private val SampleVod = listOf(
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
    "https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd",
    "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
)
private const val SampleLive = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
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
        channels = listOf(Channel(0, "beIN 1", "https://null-stream.nullsave-ai.workers.dev/live/bein1.m3u8")) +
            listOf("الإخبارية", "الرياضية", "السينما", "الوثائقية", "الأطفال", "الموسيقى",
                "المنوعات", "الدراما", "الطبخ", "الثقافية").mapIndexed { i, n -> Channel(i + 1, n, SampleLive) },
    ))
}
