package com.aura.ai.data.remote.api

object ApiConstants {
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    const val GITHUB_BASE_URL = "https://api.github.com/"
    
    const val DEFAULT_MODEL = "gemini-2.0-flash-exp"
    const val PRO_MODEL = "gemini-1.5-pro"
    
    const val TIMEOUT_CONNECT = 30L
    const val TIMEOUT_READ = 30L
    const val TIMEOUT_WRITE = 30L
}
