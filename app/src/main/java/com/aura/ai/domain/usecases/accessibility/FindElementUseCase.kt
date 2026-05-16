package com.aura.ai.domain.usecases.accessibility

import com.aura.ai.data.models.ScreenContext
import com.aura.ai.data.models.UIElement
import javax.inject.Inject

class FindElementUseCase @Inject constructor() {
    
    operator fun invoke(
        screenContext: ScreenContext,
        query: String,
        matchType: MatchType = MatchType.CONTAINS
    ): UIElement? {
        return screenContext.elements.find { element ->
            val displayText = element.getDisplayText()
            when (matchType) {
                MatchType.EXACT -> displayText.equals(query, ignoreCase = true)
                MatchType.CONTAINS -> displayText.contains(query, ignoreCase = true)
                MatchType.STARTS_WITH -> displayText.startsWith(query, ignoreCase = true)
            }
        }
    }
    
    fun findAll(
        screenContext: ScreenContext,
        query: String
    ): List<UIElement> {
        return screenContext.elements.filter { element ->
            element.getDisplayText().contains(query, ignoreCase = true)
        }
    }
}

enum class MatchType {
    EXACT, CONTAINS, STARTS_WITH
}
