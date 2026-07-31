package com.hrm.markdown.androiddemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.renderer.Markdown

/** Release-like host used exclusively by the Macrobenchmark module. */
class MarkdownBenchmarkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockCount = intent.getIntExtra(ExtraBlockCount, DefaultBlockCount)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val document = remember(blockCount) {
                        MarkdownParser().parse(longBenchmarkDocument(blockCount))
                    }
                    Markdown(
                        document = document,
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = DocumentDescription },
                        enableScroll = true,
                        enableSelection = true,
                    )
                }
            }
        }
    }

    private companion object {
        const val DefaultBlockCount = 600
    }
}

internal const val ExtraBlockCount = "benchmark.block_count"
internal const val DocumentDescription = "Markdown benchmark document"

private fun longBenchmarkDocument(blockCount: Int): String = buildString(blockCount * 240) {
    repeat(blockCount) { index ->
        val number = index + 1
        append("## Section ").append(number).append('\n').append('\n')
        append("Paragraph ").append(number)
            .append(" contains **bold**, *emphasis*, [a link](https://example.com/)")
            .append(", inline code `value_").append(number).append("`, and enough text to wrap across lines. ")
            .append("Resize and font-scale changes deliberately produce distinct inline layout cache keys.")
        append('\n').append('\n')
    }
}
