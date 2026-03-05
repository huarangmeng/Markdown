package com.hrm.markdown.renderer.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal enum class TokenType {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    FUNCTION,
    TYPE,
    OPERATOR,
    PUNCTUATION,
    PLAIN,
}

internal data class Token(val type: TokenType, val text: String)

data class SyntaxColors(
    val keyword: Color = Color(0xFFCF222E),
    val string: Color = Color(0xFF0A3069),
    val number: Color = Color(0xFF0550AE),
    val comment: Color = Color(0xFF6E7781),
    val annotation: Color = Color(0xFF8250DF),
    val function: Color = Color(0xFF8250DF),
    val type: Color = Color(0xFF953800),
    val operator: Color = Color(0xFFCF222E),
    val punctuation: Color = Color(0xFF1F2328),
    val plain: Color = Color(0xFF1F2328),
)

private val LANGUAGE_ALIASES = mapOf(
    "js" to "javascript",
    "ts" to "typescript",
    "py" to "python",
    "rb" to "ruby",
    "kt" to "kotlin",
    "kts" to "kotlin",
    "rs" to "rust",
    "cs" to "csharp",
    "sh" to "bash",
    "shell" to "bash",
    "zsh" to "bash",
    "fish" to "bash",
    "yml" to "yaml",
    "md" to "markdown",
    "objc" to "objectivec",
    "objective-c" to "objectivec",
    "tsx" to "typescript",
    "jsx" to "javascript",
    "dockerfile" to "docker",
)

