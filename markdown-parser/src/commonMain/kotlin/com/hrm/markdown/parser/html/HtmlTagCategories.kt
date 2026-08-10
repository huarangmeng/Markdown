package com.hrm.markdown.parser.html

/** CommonMark 语法分类与 HTML 元素语义分类的单一来源。 */
object HtmlTagCategories {
    /** CommonMark HTML block type 6 规范中列举的标签。 */
    fun isCommonMarkType6BlockTag(name: String): Boolean =
        name.lowercase() in COMMONMARK_TYPE_6_BLOCK_TAGS

    /** HTML 中具有块级内容语义的元素；这不是 CommonMark type 6 标签集合的别名。 */
    fun isHtmlBlockElement(name: String): Boolean = name.lowercase() in HTML_BLOCK_ELEMENTS

    @Deprecated(
        message = "Use isCommonMarkType6BlockTag() for CommonMark parsing or " +
            "isHtmlBlockElement() for HTML semantics",
        replaceWith = ReplaceWith("isCommonMarkType6BlockTag(name)"),
    )
    fun isBlockTag(name: String): Boolean = isCommonMarkType6BlockTag(name)

    fun isVoidTag(name: String): Boolean = name.lowercase() in VOID_TAGS

    private val COMMONMARK_TYPE_6_BLOCK_TAGS = setOf(
        "address", "article", "aside", "base", "basefont", "blockquote", "body",
        "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
        "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
        "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
        "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
        "nav", "noframes", "ol", "optgroup", "option", "p", "param", "search",
        "section", "summary", "table", "tbody", "td", "template", "tfoot", "th",
        "thead", "title", "tr", "track", "ul",
    )

    private val HTML_BLOCK_ELEMENTS = setOf(
        "address", "article", "aside", "blockquote", "center", "details", "dialog",
        "dir", "div", "dl", "dd", "dt", "fieldset", "figcaption", "figure",
        "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
        "hgroup", "hr", "li", "main", "menu", "nav", "ol", "p", "pre", "search",
        "section", "summary", "table", "ul",
    )

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
        "param", "source", "track", "wbr",
    )
}
