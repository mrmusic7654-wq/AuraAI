package com.aura.ai.services.orchestrator

class FallbackChain(private val metrics: AuraMetrics) {
    
    data class FallbackResult(
        val success: Boolean,
        val methodUsed: String,
        val result: String,
        val attemptsUsed: Int
    )
    
    suspend fun executeWithFallback(
        primary: suspend () -> String,
        vararg fallbacks: suspend () -> String
    ): String {
        val methods = mutableListOf<suspend () -> String>()
        methods.add(primary)
        methods.addAll(fallbacks)
        return executeChain(methods, "default")
    }
    
    suspend fun executeWithNamedFallbacks(
        primary: Pair<String, suspend () -> String>,
        vararg fallbacks: Pair<String, suspend () -> String>
    ): FallbackResult {
        val allMethods = mutableListOf<Pair<String, suspend () -> String>>()
        allMethods.add(primary)
        allMethods.addAll(fallbacks)
        
        for ((index, method) in allMethods.withIndex()) {
            try {
                val result = method.second()
                metrics.logFallbackSuccess(method.first, index + 1)
                return FallbackResult(true, method.first, result, index + 1)
            } catch (e: Exception) {
                metrics.logFallbackFailure(method.first, e.message ?: "Unknown")
                if (index == allMethods.size - 1) {
                    throw FallbackExhaustedException("All ${allMethods.size} methods failed. Last: ${e.message}")
                }
            }
        }
        throw FallbackExhaustedException("No methods to execute")
    }
    
    private suspend fun executeChain(
        methods: List<suspend () -> String>,
        chainName: String
    ): String {
        for ((index, method) in methods.withIndex()) {
            try {
                return method()
            } catch (e: Exception) {
                if (index == methods.size - 1) {
                    throw FallbackExhaustedException("All ${methods.size} methods in '$chainName' failed")
                }
            }
        }
        throw FallbackExhaustedException("No methods in chain")
    }
    
    // Pre-built fallback chains
    suspend fun tapElement(
        text: String,
        x: Float? = null,
        y: Float? = null,
        actionExecutor: ActionExecutor
    ): String {
        return executeWithFallback(
            { actionExecutor.executeAction("tap", mapOf("text" to text)) },
            { actionExecutor.executeAction("tap", mapOf("text" to text.lowercase())) },
            { actionExecutor.executeAction("tap", mapOf("text" to text.uppercase())) },
            { 
                if (x != null && y != null) {
                    actionExecutor.executeAction("tap", mapOf("x" to x.toString(), "y" to y.toString()))
                } else throw Exception("No coordinates for tap fallback")
            }
        )
    }
    
    suspend fun openApp(
        packageName: String,
        searchName: String? = null,
        actionExecutor: ActionExecutor
    ): String {
        return executeWithFallback(
            { actionExecutor.executeAction("open_app", mapOf("package" to packageName)) },
            { 
                if (searchName != null) {
                    actionExecutor.executeAction("open_app", mapOf("package" to searchName, "fallbackSearch" to "true"))
                } else throw Exception("No search name")
            },
            { 
                val resolved = com.aura.ai.services.AppController.resolve(packageName.substringAfterLast("."))
                if (resolved != null && resolved != packageName) {
                    actionExecutor.executeAction("open_app", mapOf("package" to resolved))
                } else throw Exception("Cannot resolve package")
            }
        )
    }
}

class FallbackExhaustedException(message: String) : Exception(message)
