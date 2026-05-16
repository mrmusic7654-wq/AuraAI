package com.aura.ai.utils.logger

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter @Inject constructor() {
    
    fun reportException(throwable: Throwable) {
        Timber.e(throwable, "Crash reported")
        // In production, report to Firebase Crashlytics or similar
    }
    
    fun log(message: String) {
        Timber.d(message)
    }
    
    fun setUserIdentifier(userId: String) {
        // Set user ID for crash reporting
    }
}
