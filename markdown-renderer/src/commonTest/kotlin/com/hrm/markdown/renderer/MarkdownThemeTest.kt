package com.hrm.markdown.renderer

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownThemeTest {
    @Test
    fun material3ThemeUsesProvidedColorScheme() {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF0057D9),
            onSurface = Color(0xFF101010),
            onSurfaceVariant = Color(0xFF555555),
            surfaceVariant = Color(0xFFE8F0FE),
            outline = Color(0xFF777777),
            outlineVariant = Color(0xFFBBBBBB),
            tertiaryContainer = Color(0xFFFFD8E4),
        )

        val theme = MarkdownTheme.material3(colorScheme)

        assertEquals(colorScheme.onSurface, theme.bodyStyle.color)
        assertEquals(colorScheme.onSurface, theme.headingStyles.first().color)
        assertEquals(colorScheme.primary, theme.linkColor)
        assertEquals(colorScheme.surfaceVariant, theme.inlineCodeBackground)
        assertEquals(colorScheme.surfaceVariant, theme.codeBlockBackground)
        assertEquals(colorScheme.outline, theme.blockQuoteBorderColor)
        assertEquals(colorScheme.outlineVariant, theme.dividerColor)
        assertEquals(colorScheme.tertiaryContainer, theme.highlightColor)
    }

    @Test
    fun material3ThemeKeepsDarkBaseWhenSurfaceIsDark() {
        val colorScheme = darkColorScheme(
            surface = Color(0xFF101010),
            onSurface = Color(0xFFF0F0F0),
        )

        val theme = MarkdownTheme.material3(colorScheme)

        assertEquals(MarkdownTheme.dark().codeBlockCornerRadius, theme.codeBlockCornerRadius)
        assertEquals(Color(0xFFF0F0F0), theme.mathColor)
    }
}