private val KEYWORDS = mapOf(
    "kotlin" to setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "sealed", "data",
        "abstract", "open", "override", "private", "protected", "internal", "public",
        "if", "else", "when", "for", "while", "do", "return", "break", "continue",
        "is", "as", "in", "!in", "!is", "try", "catch", "finally", "throw",
        "import", "package", "typealias", "companion", "init", "constructor",
        "by", "where", "suspend", "inline", "noinline", "crossinline", "reified",
        "annotation", "const", "lateinit", "tailrec", "operator", "infix",
        "true", "false", "null", "this", "super", "it",
    ),
    "java" to setOf(
        "public", "private", "protected", "static", "final", "abstract", "class",
        "interface", "extends", "implements", "new", "return", "if", "else",
        "for", "while", "do", "switch", "case", "break", "continue", "default",
        "try", "catch", "finally", "throw", "throws", "import", "package",
        "void", "int", "long", "double", "float", "boolean", "char", "byte",
        "short", "null", "true", "false", "this", "super", "instanceof",
        "enum", "synchronized", "volatile", "transient", "native",
    ),
    "javascript" to setOf(
        "function", "var", "let", "const", "if", "else", "for", "while", "do",
        "return", "break", "continue", "switch", "case", "default", "new",
        "this", "class", "extends", "import", "export", "from", "async", "await",
        "try", "catch", "finally", "throw", "typeof", "instanceof", "in", "of",
        "true", "false", "null", "undefined", "yield", "delete", "void",
        "static", "get", "set", "super", "with", "debugger",
    ),
    "typescript" to setOf(
        "function", "var", "let", "const", "if", "else", "for", "while", "do",
        "return", "break", "continue", "switch", "case", "default", "new",
        "this", "class", "extends", "import", "export", "from", "async", "await",
        "try", "catch", "finally", "throw", "typeof", "instanceof", "in", "of",
        "true", "false", "null", "undefined", "yield", "delete", "void",
        "interface", "type", "enum", "implements", "namespace", "module",
        "declare", "abstract", "as", "is", "keyof", "readonly", "private",
        "protected", "public", "static", "get", "set", "super", "any",
        "number", "string", "boolean", "symbol", "never", "unknown",
    ),
    "python" to setOf(
        "def", "class", "if", "elif", "else", "for", "while", "return", "yield",
        "import", "from", "as", "try", "except", "finally", "raise", "with",
        "pass", "break", "continue", "and", "or", "not", "in", "is", "lambda",
        "global", "nonlocal", "assert", "del", "True", "False", "None",
        "async", "await", "match", "case",
    ),
    "rust" to setOf(
        "fn", "let", "mut", "const", "static", "struct", "enum", "impl", "trait",
        "type", "pub", "crate", "mod", "use", "as", "if", "else", "match",
        "for", "while", "loop", "return", "break", "continue", "where",
        "unsafe", "async", "await", "move", "ref", "self", "Self", "super",
        "true", "false", "in", "dyn", "extern",
    ),
    "go" to setOf(
        "func", "var", "const", "type", "struct", "interface", "map", "chan",
        "package", "import", "if", "else", "for", "range", "switch", "case",
        "default", "return", "break", "continue", "go", "defer", "select",
        "fallthrough", "goto", "true", "false", "nil", "iota",
    ),
    "swift" to setOf(
        "func", "var", "let", "class", "struct", "enum", "protocol", "extension",
        "import", "if", "else", "guard", "switch", "case", "default", "for",
        "while", "repeat", "return", "break", "continue", "in", "where",
        "throw", "throws", "try", "catch", "defer", "as", "is", "self", "Self",
        "super", "init", "deinit", "true", "false", "nil", "static", "override",
        "private", "public", "internal", "open", "fileprivate", "mutating",
        "async", "await", "actor",
    ),
    "c" to setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "int", "long", "register", "return", "short", "signed", "sizeof",
        "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while", "NULL",
    ),
    "cpp" to setOf(
        "auto", "break", "case", "char", "const", "continue", "default", "do",
        "double", "else", "enum", "extern", "float", "for", "goto", "if",
        "int", "long", "register", "return", "short", "signed", "sizeof",
        "static", "struct", "switch", "typedef", "union", "unsigned", "void",
        "volatile", "while", "class", "public", "private", "protected",
        "virtual", "override", "namespace", "using", "template", "typename",
        "new", "delete", "try", "catch", "throw", "nullptr", "true", "false",
        "this", "const_cast", "dynamic_cast", "static_cast", "reinterpret_cast",
        "inline", "constexpr", "noexcept", "decltype", "concept", "requires",
        "co_await", "co_return", "co_yield",
    ),
    "bash" to setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
        "case", "esac", "in", "function", "return", "local", "export",
        "readonly", "declare", "typeset", "unset", "shift", "exit",
        "true", "false", "select", "until", "break", "continue",
    ),
    "sql" to setOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
        "DELETE", "CREATE", "DROP", "ALTER", "TABLE", "INDEX", "VIEW",
        "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AND", "OR", "NOT",
        "IN", "LIKE", "BETWEEN", "IS", "NULL", "AS", "ORDER", "BY", "GROUP",
        "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT", "EXISTS",
        "CASE", "WHEN", "THEN", "ELSE", "END", "COUNT", "SUM", "AVG", "MAX", "MIN",
        "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT",
        "select", "from", "where", "insert", "into", "values", "update", "set",
        "delete", "create", "drop", "alter", "table", "index", "view",
        "join", "left", "right", "inner", "outer", "on", "and", "or", "not",
        "in", "like", "between", "is", "null", "as", "order", "by", "group",
        "having", "limit", "offset", "union", "all", "distinct", "exists",
        "case", "when", "then", "else", "end",
        "primary", "key", "foreign", "references", "constraint", "default",
    ),
    "ruby" to setOf(
        "def", "class", "module", "if", "elsif", "else", "unless", "case", "when",
        "while", "until", "for", "do", "end", "begin", "rescue", "ensure",
        "raise", "return", "yield", "block_given?", "self", "super",
        "true", "false", "nil", "and", "or", "not", "in", "then",
        "require", "include", "extend", "attr_reader", "attr_writer", "attr_accessor",
        "private", "protected", "public", "lambda", "proc",
    ),
    "php" to setOf(
        "function", "class", "interface", "trait", "extends", "implements",
        "public", "private", "protected", "static", "abstract", "final",
        "if", "else", "elseif", "for", "foreach", "while", "do", "switch",
        "case", "default", "break", "continue", "return", "yield",
        "try", "catch", "finally", "throw", "new", "echo", "print",
        "true", "false", "null", "use", "namespace", "as", "instanceof",
        "array", "list", "isset", "unset", "empty", "match", "enum", "readonly",
    ),
    "css" to setOf(
        "important", "inherit", "initial", "unset", "none", "auto", "block",
        "inline", "flex", "grid", "absolute", "relative", "fixed", "sticky",
        "solid", "dashed", "dotted", "hidden", "visible", "transparent",
    ),
    "html" to setOf(
        "DOCTYPE", "html", "head", "body", "div", "span", "p", "a", "img",
        "script", "style", "link", "meta", "title", "h1", "h2", "h3",
        "table", "tr", "td", "th", "form", "input", "button", "select",
        "ul", "ol", "li", "nav", "header", "footer", "main", "section",
    ),
    "xml" to setOf<String>(),
    "json" to setOf<String>(),
    "yaml" to setOf("true", "false", "null", "yes", "no", "on", "off"),
    "toml" to setOf("true", "false"),
    "docker" to setOf(
        "FROM", "RUN", "CMD", "ENTRYPOINT", "EXPOSE", "ENV", "ADD", "COPY",
        "WORKDIR", "USER", "VOLUME", "ARG", "LABEL", "STOPSIGNAL", "HEALTHCHECK",
        "SHELL", "ONBUILD", "MAINTAINER",
    ),
    "dart" to setOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch",
        "class", "const", "continue", "covariant", "default", "deferred", "do",
        "dynamic", "else", "enum", "export", "extends", "extension", "external",
        "factory", "false", "final", "finally", "for", "Function", "get",
        "hide", "if", "implements", "import", "in", "interface", "is", "late",
        "library", "mixin", "new", "null", "on", "operator", "part",
        "required", "rethrow", "return", "set", "show", "static", "super",
        "switch", "sync", "this", "throw", "true", "try", "typedef", "var",
        "void", "while", "with", "yield",
    ),
)

