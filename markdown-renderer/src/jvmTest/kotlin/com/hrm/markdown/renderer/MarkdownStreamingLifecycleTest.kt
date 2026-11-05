package com.hrm.markdown.renderer

import com.hrm.markdown.parser.MarkdownParserConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.block.postprocessors.PostProcessor
import com.hrm.markdown.parser.flavour.CommonMarkFlavour
import com.hrm.markdown.parser.flavour.ExtendedFlavour
import com.hrm.markdown.parser.flavour.MarkdownFlavour
import com.hrm.markdown.parser.html.HtmlRenderer
import com.hrm.markdown.parser.lint.DiagnosticCode
import com.hrm.markdown.runtime.MarkdownDirectivePlugin
import com.hrm.markdown.runtime.MarkdownInputTransformer
import com.hrm.markdown.runtime.MarkdownSourceMap
import com.hrm.markdown.runtime.MarkdownTransformResult
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ThreadContextElement

/** Exercises the public Markdown entry point with real Compose effects and background workers. */
@OptIn(ExperimentalTestApi::class)
class MarkdownStreamingLifecycleTest {
    @Test
    fun should_renderCodeAtExistingBoundaries_when_usingLlmStreamingPreset() = runComposeUiTest {
        val first = "z123456789abcdef"
        val page = TestPage("```text\n$first", isStreaming = true, parserConfig = MarkdownParserConfig.LlmStreaming)
        setContent { page.Content() }
        waitUntil(timeoutMillis = 10_000) { hasVisibleCode(first) }
        val committed = page.latest

        runOnIdle { page.markdown += "ghijklmnopqrstu" }
        waitForIdle()
        runOnIdle {
            assertSame(committed.document, page.latest.document)
            assertEquals(committed.html, page.latest.html)
            assertTrue(hasVisibleCode(first))
        }

        runOnIdle { page.markdown += "v" }
        val second = first + "ghijklmnopqrstuv"
        waitUntil(timeoutMillis = 10_000) { hasVisibleCode(second) }

        runOnIdle { page.markdown += "tail\n" }
        waitUntil(timeoutMillis = 10_000) { hasVisibleCode(second + "tail") }
        val newlineCommit = page.latest
        runOnIdle { page.markdown += "last" }
        waitForIdle()
        runOnIdle {
            assertSame(newlineCommit.document, page.latest.document)
            assertEquals(newlineCommit.html, page.latest.html)
            page.isStreaming = false
        }
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes()
                .any { node ->
                    node.config[SemanticsProperties.Text].any { "last" in it.text }
                }
        }
    }

    private fun ComposeUiTest.hasVisibleCode(code: String): Boolean =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .any { node ->
                node.config[SemanticsProperties.Text]
                    .joinToString("") { it.text }.trimEnd('\n') == code
            }

    @Test
    fun should_publishOnlyLatestRevision_when_fullParsesFinishOutOfOrder() {
        val jobs = BackgroundJobContext()
        val first = BackgroundBarrier(jobs)
        val second = BackgroundBarrier(jobs)
        val page = TestPage(
            markdown = "seed|visible",
            parserConfig = gatedParserConfig(mapOf(paragraph("first") to first, paragraph("second") to second)),
            plugins = listOf(mappingPlugin()),
        )

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitHtml(page, paragraph("visible"))
                onNodeWithText("visible").assertExists()
                runOnIdle { page.markdown = "a|first" }
                awaitStarted(first)
                runOnIdle { assertMapping(page.latest, "visible", inputStart = 5) }
                runOnIdle { page.markdown = "bb|second" }
                awaitStarted(second)
                assertTrue(first.effectJob.isCancelled)

                runOnIdle { page.markdown = "ccc|latest" }
                awaitHtml(page, paragraph("latest"))
                assertTrue(second.effectJob.isCancelled)
                val latest = page.latest.document

                // The newest revision is already visible while both obsolete parses remain blocked.
                releaseAndAwait(second)
                releaseAndAwait(first)
                runOnIdle {
                    assertSame(latest, page.latest.document)
                    page.snapshots.forEach {
                        when (it.html) {
                            paragraph("visible") -> assertMapping(it, "visible", inputStart = 5)
                            paragraph("latest") -> assertMapping(it, "latest", inputStart = 4)
                            else -> error("Obsolete revision was published: ${it.html}")
                        }
                    }
                }
                onNodeWithText("latest").assertExists()
                onNodeWithText("first").assertDoesNotExist()
                onNodeWithText("second").assertDoesNotExist()
            } finally {
                first.release()
                second.release()
            }
        }
    }

    @Test
    fun should_keepStreamingSessionIntact_when_cancelledFullParseFinishes() {
        val jobs = BackgroundJobContext()
        val oldParse = BackgroundBarrier(jobs)
        val page = TestPage("old", parserConfig = gatedParserConfig(mapOf(paragraph("old") to oldParse)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(oldParse)
                runOnIdle {
                    page.markdown = "fresh **live"
                    page.isStreaming = true
                }
                awaitHtml(page, paragraph("fresh <strong>live</strong>"))
                assertTrue(oldParse.effectJob.isCancelled)
                releaseAndAwait(oldParse)

                runOnIdle { page.markdown = "fresh **live stream**" }
                awaitHtml(page, paragraph("fresh <strong>live stream</strong>"))
                runOnIdle { assertTrue(page.snapshots.none { it.html == paragraph("old") }) }
                // Exact-width text runs may stay separate; assert the complete rendered prose, not one Text item.
                val renderedText = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                    .fetchSemanticsNodes()
                    .joinToString("") { node ->
                        node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    }
                assertEquals("fresh live stream", renderedText)
            } finally {
                oldParse.release()
            }
        }
    }

    @Test
    fun should_cancelPendingTransform_when_streamIsStopped() {
        val jobs = BackgroundJobContext()
        val transform = BackgroundBarrier(jobs)
        val page = TestPage("input|**unfinished", isStreaming = true, plugins = listOf(mappingPlugin(transform)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(transform)
                runOnIdle { page.isStreaming = false }
                waitForIdle()
                assertTrue(transform.effectJob.isCancelled)
                assertTrue(page.snapshots.isEmpty())

                releaseAndAwait(transform)
                awaitHtml(page, paragraph("**unfinished"))
                runOnIdle {
                    assertTrue(page.snapshots.all { it.html == paragraph("**unfinished") })
                    page.snapshots.forEach { assertMapping(it, "**unfinished", inputStart = 6) }
                }

                runOnIdle {
                    page.markdown = "retry|new answer"
                    page.isStreaming = true
                }
                awaitHtml(page, paragraph("new answer"))
                onNodeWithText("new answer").assertExists()
            } finally {
                transform.release()
            }
        }
    }

    @Test
    fun should_flushBufferedTailAndStartCleanSession_when_streamIsStoppedAndRetried() = runComposeUiTest {
        val page = TestPage("committed\n\n", isStreaming = true, parserConfig = MarkdownParserConfig.LlmStreaming)
        setContent { page.Content() }
        awaitHtml(page, paragraph("committed"))
        val committed = page.latest.document

        runOnIdle { page.markdown += "**tail" }
        waitForIdle()
        runOnIdle { assertSame(committed, page.latest.document) }
        runOnIdle { page.isStreaming = false }
        awaitHtml(page, paragraph("committed") + paragraph("**tail"))

        runOnIdle {
            page.markdown = ""
            page.isStreaming = true
        }
        waitForIdle()
        onNodeWithText("committed").assertDoesNotExist()
        onNodeWithText("**tail").assertDoesNotExist()

        runOnIdle { page.markdown = "retry\n" }
        awaitHtml(page, paragraph("retry"))
        onNodeWithText("retry").assertExists()
    }

    @Test
    fun should_discardOldParserResult_when_configurationChangesDuringFullParse() {
        val jobs = BackgroundJobContext()
        val oldParse = BackgroundBarrier(jobs)
        val page = TestPage("==marked==", parserConfig = gatedParserConfig(mapOf(paragraph("<mark>marked</mark>") to oldParse)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(oldParse)
                runOnIdle { page.parserConfig = MarkdownParserConfig(flavour = CommonMarkFlavour) }
                awaitHtml(page, paragraph("==marked=="))
                assertTrue(oldParse.effectJob.isCancelled)
                val configured = page.latest.document

                releaseAndAwait(oldParse)
                runOnIdle {
                    assertSame(configured, page.latest.document)
                    assertTrue(page.snapshots.all { it.html == paragraph("==marked==") })
                }
                onNodeWithText("==marked==").assertExists()
            } finally {
                oldParse.release()
            }
        }
    }

    @Test
    fun should_reparseUnchangedInput_when_streamingParserConfigurationChanges() = runComposeUiTest {
        val page = TestPage(":ship:", isStreaming = true, parserConfig = MarkdownParserConfig(customEmojiMap = mapOf("ship" to "🚀")))
        setContent { page.Content() }
        awaitHtml(page, paragraph("🚀"))
        val original = page.latest.document

        runOnIdle { page.parserConfig = page.parserConfig.copy(customEmojiMap = mapOf("ship" to "🚢")) }
        awaitHtml(page, paragraph("🚢"))
        runOnIdle { assertNotSame(original, page.latest.document) }

        runOnIdle { page.parserConfig = page.parserConfig.copy(flavour = CommonMarkFlavour) }
        awaitHtml(page, paragraph(":ship:"))

        runOnIdle { page.parserConfig = page.parserConfig.copy(flavour = ExtendedFlavour, appendCoalesceThreshold = 16) }
        awaitHtml(page, "")
        onNodeWithText(":ship:").assertDoesNotExist()
        onNodeWithText("🚢").assertDoesNotExist()

        runOnIdle { page.parserConfig = page.parserConfig.copy(appendCoalesceThreshold = 0) }
        awaitHtml(page, paragraph("🚢"))
        onNodeWithText("🚢").assertExists()
    }

    @Test
    fun should_updateRenderedEmoticons_when_asciiOptionChangesWithoutChangingInput() = runComposeUiTest {
        val page = TestPage("Hello :)")
        setContent { page.Content() }
        awaitHtml(page, paragraph("Hello :)"))
        runOnIdle { page.parserConfig = page.parserConfig.copy(enableAsciiEmoticons = true) }
        awaitHtml(page, paragraph("Hello 😊"))
        val visibleText = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
        assertEquals(listOf("Hello 😊"), visibleText.map {
            it.config[SemanticsProperties.Text].joinToString("") { part -> part.text }
        })
        runOnIdle { page.parserConfig = page.parserConfig.copy(enableAsciiEmoticons = false) }
        awaitHtml(page, paragraph("Hello :)"))
    }

    @Test
    fun should_updateDocumentDiagnostics_when_lintOptionChangesWithoutChangingInput() = runComposeUiTest {
        val page = TestPage("# First\n\n### Skipped")
        setContent { page.Content() }
        waitUntil(timeoutMillis = 10_000) { page.snapshots.isNotEmpty() }
        runOnIdle { assertTrue(page.latest.document.diagnostics.diagnostics.isEmpty()) }
        runOnIdle { page.parserConfig = page.parserConfig.copy(enableLinting = true) }
        waitUntil(timeoutMillis = 10_000) {
            page.snapshots.lastOrNull()?.document?.diagnostics?.diagnostics?.any {
                it.code == DiagnosticCode.HEADING_LEVEL_SKIP
            } == true
        }
        runOnIdle {
            assertEquals(listOf(DiagnosticCode.HEADING_LEVEL_SKIP), page.latest.document.diagnostics.diagnostics.map { it.code })
        }
        runOnIdle { page.parserConfig = page.parserConfig.copy(enableLinting = false) }
        waitUntil(timeoutMillis = 10_000) { page.latest.document.diagnostics.diagnostics.isEmpty() }
    }

    @Test
    fun should_publishMatchingDocumentAndSourceMap_when_pipelineChangesDuringTransform() {
        val jobs = BackgroundJobContext()
        val oldTransform = BackgroundBarrier(jobs)
        val page = TestPage("old|new", plugins = listOf(mappingPlugin(oldTransform, beforeSeparator = true)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(oldTransform)
                runOnIdle { page.plugins = listOf(mappingPlugin()) }
                awaitHtml(page, paragraph("new"))
                assertTrue(oldTransform.effectJob.isCancelled)
                val latest = page.latest.document

                releaseAndAwait(oldTransform)
                runOnIdle {
                    assertSame(latest, page.latest.document)
                    assertTrue(page.snapshots.all { it.html == paragraph("new") })
                    page.snapshots.forEach { assertMapping(it, "new", inputStart = 4) }
                }

                runOnIdle { page.markdown = "longer prefix|next" }
                awaitHtml(page, paragraph("next"))
                runOnIdle {
                    page.snapshots.forEach {
                        when (it.html) {
                            paragraph("new") -> assertMapping(it, "new", inputStart = 4)
                            paragraph("next") -> assertMapping(it, "next", inputStart = 14)
                            else -> error("Unexpected published revision: ${it.html}")
                        }
                    }
                }
                onNodeWithText("next").assertExists()
            } finally {
                oldTransform.release()
            }
        }
    }

    @Test
    fun should_cancelPendingParseWithoutPublishing_when_pageLeavesComposition() {
        val jobs = BackgroundJobContext()
        val parse = BackgroundBarrier(jobs)
        val page = TestPage("leaving", parserConfig = gatedParserConfig(mapOf(paragraph("leaving") to parse)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(parse)
                runOnIdle { page.visible = false }
                waitForIdle()
                assertTrue(parse.effectJob.isCancelled)

                releaseAndAwait(parse)
                runOnIdle { assertTrue(page.snapshots.isEmpty()) }
                onNodeWithText("leaving").assertDoesNotExist()

                runOnIdle {
                    page.markdown = "returned"
                    page.visible = true
                }
                awaitHtml(page, paragraph("returned"))
                runOnIdle { assertTrue(page.snapshots.all { it.html == paragraph("returned") }) }
                onNodeWithText("returned").assertExists()
            } finally {
                parse.release()
            }
        }
    }

    @Test
    fun should_keepNewPageIntact_when_disposedPagesTransformFinishesLate() {
        val jobs = BackgroundJobContext()
        val transform = BackgroundBarrier(jobs)
        val page = TestPage("old|leaving", plugins = listOf(mappingPlugin(transform)))

        runComposeUiTest(effectContext = jobs) {
            try {
                setContent { page.Content() }
                awaitStarted(transform)
                runOnIdle { page.visible = false }
                waitForIdle()
                assertTrue(transform.effectJob.isCancelled)

                runOnIdle {
                    page.markdown = "returned|current"
                    page.visible = true
                }
                awaitHtml(page, paragraph("current"))
                val current = page.latest.document
                releaseAndAwait(transform)
                runOnIdle {
                    assertSame(current, page.latest.document)
                    assertTrue(page.snapshots.all { it.html == paragraph("current") })
                    page.snapshots.forEach { assertMapping(it, "current", inputStart = 9) }
                }
                onNodeWithText("current").assertExists()
                onNodeWithText("leaving").assertDoesNotExist()
            } finally {
                transform.release()
            }
        }
    }

    private fun ComposeUiTest.awaitStarted(barrier: BackgroundBarrier) =
        waitUntil(timeoutMillis = 10_000) { barrier.started }

    private fun ComposeUiTest.releaseAndAwait(barrier: BackgroundBarrier) {
        barrier.release()
        // Await the entire LaunchedEffect, including resumption after withContext, before asserting.
        waitUntil(timeoutMillis = 10_000) { barrier.effectJob.isCompleted }
        waitForIdle()
    }

    private fun ComposeUiTest.awaitHtml(page: TestPage, html: String) {
        waitUntil(timeoutMillis = 10_000) { page.snapshots.lastOrNull()?.html == html }
        waitForIdle()
    }

    private fun assertMapping(snapshot: PublishedSnapshot, output: String, inputStart: Int) {
        assertEquals(inputStart, snapshot.sourceMap.mapOutputOffset(0), snapshot.html)
        assertEquals(inputStart + output.length, snapshot.sourceMap.mapOutputOffset(output.length), snapshot.html)
    }

    private fun paragraph(body: String) = "<p>$body</p>\n"

    private class TestPage(
        markdown: String,
        isStreaming: Boolean = false,
        parserConfig: MarkdownParserConfig = MarkdownParserConfig.Default,
        plugins: List<MarkdownDirectivePlugin> = emptyList(),
    ) {
        var markdown by mutableStateOf(markdown)
        var isStreaming by mutableStateOf(isStreaming)
        var parserConfig by mutableStateOf(parserConfig)
        var plugins by mutableStateOf(plugins)
        var visible by mutableStateOf(true)
        val snapshots = ConcurrentLinkedQueue<PublishedSnapshot>()
        val latest: PublishedSnapshot get() = assertNotNull(snapshots.lastOrNull())

        @Composable
        fun Content() {
            if (visible) Markdown(
                markdown = markdown,
                isStreaming = isStreaming,
                parserConfig = parserConfig,
                directivePlugins = plugins,
                enableScroll = false,
                enableSelection = false,
                components = MarkdownComponents(header = {
                    val document = LocalRendererDocument.current
                    val sourceMap = LocalMarkdownSourceMap.current
                    SideEffect { snapshots += PublishedSnapshot(document, HtmlRenderer().render(document), sourceMap) }
                }),
            )
        }
    }

    private data class PublishedSnapshot(val document: Document, val html: String, val sourceMap: MarkdownSourceMap)

    private fun gatedParserConfig(barriers: Map<String, BackgroundBarrier>): MarkdownParserConfig {
        val observer = object : PostProcessor {
            override val priority = Int.MAX_VALUE
            override fun process(document: Document) {
                barriers[HtmlRenderer().render(document)]?.blockOnce()
            }
        }
        val flavour = ExtendedFlavour.with(observer)
        return MarkdownParserConfig(flavour = flavour)
    }

    private fun mappingPlugin(barrier: BackgroundBarrier? = null, beforeSeparator: Boolean = false) =
        object : MarkdownDirectivePlugin {
            override val id = "lifecycle-source-map"
            override val inputTransformers = listOf(object : MarkdownInputTransformer {
                override val id = "select-input-segment"
                override fun transform(input: String): MarkdownTransformResult {
                    barrier?.blockOnce()
                    val separator = input.indexOf('|')
                    val start = if (beforeSeparator) 0 else separator + 1
                    val end = if (beforeSeparator && separator >= 0) separator else input.length
                    val output = input.substring(start, end)
                    return MarkdownTransformResult(output, MarkdownSourceMap.Segmented(listOf(
                        MarkdownSourceMap.Segmented.Segment(0, output.length, start, end),
                    )))
                }
            })
        }

    /** A synchronous parser/transformer may keep working after its owning effect is cancelled. */
    private class BackgroundBarrier(private val jobs: BackgroundJobContext) {
        private val claimed = AtomicBoolean()
        private val released = CountDownLatch(1)
        @Volatile
        private var owner: Job? = null
        val started: Boolean get() = owner != null
        val effectJob: Job get() = assertNotNull(owner)

        @OptIn(ExperimentalCoroutinesApi::class)
        fun blockOnce() {
            if (!claimed.compareAndSet(false, true)) return
            // Both work sites run inside withContext(Default); its parent is the real Compose effect.
            owner = assertNotNull(assertNotNull(jobs.current.get()).parent)
            check(released.await(30, TimeUnit.SECONDS)) { "Background operation was not released" }
        }

        fun release() = released.countDown()
    }

    /** Observe effect ownership through coroutine context, without adding production test hooks. */
    private class BackgroundJobContext : ThreadContextElement<Job?>, AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<BackgroundJobContext>
        val current = ThreadLocal<Job?>()

        override fun updateThreadContext(context: CoroutineContext): Job? = current.get().also {
            current.set(context[Job])
        }

        override fun restoreThreadContext(context: CoroutineContext, oldState: Job?) {
            current.set(oldState)
        }
    }
}
