# defaults
-keep class com.oneplus.app.data.** { *; }
-keep class com.oneplus.app.player.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
