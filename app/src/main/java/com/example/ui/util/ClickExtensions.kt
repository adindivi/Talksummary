package com.example.ui.util

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role

/**
 * Extension on Modifier that prevents rapid double clicks or repeated trigger events.
 *
 * @param debounceTimeMs Minimum interval in milliseconds required between clicks (default: 500ms).
 * @param enabled Whether this click action is enabled.
 * @param role The type of user interface element (optional).
 * @param onClick The callback to execute when clicked.
 */
fun Modifier.debouncedClickable(
    debounceTimeMs: Long = 500L,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    val interactionSource = remember { MutableInteractionSource() }

    this.clickable(
        enabled = enabled,
        role = role,
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple()
    ) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceTimeMs) {
            lastClickTime = currentTime
            onClick()
        }
    }
}
