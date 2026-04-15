package com.hrm.markdown.renderer.inline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Cross-platform workaround for inline content line height issues.
 *
 * The actual line layout still uses a BoxWithConstraints-driven implementation,
 * but we wrap it in a regular Layout that provides intrinsic measurements.
 * This avoids crashes when parents such as tables ask for intrinsic sizes.
 */
@Composable
internal fun InlineFlowText(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (inlineContents.isEmpty()) {
        BasicText(
            text = annotated,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
        )
        return
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measurePolicy = remember(annotated, inlineContents, style, density, textMeasurer, maxLines) {
        inlineFlowMeasurePolicy(
            annotated = annotated,
            inlineContents = inlineContents,
            style = style,
            density = density,
            textMeasurer = textMeasurer,
            maxLines = maxLines,
        )
    }

    Layout(
        modifier = modifier,
        content = {
            InlineFlowMeasuredContent(
                annotated = annotated,
                inlineContents = inlineContents,
                style = style,
                density = density,
                textMeasurer = textMeasurer,
                maxLines = maxLines,
            )
        },
        measurePolicy = measurePolicy,
    )
}

@Composable
private fun InlineFlowMeasuredContent(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    maxLines: Int,
) {
    BoxWithConstraints {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val flowLayout = remember(annotated, inlineContents, style, maxWidthPx, density, textMeasurer, maxLines) {
            computeInlineFlowLayout(
                annotated = annotated,
                inlineContents = inlineContents,
                style = style,
                density = density,
                textMeasurer = textMeasurer,
                maxWidthPx = maxWidthPx,
                maxLines = maxLines,
            )
        }

        Layout(
            content = {
                for (line in flowLayout.lines) {
                    for (item in line.items) {
                        when (item) {
                            is LineItem.TextItem -> {
                                BasicText(
                                    text = item.text,
                                    style = line.textStyle,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }

                            is LineItem.InlineItem -> {
                                val wDp = with(density) { item.widthPx.toDp() }
                                val hDp = with(density) { item.heightPx.toDp() }
                                Box(modifier = Modifier.size(wDp, hDp)) {
                                    item.content()
                                }
                            }
                        }
                    }
                }
            },
        ) { measurables, constraints ->
            val placeables = ArrayList<androidx.compose.ui.layout.Placeable>(measurables.size)
            var idx = 0
            for (line in flowLayout.lines) {
                for (item in line.items) {
                    val measurable = measurables[idx++]
                    val w = item.widthPx.roundToInt().coerceAtLeast(0)
                    val h = item.heightPx.roundToInt().coerceAtLeast(0)
                    placeables += measurable.measure(
                        if (w == 0 || h == 0) Constraints.fixed(0, 0) else Constraints.fixed(w, h)
                    )
                }
            }

            val width = flowLayout.widthPx.roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
            val height = flowLayout.heightPx.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)

            layout(width, height) {
                var y = 0f
                var pIndex = 0
                for (line in flowLayout.lines) {
                    val lineHeight = line.lineHeightPx
                    val lineStartX = when (line.textAlign) {
                        TextAlign.Center -> ((maxWidthPx - line.lineWidthPx) / 2f).coerceAtLeast(0f)
                        TextAlign.End, TextAlign.Right -> (maxWidthPx - line.lineWidthPx).coerceAtLeast(0f)
                        else -> 0f
                    }

                    var x = lineStartX
                    for (item in line.items) {
                        val placeable = placeables[pIndex++]
                        val itemY = y + ((lineHeight - item.heightPx) / 2f).coerceAtLeast(0f)
                        placeable.placeRelative(x.roundToInt(), itemY.roundToInt())
                        x += item.widthPx
                    }
                    y += lineHeight
                }
            }
        }
    }
}

private fun inlineFlowMeasurePolicy(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    maxLines: Int,
): MeasurePolicy = object : MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>,
        constraints: Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val placeable = measurables.singleOrNull()?.measure(constraints)
        val width = placeable?.width ?: constraints.minWidth
        val height = placeable?.height ?: constraints.minHeight
        return layout(width, height) {
            placeable?.placeRelative(0, 0)
        }
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int = computeMinIntrinsicWidthPx(
        annotated = annotated,
        inlineContents = inlineContents,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        height: Int,
    ): Int = computeMaxIntrinsicWidthPx(
        annotated = annotated,
        inlineContents = inlineContents,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        width: Int,
    ): Int = computeIntrinsicHeightPx(
        annotated = annotated,
        inlineContents = inlineContents,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
        maxLines = maxLines,
        widthPx = width,
    )

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>,
        width: Int,
    ): Int = computeIntrinsicHeightPx(
        annotated = annotated,
        inlineContents = inlineContents,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
        maxLines = maxLines,
        widthPx = width,
    )
}

