package com.hrm.markdown.parser.ast

import com.hrm.markdown.parser.core.Attributes

/**
 * 文档的根节点。
 */
class Document : ContainerNode() {
    /** 解析过程中收集的链接引用定义。 */
    val linkDefinitions: MutableMap<String, LinkReferenceDefinition> = mutableMapOf()

    /** 解析过程中收集的脚注定义。 */
    val footnoteDefinitions: MutableMap<String, FootnoteDefinition> = mutableMapOf()

    /** 解析过程中收集的缩写定义。 */
    val abbreviationDefinitions: MutableMap<String, AbbreviationDefinition> = mutableMapOf()

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitDocument(this)
}

// ─────────────── 块级节点 ───────────────

/**
 * ATX 标题：`# heading` 到 `###### heading`。
 */
class Heading(
    var level: Int,
) : ContainerNode() {
    /** 来自 `{#id}` 语法的可选自定义 ID。 */
    var customId: String? = null

    /** 自动生成的标题 ID（基于标题文本的 slug）。若有 customId 则优先使用。 */
    var autoId: String? = null

    /** 获取最终使用的标题 ID（customId 优先，否则 autoId）。 */
    val id: String? get() = customId ?: autoId

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitHeading(this)
}

/**
 * Setext 标题：`heading\n===` 或 `heading\n---`。
 */
class SetextHeading(
    var level: Int,
) : ContainerNode() {
    /** 自动生成的标题 ID（基于标题文本的 slug）。 */
    var autoId: String? = null

    /** 获取最终使用的标题 ID。 */
    val id: String? get() = autoId

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitSetextHeading(this)
}

/**
 * 段落块：连续的非空行文本。
 */
class Paragraph : ContainerNode() {
    /** 解析阶段捕获的原始内容（已去除块级标记），供行内解析使用。 */
    var rawContent: String? = null
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitParagraph(this)
}

/**
 * 主题分隔线（水平线）：`---`、`***` 或 `___`。
 */
class ThematicBreak(
    var char: Char = '-'
) : LeafNode() {
    override val literal: String get() = ""
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitThematicBreak(this)
}

/**
 * 围栏代码块：``` 或 ~~~。
 */
class FencedCodeBlock(
    var info: String = "",
    var language: String = "",
    var fenceChar: Char = '`',
    var fenceLength: Int = 3,
    var fenceIndent: Int = 0,
    override var literal: String = "",
    var attributes: Attributes = Attributes(),
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitFencedCodeBlock(this)
}

/**
 * 缩进代码块：4 个空格或 1 个制表符缩进。
 */
class IndentedCodeBlock(
    override var literal: String = ""
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitIndentedCodeBlock(this)
}

/**
 * 块引用：以 `>` 为前缀的行。
 */
class BlockQuote : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitBlockQuote(this)
}

/**
 * 列表（有序或无序）。
 */
class ListBlock(
    var ordered: Boolean = false,
    var startNumber: Int = 1,
    var bulletChar: Char = '-',
    var delimiter: Char = '.',
    var tight: Boolean = true
) : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitListBlock(this)
}

/**
 * 列表中的单个列表项。
 */
class ListItem(
    var markerIndent: Int = 0,
    var contentIndent: Int = 0
) : ContainerNode() {
    /** 用于任务列表。 */
    var taskListItem: Boolean = false
    var checked: Boolean = false

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitListItem(this)
}

/**
 * HTML 块（CommonMark 规范中的类型 1-7）。
 */
class HtmlBlock(
    var htmlType: Int = 7,
    override var literal: String = ""
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitHtmlBlock(this)
}

/**
 * 链接引用定义：`[label]: destination "title"`。
 */
class LinkReferenceDefinition(
    var label: String = "",
    var destination: String = "",
    var title: String? = null
) : LeafNode() {
    override val literal: String get() = ""
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitLinkReferenceDefinition(this)
}

/**
 * 表格块（GFM 扩展）。
 */
class Table : ContainerNode() {
    var columnAlignments: List<Alignment> = emptyList()

    enum class Alignment { LEFT, CENTER, RIGHT, NONE }

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTable(this)
}

class TableHead : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTableHead(this)
}

class TableBody : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTableBody(this)
}

class TableRow : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTableRow(this)
}

class TableCell(
    var alignment: Table.Alignment = Table.Alignment.NONE,
    var isHeader: Boolean = false
) : ContainerNode() {
    /** 单元格的原始文本内容（由解析器在分割表格行时设置）。 */
    var rawContent: String = ""

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTableCell(this)
}

/**
 * 脚注定义：`[^label]: content`。
 */
