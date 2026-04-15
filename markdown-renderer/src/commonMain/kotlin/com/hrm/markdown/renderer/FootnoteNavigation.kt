package com.hrm.markdown.renderer

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf

@Stable
internal class FootnoteNavigationState {
    private val definitionRequesters = mutableMapOf<String, BringIntoViewRequester>()
    private val returnPositions = mutableStateMapOf<String, Int>()

    fun registerDefinition(label: String, requester: BringIntoViewRequester) {
        definitionRequesters[label] = requester
    }

    fun unregisterDefinition(label: String, requester: BringIntoViewRequester) {
        if (definitionRequesters[label] === requester) {
            definitionRequesters.remove(label)
        }
    }

    fun hasDefinition(label: String): Boolean = definitionRequesters.containsKey(label)

    fun rememberReturnPosition(label: String, scrollValue: Int) {
        returnPositions[label] = scrollValue
    }

    fun hasReturnPosition(label: String): Boolean = returnPositions.containsKey(label)

    fun getReturnPosition(label: String): Int? = returnPositions[label]

    suspend fun bringDefinitionIntoView(label: String): Boolean {
        val requester = definitionRequesters[label] ?: return false
        requester.bringIntoView()
        return true
    }
}