private data class InlineFlowLayout(
    val widthPx: Float,
    val heightPx: Float,
    val lines: List<InlineFlowLine>,
)

private data class InlineFlowLine(
    val textStyle: TextStyle,
    val lineWidthPx: Float,
    val lineHeightPx: Float,
    val textAlign: TextAlign,
    val items: List<LineItem>,
)

private sealed class LineItem {
    abstract val widthPx: Float
    abstract val heightPx: Float

    data class TextItem(
        val text: AnnotatedString,
        override val widthPx: Float,
        override val heightPx: Float,
    ) : LineItem()

    data class InlineItem(
        val id: String,
        override val widthPx: Float,
        override val heightPx: Float,
        val alternateText: String,
        val content: @Composable () -> Unit,
    ) : LineItem()
}

private sealed class Token {
    data class Text(val annotated: AnnotatedString) : Token()
    data class Inline(val id: String) : Token()
    data object Newline : Token()
}

private fun computeInlineFlowLayout(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    maxWidthPx: Float,
    maxLines: Int,
): InlineFlowLayout {
    val tokens = tokenizeAnnotatedString(annotated, inlineContents)
    val textStyle = textMeasurementStyle(style)
    val baseLineHeightPx = baseLineHeightPx(style, density)

    fun measureText(a: AnnotatedString): Pair<Float, Float> {
        if (a.isEmpty()) return 0f to 0f
        val res = textMeasurer.measure(
            text = a,
            style = textStyle,
            constraints = Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
            softWrap = false,
        )
        return res.size.width.toFloat() to res.size.height.toFloat()
    }

    fun inlineSizePx(id: String): Pair<Float, Float> {
        val ic = inlineContents[id] ?: return 0f to 0f
        val wPx = with(density) { ic.inlineTextContent.placeholder.width.toPx() }
        val hPx = with(density) { ic.inlineTextContent.placeholder.height.toPx() }
        return wPx to hPx
    }

    val lines = ArrayList<InlineFlowLine>()
    var currentItems = ArrayList<LineItem>()
    var currentWidth = 0f
    var maxItemHeight = 0f
    var lineCount = 0

    fun flushLine(force: Boolean = false) {
        if (!force && currentItems.isEmpty()) return
        lines += InlineFlowLine(
            textStyle = textStyle,
            lineWidthPx = currentWidth,
            lineHeightPx = maxOf(baseLineHeightPx, maxItemHeight),
            textAlign = style.textAlign,
            items = currentItems.toList(),
        )
        currentItems = ArrayList()
        currentWidth = 0f
        maxItemHeight = 0f
        lineCount++
    }

    for (token in tokens) {
        if (lineCount >= maxLines) break
        when (token) {
            Token.Newline -> flushLine(force = true)
            is Token.Inline -> {
                val (w, h) = inlineSizePx(token.id)
                if (w <= 0f || h <= 0f) continue
                if (currentWidth > 0f && currentWidth + w > maxWidthPx) {
                    flushLine(force = true)
                    if (lineCount >= maxLines) break
                }
                val ic = inlineContents[token.id] ?: continue
                currentItems.add(
                    LineItem.InlineItem(
                        id = token.id,
                        widthPx = w,
                        heightPx = h,
                        alternateText = ic.alternateText,
                        content = { ic.inlineTextContent.children(ic.alternateText) },
                    )
                )
                currentWidth += w
                maxItemHeight = maxOf(maxItemHeight, h)
            }
            is Token.Text -> {
                var remaining = token.annotated
                while (remaining.isNotEmpty() && lineCount < maxLines) {
                    val (w, h) = measureText(remaining)
                    val used = currentWidth
                    if (used + w <= maxWidthPx) {
                        currentItems.add(LineItem.TextItem(remaining, w, h))
                        currentWidth += w
                        maxItemHeight = maxOf(maxItemHeight, h)
                        break
                    }

                    val fit = splitTextToFit(
                        text = remaining,
                        style = textStyle,
                        textMeasurer = textMeasurer,
                        maxWidthPx = (maxWidthPx - used).coerceAtLeast(1f),
                    )
                    if (fit.fit.isNotEmpty()) {
                        val (fw, fh) = measureText(fit.fit)
                        currentItems.add(LineItem.TextItem(fit.fit, fw, fh))
                        currentWidth += fw
                        maxItemHeight = maxOf(maxItemHeight, fh)
                    }
                    flushLine(force = true)
                    remaining = fit.rest.trimLeadingSpaces()
                }
            }
        }
    }
    if (lineCount < maxLines) flushLine(force = false)

    return InlineFlowLayout(
        widthPx = maxWidthPx,
        heightPx = lines.sumOf { it.lineHeightPx.toDouble() }.toFloat(),
        lines = lines,
    )
}

