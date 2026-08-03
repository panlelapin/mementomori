package com.github.panlelapin.mementomori

import android.app.Application
import android.content.res.Configuration

/** Refreshes active widgets when this process receives a system configuration change. */
class MementoMoriApplication : Application() {
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshAllWidgets(this)
    }
}
