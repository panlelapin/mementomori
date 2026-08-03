package com.github.panlelapin.mementomori

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** Refreshes active widgets when this process receives a system configuration change. */
class MementoMoriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAllWidgets(this)
    }
}
