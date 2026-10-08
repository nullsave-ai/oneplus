package com.oneplus.app.data

import androidx.annotation.StringRes
import com.oneplus.app.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class Match(val id: Int, val time: String, val live: Boolean, val status: String, val home: String,
                 val away: String, val competition: String, val channel: String, val day: Int = 0)
enum class Kind(@StringRes val title: Int) { Film(R.string.sec_movies), Series(R.string.sec_series), Anime(R.string.sec_anime) }
data class Episode(val title: String, val url: String, val season: Int = 1)
data class Movie(val id: Int, val title: String, val year: Int, val rating: Float, val durationMin: Int,
                 val genres: List<String>, val synopsis: String, val director: String, val cast: List<String>,
                 val url: String,
                 val backdrop: String = "",
                 val kind: Kind = Kind.Film, val episodes: List<Episode> = emptyList())
data class Channel(val id: Int, val name: String, val url: String, val group: String, val number: Int,
                   val logo: String = "")
data class AppUpdate(val minVersionCode: Int = 1, val latestVersionCode: Int = 1, val updateUrl: String = "",
                     val updateTitle: String = "", val updateMessage: String = "", val forceUpdate: Boolean = false)
data class HomeData(val matches: List<Match>, val movies: List<Movie>, val channels: List<Channel>, val update: AppUpdate? = null)

interface HomeRepository { val data: Flow<HomeData> }

val AllGenres = listOf("أكشن", "دراما", "جريمة", "إثارة", "رعب", "كوميديا", "خيال علمي", "مغامرة", "عائلي")

val EmptyHomeData = HomeData(emptyList(), emptyList(), emptyList())

class EmptyRepository : HomeRepository {
    override val data: Flow<HomeData> = flowOf(EmptyHomeData)
}

typealias SampleRepository = EmptyRepository
