package com.hrm.markdown.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownComposeMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun compileLayoutComposeStartup() = benchmarkRule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startBenchmarkActivity()
        device.wait(Until.hasObject(By.desc(DocumentDescription)), UiTimeoutMs)
    }

    @Test
    fun longDocumentScroll() = benchmarkRule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        setupBlock = {
            killProcess()
            startBenchmarkActivity()
            benchmarkDocument().setGestureMarginPercentage(0.12f)
        },
    ) {
        val document = benchmarkDocument()
        repeat(5) { document.fling(Direction.UP) }
        repeat(5) { document.fling(Direction.DOWN) }
    }

    @Test
    fun crossBlockSelection() = benchmarkRule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 10,
        setupBlock = {
            killProcess()
            startBenchmarkActivity()
        },
    ) {
        longPressDragAcrossVisibleBlocks(benchmarkDocument())
        device.waitForIdle()
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.startBenchmarkActivity() {
        val intent = Intent().apply {
            component = ComponentName(TargetPackage, BenchmarkActivity)
            putExtra(ExtraBlockCount, 600)
        }
        startActivityAndWait(intent)
        check(device.wait(Until.hasObject(By.desc(DocumentDescription)), UiTimeoutMs)) {
            "Benchmark document did not become visible"
        }
    }

    private fun benchmarkDocument(): UiObject2 =
        checkNotNull(device.findObject(By.desc(DocumentDescription)))

    /** Hold until Compose recognizes a long press, then drag into the next visible block. */
    private fun longPressDragAcrossVisibleBlocks(document: UiObject2) {
        val bounds = document.visibleBounds
        val x = bounds.centerX()
        val startY = bounds.top + bounds.height() / 3
        val endY = bounds.top + bounds.height() * 2 / 3
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, y: Float, eventTime: Long) {
            val event = MotionEvent.obtain(downTime, eventTime, action, x.toFloat(), y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                check(automation.injectInputEvent(event, true)) { "Unable to inject selection gesture" }
            } finally {
                event.recycle()
            }
        }

        inject(MotionEvent.ACTION_DOWN, startY.toFloat(), downTime)
        SystemClock.sleep(LongPressMs)
        repeat(20) { step ->
            val fraction = (step + 1) / 20f
            val y = startY + (endY - startY) * fraction
            inject(MotionEvent.ACTION_MOVE, y, SystemClock.uptimeMillis())
            SystemClock.sleep(10)
        }
        inject(MotionEvent.ACTION_UP, endY.toFloat(), SystemClock.uptimeMillis())
    }

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private companion object {
        const val TargetPackage = "com.hrm.markdown.androiddemo"
        const val BenchmarkActivity = "$TargetPackage.MarkdownBenchmarkActivity"
        const val DocumentDescription = "Markdown benchmark document"
        const val ExtraBlockCount = "benchmark.block_count"
        const val UiTimeoutMs = 10_000L
        const val LongPressMs = 600L
    }
}
