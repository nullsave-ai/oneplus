package com.oneplus.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "img") data class Img(@PrimaryKey val url: String, val bytes: Long, val used: Long)
@Entity(tableName = "saved") data class Saved(@PrimaryKey val id: Int, val at: Long)
@Entity(tableName = "progress") data class Progress(@PrimaryKey val id: Int, val pos: Long, val dur: Long, val at: Long)
@Entity(tableName = "page") data class Page(@PrimaryKey val name: String, val body: String)

@Dao
interface Store {
    @Query("SELECT * FROM img WHERE url = :url") fun img(url: String): Img?
    @Upsert fun put(i: Img)
    @Query("UPDATE img SET used = :t WHERE url = :url") fun touch(url: String, t: Long)
    @Query("SELECT COALESCE(SUM(bytes), 0) FROM img") fun total(): Long
    @Query("SELECT * FROM img ORDER BY used LIMIT 16") fun oldest(): List<Img>
    @Query("DELETE FROM img WHERE url IN (:urls)") fun drop(urls: List<String>)

    @Query("SELECT * FROM saved ORDER BY at DESC") fun saved(): List<Saved>
    @Upsert fun save(s: Saved)
    @Query("DELETE FROM saved WHERE id = :id") fun unsave(id: Int)

    @Query("SELECT * FROM progress ORDER BY at DESC LIMIT 20") fun progress(): List<Progress>
    @Upsert fun mark(p: Progress)
    @Query("DELETE FROM progress WHERE id = :id") fun unmark(id: Int)
    @Query("DELETE FROM progress") fun clearProgress()
    @Query("DELETE FROM progress WHERE id NOT IN (SELECT id FROM progress ORDER BY at DESC LIMIT 20)") fun trimProgress()

    @Query("SELECT body FROM page WHERE name = :name") fun page(name: String): String?
    @Upsert fun putPage(p: Page)
}

@Database(entities = [Img::class, Saved::class, Progress::class, Page::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() { abstract fun store(): Store }
