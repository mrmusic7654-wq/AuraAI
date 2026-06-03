package com.aura.ai.agentic.planning

data class DecomposedTask(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String,
    val steps: List<TaskStep>,
    val estimatedTimeMs: Long = 0,
    val complexity: String = "MEDIUM" // LOW, MEDIUM, HIGH
)

data class TaskStep(
    val order: Int,
    val action: String,
    val details: String,
    val dependsOn: List<Int> = emptyList()
)

class TaskDecomposer {
    
    fun decompose(task: String): DecomposedTask {
        return when {
            task.contains("create app", ignoreCase = true) || task.contains("generate app", ignoreCase = true) -> decomposeAppCreation(task)
            task.contains("fix", ignoreCase = true) || task.contains("debug", ignoreCase = true) -> decomposeDebugging(task)
            task.contains("deploy", ignoreCase = true) || task.contains("push", ignoreCase = true) -> decomposeDeployment(task)
            task.contains("monitor", ignoreCase = true) || task.contains("check", ignoreCase = true) -> decomposeMonitoring(task)
            task.contains("update", ignoreCase = true) || task.contains("upgrade", ignoreCase = true) -> decomposeMaintenance(task)
            else -> decomposeGeneric(task)
        }
    }
    
    private fun decomposeAppCreation(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "PLAN", "Analyze requirements and plan architecture"),
                TaskStep(2, "GENERATE", "Generate build files from templates", listOf(1)),
                TaskStep(3, "GENERATE", "Generate source code files", listOf(1)),
                TaskStep(4, "CREATE_REPO", "Create GitHub repository"),
                TaskStep(5, "PUSH", "Push all files to repository", listOf(2, 3, 4)),
                TaskStep(6, "ADD_WORKFLOW", "Add CI/CD workflow", listOf(5)),
                TaskStep(7, "TRIGGER_BUILD", "Trigger GitHub Actions build", listOf(6)),
                TaskStep(8, "MONITOR", "Monitor build progress", listOf(7)),
                TaskStep(9, "FIX", "Fix build errors if any", listOf(8)),
                TaskStep(10, "REPORT", "Report build status", listOf(8, 9))
            ),
            estimatedTimeMs = 300_000,
            complexity = "MEDIUM"
        )
    }
    
    private fun decomposeDebugging(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "READ_LOGS", "Read error logs from failed build"),
                TaskStep(2, "ANALYZE", "Analyze error pattern", listOf(1)),
                TaskStep(3, "SEARCH", "Search for similar past fixes", listOf(2)),
                TaskStep(4, "GENERATE_FIX", "Generate code fix", listOf(2, 3)),
                TaskStep(5, "APPLY", "Apply fix to repository", listOf(4)),
                TaskStep(6, "REBUILD", "Trigger rebuild", listOf(5)),
                TaskStep(7, "VERIFY", "Verify build succeeds", listOf(6)),
                TaskStep(8, "REPORT", "Report fix result", listOf(7))
            ),
            estimatedTimeMs = 120_000,
            complexity = "MEDIUM"
        )
    }
    
    private fun decomposeDeployment(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "VALIDATE", "Validate all files are ready"),
                TaskStep(2, "CREATE_REPO", "Create repository if needed", listOf(1)),
                TaskStep(3, "PUSH", "Push files to GitHub", listOf(1, 2)),
                TaskStep(4, "CONFIGURE", "Add workflow configuration", listOf(3)),
                TaskStep(5, "BUILD", "Trigger build", listOf(4)),
                TaskStep(6, "MONITOR", "Monitor build", listOf(5)),
                TaskStep(7, "REPORT", "Report deployment status", listOf(6))
            ),
            estimatedTimeMs = 180_000,
            complexity = "LOW"
        )
    }
    
    private fun decomposeMonitoring(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "SCAN", "Scan all active repositories"),
                TaskStep(2, "CHECK_BUILDS", "Check build statuses", listOf(1)),
                TaskStep(3, "IDENTIFY_ISSUES", "Identify failing builds", listOf(2)),
                TaskStep(4, "PRIORITIZE", "Prioritize by severity", listOf(3)),
                TaskStep(5, "REPORT", "Generate status report", listOf(4))
            ),
            estimatedTimeMs = 60_000,
            complexity = "LOW"
        )
    }
    
    private fun decomposeMaintenance(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "SCAN", "Scan for outdated dependencies"),
                TaskStep(2, "CHECK_COMPAT", "Check version compatibility", listOf(1)),
                TaskStep(3, "UPDATE", "Update dependencies", listOf(2)),
                TaskStep(4, "BUILD", "Trigger test build", listOf(3)),
                TaskStep(5, "VERIFY", "Verify no breaking changes", listOf(4)),
                TaskStep(6, "COMMIT", "Commit updates", listOf(5)),
                TaskStep(7, "REPORT", "Report update results", listOf(6))
            ),
            estimatedTimeMs = 150_000,
            complexity = "MEDIUM"
        )
    }
    
    private fun decomposeGeneric(task: String): DecomposedTask {
        return DecomposedTask(
            description = task,
            steps = listOf(
                TaskStep(1, "ANALYZE", "Analyze task requirements"),
                TaskStep(2, "EXECUTE", "Execute task", listOf(1)),
                TaskStep(3, "VERIFY", "Verify completion", listOf(2)),
                TaskStep(4, "REPORT", "Report result", listOf(3))
            ),
            estimatedTimeMs = 60_000,
            complexity = "LOW"
        )
    }
}
