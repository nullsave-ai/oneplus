package com.oneplus.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import com.oneplus.app.data.ApiUrl
import com.oneplus.app.data.Db
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.Library
import com.oneplus.app.data.Prefs
import com.oneplus.app.data.RemoteRepository
import com.oneplus.app.data.SampleRepository
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks().penaltyLog().build())
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs: Prefs by lazy { Prefs(this, scope) }
    val db: Db by lazy { Room.databaseBuilder(this, Db::class.java, "app.db").fallbackToDestructiveMigration().build() }
    val repository: HomeRepository by lazy { if (ApiUrl.isBlank()) SampleRepository() else RemoteRepository(ApiUrl, db.store()) }
    val library: Library by lazy { Library(db.store(), scope, prefs) }
}
