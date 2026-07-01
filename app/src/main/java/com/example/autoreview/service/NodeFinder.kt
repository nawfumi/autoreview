@file:Suppress("DEPRECATION")

package com.example.autoreview.service

import android.view.accessibility.AccessibilityNodeInfo
import com.example.autoreview.util.AppLogger

enum class QuestionType { STAR_RATING, YES_NO }

data class DiscoveredQuestion(
    val questionText: String,
    val cardRoot: AccessibilityNodeInfo,
    val type: QuestionType,
    val interactiveNodes: List<AccessibilityNodeInfo> = emptyList()
)

object NodeFinder {

    fun findNodeByDescription(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString() == target) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findNodeByDescription(child, target)
                if (found != null) {
                    if (found !== child) child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    fun findNodeByText(node: AccessibilityNodeInfo, target: String, contains: Boolean = false): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        if (if (contains) text.contains(target, ignoreCase = true) else text.equals(target, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findNodeByText(child, target, contains)
                if (found != null) {
                    if (found !== child) child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }
    
    fun findEnabledSubmit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (node.isEnabled && (text.contains("Submit", ignoreCase = true) || desc.contains("Submit", ignoreCase = true))) {
            return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findEnabledSubmit(child)
                if (found != null) {
                    if (found !== child) child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    fun discoverQuestionCards(root: AccessibilityNodeInfo): List<DiscoveredQuestion> {
        val results = mutableListOf<DiscoveredQuestion>()
        val seenRoots = mutableSetOf<AccessibilityNodeInfo>()
        
        fun walk(node: AccessibilityNodeInfo) {
            if (node in seenRoots) return
            
            val starNodes = findStarLikeChildren(node)
            val yesNo = findYesNoChildren(node)
            val questionText = findLongestTextChild(node)

            if (questionText != null && starNodes.size == 5) {
                AppLogger.d("NodeFinder", "Discovered STAR question: $questionText")
                results.add(DiscoveredQuestion(questionText, node, QuestionType.STAR_RATING, starNodes))
                seenRoots.add(node)
                yesNo?.first?.recycle()
                yesNo?.second?.recycle()
                return 
            } else if (questionText != null && yesNo != null) {
                AppLogger.d("NodeFinder", "Discovered YES/NO question: $questionText")
                results.add(DiscoveredQuestion(questionText, node, QuestionType.YES_NO, listOf(yesNo.first, yesNo.second)))
                seenRoots.add(node)
                starNodes.forEach { it.recycle() }
                return
            }
            
            // Clean up unused allocated collections
            starNodes.forEach { it.recycle() }
            yesNo?.first?.recycle()
            yesNo?.second?.recycle()

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it) }
            }
        }
        walk(root)
        return results
    }

    fun findStarLikeChildren(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val stars = mutableListOf<AccessibilityNodeInfo>()
        val expectedDescriptions = listOf("1", "2", "3", "4", "5")
        
        fun collect(n: AccessibilityNodeInfo) {
            val desc = n.contentDescription?.toString()?.trim()
            if (desc in expectedDescriptions) {
                stars.add(n) // Keep reference, caller must recycle
                return
            }
            // For nested view groups
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { child -> 
                    collect(child)
                    if (child !in stars) {
                        child.recycle()
                    }
                }
            }
        }
        
        collect(node)
        
        // Filter out if not exactly 5 unique star descriptions
        val uniqueDesc = stars.map { it.contentDescription?.toString()?.trim() }.toSet()
        if (uniqueDesc.size == 5 && stars.size == 5) {
            return stars.sortedBy { it.contentDescription?.toString()?.trim() }
        }
        
        // Cleanup if not a valid group
        stars.forEach { it.recycle() }
        return emptyList()
    }

    fun findYesNoChildren(node: AccessibilityNodeInfo): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? {
        var yesNode: AccessibilityNodeInfo? = null
        var noNode: AccessibilityNodeInfo? = null

        fun collect(n: AccessibilityNodeInfo) {
            if (yesNode != null && noNode != null) return
            
            val text = n.text?.toString()?.trim() ?: n.contentDescription?.toString()?.trim()
            if (text.equals("Yes", ignoreCase = true)) {
                yesNode = n
            } else if (text.equals("No", ignoreCase = true)) {
                noNode = n
            } else {
                for (i in 0 until n.childCount) {
                    n.getChild(i)?.let { child -> 
                        collect(child) 
                        if (child !== yesNode && child !== noNode) {
                            child.recycle()
                        }
                    }
                }
            }
        }

        collect(node)
        if (yesNode != null && noNode != null) {
            return Pair(yesNode, noNode)
        }
        
        yesNode?.recycle()
        noNode?.recycle()
        return null
    }

    private fun findLongestTextChild(node: AccessibilityNodeInfo): String? {
        var longestText: String? = null
        
        fun processText(t: String?) {
            val text = t?.trim()
            if (text != null && text.length > 2 && (longestText == null || text.length > longestText!!.length)) {
                longestText = text
            }
        }

        fun collect(n: AccessibilityNodeInfo) {
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrEmpty()) {
                processText(t)
            } else {
                processText(n.contentDescription?.toString())
            }
            
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { child -> 
                    collect(child)
                    child.recycle()
                }
            }
        }
        
