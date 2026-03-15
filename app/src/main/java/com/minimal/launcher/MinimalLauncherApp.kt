package com.minimal.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MinimalLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Force night mode so AppCompat dialogs use dark surfaces automatically.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
