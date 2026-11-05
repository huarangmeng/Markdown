package com.hrm.markdown.renderer.internal.selection

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

// This factory is declared in Compose's iosMain, not its shared Apple metadata.
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry? =
    ClipEntry.withPlainText(text)