private fun resolveLanguage(lang: String): String {
    val lower = lang.lowercase().trim()
    return LANGUAGE_ALIASES[lower] ?: lower
}

private fun keywordsFor(language: String): Set<String> {
    return KEYWORDS[language] ?: emptySet()
}

internal fun tokenize(code: String, language: String): List<Token> {
    val lang = resolveLanguage(language)
    val keywords = keywordsFor(lang)
    val tokens = mutableListOf<Token>()
    val len = code.length
    var i = 0

    val isSql = lang == "sql"

    while (i < len) {
        val c = code[i]

        // Line comments
        if (lang != "css" && lang != "html" && lang != "xml" && lang != "json" && lang != "yaml" && lang != "toml") {
            if (c == '/' && i + 1 < len && code[i + 1] == '/') {
                val end = code.indexOf('\n', i)
                val comment = if (end == -1) code.substring(i) else code.substring(i, end)
                tokens.add(Token(TokenType.COMMENT, comment))
                i += comment.length
                continue
            }
            // Block comments
            if (c == '/' && i + 1 < len && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2)
                val comment = if (end == -1) code.substring(i) else code.substring(i, end + 2)
                tokens.add(Token(TokenType.COMMENT, comment))
                i += comment.length
                continue
            }
        }

        // Hash comments (Python, Ruby, Bash, YAML, TOML, Dockerfile)
        if (c == '#' && lang in setOf("python", "ruby", "bash", "yaml", "toml", "docker")) {
            val end = code.indexOf('\n', i)
            val comment = if (end == -1) code.substring(i) else code.substring(i, end)
            tokens.add(Token(TokenType.COMMENT, comment))
            i += comment.length
            continue
        }

        // HTML/XML comments
        if (c == '<' && i + 3 < len && code.substring(i, minOf(i + 4, len)) == "<!--") {
            val end = code.indexOf("-->", i + 4)
            val comment = if (end == -1) code.substring(i) else code.substring(i, end + 3)
            tokens.add(Token(TokenType.COMMENT, comment))
            i += comment.length
            continue
        }

        // CSS comments
        if (lang == "css" && c == '/' && i + 1 < len && code[i + 1] == '*') {
            val end = code.indexOf("*/", i + 2)
            val comment = if (end == -1) code.substring(i) else code.substring(i, end + 2)
            tokens.add(Token(TokenType.COMMENT, comment))
            i += comment.length
            continue
        }

        // SQL comments (--)
        if (isSql && c == '-' && i + 1 < len && code[i + 1] == '-') {
            val end = code.indexOf('\n', i)
            val comment = if (end == -1) code.substring(i) else code.substring(i, end)
            tokens.add(Token(TokenType.COMMENT, comment))
            i += comment.length
            continue
        }

        // Strings
        if (c == '"' || c == '\'' || c == '`') {
            // Triple-quoted strings (Python, Kotlin)
            if ((c == '"' || c == '\'') && i + 2 < len && code[i + 1] == c && code[i + 2] == c) {
                val tripleQuote = "$c$c$c"
                val end = code.indexOf(tripleQuote, i + 3)
                val str = if (end == -1) code.substring(i) else code.substring(i, end + 3)
                tokens.add(Token(TokenType.STRING, str))
                i += str.length
                continue
            }
            val end = findStringEnd(code, i, c)
            val str = code.substring(i, end)
            tokens.add(Token(TokenType.STRING, str))
            i += str.length
            continue
        }

        // Annotations (Kotlin, Java, Python decorators)
        if (c == '@' && i + 1 < len && (code[i + 1].isLetter() || code[i + 1] == '_')) {
            val end = findWordEnd(code, i + 1)
            tokens.add(Token(TokenType.ANNOTATION, code.substring(i, end)))
            i = end
            continue
        }

        // Numbers
        if (c.isDigit() || (c == '.' && i + 1 < len && code[i + 1].isDigit())) {
            val start = i
            if (c == '0' && i + 1 < len && (code[i + 1] == 'x' || code[i + 1] == 'X')) {
                i += 2
                while (i < len && (code[i].isDigit() || code[i] in 'a'..'f' || code[i] in 'A'..'F' || code[i] == '_')) i++
            } else if (c == '0' && i + 1 < len && (code[i + 1] == 'b' || code[i + 1] == 'B')) {
                i += 2
                while (i < len && (code[i] == '0' || code[i] == '1' || code[i] == '_')) i++
            } else {
                while (i < len && (code[i].isDigit() || code[i] == '_')) i++
                if (i < len && code[i] == '.' && i + 1 < len && code[i + 1].isDigit()) {
                    i++
                    while (i < len && (code[i].isDigit() || code[i] == '_')) i++
                }
                if (i < len && (code[i] == 'e' || code[i] == 'E')) {
                    i++
                    if (i < len && (code[i] == '+' || code[i] == '-')) i++
                    while (i < len && code[i].isDigit()) i++
                }
            }
            // Suffix (f, L, u, etc.)
            if (i < len && code[i] in "fFdDlLuU") i++
            tokens.add(Token(TokenType.NUMBER, code.substring(start, i)))
            continue
        }

        // Words (identifiers, keywords, types)
        if (c.isLetter() || c == '_' || c == '$') {
            val start = i
            while (i < len && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '$')) i++
            val word = code.substring(start, i)

            when {
                word in keywords -> tokens.add(Token(TokenType.KEYWORD, word))
                // Function call detection: word followed by (
                i < len && code[i] == '(' -> tokens.add(Token(TokenType.FUNCTION, word))
                // Type heuristic: starts with uppercase
                word[0].isUpperCase() && word.any { it.isLowerCase() } ->
                    tokens.add(Token(TokenType.TYPE, word))
                else -> tokens.add(Token(TokenType.PLAIN, word))
            }
            continue
        }

        // Operators
        if (c in "=+-*/<>!&|^%~?:") {
            val start = i
            i++
            // Consume multi-char operators
            if (i < len && code[i] in "=+-*/<>!&|^%~?:") i++
            if (i < len && code[i] == '=') i++ // e.g. >>= <<=
            tokens.add(Token(TokenType.OPERATOR, code.substring(start, i)))
            continue
        }

        // Punctuation
        if (c in "(){}[];,.:") {
            tokens.add(Token(TokenType.PUNCTUATION, c.toString()))
            i++
            continue
        }

        // Whitespace and everything else
        val start = i
        while (i < len && !code[i].isLetterOrDigit() && code[i] !in "\"'`@#=+-*/<>!&|^%~?:(){}[];,._$") {
            i++
        }
        if (i == start) i++ // prevent infinite loop
        tokens.add(Token(TokenType.PLAIN, code.substring(start, i)))
    }

    return tokens
}

