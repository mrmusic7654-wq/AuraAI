package com.aura.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AuraApplication : Application() {
    companion object {
        lateinit var instance: AuraApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
