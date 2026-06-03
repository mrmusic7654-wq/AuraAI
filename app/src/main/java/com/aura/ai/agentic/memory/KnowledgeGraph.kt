package com.aura.ai.agentic.memory

data class KnowledgeNode(
    val id: String,
    val type: String,  // "APP", "ERROR", "DEPENDENCY", "PATTERN", "PREFERENCE"
    val label: String,
    val properties: Map<String, String> = emptyMap()
)

data class KnowledgeEdge(
    val fromId: String,
    val toId: String,
    val relationship: String,  // "DEPENDS_ON", "FIXES", "USES", "PREFERS"
    val weight: Float = 1f
)

class KnowledgeGraph {
    
    private val nodes = mutableMapOf<String, KnowledgeNode>()
    private val edges = mutableListOf<KnowledgeEdge>()
    
    fun addNode(type: String, label: String, properties: Map<String, String> = emptyMap()): String {
        val id = java.util.UUID.randomUUID().toString()
        nodes[id] = KnowledgeNode(id, type, label, properties)
        return id
    }
    
    fun addEdge(fromId: String, toId: String, relationship: String, weight: Float = 1f) {
        edges.add(KnowledgeEdge(fromId, toId, relationship, weight))
    }
    
    fun learnFromTask(task: String, result: String, success: Boolean) {
        // Extract patterns and store as knowledge
        val taskNode = addNode("TASK", task, mapOf("success" to success.toString()))
        
        // If task mentions an app, link it
        val appNames = extractAppNames(task)
        for (app in appNames) {
            val appNode = findOrCreateAppNode(app)
            addEdge(taskNode, appNode, "INVOLVES")
        }
        
        // If task mentions errors, link them
        if (!success) {
            val errorNode = addNode("ERROR", result.take(100))
            addEdge(taskNode, errorNode, "CAUSED")
        }
    }
    
    fun query(question: String): List<KnowledgeNode> {
        // Simple keyword-based query
        val keywords = question.lowercase().split(" ").filter { it.length > 2 }
        return nodes.values.filter { node ->
            keywords.any { node.label.lowercase().contains(it) || node.type.lowercase().contains(it) }
        }
    }
    
    fun getRelatedNodes(nodeId: String): List<Pair<KnowledgeNode, String>> {
        return edges
            .filter { it.fromId == nodeId || it.toId == nodeId }
            .map { edge ->
                val relatedId = if (edge.fromId == nodeId) edge.toId else edge.fromId
                val relatedNode = nodes[relatedId]
                if (relatedNode != null) Pair(relatedNode, edge.relationship) else null
            }
            .filterNotNull()
    }
    
    fun findPatterns(): List<String> {
        val patterns = mutableListOf<String>()
        
        // Find frequent relationships
        val relationshipCounts = edges.groupBy { it.relationship }.mapValues { it.value.size }
        for ((rel, count) in relationshipCounts) {
            if (count >= 3) {
                patterns.add("Frequent: $rel appears $count times")
            }
        }
        
        // Find common error patterns
        val errors = nodes.values.filter { it.type == "ERROR" }
        if (errors.size >= 3) {
            patterns.add("Common errors detected: ${errors.size} total")
        }
        
        return patterns
    }
    
    private fun extractAppNames(text: String): List<String> {
        val commonApps = listOf("whatsapp", "telegram", "gmail", "deepseek", "chrome", "youtube", "github")
        return commonApps.filter { text.lowercase().contains(it) }
    }
    
    private fun findOrCreateAppNode(appName: String): String {
        val existing = nodes.values.find { it.label.equals(appName, ignoreCase = true) && it.type == "APP" }
        return existing?.id ?: addNode("APP", appName)
    }
    
    fun getStats(): String {
        return "🧠 Knowledge Graph: ${nodes.size} nodes, ${edges.size} edges, ${findPatterns().size} patterns"
    }
}
