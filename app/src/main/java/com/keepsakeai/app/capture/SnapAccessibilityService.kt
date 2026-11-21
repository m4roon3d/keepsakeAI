package com.keepsakeai.app.capture

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional, explicitly user-enabled Accessibility Service that captures visible on-screen text
 * on demand when the user taps the Floating Snap Key — eliminating repeated MediaProjection prompts.
 */
class SnapAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive: only reads screen text when explicitly triggered by the user via captureActiveScreenText()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    fun captureActiveScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb, 0)
        return sb.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (node == null || depth > 14) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            sb.appendLine(text)
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb, depth + 1)
        }
    }

    companion object {
        @Volatile
        var instance: SnapAccessibilityService? = null
            private set
    }
}
