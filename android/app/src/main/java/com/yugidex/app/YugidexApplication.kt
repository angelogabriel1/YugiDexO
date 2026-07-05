package com.yugidex.app

import android.app.Application
import com.yugidex.app.data.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YugidexApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() { super.onCreate(); container = AppContainer(this) }
}

class AppContainer(application: Application) {
    private val http = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE })
        .build()
    private fun retrofit(url: String) = Retrofit.Builder().baseUrl(url).client(http).addConverterFactory(GsonConverterFactory.create()).build()
    val database = InventoryDatabase.create(application)
    val backend: YugidexApi = retrofit(BuildConfig.API_BASE_URL).create(YugidexApi::class.java)
    val repository = CardRepository(retrofit("https://db.ygoprodeck.com/api/v7/").create(YgoApi::class.java), backend, database.inventory())
}

