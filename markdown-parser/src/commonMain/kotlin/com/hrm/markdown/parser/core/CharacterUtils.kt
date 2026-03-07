package com.hrm.markdown.parser.core

/**
 * 用于 Markdown 解析的 Unicode 字符分类工具。
 * 用于强调分隔符规则、自动链接检测等。
 */
object CharacterUtils {

    /** 可用反斜杠转义的 ASCII 标点字符。 */
    const val ESCAPABLE_CHARS = "\\`*_{}[]()#+-.!|~<>\"'/^=:;&@"

    /** 所有 ASCII 标点字符。 */
    private const val ASCII_PUNCTUATION = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    fun isAsciiPunctuation(c: Char): Boolean = c in ASCII_PUNCTUATION

    fun isEscapable(c: Char): Boolean = isAsciiPunctuation(c)

    /**
     * Unicode 标点：ASCII 标点或 Unicode 类别 P（标点）或 S（符号）。
     */
    fun isUnicodePunctuation(c: Char): Boolean {
        if (isAsciiPunctuation(c)) return true
        val category = c.category
        return category == CharCategory.DASH_PUNCTUATION ||
                category == CharCategory.START_PUNCTUATION ||
                category == CharCategory.END_PUNCTUATION ||
                category == CharCategory.CONNECTOR_PUNCTUATION ||
                category == CharCategory.OTHER_PUNCTUATION ||
                category == CharCategory.INITIAL_QUOTE_PUNCTUATION ||
                category == CharCategory.FINAL_QUOTE_PUNCTUATION ||
                category == CharCategory.MATH_SYMBOL ||
                category == CharCategory.CURRENCY_SYMBOL ||
                category == CharCategory.MODIFIER_SYMBOL ||
                category == CharCategory.OTHER_SYMBOL
    }

    /**
     * Unicode 空白：空格、制表符、换行符、换页符、回车符或 Unicode Zs 类别。
     */
    fun isUnicodeWhitespace(c: Char): Boolean {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000C' || c == '\r' ||
                c.category == CharCategory.SPACE_SEPARATOR
    }

    fun isSpaceOrTab(c: Char): Boolean = c == ' ' || c == '\t'

    fun isBlank(line: String): Boolean = line.all { isSpaceOrTab(it) }

    /**
     * 计算前导空格数（制表符按 4 空格制表位计算）。
     */
    fun countLeadingSpaces(line: String): Int {
        var spaces = 0
        for (c in line) {
            when (c) {
                ' ' -> spaces++
                '\t' -> spaces = ((spaces / 4) + 1) * 4
                else -> break
            }
        }
        return spaces
    }

    /**
     * 从行首移除最多 [n] 个空格，展开制表符。
     */
    fun removeLeadingSpaces(line: String, n: Int): String {
        var spaces = 0
        var i = 0
        while (i < line.length && spaces < n) {
            when (line[i]) {
                ' ' -> {
                    spaces++
                    i++
                }
                '\t' -> {
                    val tabWidth = 4 - (spaces % 4)
                    if (spaces + tabWidth > n) {
                        // 部分制表符：用剩余空格替换
                        val remaining = n - spaces
                        return " ".repeat(tabWidth - remaining) + line.substring(i + 1)
                    }
                    spaces += tabWidth
                    i++
                }
                else -> break
            }
        }
        return line.substring(i)
    }

    /**
     * 规范化链接标签：去除首尾空格，折叠内部空白，应用 unicode case folding。
     * uses case folding rather than simple lowercase to handle cases like
     * ẞ (U+1E9E) -> "ss" and ß (U+00DF) -> "ss".
     */
    fun normalizeLinkLabel(label: String): String {
        return unicodeCaseFold(label.trim().replace(Regex("\\s+"), " "))
    }

    /**
     * simple unicode case folding: lowercase + special cases where
     * lowercase() does not match full case folding (e.g. ẞ -> ss, ß -> ss).
     */
    private fun unicodeCaseFold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                '\u1E9E' -> sb.append("ss") // latin capital letter sharp s
                '\u00DF' -> sb.append("ss") // latin small letter sharp s
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * 对 URL 中的特殊字符进行百分号编码。
     * 保留已编码的 %XX 序列和 URL 合法字符，仅编码非法字符。
     */
    fun percentEncodeUrl(url: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < url.length) {
            val c = url[i]
            when {
                // 保留已有的百分号编码
                c == '%' && i + 2 < url.length &&
                        url[i + 1].isHexDigit() && url[i + 2].isHexDigit() -> {
                    sb.append(url, i, i + 3)
                    i += 3
                }
                // URL 合法字符不编码（仅 ASCII 范围）
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
                        c in "-._~:/?#@!$&'()*+,;=" -> {
                    sb.append(c)
                    i++
                }
                else -> {
                    // 对非 ASCII 字符进行 UTF-8 百分号编码
                    val str = c.toString()
                    val bytes = str.encodeToByteArray()
                    for (b in bytes) {
                        sb.append('%')
                        sb.append(HEX_DIGITS[(b.toInt() and 0xFF) shr 4])
                        sb.append(HEX_DIGITS[b.toInt() and 0x0F])
                    }
                    i++
                }
            }
        }
        return sb.toString()
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private val HEX_DIGITS = "0123456789ABCDEF".toCharArray()

}