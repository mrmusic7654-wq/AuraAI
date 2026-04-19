package com.aura.ai.domain.usecases.settings

import android.content.Context
import com.aura.ai.data.local.dao.AutomationRuleDao
import com.aura.ai.data.local.dao.TaskDao
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val context: Context,
    private val taskDao: TaskDao,
    private val ruleDao: AutomationRuleDao
) {
    
    private val json = Json { prettyPrint = true }
    
    suspend operator fun invoke(): Result<File> {
        return try {
            val tasks = taskDao.getAllTasks()
            val rules = ruleDao.getAllRules()
            
            val exportData = ExportData(
                exportDate = System.currentTimeMillis(),
                tasks = tasks,
                rules = rules
            )
            
            val jsonString = json.encodeToString(exportData)
            
            val file = File(context.cacheDir, "aura_backup_${System.currentTimeMillis()}.json")
            file.writeText(jsonString)
            
            Result.success(file)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@kotlinx.serialization.Serializable
data class ExportData(
    val exportDate: Long,
    val tasks: List<com.aura.ai.data.local.entities.TaskEntity>,
    val rules: List<com.aura.ai.data.local.entities.AutomationRuleEntity>
)
