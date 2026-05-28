package com.aura.ai.services.orchestrator

import com.aura.ai.services.AppController
import com.aura.ai.services.AuraAccessibilityService

class ActionExecutor(
    private val appController: AppController,
    private val fallbackChain: FallbackChain,
    private val actionVerifier: ActionVerifier
) {
    suspend fun executeAction(action: String, params: Map<String, String>): String {
        return when (action) {
            "open_app" -> openApp(params)
            "tap" -> tap(params)
            "type" -> type(params)
            "swipe" -> swipe(params)
            "read_screen" -> readScreen(params)
            "wait_for" -> waitFor(params)
            "screenshot" -> screenshot(params)
            "api_call" -> apiCall(params)
            "press_back" -> pressBack()
            "press_home" -> pressHome()
            else -> throw IllegalArgumentException("Unknown action: $action")
        }
    }
    
    private suspend fun openApp(params: Map<String, String>): String {
        val packageName = params["package"] ?: throw IllegalArgumentException("Missing package")
        val fallbackSearch = params["fallbackSearch"]?.toBoolean() ?: true
        
        return fallbackChain.executeWithFallback(
            primary = {
                appController.execute(packageName, listOf())
                actionVerifier.verifyAppOpened(packageName)
                "App opened: $packageName"
            },
            fallbacks = if (fallbackSearch) listOf(
                { 
                    val searchPkg = AppController.resolve(packageName.substringAfterLast("."))
                    if (searchPkg != null && searchPkg != packageName) {
                        appController.execute(searchPkg, listOf())
                        actionVerifier.verifyAppOpened(searchPkg)
                        "App opened via search: $searchPkg"
                    } else throw Exception("Not found")
                }
            ) else emptyList()
        )
    }
    
    private suspend fun tap(params: Map<String, String>): String {
        val text = params["text"]
        val x = params["x"]?.toFloatOrNull()
        val y = params["y"]?.toFloatOrNull()
        
        return fallbackChain.executeWithFallback(
            primary = {
                when {
                    text != null -> {
                        appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("tap", text)))
                        actionVerifier.verifyTap(text)
                    }
                    x != null && y != null -> {
                        appController.execute(appController.getCurrentApp(), listOf())
                        actionVerifier.verifyTap("coordinates ($x,$y)")
                    }
                    else -> throw IllegalArgumentException("Need text or coordinates")
                }
                "Tapped successfully"
            },
            fallbacks = listOf(
                { 
                    if (text != null) {
                        appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("tap", text.lowercase())))
                        "Tapped with lowercase"
                    } else throw Exception("No fallback")
                }
            )
        )
    }
    
    private suspend fun type(params: Map<String, String>): String {
        val text = params["text"] ?: throw IllegalArgumentException("Missing text")
        val target = params["target"] ?: "input"
        
        appController.execute(appController.getCurrentApp(), listOf(
            AppController.AppStep("tap", target),
            AppController.AppStep("type", text)
        ))
        
        actionVerifier.verifyTyped(text)
        return "Typed: ${text.take(50)}..."
    }
    
    private suspend fun swipe(params: Map<String, String>): String {
        val direction = params["direction"] ?: "up"
        appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("swipe", direction)))
        return "Swiped $direction"
    }
    
    private suspend fun readScreen(params: Map<String, String>): String {
        return appController.execute(appController.getCurrentApp(), listOf(AppController.AppStep("read")))
    }
    
    private suspend fun waitFor(params: Map<String, String>): String {
        val text = params["text"]
        val app = params["app"]
        val timeout = params["timeout"]?.toLongOrNull() ?: 30000
        
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeout) {
            when {
                text != null -> {
                    val screenText = readScreen(emptyMap())
                    if (screenText.contains(text, ignoreCase = true)) return "Text found: $text"
                }
                app != null -> {
                    if (appController.isAppOpen(app)) return "App opened: $app"
                }
            }
            kotlinx.coroutines.delay(1000)
        }
        throw TimeoutException("Wait timed out after ${timeout}ms")
    }
    
    private suspend fun screenshot(params: Map<String, String>): String {
        val analyze = params["analyze"]
        if (analyze != null) {
            return appController.analyzeScreen(params["apiKey"] ?: "", analyze)
        }
        return "Screenshot captured"
    }
    
    private suspend fun apiCall(params: Map<String, String>): String {
        return "API call placeholder - integrate with existing apiCall method"
    }
    
    private suspend fun pressBack(): String {
        val service = AuraAccessibilityService.instance ?: throw Exception("Accessibility not available")
        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        return "Back pressed"
    }
    
    private suspend fun pressHome(): String {
        val service = AuraAccessibilityService.instance ?: throw Exception("Accessibility not available")
        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        return "Home pressed"
    }
}

class TimeoutException(message: String) : Exception(message)
