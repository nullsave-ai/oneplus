package com.oneplus.app

import android.app.Application
import android.content.Context
import com.oneplus.app.data.ApiUrl
import com.oneplus.app.data.EmptyRepository
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.Library
import com.oneplus.app.data.RemoteRepository
import java.io.File

class App : Application() {
    val repository: HomeRepository by lazy {
        if (ApiUrl.isBlank()) EmptyRepository()
        else RemoteRepository(ApiUrl, File(cacheDir, "home.json"))
    }
    val library: Library by lazy { Library(getSharedPreferences("library", Context.MODE_PRIVATE)) }
}
