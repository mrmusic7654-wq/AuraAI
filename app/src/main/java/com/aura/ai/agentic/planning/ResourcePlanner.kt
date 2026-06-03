package com.aura.ai.agentic.planning

data class ResourceCheck(
    val canProceed: Boolean,
    val reason: String,
    val batteryLevel: Int = 100,
    val apiCallsRemaining: Int = 1500,
    val storageFreeMB: Long = 500,
    val estimatedApiCallsNeeded: Int = 0,
    val estimatedBatteryDrain: Int = 0
)

class ResourcePlanner {
    
    fun canAct(estimatedApiCalls: Int = 3): ResourceCheck {
        val batteryLevel = getBatteryLevel()
        val apiCallsRemaining = getApiCallsRemaining()
        
        if (batteryLevel < 5) {
            return ResourceCheck(
                canProceed = false,
                reason = "Battery critically low (${batteryLevel}%)",
                batteryLevel = batteryLevel,
                apiCallsRemaining = apiCallsRemaining
            )
        }
        
        if (apiCallsRemaining < estimatedApiCalls) {
            return ResourceCheck(
                canProceed = false,
                reason = "Insufficient API calls (${apiCallsRemaining} remaining, need $estimatedApiCalls)",
                batteryLevel = batteryLevel,
                apiCallsRemaining = apiCallsRemaining
            )
        }
        
        return ResourceCheck(
            canProceed = true,
            reason = "Resources sufficient",
            batteryLevel = batteryLevel,
            apiCallsRemaining = apiCallsRemaining,
            estimatedApiCallsNeeded = estimatedApiCalls
        )
    }
    
    fun getRecommendedBatchSize(): Int {
        val batteryLevel = getBatteryLevel()
        val apiCallsRemaining = getApiCallsRemaining()
        
        return when {
            batteryLevel > 50 && apiCallsRemaining > 500 -> 10
            batteryLevel > 20 && apiCallsRemaining > 200 -> 5
            else -> 2
        }
    }
    
    fun shouldReduceQuality(): Boolean {
        return getBatteryLevel() < 20 || getApiCallsRemaining() < 100
    }
    
    fun getScreenshotInterval(): Long {
        return when {
            getBatteryLevel() < 15 -> 120_000  // 2 min
            getBatteryLevel() < 30 -> 60_000   // 1 min
            getApiCallsRemaining() < 200 -> 45_000
            else -> 30_000  // 30 seconds default
        }
    }
    
    private fun getBatteryLevel(): Int {
        return try {
            val bm = com.aura.ai.AuraApplication.instance
                .getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { 100 }
    }
    
    private fun getApiCallsRemaining(): Int {
        // This should come from the actual usage tracker
        return 1500 // Placeholder
    }
    
    fun getResourceReport(): String {
        val check = canAct()
        return """
📊 RESOURCE REPORT:
🔋 Battery: ${check.batteryLevel}%
📡 API Calls: ${check.apiCallsRemaining} remaining
✅ Can proceed: ${if (check.canProceed) "Yes" else "No - ${check.reason}"}
        """.trimIndent()
    }
}
