package com.hrm.markdown.renderer.internal.selection

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

// Keep the macOS factory out of appleMain, whose metadata exposes only common ClipEntry APIs.
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry? =
    ClipEntry.withPlainText(text)
