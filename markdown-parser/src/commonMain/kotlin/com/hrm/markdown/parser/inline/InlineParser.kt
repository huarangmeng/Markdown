package com.hrm.markdown.parser.inline

import com.hrm.markdown.parser.ast.*
import com.hrm.markdown.parser.core.CharacterUtils
import com.hrm.markdown.parser.core.HtmlEntities
import com.hrm.markdown.parser.block.BlockParser

/**
 * 高性能行内解析器，实现 CommonMark 分隔符算法。
 *
 * 使用链表方式处理分隔符（与参考实现一致），
 * 避免在处理强调/加重强调时出现索引失效问题。
 */
class InlineParser(
    private val document: Document
) : BlockParser.InlineParserInterface {

    override fun parseInlines(content: String, parent: ContainerNode) {
        if (content.isEmpty()) return
        val parser = InlineParserInstance(content, document)
        val nodes = parser.parse()
        for (node in nodes) {
            parent.appendChild(node)
        }
    }

    companion object {
        internal val AUTOLINK_REGEX = Regex("<([a-zA-Z][a-zA-Z0-9+.-]{1,31}:[^\\s<>]*)>")
        internal val EMAIL_AUTOLINK_REGEX = Regex("<([a-zA-Z0-9.!#\$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*)>")
        internal val INLINE_HTML_REGEX = Regex(
            """<(?:""" +
            """[a-zA-Z][a-zA-Z0-9-]*(?:\s+[a-zA-Z_:][a-zA-Z0-9_.:-]*(?:\s*=\s*(?:[^\s"'=<>`]+|'[^']*'|"[^"]*"))?)*\s*/?>""" +
            """|/[a-zA-Z][a-zA-Z0-9-]*\s*>""" +
            """|!--[\s\S]*?-->""" +
            """|\?[\s\S]*?\?>""" +
            """|![A-Z]+\s+[\s\S]*?>""" +
            """|!\[CDATA\[[\s\S]*?\]\]>""" +
            """)"""
        )
        internal val GFM_URL_REGEX = Regex("""(?:https?://|www\.)[^\s<]*""")
        internal val GFM_EMAIL_REGEX = Regex("""[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*""")
        internal val KBD_REGEX = Regex("""<kbd>(.*?)</kbd>""", RegexOption.IGNORE_CASE)
    }
}

/**
 * 处理单个行内内容字符串的内部实例。
 * 使用双向链表来存放"行内节点"，分隔符条目穿插其中。
 */
