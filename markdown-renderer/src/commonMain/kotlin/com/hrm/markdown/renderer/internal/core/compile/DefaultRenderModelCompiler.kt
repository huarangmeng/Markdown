package com.hrm.markdown.renderer.internal.core.compile

import com.hrm.markdown.parser.ast.AbbreviationDefinition
import com.hrm.markdown.parser.ast.Admonition
import com.hrm.markdown.parser.ast.BibliographyDefinition
import com.hrm.markdown.parser.ast.BlankLine
import com.hrm.markdown.parser.ast.BlockQuote
import com.hrm.markdown.parser.ast.ColumnItem
import com.hrm.markdown.parser.ast.ColumnsLayout
import com.hrm.markdown.parser.ast.ContainerNode
import com.hrm.markdown.parser.ast.CustomContainer
import com.hrm.markdown.parser.ast.DefinitionDescription
import com.hrm.markdown.parser.ast.DefinitionList
import com.hrm.markdown.parser.ast.DefinitionTerm
import com.hrm.markdown.parser.ast.DiagramBlock
import com.hrm.markdown.parser.ast.DirectiveBlock
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.parser.ast.FencedCodeBlock
import com.hrm.markdown.parser.ast.Figure
import com.hrm.markdown.parser.ast.FootnoteDefinition
import com.hrm.markdown.parser.ast.FrontMatter
import com.hrm.markdown.parser.ast.Heading
import com.hrm.markdown.parser.ast.HtmlBlock
import com.hrm.markdown.parser.ast.IndentedCodeBlock
import com.hrm.markdown.parser.ast.InlineCode
import com.hrm.markdown.parser.ast.LinkReferenceDefinition
import com.hrm.markdown.parser.ast.ListBlock
import com.hrm.markdown.parser.ast.ListItem
import com.hrm.markdown.parser.ast.MathBlock
import com.hrm.markdown.parser.ast.LeafNode
import com.hrm.markdown.parser.ast.Node
import com.hrm.markdown.parser.ast.PageBreak
import com.hrm.markdown.parser.ast.Paragraph
import com.hrm.markdown.parser.ast.SetextHeading
import com.hrm.markdown.parser.ast.TabBlock
import com.hrm.markdown.parser.ast.TabItem
import com.hrm.markdown.parser.ast.Table
import com.hrm.markdown.parser.ast.TableBody
import com.hrm.markdown.parser.ast.TableCell
import com.hrm.markdown.parser.ast.TableHead
import com.hrm.markdown.parser.ast.TableRow
import com.hrm.markdown.parser.ast.Text
import com.hrm.markdown.parser.ast.ThematicBreak
import com.hrm.markdown.parser.ast.TocPlaceholder
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromText
import com.hrm.markdown.renderer.internal.core.identity.renderIdentityFromValues
import com.hrm.markdown.renderer.internal.core.model.AdmonitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.AbbreviationMetadata
import com.hrm.markdown.renderer.internal.core.model.BibliographyDefinitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.BibliographyEntryBlockModel
import com.hrm.markdown.renderer.internal.core.model.BlockQuoteBlockModel
import com.hrm.markdown.renderer.internal.core.model.CodeBlockModel
import com.hrm.markdown.renderer.internal.core.model.CodeBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.ColumnBlockModel
import com.hrm.markdown.renderer.internal.core.model.ColumnsLayoutBlockModel
import com.hrm.markdown.renderer.internal.core.model.CustomContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.DefinitionDescriptionBlockModel
import com.hrm.markdown.renderer.internal.core.model.DefinitionListBlockModel
import com.hrm.markdown.renderer.internal.core.model.DefinitionTermBlockModel
import com.hrm.markdown.renderer.internal.core.model.DiagramBlockModel
import com.hrm.markdown.renderer.internal.core.model.DiagramBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.DirectiveBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackContainerBlockModel
import com.hrm.markdown.renderer.internal.core.model.FallbackLeafBlockModel
import com.hrm.markdown.renderer.internal.core.model.FigureBlockModel
import com.hrm.markdown.renderer.internal.core.model.FootnoteDefinitionMetadata
import com.hrm.markdown.renderer.internal.core.model.FootnoteDefinitionBlockModel
import com.hrm.markdown.renderer.internal.core.model.FrontMatterMetadata
import com.hrm.markdown.renderer.internal.core.model.HeadingBlockModel
import com.hrm.markdown.renderer.internal.core.model.HtmlBlockModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderBlockModel
import com.hrm.markdown.renderer.internal.core.model.InternalRenderDocumentMetadata
import com.hrm.markdown.renderer.internal.core.model.InternalRenderDocumentModel
import com.hrm.markdown.renderer.internal.core.model.LinkReferenceMetadata
import com.hrm.markdown.renderer.internal.core.model.ListBlockModel
import com.hrm.markdown.renderer.internal.core.model.ListItemBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockModel
import com.hrm.markdown.renderer.internal.core.model.MathBlockWidgetModel
import com.hrm.markdown.renderer.internal.core.model.PageBreakBlockModel
import com.hrm.markdown.renderer.internal.core.model.ParagraphBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabBlockModel
import com.hrm.markdown.renderer.internal.core.model.TabItemBlockModel
import com.hrm.markdown.renderer.internal.core.model.TableBlockModel
import com.hrm.markdown.renderer.internal.core.model.TableCellBlockModel
import com.hrm.markdown.renderer.internal.core.model.TableRowBlockModel
import com.hrm.markdown.renderer.internal.core.model.ThematicBreakBlockModel
import com.hrm.markdown.renderer.internal.core.model.TocBlockModel
import com.hrm.markdown.renderer.internal.core.model.TocEntryBlockModel

