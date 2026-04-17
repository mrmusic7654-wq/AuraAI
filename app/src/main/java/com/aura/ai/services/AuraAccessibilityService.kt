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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class AuraAccessibilityService : AccessibilityService() {
    
    @Inject
    lateinit var screenStateManager: ScreenStateManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _screenState = MutableStateFlow<ScreenContext?>(null)
    val screenState: StateFlow<ScreenContext?> = _screenState.asStateFlow()
    
    private val actionChannel = Channel<AccessibilityAction>(Channel.BUFFERED)
    
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
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                captureCurrentScreen()
            }
        }
    }
    
    override fun onInterrupt() {
        Timber.w("Accessibility Service Interrupted")
    }
    
    fun captureCurrentScreen(): ScreenContext? {
        val root = rootInActiveWindow ?: return null
        
        val elements = mutableListOf<UIElement>()
        traverseNodeTree(root, elements)
        
        val context = ScreenContext(
            packageName = root.packageName?.toString() ?: "unknown",
            activityName = getCurrentActivityName(root),
            elements = elements
        )
        
        _screenState.value = context
        root.recycle()
        
        return context
    }
    
    private fun traverseNodeTree(node: AccessibilityNodeInfo, elements: MutableList<UIElement>) {
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
    }
    
    private fun getCurrentActivityName(node: AccessibilityNodeInfo): String {
        // Extract activity name from package context
        return node.packageName?.toString() ?: "unknown"
    }
    
    fun submitAction(action: AccessibilityAction) {
        serviceScope.launch {
            actionChannel.send(action)
        }
    }
    
    private fun executeAction(action: AccessibilityAction) {
        when (action) {
            is AccessibilityAction.Tap -> performTap(action.x, action.y)
            is AccessibilityAction.Swipe -> performSwipe(action.startX, action.startY, action.endX, action.endY, action.duration)
            is AccessibilityAction.Type -> performType(action.text)
            is AccessibilityAction.GlobalAction -> performGlobalAction(action.action)
            is AccessibilityAction.FindAndTap -> findAndTap(action.text, action.className)
        }
    }
    
    private fun performTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    private fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
    
    private fun performType(text: String) {
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val focusedNode = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) 
            ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        
        focusedNode?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        focusedNode?.recycle()
    }
    
    private fun performGlobalAction(action: Int): Boolean {
        return performGlobalAction(action)
    }
    
    private fun findAndTap(text: String, className: String? = null) {
        val root = rootInActiveWindow ?: return
        
        val targetNode = findNodeByText(root, text, className)
        if (targetNode != null) {
            val rect = Rect()
            targetNode.getBoundsInScreen(rect)
            performTap(rect.centerX().toFloat(), rect.centerY().toFloat())
            targetNode.recycle()
        }
        
        root.recycle()
    }
    
    private fun findNodeByText(node: AccessibilityNodeInfo, text: String, className: String?): AccessibilityNodeInfo? {
        if (node.text?.contains(text) == true) {
            if (className == null || node.className == className) {
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
    data class Swipe(val startX: Float, val startY: Float, val endX: Float, val endY: Float, val duration: Long) : AccessibilityAction()
    data class Type(val text: String) : AccessibilityAction()
    data class GlobalAction(val action: Int) : AccessibilityAction()
    data class FindAndTap(val text: String, val className: String? = null) : AccessibilityAction()
}

@Singleton
class ScreenStateManager @Inject constructor() {
    private val _currentScreen = MutableStateFlow<ScreenContext?>(null)
    val currentScreen: StateFlow<ScreenContext?> = _currentScreen.asStateFlow()
    
    fun updateScreen(context: ScreenContext) {
        _currentScreen.value = context
    }
}
