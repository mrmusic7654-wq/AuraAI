package com.aura.ai.agentic.planning

class DependencyResolver {
    
    fun resolveOrder(steps: List<TaskStep>): List<TaskStep> {
        val resolved = mutableListOf<TaskStep>()
        val remaining = steps.toMutableList()
        
        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { step ->
                step.dependsOn.all { depOrder ->
                    resolved.any { it.order == depOrder }
                }
            }
            
            if (ready.isEmpty()) {
                // Circular dependency or all remaining have unmet dependencies
                // Add remaining in original order
                resolved.addAll(remaining)
                break
            }
            
            resolved.addAll(ready)
            remaining.removeAll(ready)
        }
        
        return resolved
    }
    
    fun findCircularDependencies(steps: List<TaskStep>): List<Pair<Int, Int>> {
        val circular = mutableListOf<Pair<Int, Int>>()
        
        for (step in steps) {
            for (depOrder in step.dependsOn) {
                val depStep = steps.find { it.order == depOrder }
                if (depStep != null && depStep.dependsOn.contains(step.order)) {
                    circular.add(Pair(step.order, depOrder))
                }
            }
        }
        
        return circular
    }
    
    fun canExecuteParallel(step1: TaskStep, step2: TaskStep): Boolean {
        // Steps can run in parallel if neither depends on the other
        return !step1.dependsOn.contains(step2.order) && !step2.dependsOn.contains(step1.order)
    }
    
    fun getParallelGroups(steps: List<TaskStep>): List<List<TaskStep>> {
        val resolved = resolveOrder(steps)
        val groups = mutableListOf<MutableList<TaskStep>>()
        
        for (step in resolved) {
            // Find a group where this step can run in parallel with all members
            val compatibleGroup = groups.find { group ->
                group.all { canExecuteParallel(step, it) }
            }
            
            if (compatibleGroup != null) {
                compatibleGroup.add(step)
            } else {
                groups.add(mutableListOf(step))
            }
        }
        
        return groups
    }
}
