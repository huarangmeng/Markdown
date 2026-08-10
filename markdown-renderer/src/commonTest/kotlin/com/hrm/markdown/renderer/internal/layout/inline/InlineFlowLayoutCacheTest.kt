package com.hrm.markdown.renderer.internal.layout.inline

import androidx.compose.ui.text.TextStyle
import com.hrm.markdown.parser.block.postprocessors.PostProcessor
import com.hrm.markdown.parser.block.starters.BlockStarter
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.flavour.MarkdownFlavour
import com.hrm.markdown.renderer.MarkdownConfig
import com.hrm.markdown.renderer.MarkdownTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InlineFlowLayoutCacheTest {
    @Test
    fun should_bound_cacheAndReportChurn_when_widthChangesFrequently() {
        val cache = InlineFlowLayoutCache(maxEntries = 8, maxEstimatedBytes = 8 * 1024)
        val epoch = testEpoch()
        fun request(width: Float) = cache.getOrPut(
            epoch = epoch,
            layoutRevision = 1,
            widthPx = width,
            maxLines = Int.MAX_VALUE,
            style = TextStyle.Default,
        ) {
            InlineFlowLayout(widthPx = width, heightPx = 0f, lines = emptyList())
        }

        request(width = 320f)
        request(width = 320f)
        repeat(64) { index -> request(width = 280f + index) }

        val metrics = cache.metricsSnapshot()
        assertEquals(1, metrics.hits)
        assertEquals(65, metrics.misses)
        assertEquals(8, metrics.entryCount)
        assertEquals(57, metrics.evictions)
        assertTrue(metrics.estimatedBytes <= 8 * 1024)
    }

    @Test
    fun should_notReuseLayout_when_epochValuesHaveSameHashCode() {
        val firstFlavour = CollidingFlavour()
        val secondFlavour = CollidingFlavour()
        val firstConfig = MarkdownConfig(flavour = firstFlavour)
        val secondConfig = MarkdownConfig(flavour = secondFlavour)
        val collaborator = ReferentialIdentity.of(Any())
        val firstEpoch = testEpoch(firstConfig, collaborator)
        val secondEpoch = testEpoch(secondConfig, collaborator)
        val cache = InlineFlowLayoutCache(maxEntries = 8, maxEstimatedBytes = 8 * 1024)
        var computations = 0

        assertEquals(firstConfig.hashCode(), secondConfig.hashCode())
        assertEquals(firstEpoch.hashCode(), secondEpoch.hashCode())
        assertNotEquals(firstEpoch, secondEpoch)

        fun request(epoch: InlineLayoutEpoch): InlineFlowLayout = cache.getOrPut(
            epoch = epoch,
            layoutRevision = 1,
            widthPx = 320f,
            maxLines = Int.MAX_VALUE,
            style = TextStyle.Default,
        ) {
            computations++
            InlineFlowLayout(widthPx = 320f, heightPx = computations.toFloat(), lines = emptyList())
        }

        assertEquals(1f, request(firstEpoch).heightPx)
        assertEquals(2f, request(secondEpoch).heightPx)
        assertEquals(1f, request(firstEpoch).heightPx)
        assertEquals(2, computations)
        assertEquals(1, cache.metricsSnapshot().hits)
    }

    @Test
    fun should_computeStructuralHashOnlyOnce_when_keyIsReused() {
        val value = HashCountingValue(hash = 41)
        val identity = StructuralIdentity.of(value)

        assertEquals(1, value.hashCalls)
        repeat(1_000) { assertEquals(41, identity.hashCode()) }
        assertEquals(1, value.hashCalls)
    }

    @Test
    fun should_notCallMutableHashCode_when_usingReferentialIdentity() {
        val value = HashCountingValue(hash = 41)
        val first = ReferentialIdentity.of(value)
        value.hash = 99
        val second = ReferentialIdentity.of(value)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(0, value.hashCalls)
    }

    private fun testEpoch(
        config: MarkdownConfig = MarkdownConfig.Default,
        collaborator: ReferentialIdentity = ReferentialIdentity.of(Any()),
    ): InlineLayoutEpoch = InlineLayoutEpoch(
        themeIdentity = StructuralIdentity.of(MarkdownTheme()),
        codeThemeIdentity = StructuralIdentity.of(null),
        directiveRegistryIdentity = collaborator,
        configIdentity = StructuralIdentity.of(config),
        densityBits = 1f.toBits(),
        fontScaleBits = 1f.toBits(),
        textMeasurerIdentity = collaborator,
        latexMeasurerIdentity = collaborator,
        onLinkClickIdentity = collaborator,
        onFootnoteClickIdentity = collaborator,
    )

    private class CollidingFlavour : MarkdownFlavour {
        override val blockStarters: List<BlockStarter> = CommonMarkFlavour.blockStarters
        override val postProcessors: List<PostProcessor> = emptyList()

        override fun hashCode(): Int = 7
    }

    private class HashCountingValue(
        var hash: Int,
    ) {
        var hashCalls: Int = 0
            private set

        override fun hashCode(): Int {
            hashCalls++
            return hash
        }
    }
}
