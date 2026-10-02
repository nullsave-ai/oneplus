package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String)
data class Movie(val id: Int, val title: String, val year: String, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>)
data class Channel(val id: Int, val name: String)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

interface HomeRepository { val data: Flow<HomeData> }

private val SampleGenres = listOf(
    listOf("دراما", "تشويق"), listOf("أكشن", "مغامرة"), listOf("كوميديا", "عائلي"), listOf("خيال علمي", "إثارة"),
)
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
        movies = List(8) { i ->
            Movie(
                id = i, title = "فيلم ${i + 1}", year = "${2018 + i}",
                rating = 6.8f + (i % 5) * 0.4f, durationMin = 95 + i * 7,
                genres = SampleGenres[i % SampleGenres.size], synopsis = SampleSynopsis,
                director = "المخرج ${i + 1}", cast = List(5) { "ممثل ${it + 1}" },
            )
        },
        channels = listOf("الإخبارية", "الرياضية", "السينما", "الوثائقية", "الأطفال", "الموسيقى",
            "المنوعات", "الدراما", "الطبخ", "الثقافية").mapIndexed { i, n -> Channel(i, n) },
    ))
}