private data class SplitResult(val fit: AnnotatedString, val rest: AnnotatedString)

private fun splitTextToFit(
    text: AnnotatedString,
    style: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    maxWidthPx: Float,
): SplitResult {
    if (text.isEmpty()) return SplitResult(AnnotatedString(""), AnnotatedString(""))
    val full = text.text
    var lo = 0
    var hi = full.length
    var best = 0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val sub = text.subSequence(0, mid)
        val w = textMeasurer.measure(
            text = sub,
            style = style,
            constraints = Constraints(maxWidth = Int.MAX_VALUE),
            maxLines = 1,
            softWrap = false,
        ).size.width.toFloat()
        if (w <= maxWidthPx) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    if (best <= 0) best = 1.coerceAtMost(full.length)

    val region = full.substring(0, best)
    val lastSpace = region.indexOfLast { it.isWhitespace() }
    val cut = if (lastSpace >= 1) lastSpace + 1 else best
    return SplitResult(
        fit = text.subSequence(0, cut),
        rest = text.subSequence(cut, full.length),
    )
}

private fun AnnotatedString.trimLeadingSpaces(): AnnotatedString {
    val s = text
    var i = 0
    while (i < s.length && s[i].isWhitespace() && s[i] != '\n') i++
    return if (i == 0) this else subSequence(i, s.length)
}

private fun tokenizeAnnotatedString(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
): List<Token> {
    val text = annotated.text
    if (text.isEmpty()) return emptyList()

    val tokens = ArrayList<Token>()
    var start = 0
    fun pushText(end: Int) {
        if (end > start) {
            tokens += Token.Text(annotated.subSequence(start, end))
        }
        start = end
    }

    for (i in text.indices) {
        when (text[i]) {
            '\n' -> {
                pushText(i)
                tokens += Token.Newline
                start = i + 1
            }
            INLINE_PLACEHOLDER_CHAR -> {
                pushText(i)
                val anns = annotated.getStringAnnotations(tag = INLINE_PLACEHOLDER_TAG, start = i, end = i + 1)
                val id = anns.firstOrNull { inlineContents.containsKey(it.item) }?.item
                if (id != null) {
                    tokens += Token.Inline(id)
                }
                start = i + 1
            }
        }
    }
    pushText(text.length)
    return tokens
}

