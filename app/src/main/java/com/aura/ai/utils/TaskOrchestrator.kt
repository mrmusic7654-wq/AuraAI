package com.aura.ai.utils

import android.content.Context
import com.aura.ai.services.AuraAccessibilityService
import com.aura.ai.services.FloatingMonitorService
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

// ===== DATA MODELS =====
data class TaskSection(
    val id: Int, val title: String, val steps: List<TaskStep>,
    val status: SectionStatus = SectionStatus.PENDING
)
data class TaskStep(
    val id: Int, val action: String, val target: String = "",
    val value: String = "", val status: StepStatus = StepStatus.PENDING
)
enum class SectionStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }
enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED, UNCERTAIN }

data class OrchestratorState(
    val sections: List<TaskSection> = emptyList(), val currentSection: Int = 0,
    val currentStep: Int = 0, val isRunning: Boolean = false, val isPaused: Boolean = false,
    val totalSteps: Int = 0, val completedSteps: Int = 0, val log: List<String> = emptyList(),
    val templateName: String = ""
)

object TaskOrchestrator {

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()
    private var job: Job? = null
    private var isCancelled = false
    private var isPaused = false
    private var accessibility: AuraAccessibilityService? = null
    private var context: Context? = null
    private var geminiKey: String = ""

    fun init(ctx: Context, key: String) {
        context = ctx; geminiKey = key
        accessibility = AuraAccessibilityService.instance
    }

    suspend fun planTask(userCommand: String): Result<List<TaskSection>> {
        if (geminiKey.isBlank()) return Result.failure(Exception("No Gemini API key set."))
        val model = GenerativeModel("gemini-2.5-flash", geminiKey, generationConfig { temperature = 0.2f; maxOutputTokens = 4096 })
        val prompt = """
            Break down this phone automation request into logical task sections with numbered steps.
            REQUEST: "$userCommand"
            Available actions per step:
            - OPEN_APP: target = app name
            - TAP: target = exact text on screen to tap
            - TAP_COORD: target = "x,y" coordinates
            - TYPE: target = text to type
            - SCROLL: target = "up" or "down"
            - READ: reads all visible text on screen
            - WAIT: value = milliseconds to wait
            - CHECK: target = text to look for, value = timeout in ms
            - LOOP_START: value = number of iterations
            - LOOP_END: marks end of loop
            Return ONLY valid JSON: {"sections":[{"title":"Section","steps":[{"action":"OPEN_APP","target":"app"},{"action":"WAIT","value":"2000"}]}]}
            Max 50 steps, max 8 sections.
        """.trimIndent()
        return try {
            val response = model.generateContent(content { text(prompt) }).text ?: return Result.failure(Exception("Empty"))
            val jsonStr = response.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
            val json = JSONObject(jsonStr); val arr = json.getJSONArray("sections")
            val sections = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i); val stepsArr = s.getJSONArray("steps")
                val steps = (0 until stepsArr.length()).map { j ->
                    val step = stepsArr.getJSONObject(j)
                    TaskStep(id = j, action = step.optString("action", "WAIT"), target = step.optString("target", ""), value = step.optString("value", "1000"))
                }
                TaskSection(id = i, title = s.getString("title"), steps = steps)
            }
            _state.value = OrchestratorState(sections = sections, totalSteps = sections.sumOf { it.steps.size })
            Result.success(sections)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun loadTemplate(template: TaskTemplate) {
        _state.value = OrchestratorState(sections = template.sections, totalSteps = template.sections.sumOf { it.steps.size }, templateName = template.name)
    }

