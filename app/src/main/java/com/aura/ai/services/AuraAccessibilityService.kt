package com.aura.ai.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.ai.data.models.Bounds
import com.aura.ai.data.models.ScreenContext
import com.aura.ai.data.models.UIElement
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AuraAccessibilityService : AccessibilityService() {
    
    @Inject
    lateinit var screenStateManager: ScreenStateManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _screenState = MutableStateFlow<ScreenContext?>(null)
    val screenState: StateFlow<ScreenContext?> = _screenState.asStateFlow()
    
    private val actionChannel = Channel<AccessibilityAction>(Channel.UNLIMITED)
    
    override fun onCreate() {
        super.onCreate()
        Timber.d("Accessibility Service Created")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.d("Accessibility Service Connected")
        
        serviceScope.launch {
            for (action in actionChannel) {
                executeAction(action)
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                captureCurrentScreen()
            }
        }
    }
    
    override fun onInterrupt() {
        Timber.w("Accessibility Service Interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("Accessibility Service Destroyed")
    }
    
    fun captureCurrentScreen(): ScreenContext? {
        return try {
            val root = rootInActiveWindow ?: return null
            
            val elements = mutableListOf<UIElement>()
            traverseNodeTree(root, elements)
            
            val displayMetrics = resources.displayMetrics
            
            val context = ScreenContext(
                packageName = root.packageName?.toString() ?: "unknown",
                activityName = getCurrentActivityName(root),
                elements = elements,
                screenWidth = displayMetrics.widthPixels,
                screenHeight = displayMetrics.heightPixels
            )
            
            _screenState.value = context
            screenStateManager.updateScreen(context)
            
            root.recycle()
            context
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture screen")
            null
        }
    }
    
    private fun traverseNodeTree(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            val element = UIElement(
                id = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                className = node.className?.toString() ?: "unknown",
                isClickable = node.isClickable,
                isEditable = node.isEditable,
                isVisible = node.isVisibleToUser,
                bounds = Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                childCount = node.childCount
            )
            
            elements.add(element)
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    traverseNodeTree(child, elements)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error traversing node tree")
        }
    }
    
    private fun getCurrentActivityName(node: AccessibilityNodeInfo): String {
        return try {
            node.packageName?.toString() ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    
    suspend fun submitAction(action: AccessibilityAction): Boolean {
        return try {
            actionChannel.send(action)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to submit action")
            false
        }
    }
    
    private suspend fun executeAction(action: AccessibilityAction): Boolean {
        return when (action) {
            is AccessibilityAction.Tap -> performTap(action.x, action.y)
            is AccessibilityAction.Swipe -> performSwipe(
                action.startX, action.startY, 
                action.endX, action.endY, 
                action.duration
            )
            is AccessibilityAction.Type -> performType(action.text)
            is AccessibilityAction.GlobalAction -> performGlobalAction(action.action)
            is AccessibilityAction.FindAndTap -> findAndTap(action.text, action.className)
            is AccessibilityAction.OpenApp -> openApp(action.packageName)
            is AccessibilityAction.Wait -> {
                delay(action.duration)
                true
            }
        }
    }
    
    private fun performTap(x: Float, y: Float): Boolean {
        return try {
            val path = Path().apply {
                moveTo(x, y)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to perform tap")
            false
        }
    }
    
    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        return try {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to perform swipe")
            false
        }
    }
    
    private fun performType(text: String): Boolean {
        return try {
            val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) 
                ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            
            if (focusedNode != null) {
                val arguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, 
                        text
                    )
                }
                val result = focusedNode.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT, 
                    arguments
                )
                focusedNode.recycle()
                result
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to type text")
            false
        }
    }
    
    private fun performGlobalAction(action: Int): Boolean {
        return try {
            performGlobalAction(action)
        } catch (e: Exception) {
            Timber.e(e, "Failed to perform global action")
            false
        }
    }
    
    private fun findAndTap(text: String, className: String?): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            
            val targetNode = findNodeByText(root, text, className)
            if (targetNode != null) {
                val rect = Rect()
                targetNode.getBoundsInScreen(rect)
                val result = performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
                targetNode.recycle()
                root.recycle()
                result
            } else {
                root.recycle()
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to find and tap")
            false
        }
    }
    
    private fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open app")
            false
        }
    }
    
    private fun findNodeByText(
        node: AccessibilityNodeInfo, 
        text: String, 
        className: String?
    ): AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true) {
            if (className == null || node.className?.contains(className) == true) {
                return node
            }
        }
        
        if (node.contentDescription?.contains(text, ignoreCase = true) == true) {
            if (className == null || node.className?.contains(className) == true) {
                return node
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text, className)
            if (found != null) return found
        }
        
        return null
    }
}

sealed class AccessibilityAction {
    data class Tap(val x: Float, val y: Float) : AccessibilityAction()
    data class Swipe(
        val startX: Float, 
        val startY: Float, 
        val endX: Float, 
        val endY: Float, 
        val duration: Long = 300
    ) : AccessibilityAction()
    data class Type(val text: String) : AccessibilityAction()
    data class GlobalAction(val action: Int) : AccessibilityAction()
    data class FindAndTap(val text: String, val className: String? = null) : AccessibilityAction()
    data class OpenApp(val packageName: String) : AccessibilityAction()
    data class Wait(val duration: Long) : AccessibilityAction()
}