private data class CompileContext(
    val identities: RenderIdentityProvider,
    val headingNumbers: Map<Node, String>,
    val tocEntries: List<TocEntryBlockModel>,
)

object DefaultRenderModelCompiler : RenderModelCompiler {
    override fun compile(
        document: Document,
        environment: RenderCompileEnvironment,
    ): InternalRenderDocumentModel {
        val blockNodes = document.children.filter { it !is BlankLine }
        val context = CompileContext(
            identities = RenderIdentityProvider(document),
            headingNumbers = if (environment.config.enableHeadingNumbering) {
                computeHeadingNumbers(blockNodes)
            } else {
                emptyMap()
            },
            tocEntries = collectHeadingEntries(document),
        )
        val blocks = compileBlocks(blockNodes, context)
        return InternalRenderDocumentModel(
            identity = context.identities.identity(document),
            blocks = blocks,
            metadata = InternalRenderDocumentMetadata(
                footnotes = blocks.mapNotNull { block ->
                    val footnote = block as? FootnoteDefinitionBlockModel ?: return@mapNotNull null
                    footnote.label.takeIf { it.isNotBlank() }?.let { it to footnote.identity }
                }.toMap(),
                footnoteDefinitions = blocks.mapNotNull { block ->
                    val footnote = block as? FootnoteDefinitionBlockModel ?: return@mapNotNull null
                    FootnoteDefinitionMetadata(
                        label = footnote.label,
                        index = footnote.index,
                        identity = footnote.identity,
                    )
                },
                frontMatter = document.children.filterIsInstance<FrontMatter>().firstOrNull()?.let { frontMatter ->
                    FrontMatterMetadata(
                        format = frontMatter.format,
                        literal = frontMatter.literal,
                    )
                },
                linkReferences = document.linkDefinitions.values.map { reference ->
                    LinkReferenceMetadata(
                        label = reference.label,
                        destination = reference.destination,
                        title = reference.title,
                    )
                },
                abbreviations = document.abbreviationDefinitions.values.map { abbreviation ->
                    AbbreviationMetadata(
                        abbreviation = abbreviation.abbreviation,
                        fullText = abbreviation.fullText,
                    )
                },
            ),
        )
    }
}

private fun compileBlocks(
    nodes: List<Node>,
    context: CompileContext,
): List<InternalRenderBlockModel> {
    return nodes.filter { it !is BlankLine }.mapNotNull { node ->
        compileBlock(node, context)
    }
}

