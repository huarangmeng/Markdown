package com.hrm.markdown.renderer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.highlight.SyntaxColorScheme

/**
 * Markdown 渲染的完整主题配置。
 * 通过 CompositionLocal 在组件树中传递，支持外部自定义。
 *
 * 使用方式：
 * - `MarkdownTheme.light()` — 亮色主题
 * - `MarkdownTheme.dark()` — 暗色主题
 * - `MarkdownTheme.auto()` — 跟随系统日夜间模式（@Composable）
 */
@Immutable
data class MarkdownTheme(
    /** 各级标题样式 (h1 ~ h6) */
    val headingStyles: List<TextStyle> = defaultHeadingStyles(),
    /** 正文段落样式 */
    val bodyStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    /** 行内代码样式 */
    val inlineCodeStyle: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
    ),
    /** 行内代码背景色 */
    val inlineCodeBackground: Color = Color(0xFFEFF1F3),
    /** 代码块文字样式 */
    val codeBlockStyle: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    /** 代码块背景色 */
    val codeBlockBackground: Color = Color(0xFFF6F8FA),
    /** 代码块圆角 */
    val codeBlockCornerRadius: Dp = 8.dp,
    /** 代码块内边距 */
    val codeBlockPadding: Dp = 12.dp,
    /** 块引用左边框颜色 */
    val blockQuoteBorderColor: Color = Color(0xFFD0D7DE),
    /** 块引用左边框宽度 */
    val blockQuoteBorderWidth: Dp = 4.dp,
    /** 块引用内边距 */
    val blockQuotePadding: Dp = 12.dp,
    /** 块引用文字颜色 */
    val blockQuoteTextColor: Color = Color(0xFF656D76),
    /** 水平分割线颜色 */
    val dividerColor: Color = Color(0xFFD0D7DE),
    /** 水平分割线厚度 */
    val dividerThickness: Dp = 1.dp,
    /** 链接颜色 */
    val linkColor: Color = Color(0xFF0969DA),
    /** 块间距 */
    val blockSpacing: Dp = 12.dp,
    /** 列表项缩进 */
    val listIndent: Dp = 24.dp,
    /** 列表项 bullet 颜色 */
    val listBulletColor: Color = Color(0xFF1F2328),
    /** 表格边框颜色 */
    val tableBorderColor: Color = Color(0xFFD0D7DE),
    /** 表格表头背景色 */
    val tableHeaderBackground: Color = Color(0xFFF6F8FA),
    /** 表格单元格内边距 */
    val tableCellPadding: Dp = 8.dp,
    /** 删除线样式 */
    val strikethroughStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
    /** 高亮背景色 */
    val highlightColor: Color = Color(0xFFFFF3B0),
    /** 任务列表选中颜色 */
    val taskCheckedColor: Color = Color(0xFF1A7F37),
    /** 任务列表未选中颜色 */
    val taskUncheckedColor: Color = Color(0xFFD0D7DE),
    /** 数学公式字体大小 (sp) */
    val mathFontSize: Float = 16f,
    /** 数学公式块背景色 */
    val mathBlockBackground: Color = Color(0xFFF6F8FA),
    /** 数学公式文字颜色 */
    val mathColor: Color = Color(0xFF1F2328),
    /** Admonition 样式映射 */
    val admonitionStyles: Map<String, AdmonitionStyle> = defaultAdmonitionStyles(),
    /** 脚注文字样式 */
    val footnoteStyle: SpanStyle = SpanStyle(fontSize = 12.sp),
    /** 上标样式 */
    val superscriptStyle: SpanStyle = SpanStyle(fontSize = 12.sp),
    /** 下标样式 */
    val subscriptStyle: SpanStyle = SpanStyle(fontSize = 12.sp),
    /** 插入文本样式 */
    val insertedTextStyle: SpanStyle = SpanStyle(textDecoration = TextDecoration.Underline),
    /** 键盘按键样式 */
    val kbdStyle: SpanStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    ),
    /** 键盘按键背景色 */
    val kbdBackground: Color = Color(0xFFEFF1F3),
    /** 缩写提示样式（带虚线下划线） */
    val abbreviationStyle: SpanStyle = SpanStyle(
        textDecoration = TextDecoration.Underline,
    ),
    /** 代码块语法高亮配色方案 */
    val syntaxColorScheme: SyntaxColorScheme = SyntaxColorScheme.GitHubLight,
    /** 代码块标题栏背景色 */
    val codeBlockTitleBackground: Color = Color(0xFFEBEDF0),
    /** 代码块标题栏文字样式 */
    val codeBlockTitleStyle: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = FontFamily.Monospace,
    ),
    /** 代码块行号文字颜色 */
    val codeBlockLineNumberColor: Color = Color(0xFF8C959F),
    /** 代码块高亮行背景色 */
    val codeBlockHighlightLineColor: Color = Color(0xFFFFF8C5),
) {
    companion object {
        /**
         * 亮色主题（GitHub Light 风格），等同于无参构造 `MarkdownTheme()`。
         */
        fun light(): MarkdownTheme = MarkdownTheme()

        /**
         * 暗色主题（GitHub Dark 风格）。
         */
        fun dark(): MarkdownTheme = MarkdownTheme(
            headingStyles = darkHeadingStyles(),
            bodyStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = Color(0xFFE6EDF3)),
            inlineCodeBackground = Color(0xFF343942),
            codeBlockStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color(0xFFE6EDF3),
            ),
            codeBlockBackground = Color(0xFF161B22),
            blockQuoteBorderColor = Color(0xFF3D444D),
            blockQuoteTextColor = Color(0xFF9198A1),
            dividerColor = Color(0xFF3D444D),
            linkColor = Color(0xFF4493F8),
            listBulletColor = Color(0xFFE6EDF3),
            tableBorderColor = Color(0xFF3D444D),
            tableHeaderBackground = Color(0xFF161B22),
            highlightColor = Color(0xFF5C4B00),
            taskCheckedColor = Color(0xFF3FB950),
            taskUncheckedColor = Color(0xFF3D444D),
            mathBlockBackground = Color(0xFF161B22),
            mathColor = Color(0xFFE6EDF3),
            admonitionStyles = darkAdmonitionStyles(),
            kbdBackground = Color(0xFF343942),
            syntaxColorScheme = SyntaxColorScheme.GitHubDark,
            codeBlockTitleBackground = Color(0xFF21262D),
            codeBlockLineNumberColor = Color(0xFF484F58),
            codeBlockHighlightLineColor = Color(0xFF3B2E00),
        )

        /**
         * 跟随系统日夜间模式，自动选择亮色或暗色主题。
         * 用法与 `isSystemInDarkTheme()` 一致，默认跟随系统设置。
         *
         * ```
         * Markdown(
         *     markdown = "# Hello",
         *     theme = MarkdownTheme.auto(),
         * )
         * ```
         */
        @Composable
        fun auto(isDarkTheme: Boolean = isSystemInDarkTheme()): MarkdownTheme {
            return if (isDarkTheme) dark() else light()
        }
    }
}

