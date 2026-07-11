package com.example.ytdownloader

import android.app.Application
import com.example.ytdownloader.di.AppContainer

class YTDLApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
