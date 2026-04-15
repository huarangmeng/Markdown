package com.hrm.markdown.preview

import com.hrm.markdown.renderer.Markdown

private val footnoteJumpLipsumMarkdown = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.[^journey] Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.

Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.

Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer nec odio. Praesent libero. Sed cursus ante dapibus diam. Sed nisi. Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum.

Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra.[^contrast]

Curabitur sodales ligula in libero. Sed dignissim lacinia nunc. Curabitur tortor. Pellentesque nibh. Aenean quam. In scelerisque sem at dolor. Maecenas mattis.

Sed convallis tristique sem. Proin ut ligula vel nunc egestas porttitor. Morbi lectus risus, iaculis vel, suscipit quis, luctus non, massa. Fusce ac turpis quis ligula lacinia aliquet.

Mauris ipsum. Nulla metus metus, ullamcorper vel, tincidunt sed, euismod in, nibh. Quisque volutpat condimentum velit. Class aptent taciti sociosqu ad litora torquent per conubia nostra.

Nam nec ante. Sed lacinia, urna non tincidunt mattis, tortor neque adipiscing diam, a cursus ipsum ante quis turpis. Nulla facilisi. Ut fringilla.[^ending] Suspendisse potenti.

Nunc feugiat mi a tellus consequat imperdiet. Vestibulum sapien. Proin quam. Etiam ultrices. Suspendisse in justo eu magna luctus suscipit. Sed lectus. Integer euismod lacus luctus magna.

[^journey]: 第一个脚注放在长文底部，用来验证点击上标后会自动滚动到这里，而不是只触发普通链接回调。
[^contrast]: 第二个脚注用于验证同一篇长文中存在多个脚注时，仍然会跳到各自对应的定义。
[^ending]: 这个脚注刻意放在最后一段附近，方便测试长 `lipsum` 场景下的末尾跳转。
""".trimIndent()

internal val extendedPreviewGroups = listOf(
    PreviewGroup(
        id = "footnotes",
        title = "脚注",
        description = "脚注引用与定义",
        items = listOf(
            PreviewItem(
                id = "basic_footnote",
                title = "基础脚注",
                content = {
                    Markdown(
                        markdown = """
这是一段带有脚注的文本[^1]。