    fun execute(onProgress: (String) -> Unit, onComplete: (String) -> Unit = {}) {
        if (_state.value.sections.isEmpty()) { onProgress("❌ No task planned."); return }
        job = CoroutineScope(Dispatchers.IO).launch {
            isCancelled = false; isPaused = false
            val sections = _state.value.sections.toMutableList()
            var successCount = 0; var failCount = 0; var uncertainCount = 0

            sections.forEach { section -> FloatingMonitorService.updateSectionStatus(section.title, "⏳") }

            for ((si, section) in sections.withIndex()) {
                if (isCancelled) break; while (isPaused) { delay(100) }
                sections[si] = section.copy(status = SectionStatus.IN_PROGRESS)
                _state.value = _state.value.copy(sections = sections, currentSection = si)
                FloatingMonitorService.updateSectionStatus(section.title, "🔄")
                FloatingMonitorService.updateTask(section.title, _state.value.completedSteps, _state.value.totalSteps, si + 1, sections.size)
                onProgress("━━━ 📋 ${section.title} ━━━")
                val steps = section.steps.toMutableList()

                for ((ti, step) in steps.withIndex()) {
                    if (isCancelled) break; while (isPaused) { delay(100) }
                    steps[ti] = step.copy(status = StepStatus.IN_PROGRESS)
                    _state.value = _state.value.copy(sections = sections, currentStep = ti)
                    try {
                        val result = executeStep(step)
                        steps[ti] = step.copy(status = result)
                        when (result) { StepStatus.COMPLETED -> successCount++; StepStatus.FAILED -> failCount++; StepStatus.UNCERTAIN -> uncertainCount++; else -> {} }
                        _state.value = _state.value.copy(sections = sections, completedSteps = _state.value.completedSteps + (if (result == StepStatus.COMPLETED) 1 else 0))
                        FloatingMonitorService.updateTask(section.title, _state.value.completedSteps, _state.value.totalSteps, si + 1, sections.size)
                        onProgress("  ${emoji(result)} ${step.action}: ${step.target.ifBlank { step.value }}")
                    } catch (e: Exception) { steps[ti] = step.copy(status = StepStatus.FAILED); failCount++; onProgress("  ❌ ${step.action}: ${e.message}") }
                    delay(500)
                }
                val sectionStatus = if (steps.all { it.status == StepStatus.COMPLETED }) SectionStatus.COMPLETED else if (steps.any { it.status == StepStatus.FAILED }) SectionStatus.FAILED else SectionStatus.COMPLETED
                sections[si] = section.copy(status = sectionStatus, steps = steps)
                _state.value = _state.value.copy(sections = sections)
                FloatingMonitorService.updateSectionStatus(section.title, if (sectionStatus == SectionStatus.COMPLETED) "✅" else "❌")
            }
            _state.value = _state.value.copy(isRunning = false)
            val summary = "✅ $successCount | ❌ $failCount | ❓ $uncertainCount | Total: ${_state.value.totalSteps}"
            onProgress("\n🏁 Complete! $summary")

            if (successCount > 0 && _state.value.templateName.isBlank() && context != null && sections.isNotEmpty()) {
                val name = sections.firstOrNull()?.title?.take(30) ?: "Task"
                val triggers = TemplateStorage.autoGenerateTriggers(sections)
                TemplateStorage.saveTemplate(context!!, name, triggers, sections)
                _state.value = _state.value.copy(templateName = name)
                onProgress("💾 Template auto-saved as '$name'")
            }
            FloatingMonitorService.updateTask("Complete", _state.value.totalSteps, _state.value.totalSteps, sections.size, sections.size)
            onComplete(summary)
        }
        _state.value = _state.value.copy(isRunning = true)
    }

    fun pause() { isPaused = true; _state.value = _state.value.copy(isPaused = true) }
    fun resume() { isPaused = false; _state.value = _state.value.copy(isPaused = false) }
    fun cancel() { isCancelled = true; job?.cancel(); _state.value = _state.value.copy(isRunning = false) }

