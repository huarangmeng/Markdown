package com.hrm.markdown.parser.html

/** HTML fragment 中保持源码顺序的 token。 */
sealed interface HtmlFragmentToken {
    val literal: String

    data class Text(
        override val literal: String,
    ) : HtmlFragmentToken

    data class Tag(
        override val literal: String,
        val tag: HtmlTagToken,
    ) : HtmlFragmentToken
}

/**
 * 将原始 HTML fragment 分解为文本与单标签 token。
 *
 * 这里只识别 token 边界，不构建 DOM，也不做浏览器式错误修复。遇到未终止的标签、
 * 注释、CDATA 或处理指令时返回 `null`，让调用方安全回退到原始 HTML。
 */
object HtmlFragmentTokenizer {
    fun tokenize(fragment: String): List<HtmlFragmentToken>? {
        val tokens = mutableListOf<HtmlFragmentToken>()
        var cursor = 0

        fun appendText(text: String) {
            if (text.isEmpty()) return
            val previous = tokens.lastOrNull()
            if (previous is HtmlFragmentToken.Text) {
                tokens[tokens.lastIndex] = previous.copy(literal = previous.literal + text)
            } else {
                tokens += HtmlFragmentToken.Text(text)
            }
        }

        while (cursor < fragment.length) {
            val tagStart = fragment.indexOf('<', cursor)
            if (tagStart < 0) {
                appendText(fragment.substring(cursor))
                break
            }
            appendText(fragment.substring(cursor, tagStart))

            val tagEnd = findTagEnd(fragment, tagStart) ?: return null
            val literal = fragment.substring(tagStart, tagEnd)
            val tag = HtmlTagParser.parse(literal)
            if (tag == null) {
                appendText("<")
                cursor = tagStart + 1
            } else {
                tokens += HtmlFragmentToken.Tag(literal, tag)
                cursor = tagEnd
            }
        }

        return tokens
    }

    private fun findTagEnd(fragment: String, start: Int): Int? {
        if (fragment.startsWith("<!-->", start)) return start + "<!-->".length
        if (fragment.startsWith("<!--->", start)) return start + "<!--->".length
        if (fragment.startsWith("<!--", start)) {
            val end = fragment.indexOf("-->", start + 4)
            return end.takeIf { it >= 0 }?.plus(3)
        }
        if (fragment.startsWith("<![CDATA[", start, ignoreCase = true)) {
            val end = fragment.indexOf("]]>", start + 9)
            return end.takeIf { it >= 0 }?.plus(3)
        }
        if (fragment.startsWith("<?", start)) {
            val end = fragment.indexOf("?>", start + 2)
            return end.takeIf { it >= 0 }?.plus(2)
        }

        var quote: Char? = null
        var index = start + 1
        while (index < fragment.length) {
            val char = fragment[index]
            when {
                quote != null && char == quote -> quote = null
                quote == null && (char == '\'' || char == '"') -> quote = char
                quote == null && char == '>' -> return index + 1
            }
            index++
        }
        return null
    }
}