        collect(node)
        return longestText
    }

    fun findScrollableContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val found = findScrollableContainer(child)
                if (found != null) {
                    if (found !== child) child.recycle()
                    return found
                }
                child.recycle()
            }
        }
        return null
    }

    fun performScrollForward(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val scrolled = node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        return scrolled
    }

    /**
     * Attempts to perform a click on the given [node] or its parents.
     * Note: This method DOES NOT recycle the original [node]. The caller is responsible for recycling it.
     */
    fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?, service: android.accessibilityservice.AccessibilityService, logTag: String = "NodeFinder"): Boolean {
        if (node == null) {
            AppLogger.e(logTag, "Cannot click: node is null")
            return false
        }
        
        AppLogger.d(logTag, "Attempting to click node with text='${node.text}' desc='${node.contentDescription}' class='${node.className}'")

        // 1. Try standard Accessibility ACTION_CLICK on the node or its parents
        var current = node
        var first = true
        var actionClickSuccess = false
        var depth = 0
        while (current != null && depth < 10) {
            val isClickable = current.isClickable
            AppLogger.d(logTag, "  -> Checking parent depth=$depth: class='${current.className}' isClickable=$isClickable")
            
            if (isClickable) {
                actionClickSuccess = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                AppLogger.d(logTag, "  -> ACTION_CLICK performed on depth=$depth, returned: $actionClickSuccess")
                if (actionClickSuccess) {
                    if (!first) current.recycle()
                    break
                }
            }
            val parent = current.parent
            if (!first) current.recycle()
            current = parent
            first = false
            depth++
        }
        
        if (actionClickSuccess) {
            AppLogger.d(logTag, "Click succeeded using standard ACTION_CLICK")
            return true
        }

        AppLogger.d(logTag, "Standard ACTION_CLICK failed or no clickable parent found. Falling back to gesture.")

        // 2. Fallback: Attempt physical gesture click
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        AppLogger.d(logTag, "  -> Target screen bounds: $rect (isEmpty=${rect.isEmpty})")
        
        if (!rect.isEmpty && rect.centerX() > 0 && rect.centerY() > 0) {
            val path = android.graphics.Path().apply {
                moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
            }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            
            val dispatched = service.dispatchGesture(gesture, null, null)
            AppLogger.d(logTag, "  -> dispatchGesture returned: $dispatched")
            return dispatched
        } else {
            AppLogger.e(logTag, "  -> Cannot dispatch gesture: invalid bounds")
        }

        return false
    }

    fun isNodeVisible(node: AccessibilityNodeInfo, containerRect: android.graphics.Rect?, screenHeight: Int, screenWidth: Int): Boolean {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        
        val centerY = rect.centerY()
        val centerX = rect.centerX()
        
        val top = containerRect?.top ?: 0
        val bottom = containerRect?.bottom ?: screenHeight
        val left = containerRect?.left ?: 0
        val right = containerRect?.right ?: screenWidth
        
        val margin = 20
        return centerY > top + margin && 
               centerY < bottom - margin &&
               centerX >= left && 
               centerX <= right
    }
}
