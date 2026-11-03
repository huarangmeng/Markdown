package com.hrm.markdown.renderer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 行内元素装饰请求。与 [MarkdownInlineElementClickEvent] 同源分发：
 * 每个命中语义 mark（`==highlight==`、`<mark>` 等）的文本 run 在绘制前询问一次，
 * 返回 null 表示保持默认渲染。
 */
@Immutable
data class MarkdownInlineDecorationRequest(
    val kind: String,
    val text: String,
    val payload: Map<String, String> = emptyMap(),
    /** 元素原始属性，库不解释；使用方按自己的约定读取。 */
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * 绘制在文本下方的装饰线。跨行文本按可见行逐行绘制，
 * 每行只覆盖该行内实际存在的字符区间。
 *
 * 三种线型按以下优先级生效：圆点（[dotDiameter]）→ 虚线（[dashWidth] + [gapWidth]）→ 实线。
 */
@Immutable
data class MarkdownInlineUnderline(
    val color: Color,
    val strokeWidth: Dp = 1.dp,
    /** 虚线段长；与 [gapWidth] 同时提供时绘制虚线，否则为实线。 */
    val dashWidth: Dp? = null,
    /** 虚线间隔。 */
    val gapWidth: Dp? = null,
    /** 圆点直径。提供时绘制一系列圆点（点线），忽略 [dashWidth]/[gapWidth]。 */
    val dotDiameter: Dp? = null,
    /** 圆点之间的间隔，仅在 [dotDiameter] 提供时生效。 */
    val dotGap: Dp = 3.dp,
    /** 相对文本行底边向上抬升的距离。 */
    val offset: Dp = 2.dp,
    /** 仅在每个语义范围的最后一个文字端点绘制；不参与正文排版或文本投影。 */
    val endMarker: MarkdownInlineEndMarker? = null,
) {
    /** 圆点连成实线（[dotGap] 小于直径时视觉连续）；供只想区分线型的调用方快速构造。 */
    companion object {
        fun solid(color: Color, strokeWidth: Dp = 1.dp, offset: Dp = 2.dp) =
            MarkdownInlineUnderline(color = color, strokeWidth = strokeWidth, offset = offset)
    }
}

/** 装饰线末端的纯绘制标记；未提供 [painter] 时保持原有圆点样式。 */
@Immutable
data class MarkdownInlineEndMarker(
    val color: Color,
    val diameter: Dp = 4.dp,
    /** 标记与文字末端的间距。 */
    val gap: Dp = 3.dp,
    /** 绘制在 [diameter] 大小的方框内，由 [color] 着色，不插入正文字符或占位符。 */
    val painter: Painter? = null,
)