    private suspend fun executeStep(step: TaskStep): StepStatus {
        val service = accessibility ?: return StepStatus.FAILED
        val ctx = context ?: return StepStatus.FAILED
        return when (step.action) {
            "OPEN_APP" -> {
                val pkg = resolvePackage(step.target)
                if (pkg != null) {
                    val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent); delay(step.value.toLongOrNull() ?: 2000); StepStatus.COMPLETED }
                    else StepStatus.FAILED
                } else StepStatus.UNCERTAIN
            }
            "TAP" -> {
                val root = service.rootInActiveWindow
                if (root != null) {
                    val node = findNodeByText(root, step.target)
                    if (node != null) {
                        val rect = android.graphics.Rect(); node.getBoundsInScreen(rect)
                        root.recycle(); node.recycle()
                        val path = android.graphics.Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
                        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)).build()
                        if (service.dispatchGesture(gesture, null, null)) StepStatus.COMPLETED else StepStatus.FAILED
                    } else { root.recycle(); StepStatus.UNCERTAIN }
                } else StepStatus.FAILED
            }
            "TAP_COORD" -> {
                val parts = step.target.split(",")
                if (parts.size == 2) {
                    val x = parts[0].trim().toFloatOrNull(); val y = parts[1].trim().toFloatOrNull()
                    if (x != null && y != null) {
                        val path = android.graphics.Path().apply { moveTo(x, y) }
                        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)).build()
                        if (service.dispatchGesture(gesture, null, null)) StepStatus.COMPLETED else StepStatus.FAILED
                    } else StepStatus.FAILED
                } else StepStatus.FAILED
            }
            "TYPE" -> {
                val focused = service.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null) {
                    val args = android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.target) }
                    val result = focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    focused.recycle()
                    if (result) StepStatus.COMPLETED else StepStatus.FAILED
                } else StepStatus.FAILED
            }
            "SCROLL" -> {
                val display = service.resources.displayMetrics
                if (step.target == "down") {
                    val path = android.graphics.Path().apply { moveTo(display.widthPixels / 2f, display.heightPixels * 0.8f); lineTo(display.widthPixels / 2f, display.heightPixels * 0.3f) }
                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build()
                    if (service.dispatchGesture(gesture, null, null)) StepStatus.COMPLETED else StepStatus.FAILED
                } else if (step.target == "up") {
                    val path = android.graphics.Path().apply { moveTo(display.widthPixels / 2f, display.heightPixels * 0.3f); lineTo(display.widthPixels / 2f, display.heightPixels * 0.8f) }
                    val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)).build()
                    if (service.dispatchGesture(gesture, null, null)) StepStatus.COMPLETED else StepStatus.FAILED
                } else StepStatus.FAILED
            }
            "READ" -> {
                val screen = service.captureCurrentScreen()
                if (screen != null) { _state.value = _state.value.copy(log = _state.value.log + "READ: ${screen.elements.joinToString(" ") { it.text ?: "" }.take(1000)}"); StepStatus.COMPLETED }
                else StepStatus.FAILED
            }
            "WAIT" -> { delay(step.value.toLongOrNull() ?: 1000); StepStatus.COMPLETED }
            "CHECK" -> {
                val timeout = step.value.toLongOrNull() ?: 5000; val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeout) {
                    val screen = service.captureCurrentScreen()
                    if (screen?.elements?.any { it.text?.contains(step.target, true) == true || it.contentDescription?.contains(step.target, true) == true } == true) return StepStatus.COMPLETED
                    delay(500)
                }; StepStatus.UNCERTAIN
            }
            "LOOP_START", "LOOP_END" -> StepStatus.COMPLETED
            else -> StepStatus.SKIPPED
        }
    }

    private fun findNodeByText(node: android.view.accessibility.AccessibilityNodeInfo, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.text?.contains(text, true) == true || node.contentDescription?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) { node.getChild(i)?.let { findNodeByText(it, text) }?.let { return it } }
        return null
    }

    private fun resolvePackage(name: String): String? = when (name.lowercase()) {
        "whatsapp" -> "com.whatsapp"; "youtube" -> "com.google.android.youtube"; "chrome" -> "com.android.chrome"; "settings" -> "com.android.settings"; "camera" -> "com.android.camera"; "gallery","photos" -> "com.google.android.apps.photos"; "gmail" -> "com.google.android.gm"; "maps" -> "com.google.android.apps.maps"; "play store" -> "com.android.vending"; "calculator" -> "com.android.calculator2"; "calendar" -> "com.android.calendar"; "clock" -> "com.android.deskclock"; "files" -> "com.android.documentsui"; "phone" -> "com.android.dialer"; "messages" -> "com.google.android.apps.messaging"; "instagram" -> "com.instagram.android"; "facebook" -> "com.facebook.katana"; "twitter","x" -> "com.twitter.android"; "spotify" -> "com.spotify.music"; "netflix" -> "com.netflix.mediaclient"; "telegram" -> "org.telegram.messenger"; "chatgpt" -> "com.openai.chatgpt"; "meta ai" -> "com.facebook.meta_ai"; "copilot" -> "com.microsoft.copilot"; "notes" -> "com.google.android.apps.docs"; else -> null
    }

    private fun emoji(s: StepStatus) = when (s) { StepStatus.COMPLETED -> "✅"; StepStatus.FAILED -> "❌"; StepStatus.UNCERTAIN -> "❓"; StepStatus.SKIPPED -> "⏭️"; StepStatus.IN_PROGRESS -> "🔄"; StepStatus.PENDING -> "⏳" }
}
