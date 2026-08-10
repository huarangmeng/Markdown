package com.hrm.markdown.parser.html

/** CommonMark/HTML 标签分类的单一来源。 */
object HtmlTagCategories {
    fun isBlockTag(name: String): Boolean = name.lowercase() in BLOCK_TAGS

    fun isVoidTag(name: String): Boolean = name.lowercase() in VOID_TAGS

    private val BLOCK_TAGS = setOf(
        "address", "article", "aside", "base", "basefont", "blockquote", "body",
        "caption", "center", "col", "colgroup", "dd", "details", "dialog", "dir",
        "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form",
        "frame", "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header",
        "hr", "html", "iframe", "legend", "li", "link", "main", "menu", "menuitem",
        "nav", "noframes", "ol", "optgroup", "option", "p", "param", "search",
        "section", "summary", "table", "tbody", "td", "template", "tfoot", "th",
        "thead", "title", "tr", "track", "ul",
    )

    private val VOID_TAGS = setOf(
        "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta",
        "param", "source", "track", "wbr",
    )
}