private fun compileBlock(
    node: Node,
    context: CompileContext,
): InternalRenderBlockModel? {
    val identity = context.identities.identity(node)
    return when (node) {
        is Paragraph -> ParagraphBlockModel(
            identity = identity,
            inline = compileInlineModel(node.children, context.identities.semanticRevision(node)),
        )

        is Heading -> HeadingBlockModel(
            identity = identity,
            level = node.level,
            numbering = context.headingNumbers[node],
            inline = compileInlineModel(node.children, context.identities.semanticRevision(node)),
        )

        is SetextHeading -> HeadingBlockModel(
            identity = identity,
            level = node.level,
            numbering = context.headingNumbers[node],
            inline = compileInlineModel(node.children, context.identities.semanticRevision(node)),
        )

        is ThematicBreak -> ThematicBreakBlockModel(identity)

        is FencedCodeBlock -> CodeBlockModel(
            identity = identity,
            language = node.language,
            title = node.attributes.pairs["title"],
            code = node.literal,
            showLineNumbers = node.showLineNumbers,
            startLine = node.startLineNumber,
            highlightedLines = node.highlightLines.flattenLineNumbers(),
            widget = CodeBlockWidgetModel(
                identity = identity,
                code = node.literal,
                language = node.language,
                title = node.attributes.pairs["title"],
            ),
        )

        is IndentedCodeBlock -> CodeBlockModel(
            identity = identity,
            language = "",
            title = null,
            code = node.literal,
            showLineNumbers = true,
            startLine = 1,
            highlightedLines = emptySet(),
            widget = CodeBlockWidgetModel(
                identity = identity,
                code = node.literal,
                language = "",
                title = null,
            ),
        )

        is MathBlock -> MathBlockModel(
            identity = identity,
            latex = node.literal,
            widget = MathBlockWidgetModel(identity, node.literal),
        )

        is DiagramBlock -> DiagramBlockModel(
            identity = identity,
            diagramType = node.diagramType,
            code = node.literal,
            widget = DiagramBlockWidgetModel(
                identity = identity,
                hostKey = diagramHostKey(node, identity.stableId),
                diagramType = node.diagramType,
                code = node.literal,
            ),
        )

        is HtmlBlock -> HtmlBlockModelCompiler.compile(node, identity) ?: HtmlBlockModel(
            identity = identity,
            html = node.literal,
        )

        is BlockQuote -> BlockQuoteBlockModel(
            identity = identity,
            children = compileBlocks(node.children, context),
        )

        is ListBlock -> ListBlockModel(
            identity = identity,
            ordered = node.ordered,
            startNumber = node.startNumber,
            bulletChar = node.bulletChar,
            delimiter = node.delimiter,
            tight = node.tight,
            items = node.children.filterIsInstance<ListItem>().map { compileListItem(it, context) },
        )

        is Table -> TableBlockModel(
            identity = identity,
            columnAlignments = node.columnAlignments,
            rows = buildList {
                node.children.filterIsInstance<TableHead>().firstOrNull()
                    ?.children?.filterIsInstance<TableRow>()
                    ?.forEach { add(compileTableRow(it, true, context)) }
                node.children.filterIsInstance<TableBody>().firstOrNull()
                    ?.children?.filterIsInstance<TableRow>()
                    ?.forEach { add(compileTableRow(it, false, context)) }
            },
        )

        is Admonition -> AdmonitionBlockModel(
            identity = identity,
            type = node.type,
            title = node.title,
            children = compileBlocks(node.children, context),
        )

        is CustomContainer -> CustomContainerBlockModel(
            identity = identity,
            type = node.type,
            title = node.title,
            cssClasses = node.cssClasses,
            cssId = node.cssId,
            children = compileBlocks(node.children, context),
        )

        is ColumnsLayout -> ColumnsLayoutBlockModel(
            identity = identity,
            columns = node.children.filterIsInstance<ColumnItem>().map { column ->
                ColumnBlockModel(
                    identity = context.identities.identity(column),
                    width = column.width,
                    children = compileBlocks(column.children, context),
                )
            },
        )

        is DefinitionList -> DefinitionListBlockModel(
            identity = identity,
            items = node.children.mapNotNull { child ->
                when (child) {
                    is DefinitionTerm -> DefinitionTermBlockModel(
                        identity = context.identities.identity(child),
                        inline = compileInlineModel(child.children, context.identities.semanticRevision(child)),
                    )
                    is DefinitionDescription -> DefinitionDescriptionBlockModel(
                        identity = context.identities.identity(child),
                        children = compileBlocks(child.children, context),
                    )
                    else -> null
                }
            },
        )

        is FootnoteDefinition -> FootnoteDefinitionBlockModel(
            identity = identity,
            label = node.label,
            index = node.index,
            children = compileBlocks(node.children, context),
        )

        is TocPlaceholder -> TocBlockModel(
            identity = identity,
            entries = context.tocEntries
                .filter { it.level in node.minDepth..node.maxDepth }
                .filter { node.excludeIds.isEmpty() || it.id == null || it.id !in node.excludeIds }
                .let { entries -> if (node.order == "desc") entries.reversed() else entries },
        )

        is PageBreak -> PageBreakBlockModel(identity)

        is DirectiveBlock -> DirectiveBlockModel(
            identity = identity,
            tagName = node.tagName,
            args = node.args,
            children = compileBlocks(node.children, context),
        )

        is TabBlock -> TabBlockModel(
            identity = identity,
            items = node.children.filterIsInstance<TabItem>().map { tab ->
                TabItemBlockModel(
                    identity = context.identities.identity(tab),
                    title = tab.title,
                    children = compileBlocks(tab.children, context),
                )
            },
        )

        is BibliographyDefinition -> BibliographyDefinitionBlockModel(
            identity = identity,
            entries = node.entries.values.map { entry ->
                BibliographyEntryBlockModel(
                    key = entry.key,
                    content = entry.content,
                )
            },
        )

        is Figure -> FigureBlockModel(
            identity = identity,
            imageUrl = node.imageUrl,
            caption = node.caption,
            imageWidth = node.imageWidth,
            imageHeight = node.imageHeight,
            attributes = node.attributes,
        )

        is FrontMatter,
        is LinkReferenceDefinition,
        is AbbreviationDefinition,
        is BlankLine -> null

        is ContainerNode -> FallbackContainerBlockModel(
            identity = identity,
            children = compileBlocks(node.children, context),
        )

        is LeafNode -> FallbackLeafBlockModel(identity)
    }
}

