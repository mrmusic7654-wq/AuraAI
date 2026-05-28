package com.aura.ai.services.intelligence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Experience(
    val id: Long = 0,
    val action: String,
    val appPackage: String,
    val context: String,
    val result: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class ExperienceDB(context: Context) : SQLiteOpenHelper(context, "aura_experience.db", null, 1) {
    
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS experiences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action TEXT NOT NULL,
                app_package TEXT NOT NULL,
                context TEXT,
                result TEXT,
                success INTEGER DEFAULT 0,
                timestamp INTEGER DEFAULT 0
            )
        """)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_action ON experiences(action, app_package)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_success ON experiences(success)")
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS experiences")
        onCreate(db)
    }
    
    fun recordExperience(action: String, appPackage: String, context: String, result: String, success: Boolean) {
        writableDatabase.execSQL(
            "INSERT INTO experiences (action, app_package, context, result, success, timestamp) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(action, appPackage, context.take(500), result.take(500), if (success) 1 else 0, System.currentTimeMillis())
        )
    }
    
    fun getSimilarExperiences(action: String, appPackage: String, limit: Int = 5): List<Experience> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM experiences WHERE action = ? AND app_package = ? ORDER BY timestamp DESC LIMIT ?",
            arrayOf(action, appPackage, limit.toString())
        )
        val experiences = mutableListOf<Experience>()
        while (cursor.moveToNext()) {
            experiences.add(Experience(
                id = cursor.getLong(0),
                action = cursor.getString(1),
                appPackage = cursor.getString(2),
                context = cursor.getString(3) ?: "",
                result = cursor.getString(4) ?: "",
                success = cursor.getInt(5) == 1,
                timestamp = cursor.getLong(6)
            ))
        }
        cursor.close()
        return experiences
    }
    
    fun getSuccessRate(action: String, appPackage: String): Float {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(success) FROM experiences WHERE action = ? AND app_package = ?",
            arrayOf(action, appPackage)
        )
        var rate = 0.5f
        if (cursor.moveToFirst()) {
            val total = cursor.getInt(0)
            val success = cursor.getInt(1)
            if (total > 0) rate = success.toFloat() / total
        }
        cursor.close()
        return rate
    }
    
    fun shouldAvoid(action: String, appPackage: String): Boolean {
        val rate = getSuccessRate(action, appPackage)
        val total = getTotalCount(action, appPackage)
        return total >= 3 && rate < 0.3f
    }
    
    fun getBestMethod(action: String, appPackage: String): String? {
        val cursor = readableDatabase.rawQuery(
            "SELECT result FROM experiences WHERE action = ? AND app_package = ? AND success = 1 ORDER BY timestamp DESC LIMIT 1",
            arrayOf(action, appPackage)
        )
        val result = if (cursor.moveToFirst()) cursor.getString(0) else null
        cursor.close()
        return result
    }
    
    private fun getTotalCount(action: String, appPackage: String): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM experiences WHERE action = ? AND app_package = ?",
            arrayOf(action, appPackage)
        )
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }
    
    fun cleanup(daysOld: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysOld * 86400000L)
        writableDatabase.execSQL("DELETE FROM experiences WHERE timestamp < ?", arrayOf(cutoff))
    }
}