private class InlineParserInstance(
    private val input: String,
    private val document: Document
) {
    // 链表包装 AST 节点
    private class LLNode(var astNode: Node) {
        var prev: LLNode? = null
        var next: LLNode? = null
        var delimEntry: DelimEntry? = null
    }

    private class DelimEntry(
        val llNode: LLNode,
        val char: Char,
        var count: Int,
        val origCount: Int,
        val canOpen: Boolean,
        val canClose: Boolean
    ) {
        var prev: DelimEntry? = null
        var next: DelimEntry? = null
    }

    private class BracketEntry(
        val llNode: LLNode,
        val isImage: Boolean,
        var active: Boolean,
        val prevDelim: DelimEntry?, // delimiter stack bottom
        var prev: BracketEntry?
    )

    // 链表头/尾
    private var llHead: LLNode? = null
    private var llTail: LLNode? = null

    // 分隔符栈（双向链表）
    private var delimHead: DelimEntry? = null
    private var delimTail: DelimEntry? = null

    // 方括号栈
    private var bracketTop: BracketEntry? = null

    private val scanner = InlineScanner(input)

    fun parse(): List<Node> {
        // 第一阶段：扫描并构建链表
        while (!scanner.isAtEnd) {
            val c = scanner.peek()
            when {
                c == '\\' -> appendEscape()
                c == '`' -> appendBackticks()
                c == '<' -> appendAngleBracket()
                c == '&' -> appendEntity()
                c == '[' -> appendOpenBracket(isImage = false)
                c == '!' && scanner.peek(1) == '[' -> {
                    scanner.advance() // !
                    appendOpenBracket(isImage = true)
                }
                c == ']' -> appendCloseBracket()
                c == '*' || c == '_' -> appendDelimiterRun(c)
                c == '~' -> appendTildeRun()
                c == '=' && scanner.peek(1) == '=' -> appendPairedDelim('=', 2)
                c == '+' && scanner.peek(1) == '+' -> appendPairedDelim('+', 2)
                c == '^' -> appendPairedDelim('^', 1)
                c == '$' -> appendDollar()
                c == ':' -> appendPossibleEmoji()
                c == '\n' -> appendLineBreak()
                else -> appendText()
            }
        }

        // 第二阶段：处理强调/分隔符
        processEmphasis(null)

        // 第三阶段：收集结果并合并相邻的 Text 节点
        val result = mutableListOf<Node>()
        var cur = llHead
        while (cur != null) {
            val astNode = cur.astNode
            if (astNode is Text && result.isNotEmpty() && result.last() is Text) {
                // 合并相邻的文本节点
                (result.last() as Text).literal += astNode.literal
            } else {
                result.add(astNode)
            }
            cur = cur.next
        }
        return result
    }

    // ────── 链表操作 ──────

    private fun appendLL(node: Node): LLNode {
        val ll = LLNode(node)
        ll.prev = llTail
        if (llTail != null) {
            llTail!!.next = ll
        } else {
            llHead = ll
        }
        llTail = ll
        return ll
    }

    private fun insertAfterLL(after: LLNode, node: Node): LLNode {
        val ll = LLNode(node)
        ll.prev = after
        ll.next = after.next
        if (after.next != null) {
            after.next!!.prev = ll
        } else {
            llTail = ll
        }
        after.next = ll
        return ll
    }

    private fun removeLL(ll: LLNode) {
        if (ll.prev != null) ll.prev!!.next = ll.next else llHead = ll.next
        if (ll.next != null) ll.next!!.prev = ll.prev else llTail = ll.prev
    }

    // ────── 分隔符栈操作 ──────

    private fun pushDelim(entry: DelimEntry) {
        entry.prev = delimTail
        if (delimTail != null) delimTail!!.next = entry else delimHead = entry
        delimTail = entry
    }

    private fun removeDelim(entry: DelimEntry) {
        if (entry.prev != null) entry.prev!!.next = entry.next else delimHead = entry.next
        if (entry.next != null) entry.next!!.prev = entry.prev else delimTail = entry.prev
    }

    // ────── 扫描器 ──────

    private fun appendEscape() {
        val pos = scanner.pos
        scanner.advance() // skip '\'
        if (scanner.isAtEnd) {
            appendLL(Text("\\"))
            return
        }
        val next = scanner.peek()
        if (next == '\n') {
            scanner.advance()
            appendLL(HardLineBreak())
        } else if (CharacterUtils.isAsciiPunctuation(next)) {
            scanner.advance()
            appendLL(EscapedChar(next.toString()))
        } else {
            appendLL(Text("\\"))
        }
    }

    private fun appendBackticks() {
        val pos = scanner.pos
        var count = 0
        while (!scanner.isAtEnd && scanner.peek() == '`') {
            scanner.advance()
            count++
        }

        val startContent = scanner.pos
        val saved = scanner.pos
        while (!scanner.isAtEnd) {
            if (scanner.peek() == '`') {
                val closeStart = scanner.pos
                var closeCount = 0
                while (!scanner.isAtEnd && scanner.peek() == '`') {
                    scanner.advance()
                    closeCount++
                }
                if (closeCount == count) {
                    var content = input.substring(startContent, closeStart)
                    content = content.replace('\n', ' ')
                    if (content.length >= 2 && content[0] == ' ' && content.last() == ' ' && !content.all { it == ' ' }) {
                        content = content.drop(1).dropLast(1)
                    }
                    appendLL(InlineCode(content))
                    return
                }
            } else {
                scanner.advance()
            }
        }
        scanner.pos = saved
        appendLL(Text("`".repeat(count)))
    }

    private fun appendAngleBracket() {
        val pos = scanner.pos
        scanner.advance() // skip '<'

        // 轻量前缀检查：仅当下一个字符可能开始有效结构时才启用正则
        if (!scanner.isAtEnd) {
            val nextChar = scanner.peek()

            // 自动链接：<scheme:...>  — 下一个字符必须是字母
            if (nextChar.isLetter()) {
                val autolinkMatch = InlineParser.AUTOLINK_REGEX.find(input, pos)
                if (autolinkMatch != null && autolinkMatch.range.first == pos) {
                    scanner.pos = autolinkMatch.range.last + 1
                    appendLL(Autolink(destination = CharacterUtils.percentEncodeUrl(autolinkMatch.groupValues[1]), isEmail = false))
                    return
                }

                // 邮件自动链接也以字母开头
                val emailMatch = InlineParser.EMAIL_AUTOLINK_REGEX.find(input, pos)
                if (emailMatch != null && emailMatch.range.first == pos) {
                    scanner.pos = emailMatch.range.last + 1
                    appendLL(Autolink(destination = emailMatch.groupValues[1], isEmail = true))
                    return
                }
            }

            // <kbd>...</kbd> 键盘按键标签
            if (nextChar == 'k' || nextChar == 'K') {
                val kbdMatch = InlineParser.KBD_REGEX.find(input, pos)
                if (kbdMatch != null && kbdMatch.range.first == pos) {
                    scanner.pos = kbdMatch.range.last + 1
                    appendLL(KeyboardInput(kbdMatch.groupValues[1]))
                    return
                }
            }

            // 行内 HTML：<tag>、</tag>、<!--、<?、<!、<![CDATA[
            if (nextChar.isLetter() || nextChar == '/' || nextChar == '!' || nextChar == '?') {
                val htmlMatch = InlineParser.INLINE_HTML_REGEX.find(input, pos)
                if (htmlMatch != null && htmlMatch.range.first == pos) {
                    scanner.pos = htmlMatch.range.last + 1
                    appendLL(InlineHtml(htmlMatch.value))
                    return
                }
            }
        }

        appendLL(Text("<"))
    }

    private fun appendEntity() {
        val pos = scanner.pos
        val match = HtmlEntities.matchAt(input, pos)
        if (match != null) {
            val entity = match.value
            val resolved = HtmlEntities.resolve(entity)
            scanner.pos = pos + entity.length
            if (resolved != null) {
                appendLL(HtmlEntity(entity, resolved))
            } else {
                appendLL(Text(entity))
            }
        } else {
            scanner.advance()
            appendLL(Text("&"))
        }
    }

    private fun appendOpenBracket(isImage: Boolean) {
        scanner.advance() // skip '['
        val text = if (isImage) "![" else "["
        val ll = appendLL(Text(text))
        bracketTop = BracketEntry(
            llNode = ll,
            isImage = isImage,
            active = true,
            prevDelim = delimTail,
            prev = bracketTop
        )
    }

    private fun appendCloseBracket() {
        scanner.advance() // skip ']'
        val bracket = bracketTop
        if (bracket == null || !bracket.active) {
            appendLL(Text("]"))
            if (bracket != null) bracketTop = bracket.prev
            return
        }

        // 尝试脚注引用：[^label]
        if (!bracket.isImage) {
            val footnoteRef = tryParseFootnoteReference(bracket)
            if (footnoteRef != null) {
                // 移除方括号内的所有节点
                var cur = bracket.llNode.next
                while (cur != null) {
                    val next = cur.next
                    removeLL(cur)
                    cur.delimEntry?.let { removeDelim(it) }
                    cur = next
                }
                // 用脚注引用节点替换方括号开始符
                bracket.llNode.astNode = footnoteRef
                bracketTop = bracket.prev
                return
            }
        }

        // 尝试行内链接：[text](url "title")
        val linkResult = tryParseLinkTail()
        if (linkResult != null) {
            // 处理方括号内容中的强调
            processEmphasis(bracket.prevDelim)

            val node: ContainerNode = if (bracket.isImage) {
                Image(destination = linkResult.first, title = linkResult.second)
            } else {
                Link(destination = linkResult.first, title = linkResult.second)
            }

            // 收集方括号开始符和当前位置之间的节点
            var cur = bracket.llNode.next
            while (cur != null) {
                val next = cur.next
                removeLL(cur)
                node.appendChild(cur.astNode)
                cur = next
            }

            // 用链接/图片节点替换方括号开始符
            bracket.llNode.astNode = node

            bracketTop = bracket.prev

            // 对于链接，停用之前的方括号
            if (!bracket.isImage) {
                var b = bracketTop
                while (b != null) {
                    if (!b.isImage) b.active = false
                    b = b.prev
                }
            }
            return
        }

        // 尝试引用链接
        val refResult = tryParseRefLink(bracket)
        if (refResult != null) {
            processEmphasis(bracket.prevDelim)

            val node: ContainerNode = if (bracket.isImage) {
                Image(destination = refResult.first, title = refResult.second)
            } else {
                Link(destination = refResult.first, title = refResult.second)
            }

            var cur = bracket.llNode.next
            while (cur != null) {
                val next = cur.next
                removeLL(cur)
                node.appendChild(cur.astNode)
                cur = next
            }

            bracket.llNode.astNode = node
            bracketTop = bracket.prev

            if (!bracket.isImage) {
                var b = bracketTop
                while (b != null) {
                    if (!b.isImage) b.active = false
                    b = b.prev
                }
            }
            return
        }

        // 不是链接
        bracketTop = bracket.prev
        appendLL(Text("]"))
    }

    private fun appendDelimiterRun(delimChar: Char) {
        val pos = scanner.pos
        var count = 0
        while (!scanner.isAtEnd && scanner.peek() == delimChar) {
            scanner.advance()
            count++
        }

        val charBefore = if (pos > 0) input[pos - 1] else '\n'
        val charAfter = if (scanner.pos < input.length) input[scanner.pos] else '\n'

        val leftFlanking = !CharacterUtils.isUnicodeWhitespace(charAfter) &&
                (!CharacterUtils.isUnicodePunctuation(charAfter) ||
                        CharacterUtils.isUnicodeWhitespace(charBefore) ||
                        CharacterUtils.isUnicodePunctuation(charBefore))

        val rightFlanking = !CharacterUtils.isUnicodeWhitespace(charBefore) &&
                (!CharacterUtils.isUnicodePunctuation(charBefore) ||
                        CharacterUtils.isUnicodeWhitespace(charAfter) ||
                        CharacterUtils.isUnicodePunctuation(charAfter))

        val canOpen = if (delimChar == '_') {
            leftFlanking && (!rightFlanking || CharacterUtils.isUnicodePunctuation(charBefore))
        } else {
            leftFlanking
        }

        val canClose = if (delimChar == '_') {
            rightFlanking && (!leftFlanking || CharacterUtils.isUnicodePunctuation(charAfter))
        } else {
            rightFlanking
        }

        val textNode = Text(delimChar.toString().repeat(count))
        val ll = appendLL(textNode)

        if (canOpen || canClose) {
            val entry = DelimEntry(ll, delimChar, count, count, canOpen, canClose)
            ll.delimEntry = entry
            pushDelim(entry)
        }
    }

    private fun appendTildeRun() {
        val pos = scanner.pos
        var count = 0
        while (!scanner.isAtEnd && scanner.peek() == '~') {
            scanner.advance()
            count++
        }

        if (count == 2) {
            val charBefore = if (pos > 0) input[pos - 1] else '\n'
            val charAfter = if (scanner.pos < input.length) input[scanner.pos] else '\n'
            val canOpen = !CharacterUtils.isUnicodeWhitespace(charAfter)
            val canClose = !CharacterUtils.isUnicodeWhitespace(charBefore)
            val textNode = Text("~~")
            val ll = appendLL(textNode)
            if (canOpen || canClose) {
                val entry = DelimEntry(ll, '~', 2, 2, canOpen, canClose)
                ll.delimEntry = entry
                pushDelim(entry)
            }
        } else if (count == 1) {
            val charBefore = if (pos > 0) input[pos - 1] else '\n'
            val charAfter = if (scanner.pos < input.length) input[scanner.pos] else '\n'
            val canOpen = !CharacterUtils.isUnicodeWhitespace(charAfter)
            val canClose = !CharacterUtils.isUnicodeWhitespace(charBefore)
            val textNode = Text("~")
            val ll = appendLL(textNode)
            if (canOpen || canClose) {
                val entry = DelimEntry(ll, '~', 1, 1, canOpen, canClose)
                ll.delimEntry = entry
                pushDelim(entry)
            }
        } else {
            appendLL(Text("~".repeat(count)))
        }
    }

    private fun appendPairedDelim(char: Char, count: Int) {
        val pos = scanner.pos
        repeat(count) { scanner.advance() }
        val charBefore = if (pos > 0) input[pos - 1] else '\n'
        val charAfter = if (scanner.pos < input.length) input[scanner.pos] else '\n'
        val canOpen = !CharacterUtils.isUnicodeWhitespace(charAfter)
        val canClose = !CharacterUtils.isUnicodeWhitespace(charBefore)
        val textNode = Text(char.toString().repeat(count))
        val ll = appendLL(textNode)
        if (canOpen || canClose) {
            val entry = DelimEntry(ll, char, count, count, canOpen, canClose)
            ll.delimEntry = entry
            pushDelim(entry)
        }
    }

    private fun appendDollar() {
        val pos = scanner.pos
        if (scanner.peek(1) == '$') {
            scanner.advance()
            scanner.advance()
            val startContent = scanner.pos
            val endIdx = input.indexOf("$$", startContent)
            if (endIdx >= 0) {
                val content = input.substring(startContent, endIdx)
                scanner.pos = endIdx + 2
                appendLL(InlineMath(content))
                return
            }
            appendLL(Text("$$"))
            return
        }

        scanner.advance()
        if (pos > 0 && input[pos - 1].isDigit()) {
            appendLL(Text("$"))
            return
        }
        if (scanner.isAtEnd || scanner.peek().isWhitespace()) {
            appendLL(Text("$"))
            return
        }

        val startContent = scanner.pos
        val savedPos = scanner.pos
        while (!scanner.isAtEnd) {
            if (scanner.peek() == '$') {
                val content = input.substring(startContent, scanner.pos)
                scanner.advance()
                if (content.isNotEmpty() && !content.last().isWhitespace()) {
                    appendLL(InlineMath(content))
                    return
                }
                appendLL(Text("\$$content\$"))
                return
            }
            if (scanner.peek() == '\\' && scanner.pos + 1 < input.length) {
                scanner.advance()
            }
            scanner.advance()
        }
        scanner.pos = pos + 1
        appendLL(Text("$"))
    }

    private fun appendPossibleEmoji() {
        val pos = scanner.pos
        scanner.advance() // skip ':'
        if (scanner.isAtEnd || !(scanner.peek().isLetterOrDigit() || scanner.peek() == '_' || scanner.peek() == '+' || scanner.peek() == '-')) {
            appendLL(Text(":"))
            return
        }

        val startName = scanner.pos
        while (!scanner.isAtEnd && (scanner.peek().isLetterOrDigit() || scanner.peek() == '_' || scanner.peek() == '-' || scanner.peek() == '+')) {
            scanner.advance()
        }

        if (!scanner.isAtEnd && scanner.peek() == ':') {
            val name = input.substring(startName, scanner.pos)
            scanner.advance()
            appendLL(Emoji(shortcode = name, literal = ":$name:"))
            return
        }

        scanner.pos = pos + 1
        appendLL(Text(":"))
    }

    private fun appendLineBreak() {
        val pos = scanner.pos
        scanner.advance()
        if (pos >= 2 && input[pos - 1] == ' ' && input[pos - 2] == ' ') {
            appendLL(HardLineBreak())
        } else {
            appendLL(SoftLineBreak())
        }
    }

    private fun appendText() {
        val sb = StringBuilder()
        while (!scanner.isAtEnd) {
            val c = scanner.peek()
            if (c == '\\' || c == '`' || c == '<' || c == '&' || c == '[' || c == ']' ||
                c == '*' || c == '_' || c == '~' || c == '\n' || c == '$' || c == ':' || c == '^') {
                break
            }
            if (c == '!' && scanner.peek(1) == '[') break
            if (c == '=' && scanner.peek(1) == '=') break
            if (c == '+' && scanner.peek(1) == '+') break

            // GFM 自动链接检测（在任意位置触发，不仅仅是文本开头）
            if (c == 'h' || c == 'H' || c == 'w' || c == 'W') {
                val remaining = input.substring(scanner.pos)
                val urlMatch = InlineParser.GFM_URL_REGEX.find(remaining)
                if (urlMatch != null && urlMatch.range.first == 0) {
                    // 先输出已收集的文本
                    if (sb.isNotEmpty()) {
                        appendLL(Text(sb.toString()))
                        sb.clear()
                    }
                    var url = urlMatch.value
                    url = trimAutolinkTrailing(url)
                    scanner.pos += url.length
                    val fullUrl = if (url.lowercase().startsWith("www.")) "http://$url" else url
                    val encodedUrl = CharacterUtils.percentEncodeUrl(fullUrl)
                    val link = Link(destination = encodedUrl)
                    link.appendChild(Text(url))
                    appendLL(link)
                    return
                }
            }

            sb.append(c)
            scanner.advance()
        }
        if (sb.isNotEmpty()) {
            appendLL(Text(sb.toString()))
        }
    }

    private fun trimAutolinkTrailing(url: String): String {
        var result = url
        while (result.isNotEmpty()) {
            val last = result.last()
            when {
                last == '.' || last == ',' || last == ':' || last == ';' ||
                        last == '!' || last == '?' || last == '\'' || last == '"' -> {
                    result = result.dropLast(1)
                }
                last == ')' -> {
                    if (result.count { it == ')' } > result.count { it == '(' }) {
                        result = result.dropLast(1)
                    } else break
                }
                else -> break
            }
        }
        return result
    }

    // ────── 强调处理（CommonMark 算法） ──────

    private fun processEmphasis(stackBottom: DelimEntry?) {
        // 查找 stackBottom 上方的第一个分隔符
        var closer = if (stackBottom != null) stackBottom.next else delimHead

        while (closer != null) {
            if (!closer.canClose) {
                closer = closer.next
                continue
            }

            // 查找匹配的开始分隔符
            var opener = closer.prev
            var openerFound = false
            while (opener != null && opener !== stackBottom) {
                if (opener.canOpen && opener.char == closer.char && delimsMatch(opener, closer)) {
                    openerFound = true
                    break
                }
                opener = opener.prev
            }

            if (!openerFound || opener == null) {
                closer = closer.next
                continue
            }

            // 创建适当的包装节点
            val wrapperNode: ContainerNode
            val useCount: Int

            when (closer.char) {
                '*', '_' -> {
                    useCount = if (opener.count >= 2 && closer.count >= 2) 2 else 1
                    wrapperNode = if (useCount == 2) {
                        StrongEmphasis().also { it.delimiter = closer.char }
                    } else {
                        Emphasis().also { it.delimiter = closer.char }
                    }
                }
                '~' -> {
                    if (opener.count >= 2 && closer.count >= 2) {
                        useCount = 2
                        wrapperNode = Strikethrough()
                    } else {
                        useCount = 1
                        wrapperNode = Subscript()
                    }
                }
                '=' -> {
                    useCount = 2
                    wrapperNode = Highlight()
                }
                '+' -> {
                    useCount = 2
                    wrapperNode = InsertedText()
                }
                '^' -> {
                    useCount = 1
                    wrapperNode = Superscript()
                }
                else -> {
                    closer = closer.next
                    continue
                }
            }

            opener.count -= useCount
            closer.count -= useCount

            // 更新开始和关闭分隔符的文本
            if (opener.count > 0) {
                (opener.llNode.astNode as Text).literal = opener.char.toString().repeat(opener.count)
            }
            if (closer.count > 0) {
                (closer.llNode.astNode as Text).literal = closer.char.toString().repeat(closer.count)
            }

            // 将开始和关闭分隔符之间的节点移入包装节点
            var node = opener.llNode.next
            while (node != null && node !== closer.llNode) {
                val next = node.next
                removeLL(node)
                // 如果有分隔符条目，也从分隔符栈中移除
                node.delimEntry?.let { removeDelim(it) }
                wrapperNode.appendChild(node.astNode)
                node = next
            }

            // 在开始分隔符之后插入包装节点
            val wrapperLL = insertAfterLL(opener.llNode, wrapperNode)

            // 如果计数为 0 则移除开始分隔符
            if (opener.count == 0) {
                removeLL(opener.llNode)
                removeDelim(opener)
            }

            // 如果计数为 0 则移除关闭分隔符，并前进
            if (closer.count == 0) {
                val nextCloser = closer.next
                removeLL(closer.llNode)
                removeDelim(closer)
                closer = nextCloser
            } else {
                closer = closer.next
            }
        }

        // 移除 stackBottom 上方的剩余分隔符
        var d = if (stackBottom != null) stackBottom.next else delimHead
        while (d != null) {
            val next = d.next
            removeDelim(d)
            d = next
        }
    }

    private fun delimsMatch(opener: DelimEntry, closer: DelimEntry): Boolean {
        if (opener.char != closer.char) return false
        if (opener.char == '*' || opener.char == '_') {
            if ((opener.canOpen && opener.canClose) || (closer.canOpen && closer.canClose)) {
                if ((opener.origCount + closer.origCount) % 3 == 0 &&
                    opener.origCount % 3 != 0 && closer.origCount % 3 != 0
                ) {
                    return false
                }
            }
        }
        if (opener.char == '~') {
            // 双 ~ 匹配删除线，单 ~ 匹配下标，不混合
            if (opener.count >= 2 && closer.count >= 2) return true
            if (opener.count == 1 && closer.count == 1) return true
            return false
        }
        if (opener.char == '=' || opener.char == '+') {
            return opener.count >= 2 && closer.count >= 2
        }
        return true
    }

    // ────── 链接解析 ──────

    private fun tryParseLinkTail(): Pair<String, String?>? {
        val pos = scanner.pos
        if (pos >= input.length || input[pos] != '(') return null

        var i = pos + 1
        while (i < input.length && (input[i] == ' ' || input[i] == '\t' || input[i] == '\n')) i++

        if (i >= input.length) return null

        // 空链接 ()
        if (input[i] == ')') {
            scanner.pos = i + 1
            return Pair("", null)
        }

        val (dest, nextPos, isAngleBracket) = parseLinkDestination(i) ?: return null
        i = nextPos

        while (i < input.length && (input[i] == ' ' || input[i] == '\t' || input[i] == '\n')) i++

        var title: String? = null
        if (i < input.length && (input[i] == '"' || input[i] == '\'' || input[i] == '(')) {
            val (t, tNext) = parseLinkTitle(i) ?: return null
            title = t
            i = tNext
            while (i < input.length && (input[i] == ' ' || input[i] == '\t')) i++
        }

        if (i >= input.length || input[i] != ')') return null
        scanner.pos = i + 1
        // 仅对非尖括号包裹的 URL 进行百分号编码
        val finalDest = if (!isAngleBracket) CharacterUtils.percentEncodeUrl(dest) else dest
        return Pair(finalDest, title)
    }

    private data class LinkDestResult(val dest: String, val nextPos: Int, val isAngleBracket: Boolean)

    private fun parseLinkDestination(start: Int): LinkDestResult? {
        var i = start
        if (i >= input.length) return null

        if (input[i] == '<') {
            i++
            val sb = StringBuilder()
            while (i < input.length && input[i] != '>') {
                if (input[i] == '<') return null
                if (input[i] == '\\' && i + 1 < input.length) {
                    i++
                    sb.append(input[i])
                } else {
                    sb.append(input[i])
                }
                i++
            }
            if (i >= input.length) return null
            i++
            return LinkDestResult(sb.toString(), i, true)
        }

        val sb = StringBuilder()
        var parenDepth = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == ' ' || c == '\t' || c == '\n' -> break
                c == ')' && parenDepth == 0 -> break
                c == '(' -> { parenDepth++; sb.append(c) }
                c == ')' -> { parenDepth--; sb.append(c) }
                c == '\\' && i + 1 < input.length -> { i++; sb.append(input[i]) }
                c.code < 0x20 -> break
                else -> sb.append(c)
            }
            i++
        }
        return LinkDestResult(sb.toString(), i, false)
    }

    private fun parseLinkTitle(start: Int): Pair<String, Int>? {
        var i = start
        if (i >= input.length) return null
        val openChar = input[i]
        val closeChar = when (openChar) {
            '"' -> '"'; '\'' -> '\''; '(' -> ')'; else -> return null
        }
        i++
        val sb = StringBuilder()
        while (i < input.length && input[i] != closeChar) {
            if (input[i] == '\\' && i + 1 < input.length) {
                i++
                sb.append(input[i])
            } else {
                sb.append(input[i])
            }
            i++
        }
        if (i >= input.length) return null
        i++
        return Pair(sb.toString(), i)
    }

    private fun tryParseRefLink(bracket: BracketEntry): Pair<String, String?>? {
        val pos = scanner.pos

        // [text][label]
        if (pos < input.length && input[pos] == '[') {
            val closeIdx = input.indexOf(']', pos + 1)
            if (closeIdx >= 0) {
                val label = input.substring(pos + 1, closeIdx)
                val normalized = CharacterUtils.normalizeLinkLabel(label)
                val def = document.linkDefinitions[normalized]
                if (def != null) {
                    scanner.pos = closeIdx + 1
                    return Pair(def.destination, def.title)
                }
            }
        }

        // 折叠 [label][] 或简写 [label]
        val textContent = extractBracketText(bracket)
        val normalized = CharacterUtils.normalizeLinkLabel(textContent)
        val def = document.linkDefinitions[normalized]
        if (def != null) {
            // 检查 []
            if (pos + 1 < input.length && input[pos] == '[' && input[pos + 1] == ']') {
                scanner.pos = pos + 2
            }
            return Pair(def.destination, def.title)
        }

        return null
    }

    private fun tryParseFootnoteReference(bracket: BracketEntry): FootnoteReference? {
        // 提取方括号内的文本内容
        val text = extractBracketText(bracket)
        // 脚注引用格式：[^label]，内容必须以 ^ 开头
        if (!text.startsWith("^")) return null
        val label = text.substring(1)
        if (label.isEmpty() || label.contains(' ') || label.contains('\n')) return null

        // 不后跟 ( 或 [（否则可能是链接）
        val pos = scanner.pos
        if (pos < input.length && (input[pos] == '(' || input[pos] == '[')) return null

        // 查找对应的脚注定义
        val def = document.footnoteDefinitions[label]
        val index = if (def != null) {
            if (def.index == 0) {
                def.index = document.footnoteDefinitions.values.count { it.index > 0 } + 1
            }
            def.index
        } else {
            0
        }

        return FootnoteReference(label = label, index = index)
    }

    private fun extractBracketText(bracket: BracketEntry): String {
        val sb = StringBuilder()
        var cur = bracket.llNode.next
        while (cur != null) {
            sb.append(nodeToText(cur.astNode))
            cur = cur.next
        }
        return sb.toString()
    }

    private fun nodeToText(node: Node): String = when (node) {
        is Text -> node.literal
        is InlineCode -> node.literal
        is EscapedChar -> node.literal
        is SoftLineBreak -> " "
        is HardLineBreak -> " "
        is ContainerNode -> node.children.joinToString("") { nodeToText(it) }
        is LeafNode -> node.literal
    }
}

// ────── 行内扫描器 ──────

internal class InlineScanner(val text: String) {
    var pos: Int = 0

    val isAtEnd: Boolean get() = pos >= text.length

    fun peek(): Char = if (pos < text.length) text[pos] else '\u0000'
    fun peek(offset: Int): Char {
        val idx = pos + offset
        return if (idx in text.indices) text[idx] else '\u0000'
    }

    fun advance(): Char {
        if (pos >= text.length) return '\u0000'
        return text[pos++]
    }
}
