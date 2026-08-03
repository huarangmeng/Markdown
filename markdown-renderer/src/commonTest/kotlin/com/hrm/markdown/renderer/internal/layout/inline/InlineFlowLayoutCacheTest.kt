package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InlineFlowLayoutCacheTest {
    @Test
    fun should_bound_cacheAndReportChurn_when_widthAndFontScaleChangeFrequently() = runComposeUiTest {
        val cache = InlineFlowLayoutCache(maxEntries = 8, maxEstimatedBytes = 8 * 1024)
        var exerciseCache: (() -> Unit)? = null

        setContent {
            val textMeasurer = rememberTextMeasurer()
            exerciseCache = {
                val epoch = InlineLayoutEpoch(
                    themeHash = 0,
                    codeThemeHash = 0,
                    directiveRegistryHash = 0,
                    configHash = 0,
                    densityBits = 0,
                    fontScaleBits = 0,
                    textMeasurerHash = 0,
                    latexMeasurerHash = 0,
                    onLinkClickHash = 0,
                    onFootnoteClickHash = 0,
                )
                fun request(width: Float, fontScale: Float) = cache.getOrPut(
                    epoch = epoch,
                    layoutRevision = 1,
                    widthPx = width,
                    maxLines = Int.MAX_VALUE,
                    style = TextStyle.Default,
                    density = Density(density = 1f, fontScale = fontScale),
                    textMeasurer = textMeasurer,
                ) {
                    InlineFlowLayout(widthPx = width, heightPx = 0f, lines = emptyList())
                }

                request(width = 320f, fontScale = 1f)
                request(width = 320f, fontScale = 1f)
                repeat(64) { index ->
                    request(
                        width = 280f + index,
                        fontScale = 1f + (index % 5) * 0.1f,
                    )
                }
            }
        }

        waitForIdle()
        runOnIdle { checkNotNull(exerciseCache).invoke() }

        val metrics = cache.metricsSnapshot()
        assertEquals(1, metrics.hits)
        assertEquals(65, metrics.misses)
        assertEquals(8, metrics.entryCount)
        assertEquals(57, metrics.evictions)
        assertTrue(metrics.estimatedBytes <= 8 * 1024)
    }
}