private fun textMeasurementStyle(style: TextStyle): TextStyle {
    return if (style.lineHeight.value.isNaN()) style else style.copy(lineHeight = TextUnit.Unspecified)
}

private fun baseLineHeightPx(style: TextStyle, density: Density): Float = with(density) {
    val lh = style.lineHeight.value.takeUnless { it.isNaN() }
        ?: style.fontSize.value.takeUnless { it.isNaN() }?.times(1.5f)
        ?: 0f
    lh.sp.toPx()
}.coerceAtLeast(0f)

private fun computeMaxIntrinsicWidthPx(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
): Int {
    val tokens = tokenizeAnnotatedString(annotated, inlineContents)
    val textStyle = textMeasurementStyle(style)
    var lineWidth = 0f
    var maxLineWidth = 0f
    for (token in tokens) {
        when (token) {
            Token.Newline -> {
                maxLineWidth = maxOf(maxLineWidth, lineWidth)
                lineWidth = 0f
            }
            is Token.Inline -> {
                val entry = inlineContents[token.id] ?: continue
                lineWidth += with(density) { entry.inlineTextContent.placeholder.width.toPx() }
            }
            is Token.Text -> {
                if (token.annotated.isEmpty()) continue
                val width = textMeasurer.measure(
                    text = token.annotated,
                    style = textStyle,
                    constraints = Constraints(maxWidth = Int.MAX_VALUE),
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
                lineWidth += width
            }
        }
    }
    maxLineWidth = maxOf(maxLineWidth, lineWidth)
    return ceil(maxLineWidth).toInt()
}

private fun computeMinIntrinsicWidthPx(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
): Int {
    val textStyle = textMeasurementStyle(style)
    var maxPieceWidth = 0f
    for (token in tokenizeAnnotatedString(annotated, inlineContents)) {
        when (token) {
            Token.Newline -> Unit
            is Token.Inline -> {
                val entry = inlineContents[token.id] ?: continue
                maxPieceWidth = maxOf(maxPieceWidth, with(density) { entry.inlineTextContent.placeholder.width.toPx() })
            }
            is Token.Text -> {
                val pieces = token.annotated.text.split(Regex("(?<=\\\\s)|(?=\\\\s)"))
                var cursor = 0
                for (piece in pieces) {
                    if (piece.isEmpty()) continue
                    val end = cursor + piece.length
                    val sub = token.annotated.subSequence(cursor, end)
                    val width = textMeasurer.measure(
                        text = sub,
                        style = textStyle,
                        constraints = Constraints(maxWidth = Int.MAX_VALUE),
                        maxLines = 1,
                        softWrap = false,
                    ).size.width.toFloat()
                    maxPieceWidth = maxOf(maxPieceWidth, width)
                    cursor = end
                }
            }
        }
    }
    return ceil(maxPieceWidth).toInt()
}

private fun computeIntrinsicHeightPx(
    annotated: AnnotatedString,
    inlineContents: Map<String, InlineContentEntry>,
    style: TextStyle,
    density: Density,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    maxLines: Int,
    widthPx: Int,
): Int {
    val targetWidth = if (widthPx == Constraints.Infinity || widthPx <= 0) {
        computeMaxIntrinsicWidthPx(annotated, inlineContents, style, density, textMeasurer).coerceAtLeast(1)
    } else {
        widthPx
    }
    val layout = computeInlineFlowLayout(
        annotated = annotated,
        inlineContents = inlineContents,
        style = style,
        density = density,
        textMeasurer = textMeasurer,
        maxWidthPx = targetWidth.toFloat(),
        maxLines = maxLines,
    )
    return ceil(layout.heightPx).toInt()
}
