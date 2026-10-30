package com.example.localplayer

import android.app.Application
import com.example.localplayer.data.AppGraph
import com.example.localplayer.data.Art
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppGraph.appScope.launch { Art.backfill(this@App) }
    }
}
