package com.aura.ai.services.orchestrator

class FallbackChain {
    suspend fun executeWithFallback(primary: suspend () -> String, vararg fallbacks: suspend () -> String): String {
        try { return primary() } catch (e1: Exception) {
            for (fb in fallbacks) { try { return fb() } catch (e2: Exception) { continue } }
            throw Exception("All failed")
        }
    }
}
