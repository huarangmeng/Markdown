package com.hrm.markdown.parser.html

import com.hrm.markdown.parser.core.HtmlEntities

/**
 * 结构化的行内 HTML 标签类型。
 *
 * 这里只描述 CommonMark 已经识别出的单个原始 HTML token，不尝试构建 DOM，
 * 也不负责匹配开始/结束标签。
 */
enum class HtmlTagKind {
    OPENING,
    CLOSING,
    SELF_CLOSING,
    COMMENT,
    PROCESSING_INSTRUCTION,
    DECLARATION,
    CDATA,
}

/**
 * 单个 HTML token 的结构化表示。
 *
 * [name] 与属性名统一为小写，属性值保持输入中的大小写、移除外围引号并解析 HTML 实体。
 */
data class HtmlTagToken(
    val kind: HtmlTagKind,
    val name: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * 解析单个 CommonMark 行内 HTML token。
 *
 * 该解析器刻意只处理一个标签，不做容错式 DOM 修复。调用方可以据此安全地将
 * HTML 语义映射到自己的渲染模型，同时继续用原始 literal 作为失败回退。
 */
object HtmlTagParser {
    fun parse(literal: String): HtmlTagToken? {
        if (literal.length < 3 || literal.first() != '<' || literal.last() != '>') return null

        return when {
            literal.startsWith("<!--") -> {
                if (literal == "<!-->" || literal == "<!--->" || literal.endsWith("-->")) {
                    HtmlTagToken(HtmlTagKind.COMMENT)
                } else {
                    null
                }
            }
            literal.startsWith("<![CDATA[", ignoreCase = true) -> {
                if (literal.endsWith("]]>")) HtmlTagToken(HtmlTagKind.CDATA) else null
            }
            literal.startsWith("<?") -> {
                if (literal.endsWith("?>")) HtmlTagToken(HtmlTagKind.PROCESSING_INSTRUCTION) else null
            }
            literal.startsWith("<!") -> {
                if (literal[2] in 'A'..'Z') HtmlTagToken(HtmlTagKind.DECLARATION) else null
            }
            literal.startsWith("</") -> parseClosingTag(literal)
            else -> parseOpeningTag(literal)
        }
    }

    private fun parseClosingTag(literal: String): HtmlTagToken? {
        var index = 2
        val nameStart = index
        if (index >= literal.lastIndex || !literal[index].isAsciiLetter()) return null
        index++
        while (index < literal.lastIndex && literal[index].isTagNameChar()) index++
        val name = literal.substring(nameStart, index).lowercase()
        while (index < literal.lastIndex && literal[index].isHtmlWhitespace()) index++
        if (index != literal.lastIndex) return null
        return HtmlTagToken(kind = HtmlTagKind.CLOSING, name = name)
    }

    private fun parseOpeningTag(literal: String): HtmlTagToken? {
        var index = 1
        val nameStart = index
        if (index >= literal.lastIndex || !literal[index].isAsciiLetter()) return null
        index++
        while (index < literal.lastIndex && literal[index].isTagNameChar()) index++
        val name = literal.substring(nameStart, index).lowercase()
        val attributes = linkedMapOf<String, String>()

        while (index < literal.lastIndex) {
            while (index < literal.lastIndex && literal[index].isHtmlWhitespace()) index++
            if (index >= literal.lastIndex) break

            if (literal[index] == '/') {
                if (index + 1 != literal.lastIndex) return null
                return HtmlTagToken(
                    kind = HtmlTagKind.SELF_CLOSING,
                    name = name,
                    attributes = attributes,
                )
            }

            val attributeStart = index
            if (!literal[index].isAttributeNameStart()) return null
            index++
            while (index < literal.lastIndex && literal[index].isAttributeNameChar()) index++
            val attributeName = literal.substring(attributeStart, index).lowercase()

            while (index < literal.lastIndex && literal[index].isHtmlWhitespace()) index++
            var attributeValue = ""
            if (index < literal.lastIndex && literal[index] == '=') {
                index++
                while (index < literal.lastIndex && literal[index].isHtmlWhitespace()) index++
                if (index >= literal.lastIndex) return null

                attributeValue = when (val quote = literal[index]) {
                    '\'', '"' -> {
                        index++
                        val valueStart = index
                        while (index < literal.lastIndex && literal[index] != quote) index++
                        if (index >= literal.lastIndex) return null
                        literal.substring(valueStart, index).also { index++ }
                    }
                    else -> {
                        val valueStart = index
                        while (
                            index < literal.lastIndex &&
                            literal[index].isUnquotedAttributeValueChar() &&
                            !(literal[index] == '/' && index + 1 == literal.lastIndex)
                        ) {
                            index++
                        }
                        if (valueStart == index) return null
                        literal.substring(valueStart, index)
                    }
                }
            }
            attributes[attributeName] = HtmlEntities.replaceAll(attributeValue)
        }

        return HtmlTagToken(
            kind = HtmlTagKind.OPENING,
            name = name,
            attributes = attributes,
        )
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun Char.isTagNameChar(): Boolean = isAsciiLetter() || isDigit() || this == '-'

    private fun Char.isAttributeNameStart(): Boolean = isAsciiLetter() || this == '_' || this == ':'

    private fun Char.isAttributeNameChar(): Boolean =
        isAttributeNameStart() || isDigit() || this == '.' || this == '-'

    private fun Char.isHtmlWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\n' || this == '\r'

    private fun Char.isUnquotedAttributeValueChar(): Boolean =
        !isHtmlWhitespace() && this !in charArrayOf('"', '\'', '=', '<', '>', '`')

}
