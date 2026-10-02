package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String)
data class Movie(val id: Int, val title: String, val year: String)
data class Channel(val id: Int, val name: String)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>)

interface HomeRepository { val data: Flow<HomeData> }

/** Placeholder data. Replace with the real API/cache layer behind HomeRepository. */
class SampleRepository : HomeRepository {
    override val data: Flow<HomeData> = flowOf(HomeData(
        matches = listOf(
            Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري روشن", "الرياضية 1"),
            Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "الرياضية 2"),
            Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "الرياضية 3"),
            Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "دوري روشن", "الرياضية 1"),
        ),
        movies = List(8) { Movie(it, "فيلم ${it + 1}", "${2018 + it}") },
        channels = listOf("الإخبارية", "الرياضية", "السينما", "الوثائقية", "الأطفال", "الموسيقى",
            "المنوعات", "الدراما", "الطبخ", "الثقافية").mapIndexed { i, n -> Channel(i, n) },
    ))
}
