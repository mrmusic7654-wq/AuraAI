package com.aura.ai.services.orchestrator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.app.ActivityManager

data class ResourceState(
    val batteryLevel: Int,
    val batteryCharging: Boolean,
    val storageFreeMB: Long,
    val storageTotalMB: Long,
    val memoryUsedMB: Long,
    val memoryTotalMB: Long,
    val networkType: String,
    val isNetworkMetered: Boolean,
    val isPowerSaveMode: Boolean
)

class ResourceManager(private val context: Context) {
    
    fun getCurrentState(): ResourceState {
        val battery = getBatteryInfo()
        val storage = getStorageInfo()
        val memory = getMemoryInfo()
        
        return ResourceState(
            batteryLevel = battery.first,
            batteryCharging = battery.second,
            storageFreeMB = storage.first,
            storageTotalMB = storage.second,
            memoryUsedMB = memory.first,
            memoryTotalMB = memory.second,
            networkType = getNetworkType(),
            isNetworkMetered = isNetworkMetered(),
            isPowerSaveMode = isPowerSaveMode()
        )
    }
    
    fun canRunTask(taskComplexity: String = "normal"): Boolean {
        val state = getCurrentState()
        
        return when (taskComplexity) {
            "critical" -> state.batteryLevel > 20 || state.batteryCharging
            "heavy" -> state.batteryLevel > 30 && state.storageFreeMB > 500
            "normal" -> state.batteryLevel > 10 && state.storageFreeMB > 200
            "light" -> state.batteryLevel > 5
            else -> true
        }
    }
    
    fun shouldReduceQuality(): Boolean {
        val state = getCurrentState()
        return state.batteryLevel < 20 || state.isPowerSaveMode || state.isNetworkMetered
    }
    
    fun getScreenshotInterval(): Long {
        val state = getCurrentState()
        return when {
            state.batteryLevel < 10 -> 120_000 // 2 minutes
            state.batteryLevel < 20 -> 60_000  // 1 minute
            state.isPowerSaveMode -> 90_000    // 1.5 minutes
            state.isNetworkMetered -> 45_000   // 45 seconds
            else -> 30_000                      // 30 seconds (default)
        }
    }
    
    fun shouldPauseTasks(): Boolean {
        val state = getCurrentState()
        return (state.batteryLevel < 5 && !state.batteryCharging) || state.storageFreeMB < 100
    }
    
    fun cleanOldFiles(maxAgeMs: Long = 86400000) { // 24 hours
        val screenshotsDir = java.io.File(context.filesDir, "screenshots")
        if (screenshotsDir.exists()) {
            screenshotsDir.listFiles()?.forEach { file ->
                if (System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
                    file.delete()
                }
            }
        }
    }
    
    fun getResourceReport(): String {
        val state = getCurrentState()
        return """
📊 RESOURCE REPORT:
🔋 Battery: ${state.batteryLevel}% ${if (state.batteryCharging) "⚡" else ""}
💾 Storage: ${state.storageFreeMB}MB / ${state.storageTotalMB}MB free
🧠 Memory: ${state.memoryUsedMB}MB / ${state.memoryTotalMB}MB used
📶 Network: ${state.networkType} ${if (state.isNetworkMetered) "(metered)" else ""}
🔌 Power Save: ${if (state.isPowerSaveMode) "ON" else "OFF"}
        """.trimIndent()
    }
    
    private fun getBatteryInfo(): Pair<Int, Boolean> {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (scale > 0) (level * 100 / scale) else 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(batteryPct, charging)
    }
    
    private fun getStorageInfo(): Pair<Long, Long> {
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
        val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024)
        return Pair(free, total)
    }
    
    private fun getMemoryInfo(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val used = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val total = memInfo.totalMem / (1024 * 1024)
        return Pair(used, total)
    }
    
    private fun getNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            when {
                caps == null -> "None"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
                else -> "Other"
            }
        } catch (e: Exception) { "Unknown" }
    }
    
    private fun isNetworkMetered(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.isActiveNetworkMetered
        } catch (e: Exception) { false }
    }
    
    private fun isPowerSaveMode(): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isPowerSaveMode
        } catch (e: Exception) { false }
    }
}