private fun compileListItem(
    node: ListItem,
    context: CompileContext,
): ListItemBlockModel {
    return ListItemBlockModel(
        identity = context.identities.identity(node),
        taskListItem = node.taskListItem,
        checked = node.checked,
        children = compileBlocks(node.children, context),
    )
}

private fun compileTableRow(
    row: TableRow,
    isHeader: Boolean,
    context: CompileContext,
): TableRowBlockModel {
    return TableRowBlockModel(
        identity = context.identities.identity(row),
        isHeader = isHeader,
        cells = row.children.filterIsInstance<TableCell>().map { cell ->
            TableCellBlockModel(
                identity = context.identities.identity(cell),
                alignment = cell.alignment,
                isHeader = isHeader || cell.isHeader,
                inline = compileInlineModel(cell.children, context.identities.semanticRevision(cell)),
            )
        },
    )
}

private fun collectHeadingEntries(document: Document): List<TocEntryBlockModel> {
    val result = mutableListOf<TocEntryBlockModel>()
    for (child in document.children) {
        collectHeadingEntriesRecursive(child, result)
    }
    return result
}

private fun collectHeadingEntriesRecursive(
    node: Node,
    sink: MutableList<TocEntryBlockModel>,
) {
    when (node) {
        is Heading -> sink += TocEntryBlockModel(
            text = extractPlainText(node),
            level = node.level,
            id = node.id,
        )
        is SetextHeading -> sink += TocEntryBlockModel(
            text = extractPlainText(node),
            level = node.level,
            id = node.id,
        )
        is ContainerNode -> node.children.forEach { collectHeadingEntriesRecursive(it, sink) }
        else -> Unit
    }
}

private fun diagramHostKey(node: DiagramBlock, stableId: Long): Long {
    return renderIdentityFromValues(
        renderIdentityFromText("DiagramHost"),
        renderIdentityFromText(node.diagramType.lowercase()),
        stableId,
    )
}

private fun List<IntRange>.flattenLineNumbers(): Set<Int> = buildSet {
    for (range in this@flattenLineNumbers) {
        addAll(range)
    }
}

private fun computeHeadingNumbers(nodes: List<Node>): Map<Node, String> {
    val counters = IntArray(6)
    val result = linkedMapOf<Node, String>()
    for (node in nodes) {
        val level = when (node) {
            is Heading -> node.level
            is SetextHeading -> node.level
            else -> continue
        }
        val index = (level - 1).coerceIn(0, counters.lastIndex)
        counters[index]++
        for (i in index + 1..counters.lastIndex) {
            counters[i] = 0
        }
        result[node] = buildString {
            for (i in 0..index) {
                if (isNotEmpty()) append('.')
                append(counters[i].coerceAtLeast(1))
            }
        }
    }
    return result
}

private fun extractPlainText(node: Node): String = buildString {
    when (node) {
        is Text -> append(node.literal)
        is InlineCode -> append(node.literal)
        is com.hrm.markdown.parser.ast.Image -> node.children.forEach { append(extractPlainText(it)) }
        is ContainerNode -> node.children.forEach { append(extractPlainText(it)) }
        else -> Unit
    }
}
