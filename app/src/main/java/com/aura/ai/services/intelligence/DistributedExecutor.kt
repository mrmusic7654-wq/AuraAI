package com.aura.ai.services.intelligence

import android.content.Context
import org.json.JSONObject
import java.io.File

data class RemoteNode(
    val id: String,
    val name: String,
    val capabilities: List<String>,
    val lastSeen: Long
)

class DistributedExecutor(context: Context) {
    
    private val nodesDir = File(context.filesDir, "nodes")
    private val nodes = mutableMapOf<String, RemoteNode>()
    
    init { nodesDir.mkdirs() }
    
    fun registerNode(id: String, name: String, capabilities: List<String>) {
        nodes[id] = RemoteNode(id, name, capabilities, System.currentTimeMillis())
        saveNodes()
    }
    
    fun getAvailableNodes(): List<RemoteNode> {
        val cutoff = System.currentTimeMillis() - 300000 // 5 minutes
        return nodes.values.filter { it.lastSeen > cutoff }
    }
    
    fun findNodeForTask(task: String): RemoteNode? {
        return getAvailableNodes().find { node ->
            node.capabilities.any { cap -> task.contains(cap, ignoreCase = true) }
        }
    }
    
    fun getNodeCount(): Int = getAvailableNodes().size
    
    fun getStatus(): String {
        val available = getAvailableNodes()
        return if (available.isEmpty()) "📡 No remote nodes available"
        else "📡 ${available.size} node(s) available:\n${available.joinToString("\n") { "  • ${it.name}: ${it.capabilities.joinToString()}" }}"
    }
    
    private fun saveNodes() {
        val json = JSONObject()
        nodes.forEach { (id, node) ->
            json.put(id, JSONObject().apply {
                put("name", node.name)
                put("capabilities", org.json.JSONArray(node.capabilities))
                put("lastSeen", node.lastSeen)
            })
        }
        File(nodesDir, "nodes.json").writeText(json.toString())
    }
    
    fun cleanupStaleNodes() {
        val cutoff = System.currentTimeMillis() - 3600000 // 1 hour
        nodes.entries.removeAll { it.value.lastSeen < cutoff }
        saveNodes()
    }
}
