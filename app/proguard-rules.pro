-repackageclasses ''
-allowaccessmodification

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.Dao *;
}

# Keep data models
-keepclassmembers class com.oneplus.app.data.** { *; }

# Media3 ExoPlayer
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**