class FootnoteDefinition(
    var label: String = "",
    var index: Int = 0
) : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitFootnoteDefinition(this)
}

/**
 * 数学公式块：`$$...$$`。
 */
class MathBlock(
    override var literal: String = ""
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitMathBlock(this)
}

/**
 * 定义列表（扩展语法）。
 */
class DefinitionList : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitDefinitionList(this)
}

class DefinitionTerm : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitDefinitionTerm(this)
}

class DefinitionDescription : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitDefinitionDescription(this)
}

/**
 * 警告/提示块：`> [!NOTE]`、`> [!WARNING]` 等。
 */
class Admonition(
    var type: String = "NOTE",
    var title: String = ""
) : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitAdmonition(this)
}

/**
 * 前置元数据块：YAML `---` 或 TOML `+++`。
 */
class FrontMatter(
    var format: String = "yaml",
    override var literal: String = ""
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitFrontMatter(this)
}

/**
 * 空行节点（内部用于松散列表检测）。
 */
class BlankLine : LeafNode() {
    override val literal: String get() = ""
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitBlankLine(this)
}

/**
 * TOC 占位符：`[TOC]` 或 `[[toc]]`，渲染时自动生成目录。
 *
 * 支持高级配置参数（紧跟 `[TOC]` 之后的行）：
 * - `:depth=2-4`：标题深度范围
 * - `:exclude=#ignore`：排除指定 ID 的标题
 * - `:order=asc|desc`：排序方式
 */
class TocPlaceholder : LeafNode() {
    override val literal: String get() = ""

    /** 最小标题深度（默认 1）。 */
    var minDepth: Int = 1

    /** 最大标题深度（默认 6）。 */
    var maxDepth: Int = 6

    /** 要排除的标题 ID 列表。 */
    var excludeIds: List<String> = emptyList()

    /** 排序方式："asc"（默认，按文档顺序）或 "desc"（逆序）。 */
    var order: String = "asc"

    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitTocPlaceholder(this)
}

/**
 * 缩写定义：`*[abbr]: Full Text`。
 */
class AbbreviationDefinition(
    var abbreviation: String = "",
    var fullText: String = ""
) : LeafNode() {
    override val literal: String get() = ""
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitAbbreviationDefinition(this)
}

/**
 * 自定义容器块：`:::type` 到 `:::`。
 *
 * 语法：
 * ```
 * ::: warning "标题"
 * 容器内容（支持嵌套块级元素）
 * :::
 * ```
 *
 * 支持自定义类型、标题、CSS class/ID 属性，以及容器嵌套。
 */
class CustomContainer(
    /** 容器类型，如 "warning"、"note"、"card" 等 */
    var type: String = "",
    /** 可选标题（引号内的文本） */
    var title: String = "",
    /** CSS class 列表，从 `{.class1 .class2}` 中提取 */
    var cssClasses: List<String> = emptyList(),
    /** CSS ID，从 `{#my-id}` 中提取 */
    var cssId: String? = null,
) : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitCustomContainer(this)
}

/**
 * 图表块：Mermaid / PlantUML 等图表代码块。
 *
 * 由围栏代码块中 info string 为 `mermaid`、`plantuml` 等关键字时自动转换。
 * 内部不解析 Markdown 语法，保留原始图表代码供渲染引擎处理。
 */
class DiagramBlock(
    /** 图表类型：如 "mermaid"、"plantuml" */
    var diagramType: String = "",
    /** 原始图表代码 */
    override var literal: String = ""
) : LeafNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitDiagramBlock(this)
}

/**
 * 多列布局容器：`:::columns` 到 `:::`。
 *
 * 语法：
 * ```
 * :::columns
 * :::column{width=50%}
 * 左列内容
 * :::column{width=50%}
 * 右列内容
 * :::
 * ```
 *
 * 包含多个 [ColumnItem] 子节点，每个子节点代表一列。
 */
class ColumnsLayout : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitColumnsLayout(this)
}

/**
 * 多列布局中的单列：`:::column{width=50%}`。
 *
 * @property width 列宽，如 "50%"、"300px"，为空时表示平均分配
 */
class ColumnItem(
    var width: String = "",
) : ContainerNode() {
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitColumnItem(this)
}

/**
 * 分页符标记：`***pagebreak***`。
 *
 * 用于 PDF 导出/打印场景，渲染器据此插入分页样式。
 */
class PageBreak : LeafNode() {
    override val literal: String get() = ""
    override fun <R> accept(visitor: NodeVisitor<R>): R = visitor.visitPageBreak(this)
}