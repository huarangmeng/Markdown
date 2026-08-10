package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.rememberTextMeasurer
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.markdown.renderer.MarkdownTheme
import com.hrm.markdown.renderer.internal.layout.inline.InlineLayoutEpoch
import com.hrm.markdown.runtime.MarkdownDirectiveRegistry
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class InlineLayoutEpochRememberTest {
    @Test
    fun should_reuseEpoch_untilAnIdentityDependencyChanges() = runComposeUiTest {
        val observations = mutableListOf<InlineLayoutEpoch>()
        var recomposeUnrelated: (() -> Unit)? = null
        var changeTheme: (() -> Unit)? = null

        setContent {
            var tick by remember { mutableStateOf(0) }
            var darkTheme by remember { mutableStateOf(false) }
            val theme = if (darkTheme) MarkdownTheme.dark() else MarkdownTheme.light()
            val epoch = rememberInlineLayoutEpoch(
                theme = theme,
                codeTheme = null,
                directiveRegistry = MarkdownDirectiveRegistry.Empty,
                config = null,
                onLinkClick = null,
                onFootnoteClick = null,
                density = LocalDensity.current,
                textMeasurer = rememberTextMeasurer(),
                latexMeasurer = rememberLatexMeasurer(),
            )
            SideEffect { observations += epoch }
            recomposeUnrelated = { tick++ }
            changeTheme = { darkTheme = true }
            BasicText(tick.toString())
        }

        waitForIdle()
        val initial = observations.last()

        runOnIdle { checkNotNull(recomposeUnrelated).invoke() }
        waitForIdle()
        assertSame(initial, observations.last())

        runOnIdle { checkNotNull(changeTheme).invoke() }
        waitForIdle()
        assertNotSame(initial, observations.last())
    }
}
