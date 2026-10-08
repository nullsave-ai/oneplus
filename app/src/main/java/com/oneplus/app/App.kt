package com.oneplus.app

import android.app.Application
import android.content.Context
import com.oneplus.app.data.ApiUrl
import com.oneplus.app.data.EmptyRepository
import com.oneplus.app.data.HomeRepository
import com.oneplus.app.data.Library
import com.oneplus.app.data.RemoteRepository
import com.oneplus.app.data.local.AppDatabase

class App : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: HomeRepository by lazy {
        if (ApiUrl.isBlank()) EmptyRepository()
        else RemoteRepository(ApiUrl, database)
    }
    val library: Library by lazy { Library(getSharedPreferences("library", Context.MODE_PRIVATE)) }
}