@Immutable
data class AdmonitionStyle(
    val borderColor: Color,
    val backgroundColor: Color,
    val iconText: String,
    val titleColor: Color,
)

internal fun defaultHeadingStyles(): List<TextStyle> = listOf(
    TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),      // h1
    TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),      // h2
    TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),  // h3
    TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),  // h4
    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),  // h5
    TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),    // h6
)

internal fun darkHeadingStyles(): List<TextStyle> {
    val headingColor = Color(0xFFE6EDF3)
    return listOf(
        TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, color = headingColor),      // h1
        TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, color = headingColor),      // h2
        TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp, color = headingColor),  // h3
        TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, color = headingColor),  // h4
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, color = headingColor),  // h5
        TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, color = headingColor),    // h6
    )
}

internal fun defaultAdmonitionStyles(): Map<String, AdmonitionStyle> = mapOf(
    "NOTE" to AdmonitionStyle(
        borderColor = Color(0xFF0969DA),
        backgroundColor = Color(0xFFDDF4FF),
        iconText = "ℹ️",
        titleColor = Color(0xFF0969DA),
    ),
    "TIP" to AdmonitionStyle(
        borderColor = Color(0xFF1A7F37),
        backgroundColor = Color(0xFFDCFFE4),
        iconText = "💡",
        titleColor = Color(0xFF1A7F37),
    ),
    "IMPORTANT" to AdmonitionStyle(
        borderColor = Color(0xFF8250DF),
        backgroundColor = Color(0xFFFBEFFF),
        iconText = "❗",
        titleColor = Color(0xFF8250DF),
    ),
    "WARNING" to AdmonitionStyle(
        borderColor = Color(0xFFBF8700),
        backgroundColor = Color(0xFFFFF8C5),
        iconText = "⚠️",
        titleColor = Color(0xFFBF8700),
    ),
    "CAUTION" to AdmonitionStyle(
        borderColor = Color(0xFFCF222E),
        backgroundColor = Color(0xFFFFEBE9),
        iconText = "🔴",
        titleColor = Color(0xFFCF222E),
    ),
)

internal fun darkAdmonitionStyles(): Map<String, AdmonitionStyle> = mapOf(
    "NOTE" to AdmonitionStyle(
        borderColor = Color(0xFF4493F8),
        backgroundColor = Color(0xFF0D1D30),
        iconText = "ℹ️",
        titleColor = Color(0xFF4493F8),
    ),
    "TIP" to AdmonitionStyle(
        borderColor = Color(0xFF3FB950),
        backgroundColor = Color(0xFF0D2818),
        iconText = "💡",
        titleColor = Color(0xFF3FB950),
    ),
    "IMPORTANT" to AdmonitionStyle(
        borderColor = Color(0xFFAB7DF8),
        backgroundColor = Color(0xFF1B1030),
        iconText = "❗",
        titleColor = Color(0xFFAB7DF8),
    ),
    "WARNING" to AdmonitionStyle(
        borderColor = Color(0xFFD29922),
        backgroundColor = Color(0xFF2A1F00),
        iconText = "⚠️",
        titleColor = Color(0xFFD29922),
    ),
    "CAUTION" to AdmonitionStyle(
        borderColor = Color(0xFFF85149),
        backgroundColor = Color(0xFF300C0C),
        iconText = "🔴",
        titleColor = Color(0xFFF85149),
    ),
)

internal val LocalMarkdownTheme = compositionLocalOf { MarkdownTheme() }

/**
 * 提供 Markdown 主题到组件树。
 */
@Composable
internal fun ProvideMarkdownTheme(
    theme: MarkdownTheme = MarkdownTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMarkdownTheme provides theme) {
        content()
    }
}
