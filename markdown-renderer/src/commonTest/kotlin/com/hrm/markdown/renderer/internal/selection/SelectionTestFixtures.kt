package com.hrm.markdown.renderer.internal.selection

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.renderer.internal.core.identity.RenderIdentity
import com.hrm.markdown.renderer.internal.core.model.TableBlockModel
import com.hrm.markdown.renderer.internal.layout.model.InternalLayoutBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutInlineLine
import com.hrm.markdown.renderer.internal.layout.model.LayoutRect
import com.hrm.markdown.renderer.internal.layout.model.LayoutTableBlockModel
import com.hrm.markdown.renderer.internal.layout.model.LayoutTextRun

internal fun selIdentity(id: Long): RenderIdentity =
    RenderIdentity(stableId = id, contentRevision = id, layoutRevision = id, paintRevision = id)

internal fun selRect(
    left: Float = 0f,
    top: Float = 0f,
    width: Float = 320f,
    height: Float = 20f,
): LayoutRect = LayoutRect(left = left, top = top, width = width, height = height)

/** 单行单 run 的 inline 块。 */
internal fun inlineTextBlock(
    id: Long,
    text: String,
    top: Float = 0f,
    width: Float = 260f,
    height: Float = 20f,
): LayoutInlineBlockModel =
    LayoutInlineBlockModel(
        identity = selIdentity(id),
        frame = selRect(top = top, width = width, height = height),
        contentFrame = selRect(top = top, width = width, height = height),
        style = TextStyle.Default,
        inlinePayloads = emptyMap(),
        lines = listOf(
            LayoutInlineLine(
                frame = selRect(top = top, width = width, height = height),
                baseline = 16f,
                runs = listOf(
                    LayoutTextRun(
                        identity = selIdentity(id * 1000 + 1),
                        frame = selRect(left = 0f, top = top, width = width, height = height),
                        text = AnnotatedString(text),
                    )
                ),
            )
        ),
    )

/** 多行多 run 的 inline 块：每个 (runText) 占一行，行高 [lineHeight]。 */
internal fun inlineMultiRunBlock(
    id: Long,
    runs: List<String>,
    lineHeight: Float = 20f,
    runWidth: Float = 100f,
): LayoutInlineBlockModel {
    val lines = runs.mapIndexed { i, runText ->
        val top = i * lineHeight
        LayoutInlineLine(
            frame = selRect(top = top, width = runWidth, height = lineHeight),
            baseline = top + 16f,
            runs = listOf(
                LayoutTextRun(
                    identity = selIdentity(id * 1000 + i + 1),
                    frame = selRect(left = 0f, top = top, width = runWidth, height = lineHeight),
                    text = AnnotatedString(runText),
                )
            ),
        )
    }
    val totalHeight = runs.size * lineHeight
    return LayoutInlineBlockModel(
        identity = selIdentity(id),
        frame = selRect(top = 0f, width = runWidth, height = totalHeight),
        contentFrame = selRect(top = 0f, width = runWidth, height = totalHeight),
        style = TextStyle.Default,
        inlinePayloads = emptyMap(),
        lines = lines,
    )
}

internal fun selDocument(vararg blocks: InternalLayoutBlockModel): List<InternalLayoutBlockModel> =
    blocks.toList()

/** 一个不参与选区索引的 gap 块（表格），用于验证跨 gap 选中行为。 */
internal fun LayoutTableBlockModelGap(id: Long): LayoutTableBlockModel =
    LayoutTableBlockModel(
        identity = selIdentity(id),
        frame = selRect(),
        contentFrame = selRect(),
        block = TableBlockModel(
            identity = selIdentity(id),
            columnAlignments = listOf(Table.Alignment.NONE),
            rows = emptyList(),
        ),
        columnWidths = listOf(100f),
        rows = emptyList(),
    )
