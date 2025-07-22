package com.example.livelens

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize MapLibre when the app starts
        MapLibre.getInstance(
            applicationContext,
            "", // No API key needed
            WellKnownTileServer.MapLibre // or MAPLIBRE if you want open demo tiles
        )
    }
}
