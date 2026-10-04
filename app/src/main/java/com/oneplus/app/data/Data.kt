package com.oneplus.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

class SampleRepository : HomeRepository {
    override val data: Flow<HomeData> = flowOf(HomeData(
        matches = listOf(
            Match(1, "20:00", true, "مباشر", "الهلال", "النصر", "دوري روشن", "SSC 1 HD"),
            Match(2, "22:00", false, "قريبًا", "ريال مدريد", "برشلونة", "الدوري الإسباني", "beIN SPORTS 1 HD"),
            Match(3, "23:30", false, "قريبًا", "ليفربول", "مانشستر سيتي", "الدوري الإنجليزي", "beIN SPORTS 1 HD"),
            Match(4, "01:00", false, "قريبًا", "الأهلي", "الاتحاد", "دوري روشن", "SSC 1 HD"),
            Match(5, "21:00", false, "قريبًا", "يوفنتوس", "ميلان", "الدوري الإيطالي", "beIN SPORTS 2 HD"),
            Match(6, "22:45", false, "قريبًا", "بايرن ميونخ", "دورتموند", "الدوري الألماني", "beIN SPORTS 3 HD"),
            Match(7, "19:00", false, "قريبًا", "الشباب", "الفتح", "دوري روشن", "SSC 2 HD", 1),
            Match(8, "21:30", false, "قريبًا", "أتلتيكو مدريد", "إشبيلية", "الدوري الإسباني", "beIN SPORTS 1 HD", 1),
            Match(9, "22:00", false, "قريبًا", "تشيلسي", "آرسنال", "الدوري الإنجليزي", "beIN SPORTS 2 HD", 1),
            Match(10, "23:00", false, "قريبًا", "إنتر", "نابولي", "الدوري الإيطالي", "beIN SPORTS 3 HD", 1),
            Match(11, "00:00", false, "قريبًا", "باريس سان جيرمان", "مارسيليا", "الدوري الفرنسي", "beIN SPORTS 4 HD", 1),
            Match(12, "20:00", false, "قريبًا", "التعاون", "الاتفاق", "دوري روشن", "SSC 1 HD", 1),
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
            // === beIN SPORTS HD ===
            Channel(1001, "beIN SPORTS NEWS HD", "turbo://stream.netflyapp.com/live/bein_news/master.m3u8?id=1001", "beIN SPORTS HD", 1),
            Channel(1002, "beIN SPORTS 1 HD", "turbo://stream.netflyapp.com/live/bein1/master.m3u8?id=1002", "beIN SPORTS HD", 2),
            Channel(1003, "beIN SPORTS 2 HD", "turbo://stream.netflyapp.com/live/bein2/master.m3u8?id=1003", "beIN SPORTS HD", 3),
            Channel(1004, "beIN SPORTS 3 HD", "turbo://stream.netflyapp.com/live/bein3/master.m3u8?id=1004", "beIN SPORTS HD", 4),
            Channel(1005, "beIN SPORTS 4 HD", "turbo://stream.netflyapp.com/live/bein4/master.m3u8?id=1005", "beIN SPORTS HD", 5),
            Channel(1006, "beIN SPORTS 5 HD", "turbo://stream.netflyapp.com/live/bein5/master.m3u8?id=1006", "beIN SPORTS HD", 6),
            Channel(1007, "beIN SPORTS 6 HD", "turbo://stream.netflyapp.com/live/bein6/master.m3u8?id=1007", "beIN SPORTS HD", 7),
            Channel(1008, "beIN SPORTS 7 HD", "turbo://stream.netflyapp.com/live/bein7/master.m3u8?id=1008", "beIN SPORTS HD", 8),
            Channel(1009, "beIN SPORTS 8 HD", "turbo://stream.netflyapp.com/live/bein8/master.m3u8?id=1009", "beIN SPORTS HD", 9),
            Channel(1010, "beIN SPORTS 9 HD", "turbo://stream.netflyapp.com/live/bein9/master.m3u8?id=1010", "beIN SPORTS HD", 10),
            Channel(1011, "beIN SPORTS AFC 1 HD", "turbo://stream.netflyapp.com/live/bein_afc1/master.m3u8?id=1011", "beIN SPORTS HD", 11),
            Channel(1012, "beIN SPORTS AFC 2 HD", "turbo://stream.netflyapp.com/live/bein_afc2/master.m3u8?id=1012", "beIN SPORTS HD", 12),
            // === SSC Sports HD ===
            Channel(2001, "SSC 1 HD", "turbo://stream.netflyapp.com/live/ssc1/master.m3u8?id=2001", "SSC Sports HD", 1),
            Channel(2002, "SSC 2 HD", "turbo://stream.netflyapp.com/live/ssc2/master.m3u8?id=2002", "SSC Sports HD", 2),
            Channel(2003, "SSC 3 HD", "turbo://stream.netflyapp.com/live/ssc3/master.m3u8?id=2003", "SSC Sports HD", 3),
            Channel(2004, "SSC 4 HD", "turbo://stream.netflyapp.com/live/ssc4/master.m3u8?id=2004", "SSC Sports HD", 4),
            Channel(2005, "SSC 5 HD", "turbo://stream.netflyapp.com/live/ssc5/master.m3u8?id=2005", "SSC Sports HD", 5),
            Channel(2006, "SSC EXTRA 1 HD", "turbo://stream.netflyapp.com/live/ssc_extra1/master.m3u8?id=2006", "SSC Sports HD", 6),
            Channel(2007, "SSC NEWS HD", "turbo://stream.netflyapp.com/live/ssc_news/master.m3u8?id=2007", "SSC Sports HD", 7),
            // === Alkass Sports HD ===
            Channel(3001, "Alkass 1 HD", "turbo://stream.netflyapp.com/live/alkass1/master.m3u8?id=3001", "Alkass Sports HD", 1),
            Channel(3002, "Alkass 2 HD", "turbo://stream.netflyapp.com/live/alkass2/master.m3u8?id=3002", "Alkass Sports HD", 2),
            Channel(3003, "Alkass 3 HD", "turbo://stream.netflyapp.com/live/alkass3/master.m3u8?id=3003", "Alkass Sports HD", 3),
            Channel(3004, "Alkass 4 HD", "turbo://stream.netflyapp.com/live/alkass4/master.m3u8?id=3004", "Alkass Sports HD", 4),
            Channel(3005, "Alkass 5 Extra HD", "turbo://stream.netflyapp.com/live/alkass5/master.m3u8?id=3005", "Alkass Sports HD", 5),
            Channel(3006, "Alkass 6 Extra HD", "turbo://stream.netflyapp.com/live/alkass6/master.m3u8?id=3006", "Alkass Sports HD", 6),
            // === International Sports ===
            Channel(4001, "AD SPORTS 1 HD", "turbo://stream.netflyapp.com/live/adsports1/master.m3u8?id=4001", "International Sports", 1),
            Channel(4002, "AD SPORTS 2 HD", "turbo://stream.netflyapp.com/live/adsports2/master.m3u8?id=4002", "International Sports", 2),
            Channel(4003, "ON Time Sports 1 HD", "turbo://stream.netflyapp.com/live/ontime1/master.m3u8?id=4003", "International Sports", 3),
            Channel(4004, "Sky Sports Main Event", "turbo://stream.netflyapp.com/live/sky_main/master.m3u8?id=4004", "International Sports", 4),
            Channel(4005, "Sky Sports Premier League", "turbo://stream.netflyapp.com/live/sky_pl/master.m3u8?id=4005", "International Sports", 5),
            Channel(4006, "TNT Sports 1 UK", "turbo://stream.netflyapp.com/live/tnt1/master.m3u8?id=4006", "International Sports", 6),
            Channel(4007, "Canal+ Sport France", "turbo://stream.netflyapp.com/live/canal_sport/master.m3u8?id=4007", "International Sports", 7),
            Channel(4008, "Eurosport 1 HD", "turbo://stream.netflyapp.com/live/eurosport1/master.m3u8?id=4008", "International Sports", 8),
            // === OSN Cinema & Entertainment ===
            Channel(5001, "OSN Movies Premiere HD", "turbo://stream.netflyapp.com/live/osn_premiere/master.m3u8?id=5001", "OSN Cinema & Entertainment", 1),
            Channel(5002, "OSN Movies Action HD", "turbo://stream.netflyapp.com/live/osn_action/master.m3u8?id=5002", "OSN Cinema & Entertainment", 2),
            Channel(5003, "OSN Showcase HD", "turbo://stream.netflyapp.com/live/osn_showcase/master.m3u8?id=5003", "OSN Cinema & Entertainment", 3),
            Channel(5004, "OSN Comedy HD", "turbo://stream.netflyapp.com/live/osn_comedy/master.m3u8?id=5004", "OSN Cinema & Entertainment", 4),
            Channel(5005, "HBO HD", "turbo://stream.netflyapp.com/live/hbo/master.m3u8?id=5005", "OSN Cinema & Entertainment", 5),
            Channel(5006, "Rotana Cinema HD", "turbo://stream.netflyapp.com/live/rotana_cinema/master.m3u8?id=5006", "OSN Cinema & Entertainment", 6),
            Channel(5007, "Rotana Classic", "turbo://stream.netflyapp.com/live/rotana_classic/master.m3u8?id=5007", "OSN Cinema & Entertainment", 7),
            Channel(5008, "Rotana Drama HD", "turbo://stream.netflyapp.com/live/rotana_drama/master.m3u8?id=5008", "OSN Cinema & Entertainment", 8),
            // === MBC Network & Arabic TV ===
            Channel(6001, "MBC 1 HD", "turbo://stream.netflyapp.com/live/mbc1/master.m3u8?id=6001", "MBC Network & Arabic TV", 1),
            Channel(6002, "MBC 2 HD", "turbo://stream.netflyapp.com/live/mbc2/master.m3u8?id=6002", "MBC Network & Arabic TV", 2),
            Channel(6003, "MBC 4 HD", "turbo://stream.netflyapp.com/live/mbc4/master.m3u8?id=6003", "MBC Network & Arabic TV", 3),
            Channel(6004, "MBC Action HD", "turbo://stream.netflyapp.com/live/mbc_action/master.m3u8?id=6004", "MBC Network & Arabic TV", 4),
            Channel(6005, "MBC Max HD", "turbo://stream.netflyapp.com/live/mbc_max/master.m3u8?id=6005", "MBC Network & Arabic TV", 5),
            Channel(6006, "Al Jazeera Arabic HD", "turbo://stream.netflyapp.com/live/aljazeera/master.m3u8?id=6006", "MBC Network & Arabic TV", 6),
            Channel(6007, "Al Arabiya HD", "turbo://stream.netflyapp.com/live/alarabiya/master.m3u8?id=6007", "MBC Network & Arabic TV", 7),
            // === Kids & Documentary ===
            Channel(7001, "Spacetoon HD", "turbo://stream.netflyapp.com/live/spacetoon/master.m3u8?id=7001", "Kids & Documentary", 1),
            Channel(7002, "MBC 3 HD", "turbo://stream.netflyapp.com/live/mbc3/master.m3u8?id=7002", "Kids & Documentary", 2),
            Channel(7003, "National Geographic Abu Dhabi", "turbo://stream.netflyapp.com/live/natgeo/master.m3u8?id=7003", "Kids & Documentary", 3),
            Channel(7004, "Al Jazeera Documentary HD", "turbo://stream.netflyapp.com/live/jazeera_doc/master.m3u8?id=7004", "Kids & Documentary", 4),
        )
    ))
}