private fun findStringEnd(code: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < code.length) {
        if (code[i] == '\\') {
            i += 2
            continue
        }
        if (code[i] == quote) return i + 1
        if (code[i] == '\n' && quote != '`') return i // unterminated single-line string
        i++
    }
    return code.length
}

private fun findWordEnd(code: String, start: Int): Int {
    var i = start
    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '.')) i++
    return i
}

internal fun highlightCode(code: String, language: String, colors: SyntaxColors): AnnotatedString {
    val tokens = tokenize(code, language)
    return buildAnnotatedString {
        for (token in tokens) {
            val style = when (token.type) {
                TokenType.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.Bold)
                TokenType.STRING -> SpanStyle(color = colors.string)
                TokenType.NUMBER -> SpanStyle(color = colors.number)
                TokenType.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                TokenType.ANNOTATION -> SpanStyle(color = colors.annotation)
                TokenType.FUNCTION -> SpanStyle(color = colors.function)
                TokenType.TYPE -> SpanStyle(color = colors.type)
                TokenType.OPERATOR -> SpanStyle(color = colors.operator)
                TokenType.PUNCTUATION -> SpanStyle(color = colors.punctuation)
                TokenType.PLAIN -> SpanStyle(color = colors.plain)
            }
            withStyle(style) {
                append(token.text)
            }
        }
    }
}
