package com.hrm.markdown.renderer.internal.layout.engine

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.hrm.markdown.renderer.internal.core.model.BlockTextAlignment

internal fun TextStyle.withBlockTextAlignment(alignment: BlockTextAlignment): TextStyle = when (alignment) {
    BlockTextAlignment.INHERIT -> this
    BlockTextAlignment.START -> copy(textAlign = TextAlign.Start)
    BlockTextAlignment.CENTER -> copy(textAlign = TextAlign.Center)
    BlockTextAlignment.END -> copy(textAlign = TextAlign.End)
}