[^1]: 这是脚注的内容。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "footnote_jump_lipsum",
                title = "长文脚注跳转（Lipsum）",
                content = {
                    Markdown(markdown = footnoteJumpLipsumMarkdown)
                }
            ),
        )
    ),
    PreviewGroup(
        id = "definition_list",
        title = "定义列表",
        description = "术语与定义",
        items = listOf(
            PreviewItem(
                id = "basic_dl",
                title = "基础定义列表",
                content = {
                    Markdown(
                        markdown = """
Kotlin
: 一种现代的编程语言，运行在 JVM 上。

Compose Multiplatform
: JetBrains 的跨平台 UI 框架。

  支持 Android、iOS、Desktop、Web 多平台开发。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "rich_dl",
                title = "包含代码和列表的定义",
                content = {
                    Markdown(
                        markdown = """
Compose Multiplatform
: JetBrains 的跨平台 UI 框架。

  ```kotlin
  @Composable
  fun Greeting(name: String) {
      Text("Hello, ${'$'}name!")
  }
  ```

  - 声明式 UI
  - 跨平台复用
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "emoji",
        title = "Emoji",
        description = "Emoji 短代码支持、Unicode 映射",
        items = listOf(
            PreviewItem(
                id = "emoji_basic",
                title = "Emoji 短代码（Unicode 映射）",
                content = {
                    Markdown(markdown = "支持 Emoji 短代码自动映射为 Unicode：:smile: :heart: :rocket: :fire: :thumbsup: :tada:")
                }
            ),
            PreviewItem(
                id = "emoji_multiple",
                title = "多种 Emoji",
                content = {
                    Markdown(markdown = "表情：:grin: :wink: :sunglasses: :thinking:\n\n动物：:cat: :dog: :penguin: :turtle:\n\n物品：:coffee: :pizza: :gift: :trophy:")
                }
            ),
            PreviewItem(
                id = "emoji_unknown",
                title = "未知短代码",
                content = {
                    Markdown(markdown = "已知 :smile: vs 未知 :unknown_code: — 未知的保留原始文本。")
                }
            ),
        )
    ),
    PreviewGroup(
        id = "keyboard",
        title = "键盘按键",
        description = "<kbd> 标签",
        items = listOf(
            PreviewItem(
                id = "kbd_basic",
                title = "键盘快捷键",
                content = {
                    Markdown(markdown = "使用 <kbd>Ctrl</kbd>+<kbd>C</kbd> 复制，<kbd>Ctrl</kbd>+<kbd>V</kbd> 粘贴。")
                }
            ),
        )
    ),
    PreviewGroup(
        id = "abbreviation",
        title = "缩写",
        description = "缩写标签与悬浮提示",
        items = listOf(
            PreviewItem(
                id = "abbr_basic",
                title = "缩写示例",
                content = {
                    Markdown(
                        markdown = """
*[HTML]: Hyper Text Markup Language
*[CSS]: Cascading Style Sheets

HTML 和 CSS 是 Web 开发的基础技术。HTML 定义页面结构，CSS 负责样式。
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "custom_container",
        title = "自定义容器",
        description = "::: 围栏容器与嵌套",
        items = listOf(
            PreviewItem(
                id = "container_note",
                title = "Note 容器",
                content = {
                    Markdown(
                        markdown = """
::: note "提示信息"
这是一个使用 `:::` 围栏语法创建的自定义容器。

支持**完整的 Markdown 语法**，包括列表：
- 项目一
- 项目二
:::
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "container_warning",
                title = "Warning 容器",
                content = {
                    Markdown(
                        markdown = """
::: warning
未指定标题时，容器类型名作为默认标题显示。
:::
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "container_nested",
                title = "嵌套容器",
                content = {
                    Markdown(
                        markdown = """
:::: card
::: note
容器支持**嵌套**，外层使用更多冒号（`::::`）。
:::
::::
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "thematic_break",
        title = "分隔线",
        description = "水平分隔线",
        items = listOf(
            PreviewItem(
                id = "hr",
                title = "分隔线",
                content = {
                    Markdown(
                        markdown = """
上面的内容

---

下面的内容
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "columns_layout",
        title = "多列布局",
        description = "基于自定义容器的多列布局",
        items = listOf(
            PreviewItem(
                id = "two_columns",
                title = "两列布局",
                content = {
                    Markdown(
                        markdown = """
:::columns
:::column "width=50%"
### 左列

这是左列的内容，包含 **粗体** 和 *斜体*。

- 列表项 1
- 列表项 2
:::column "width=50%"
### 右列

这是右列的内容。

> 引用块也可以放在列中。
:::
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "three_columns",
                title = "三列布局",
                content = {
                    Markdown(
                        markdown = """
:::columns
:::column
**第一列**

简短内容。
:::column
**第二列**

中间的列。
:::column
**第三列**

最后一列。
:::
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "toc_advanced",
        title = "目录高级配置",
        description = "TOC 深度、排除、排序",
        items = listOf(
            PreviewItem(
                id = "toc_depth",
                title = "TOC 深度限制",
                content = {
                    Markdown(
                        markdown = """
[TOC]
:depth=2-3

# 一级标题

## 二级标题

### 三级标题

#### 四级标题（不会出现在目录中）

## 另一个二级标题
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "toc_full_config",
                title = "TOC 完整配置",
                content = {
                    Markdown(
                        markdown = """
[TOC]
:depth=1-3
:order=asc

# 概述

## 安装

### 环境要求

## 使用指南

### 快速开始

# 参考
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "styled_text",
        title = "自定义行内样式",
        description = "[文本]{.class style=\"...\"} 属性语法",
        items = listOf(
            PreviewItem(
                id = "styled_class",
                title = "CSS 类样式",
                content = {
                    Markdown(markdown = "这是 [红色文本]{.red} 和 [粗体文本]{.bold} 以及 [红色粗体]{.red .bold}。")
                }
            ),
            PreviewItem(
                id = "styled_inline",
                title = "行内 style 属性",
                content = {
                    Markdown(markdown = "自定义背景：[高亮文本]{style=\"background:yellow\"} 和 [蓝色文本]{style=\"color:blue\"}。")
                }
            ),
            PreviewItem(
                id = "styled_nested",
                title = "嵌套格式",
                content = {
                    Markdown(markdown = "组合：[**粗体** *斜体* 混合]{.red .underline}")
                }
            ),
        )
    ),
    PreviewGroup(
        id = "page_break",
        title = "分页符",
        description = "PDF 导出/打印用分页标记",
        items = listOf(
            PreviewItem(
                id = "pagebreak_basic",
                title = "基础分页符",
                content = {
                    Markdown(
                        markdown = """
# 第一页内容

这是第一页的内容。

***pagebreak***

# 第二页内容

这是第二页的内容。
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "block_attributes",
        title = "块级属性",
        description = "{.class #id key=value} 块级属性语法",
        items = listOf(
            PreviewItem(
                id = "block_attr_heading",
                title = "标题属性",
                content = {
                    Markdown(
                        markdown = """
# 带属性的标题
{.special-heading #main-title}

## 另一个标题
{.subtitle data-section="intro"}

普通段落跟在后面。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "block_attr_paragraph",
                title = "段落属性",
                content = {
                    Markdown(
                        markdown = """
这段文字带有自定义属性。
{.highlight style="background:yellow"}

这是普通段落，没有属性。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "block_attr_mixed",
                title = "多种块级元素属性",
                content = {
                    Markdown(
                        markdown = """
# 标题
{.title-class #page-title}

> 引用块也可以带属性
{.important}

- 列表项 1
- 列表项 2
{.custom-list}

| 列A | 列B |
|-----|-----|
| 1   | 2   |
{.striped-table #data-table}
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "bibliography",
        title = "参考文献引用",
        description = "学术论文/技术专著场景的参考文献引用",
        items = listOf(
            PreviewItem(
                id = "citation_basic",
                title = "基础参考文献引用",
                content = {
                    Markdown(
                        markdown = """
根据 Smith 的研究[@smith2020]，Kotlin 是一种现代编程语言。

这一观点得到了其他研究者的支持[@jones2021]。

[^bibliography]: smith2020: Smith, J. "Modern Programming Languages". Tech Press, 2020
jones2021: Jones, A. "Kotlin in Practice". Developer Publishing, 2021
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "citation_multiple",
                title = "多引用与文献列表",
                content = {
                    Markdown(
                        markdown = """
Compose Multiplatform[@compose2023] 基于 Jetpack Compose[@jetpack2022] 构建，
支持跨平台 UI 开发[@kmp2024]。

[^bibliography]: compose2023: JetBrains. "Compose Multiplatform Guide". 2023
jetpack2022: Google. "Jetpack Compose Documentation". 2022
kmp2024: JetBrains. "Kotlin Multiplatform Handbook". 2024
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "tab_block",
        title = "内容标签页",
        description = "MkDocs Material 风格多标签切换",
        items = listOf(
            PreviewItem(
                id = "tab_basic",
                title = "基础标签页",
                content = {
                    Markdown(
                        markdown = """
=== "Kotlin"
    ```kotlin
    fun main() {
        println("Hello, World!")
    }
    ```

=== "Python"
    ```python
    def main():
        print("Hello, World!")
    ```

=== "Swift"
    ```swift
    func main() {
        print("Hello, World!")
    }
    ```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "tab_rich_content",
                title = "包含丰富内容的标签页",
                content = {
                    Markdown(
                        markdown = """
=== "安装"
    ### 环境要求

    - JDK 17+
    - Gradle 8.0+

    ```bash
    git clone https://github.com/example/repo.git
    cd repo && ./gradlew build
    ```

=== "使用"
    ### 快速开始

    在 `build.gradle.kts` 中添加依赖：

    ```kotlin
    dependencies {
        implementation("com.example:library:1.0.0")
    }
    ```

    > 支持 Android、iOS、Desktop、Web 四大平台。
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "spoiler",
        title = "剧透文本",
        description = ">!spoiler!< 点击可见的遮挡文本",
        items = listOf(
            PreviewItem(
                id = "spoiler_basic",
                title = "基础剧透文本",
                content = {
                    Markdown(
                        markdown = "这部电影的结局是 >!主角最终拯救了世界!<，非常精彩。"
                    )
                }
            ),
            PreviewItem(
                id = "spoiler_multiple",
                title = "多个剧透",
                content = {
                    Markdown(
                        markdown = """
剧情摘要：

- 第一章：>!主角发现了一个神秘的入口!<
- 第二章：>!与反派展开了激烈的对决!<
- 结局：>!一切回归平静，但留下了悬念!<
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "html_renderer",
        title = "HTML 渲染器",
        description = "HtmlRenderer 将 AST 输出为标准 HTML",
        items = listOf(
            PreviewItem(
                id = "html_basic",
                title = "基础 HTML 输出示例",
                content = {
                    Markdown(
                        markdown = """
以下是 `HtmlRenderer` 的使用示例：

```kotlin
val parser = MarkdownParser()
val doc = parser.parse("# Hello\n\n**Bold** text.")
val html = HtmlRenderer.render(doc)
// 输出:
// <h1 id="hello">Hello</h1>
// <p><strong>Bold</strong> text.</p>
```

也可直接调用便捷方法：

```kotlin
val html = HtmlRenderer.renderMarkdown("# Hello")
```
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "html_features",
                title = "HTML 渲染支持的元素",
                content = {
                    Markdown(
                        markdown = """
`HtmlRenderer` 完整支持所有 AST 节点：

- **块级**: 标题、段落、代码块、引用、列表、表格、数学块、Admonition 等
- **行内**: 粗体、斜体、删除线、高亮、上下标、链接、图片、脚注等
- **扩展**: 自定义容器、图表块、多列布局、分页符

配置选项：
- `softBreak`: 软换行输出（默认 `\n`，可设为 `<br />`）
- `escapeHtml`: 是否转义原始 HTML
- `xhtml`: 是否使用 XHTML 自闭合标签
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
)
