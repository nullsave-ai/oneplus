package com.oneplus.app.data.local

import android.content.Context
import androidx.room.*

@Entity(tableName = "data_cache")
data class DataCacheEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val url: String,
    val filePath: String,
    val size: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val url: String,
    val groupName: String,
    val number: Int,
    val logo: String
)

@Dao
interface DataCacheDao {
    @Query("SELECT payload FROM data_cache WHERE `key` = :key LIMIT 1")
    suspend fun getPayload(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: DataCacheEntity)
}

@Dao
interface ImageDao {
    @Query("SELECT * FROM images WHERE url = :url LIMIT 1")
    suspend fun find(url: String): ImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ImageEntity)

    @Query("UPDATE images SET updatedAt = :now WHERE url = :url")
    suspend fun touch(url: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM images WHERE url NOT IN (SELECT url FROM images ORDER BY updatedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("SELECT filePath FROM images")
    suspend fun allPaths(): List<String>
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY number ASC")
    suspend fun getAll(): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(channels: List<ChannelEntity>) {
        clear()
        insertAll(channels)
    }
}

@Database(
    entities = [DataCacheEntity::class, ImageEntity::class, ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dataCache(): DataCacheDao
    abstract fun images(): ImageDao
    abstract fun channels(): ChannelDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "oneplus.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
            }
    }
}
