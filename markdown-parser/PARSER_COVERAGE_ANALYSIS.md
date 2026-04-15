# Markdown 解析器功能覆盖分析

## 1. 块级结构 — 标题

### ✅ 已支持

#### ATX 标题
- ✅ `# heading` 1 级标题
- ✅ `## heading` 2 级标题
- ✅ `### heading` 3 级标题
- ✅ `#### heading` 4 级标题
- ✅ `##### heading` 5 级标题
- ✅ `###### heading` 6 级标题
- ✅ 标题尾部可选 `#` 闭合（`# heading #`）
- ✅ 标题前最多 3 个空格缩进
- ✅ `#` 后必须有空格或行尾（`#heading` 不是标题）

#### Setext 标题
- ✅ `heading\n===` Setext 标题（`=` 下划线，1 级）
- ✅ `heading\n---` Setext 标题（`-` 下划线，2 级）
- ✅ 下划线长度 ≥ 1
- ✅ 下划线前最多 3 个空格缩进
- ✅ Setext 标题可以跨多行内容

#### 标题 ID（扩展）
- ✅ `### heading {#custom-id}` 自定义标题 ID
- ✅ 自动生成标题 ID（基于标题文本的 slug，支持去重）
- ✅ 标题锚点链接 `[link](#custom-id)`（渲染器支持 TOC 生成）

**覆盖率**: 17/17 (100%)

---

## 2. 块级结构 — 段落与空行

### ✅ 已支持
- ✅ 连续非空行合并为段落
- ✅ 空行分隔段落
- ✅ 段落的 lazy continuation（块引用/列表中的延续行）
- ✅ 段落首行前最多 3 个空格不算缩进代码块
- ✅ 空行（blank line）：只包含空格/Tab 的行视为空行

**覆盖率**: 5/5 (100%)

---

## 3. 块级结构 — 代码块

### ✅ 已支持

#### 语法高亮（扩展）
- ✅ 根据 info string 语言标识进行语法高亮（渲染器职责，SyntaxHighlighter 支持 20+ 语言）

> **备注**: `SyntaxHighlighter` 基于正则的语法高亮引擎，支持 20+ 语言（Kotlin, Java, Python, JS/TS, Swift, Go, Rust, C/C++, SQL, HTML, CSS 等）。渲染器根据 FencedCodeBlock 的 info string 自动匹配语言规则并生成带语法着色的 `AnnotatedString`。

#### 围栏代码块
- ✅ `` ``` `` 反引号围栏（≥ 3 个）
- ✅ `~~~` 波浪线围栏（≥ 3 个）
- ✅ 围栏后 info string（语言标识，如 `` ```kotlin ``）
- ✅ info string 仅取第一个词作为语言
- ✅ 闭合围栏长度 ≥ 开启围栏长度
- ✅ 闭合围栏字符须与开启围栏相同（不能混用 `` ` `` 和 `~`）
- ✅ 围栏前最多 3 个空格缩进
- ✅ 未闭合围栏延伸到文档末尾
- ✅ 围栏代码块内容不解析 Markdown 语法

#### 代码块属性增强（扩展）
- ✅ `hl_lines` 属性：`` ```python {hl_lines="1 3-5"} `` 解析 info string 中 `{...}` 内的行高亮参数
- ✅ `linenums` 属性：`` ```python {linenums=true} `` 解析是否显示行号
- ✅ `startline` 属性：`` ```python {startline=10} `` 解析起始行号

> **备注**: 围栏代码块的 info string 中 `{...}` 属性块由 `FencedCodeBlockStarter` 解析。`FencedCodeBlock` AST 节点携带 `highlightLines: List<IntRange>`、`showLineNumbers: Boolean`、`startLineNumber: Int` 三个额外字段。`HtmlRenderer` 在 `<pre>` 标签上输出 `data-hl-lines`、`data-linenums`、`data-startline` 属性。

#### 缩进代码块
- ✅ 4 个空格或 1 个 Tab 缩进
- ✅ 连续缩进行合并为一个代码块
- ✅ 代码块中间允许空行（空行后仍缩进则继续）
- ✅ 不能打断段落（段落后紧跟缩进行仍属于段落）

> **备注**: `IndentedCodeBlockStarter` 增加上下文感知，在 DefinitionList/DefinitionDescription/FootnoteDefinition 内自动让步，避免定义列表和脚注中的缩进内容被误解析为缩进代码块。

**覆盖率**: 17/17 (100%)

---

## 4. 块级结构 — 块引用

### ✅ 已支持
- ✅ `>` 前缀标记块引用
- ✅ `>` 后可选一个空格
- ✅ `>` 前最多 3 个空格缩进
- ✅ 多行块引用（每行 `>` 前缀）
- ✅ Lazy continuation（后续行不带 `>` 也属于同一块引用）
- ✅ 嵌套块引用（`>> ...`）
- ✅ 块引用内包含其他块级元素（标题、列表、代码块等）
- ✅ 空行终止块引用（除非后续行有 `>`）

**覆盖率**: 8/8 (100%)

---

## 5. 块级结构 — 列表

### ✅ 已支持

#### 无序列表
- ✅ `-` 标记符
- ✅ `*` 标记符
- ✅ `+` 标记符
- ✅ 标记符后至少 1 个空格
- ✅ 不同标记符产生不同列表（不合并）

#### 有序列表
- ✅ `1.` 标记符（数字 + 点）
- ✅ `1)` 标记符（数字 + 右括号）
- ✅ 起始编号支持（非 1 开头）
- ✅ 数字最多 9 位
- ✅ 不同定界符（`.` 和 `)`）产生不同列表

#### 列表通用
- ✅ 紧凑列表（tight）与松散列表（loose）区分
- ✅ 列表项嵌套（缩进对齐）
- ✅ 列表项包含多个块级元素（段落、代码块等）
- ✅ 列表项 continuation（缩进延续行）
- ✅ 列表项中空行处理（空行使列表变为 loose）
- ✅ 列表可以打断段落（无序列表总是可以，有序列表仅 `1.` 可以）

#### 任务列表（GFM 扩展）
- ✅ `- [ ] ` 未完成任务
- ✅ `- [x] ` 已完成任务
- ✅ `- [X] ` 已完成任务（大小写不敏感）
- ✅ 任务列表嵌套

**覆盖率**: 20/20 (100%)

---

## 6. 块级结构 — 分隔线

### ✅ 已支持
- ✅ `---` 三个或更多连字符
- ✅ `***` 三个或更多星号
- ✅ `___` 三个或更多下划线
- ✅ 符号之间允许空格（如 `- - -`、`* * *`）
- ✅ 前面最多 3 个空格缩进
- ✅ 分隔线优先于 Setext 标题（在特定上下文中）

**覆盖率**: 6/6 (100%)

---

## 7. 块级结构 — 表格（GFM 扩展）

### ✅ 已支持
- ✅ `|` 分隔列
- ✅ 表头行（第一行）
- ✅ 分隔行（`|---|---|`，仅含 `-`、`:`、`|`、空格）
- ✅ 左对齐 `:---`
- ✅ 右对齐 `---:`
- ✅ 居中对齐 `:---:`
- ✅ 默认对齐（无冒号，左对齐）
- ✅ 单元格内行内元素解析
- ✅ 首尾 `|` 可选
- ✅ 单元格内 `|` 转义（`\|` 或 `&#124;`）
- ✅ 列数以分隔行为准，不足补空，多余截断

**覆盖率**: 11/11 (100%)

> **备注**: GFM 规范中表格的 header 行来自段落内容转换，分隔行触发表格检测。
> 这是标准 GFM 行为，段落内容被转换为表头而非被"打断"。

---

## 8. 块级结构 — HTML 块

### ✅ 已支持
- ✅ 类型 1：`<script>`, `<pre>`, `<style>`, `<textarea>`（到对应闭合标签或文档末尾）
- ✅ 类型 2：`<!--` HTML 注释（到 `-->`）
- ✅ 类型 3：`<?` 处理指令（到 `?>`）
- ✅ 类型 4：`<!` 声明（如 `<!DOCTYPE>`，到 `>`）
- ✅ 类型 5：`<![CDATA[`（到 `]]>`）
- ✅ 类型 6：已知块级 HTML 标签（`<div>`, `<table>`, `<p>`, `<h1>`-`<h6>`, `<section>`, `<article>`, `<nav>`, `<header>`, `<footer>`, `<main>`, `<details>`, `<summary>` 等，到空行）
- ✅ 类型 7：其他独立 HTML 标签（到空行）
- ✅ HTML 块前最多 3 个空格缩进
- ✅ HTML 块可以打断段落（类型 1-6）
- ✅ GFM 禁止的原始 HTML 标签过滤（`<title>`, `<textarea>`, `<style>`, `<xmp>`, `<iframe>`, `<noembed>`, `<noframes>`, `<script>`, `<plaintext>`）

**覆盖率**: 10/10 (100%)

---

## 9. 块级结构 — 链接引用定义

### ✅ 已支持
- ✅ `[label]: destination` 基本格式
- ✅ `[label]: destination "title"` 带双引号标题
- ✅ `[label]: destination 'title'` 带单引号标题
- ✅ `[label]: destination (title)` 带括号标题
- ✅ `[label]: <destination>` 尖括号包裹 URL
- ✅ destination 可含空格（需尖括号包裹）
- ✅ label 大小写不敏感匹配
- ✅ label 折叠连续空白为单个空格
- ✅ 定义不产生输出，仅供引用链接使用
- ✅ 同名 label 取第一个定义
- ✅ 链接引用定义前最多 3 个空格
- ✅ 标题可跨行（续行）

**覆盖率**: 12/12 (100%)

---

## 10. 块级结构 — 扩展

### ✅ 已支持

#### 目录（TOC，扩展）
- ✅ `[TOC]` 自动生成目录
- ✅ `[[toc]]` 自动生成目录（备选语法）
- ✅ `[TOC]\n:depth=2-4` 深度范围过滤（minDepth-maxDepth）
- ✅ `[TOC]\n:exclude=#ignore` 按标题 ID 排除
- ✅ `[TOC]\n:order=asc|desc` 排序方式（正序/逆序）

> **备注**: `BlockParser.tryParseTocPlaceholder()` 解析 `[TOC]` 后续的配置行（`:key=value` 格式），将参数写入 `TocPlaceholder` 节点的 `minDepth`/`maxDepth`/`excludeIds`/`order` 属性。渲染器据此过滤标题深度范围、排除指定 ID、调整排序方式。

#### 可折叠内容（扩展）
- ✅ `<details><summary>标题</summary>内容</details>` 折叠/展开块（作为 HTML 块类型 6 处理）

#### 脚注定义（扩展）
- ✅ `[^label]: content` 单行脚注
- ✅ 多行脚注（续行缩进 4 空格）
- ✅ 脚注内包含块级元素
- ✅ 脚注自动编号

#### 数学公式块（扩展）
- ✅ `$$...$$` 多行数学公式
- ✅ 单行 `$$ content $$`
- ✅ 数学块内不解析 Markdown 语法
- ✅ `\tag{N}` 公式编号（LaTeX 渲染库原生支持，literal 保留完整 LaTeX 文本）
- ✅ `\begin{equation}...\end{equation}` 等环境自动编号（LaTeX 渲染库原生支持）
- ✅ `\ref{}`/`\eqref{}` 公式引用（LaTeX 渲染库原生支持）

> **备注**: 公式编号、环境自动编号和引用功能均由 LaTeX 渲染库（`io.github.huarangmeng:latex-renderer`）原生处理。Parser 层保留完整 LaTeX 文本，不做编号提取，交由 LaTeX 库的 `EquationNumbering` + `TagMeasurer` + `RefMeasurer` 完整处理编号生命周期。

#### 定义列表（扩展）
- ✅ 术语行（紧接定义前的非空行）
- ✅ `: definition` 定义行（冒号 + 空格 + 定义内容）
- ✅ 一个术语多个定义
- ✅ 多个术语共享定义
- ✅ 定义内包含块级元素（多段落、代码块等）

> **备注**: `BlockParser` 中已实现 `tryStartDefinitionDescription` 解析逻辑，支持段落 → DefinitionTerm 转换和 DefinitionList 创建。定义内嵌套块级元素通过类似 ListItem 的空行计数 + 缩进延续机制实现。

#### 告示/提醒块（Admonition，扩展）
- ✅ `> [!NOTE]` 提示块
- ✅ `> [!TIP]` 技巧块
- ✅ `> [!IMPORTANT]` 重要块
- ✅ `> [!WARNING]` 警告块
- ✅ `> [!CAUTION]` 注意块

> **备注**: `checkAdmonition()` 已实现 BlockQuote → Admonition 转换逻辑，检测首行 `[!TYPE]` 模式并提取类型和可选标题。

#### Front Matter（扩展）
- ✅ `---\n...\n---` YAML front matter
- ✅ `+++\n...\n+++` TOML front matter

> **备注**: `FrontMatterStarter` 采用无状态设计，移除 `SourceText` 依赖，仅在文档首行匹配 `---`/`+++` 时启动。未闭合的 YAML FrontMatter 优雅降级为 `ThematicBreak`，TOML FrontMatter 延伸到文档末尾。

#### 自定义容器（Custom Container，扩展）
- ✅ `:::\n内容\n:::` 基础容器语法（至少 3 个冒号）
- ✅ `:::type` 指定容器类型
- ✅ `:::type "标题"` 带标题的容器
- ✅ `:::type{.class #id}` 带 CSS class/ID 属性的容器
- ✅ 容器内支持完整的块级元素解析（标题、段落、列表、代码块等）
- ✅ 容器嵌套（外层使用更多冒号如 `::::` 与内层 `:::` 区分）
- ✅ 未闭合容器自动关闭到文档末尾

> **备注**: `tryStartCustomContainer()` 识别 `:::` 围栏语法，解析容器类型、属性（class/ID）和标题。生成 `CustomContainer` AST 节点。容器作为 ContainerNode 允许内部块级解析。嵌套通过冒号数量匹配关闭围栏实现。

#### 图表块（Diagram Block，扩展）
- ✅ `` ```mermaid `` Mermaid 图表块
- ✅ `` ```plantuml `` PlantUML 图表块
- ✅ `` ```dot `` / `` ```graphviz `` Graphviz 图表块
- ✅ 支持 `ditaa`、`flowchart`、`sequence`、`gantt`、`pie`、`mindmap` 等图表类型
- ✅ 图表类型大小写不敏感
- ✅ 图表代码原样保留，不解析 Markdown 语法

> **备注**: 后处理阶段 `convertDiagramBlocks()` 将 info string 匹配已知图表语言的 FencedCodeBlock 转换为 `DiagramBlock` AST 节点。渲染器已支持 Mermaid flowchart/graph 和 PlantUML sequence diagram 的原生 Compose Canvas 绘制（含节点形状、箭头、标签、自动布局），其他图表类型以带类型标签的代码块形式展示。

#### 多列布局（Columns Layout，扩展）
- ✅ `:::columns` 多列布局容器
- ✅ `:::column` 列项（等宽分配）
- ✅ `:::column "width=50%"` 列项（指定百分比宽度）
- ✅ `:::column{width=300px}` 列项（指定像素宽度）
- ✅ 列内支持完整的块级元素解析
- ✅ 支持两列、三列及更多列的布局

> **备注**: 基于已实现的 `CustomContainer` 解析，`ColumnsLayoutProcessor` 后处理器将 `CustomContainer(type="columns")` 转换为 `ColumnsLayout` AST 节点，内部的 `CustomContainer(type="column")` 转换为 `ColumnItem` 节点。处理器通过递归展平算法处理块解析器产生的嵌套结构。列宽支持百分比（`%`）和像素（`px`）两种单位。

#### 分页符（Page Break，扩展）
- ✅ `***pagebreak***` 分页标记（大小写不敏感）
- ✅ 分页标记前后允许空格

> **备注**: `PageBreakStarter` 以优先级 205 注册，在 `ThematicBreak` 之前检测 `***pagebreak***` 模式（正则 `^\s{0,3}\*{3}pagebreak\*{3}\s*$`），生成 `PageBreak` AST 节点。渲染器以虚线分隔符样式展示，可适配 PDF 导出/打印场景的分页控制。

#### 块级属性语法（Attributes，扩展）
- ✅ `{.class}` 块级 CSS 类名属性
- ✅ `{#id}` 块级 ID 属性
- ✅ `{key=value}` 块级键值对属性
- ✅ `{key="quoted value"}` 块级带引号值属性
- ✅ `{.class1 .class2 #id key=value}` 混合属性
- ✅ 独立属性段落（仅含 `{...}`，附加到前一个块）
- ✅ 段落尾部属性行（段落最后一行为 `{...}`，附加到该段落）
- ✅ 支持 Heading、Paragraph、BlockQuote、ListBlock、Table、FencedCodeBlock、CustomContainer
- ✅ 跳过空行匹配前置块

> **备注**: `BlockAttributeProcessor` 后处理器（优先级 150）实现 kramdown/Pandoc 风格的块属性语法。支持两种用法：独立属性段落附加到前一个块并移除，段落尾部属性行附加到该段落并剥离属性文本。行内属性（Link、Image、StyledText）已在 `InlineParser.tryParseAttributes()` 中原生支持。

#### 参考文献引用（Bibliography / Citation，扩展）
- ✅ `[@key]` 行内参考文献引用（渲染为上标链接）
- ✅ `[^bibliography]: key: content` 参考文献定义块
- ✅ 多条目参考文献列表（每行一条 `key: content` 格式）
- ✅ `BibliographyDefinition` AST 节点（块级，携带 `entries: Map<String, BibEntry>`）
- ✅ `CitationReference` AST 节点（行内，携带 `key` 字段）
- ✅ `BibliographyProcessor` 后处理器（优先级 180，将脚注定义转换为参考文献定义）
- ✅ HtmlRenderer 输出 `<div class="bibliography">` + `<sup><a class="citation-ref">` 格式

> **备注**: 参考文献复用脚注定义的解析机制，通过 `BibliographyProcessor` 后处理器将 label 为 `"bibliography"` 的 `FootnoteDefinition` 转换为 `BibliographyDefinition` 节点。行内 `[@key]` 由 `InlineParser.tryParseCitationReference()` 在方括号闭合时检测。

#### 内容标签页（Tabs，扩展）
- ✅ `=== "Tab Title"` 标签页语法（MkDocs Material 风格）
- ✅ 双引号和单引号标题
- ✅ 多标签切换（多个 `===` 块连续排列）
- ✅ 标签内容缩进 4 空格
- ✅ 标签页内支持完整块级元素（代码块、列表、引用等）
- ✅ `TabBlock` AST 节点（块级容器，包含多个 `TabItem` 子节点）
- ✅ `TabItem` AST 节点（块级容器，携带 `title` 属性）

> **备注**: `TabBlockStarter`（优先级 295）检测 `=== "Title"` 语法启动 TabBlock。`BlockParser` 通过缩进续行逻辑管理 TabItem 的内容归属，空行计数控制 TabItem 终止。渲染器以 Material 风格标签栏 + 内容面板呈现，支持交互式标签切换。

#### Figure / 图片标题（扩展）
- ✅ 独立段落中的单个图片自动转换为 `Figure` 节点
- ✅ 图片 title 优先作为 figcaption，fallback 到 alt 文本
- ✅ 保留图片宽高和属性信息
- ✅ `Figure` AST 节点（LeafNode，携带 `imageUrl`、`caption`、`imageWidth`、`imageHeight`、`attributes`）
- ✅ 仅转换独立段落中的单个图片（多图片或含文本的段落不转换）
- ✅ 递归处理容器节点（BlockQuote、ListItem 等内部的独立图片也转换）

> **备注**: `FigureProcessor` 后处理器（优先级 450）实现 Pandoc implicit_figures 语义。在 HTML 过滤（400）之后运行，遍历所有 Paragraph 节点，过滤掉 SoftLineBreak 和空白 Text 后，如果恰好剩余一个 Image 节点，则替换为 Figure 节点。渲染器以居中图片 + 斜体 caption 样式呈现。

**覆盖率**: 85/85 (100%)

---

## 11. 行内元素 — 强调

### ✅ 已支持

#### 斜体
- ✅ `*text*` 星号斜体
- ✅ `_text_` 下划线斜体
- ✅ 左侧 flanking delimiter run 规则
- ✅ 右侧 flanking delimiter run 规则
- ✅ `_` 不在单词内部生效（CommonMark 规则：`foo_bar_baz` 不解析）

#### 粗体
- ✅ `**text**` 星号粗体
- ✅ `__text__` 下划线粗体

#### 粗斜体
- ✅ `***text***` 三星号粗斜体
- ✅ `___text___` 三下划线粗斜体

#### 嵌套
- ✅ 粗体内嵌斜体 `**bold *italic* bold**`
- ✅ 斜体内嵌粗体 `*italic **bold** italic*`
- ✅ 多层嵌套强调
- ✅ 强调定界符的 "多个3的规则"（sum of delimiter lengths not multiple of 3）

**覆盖率**: 13/13 (100%)

---

## 12. 行内元素 — 删除线（GFM 扩展）

### ✅ 已支持
- ✅ `~~text~~` 双波浪线删除线
- ✅ `~text~` 单波浪线删除线（部分处理器支持）
- ✅ 删除线内嵌套其他行内元素
- ✅ 三个及以上波浪线不产生删除线

**覆盖率**: 4/4 (100%)

---

## 13. 行内元素 — 代码

### ✅ 已支持
- ✅ `` `code` `` 单反引号行内代码
- ✅ ` `` code `` ` 双反引号（内容含单反引号时）
- ✅ 多反引号匹配（开闭反引号数量须一致）
- ✅ 开闭标记后/前的单个空格剥离（`` ` `` foo `` ` `` → `foo`）
- ✅ 仅含空格的代码段不做空格剥离
- ✅ 行内代码不解析内部 Markdown 语法
- ✅ 行内代码中换行转为空格
- ✅ 反引号串未匹配时按字面文本处理

**覆盖率**: 8/8 (100%)

---

## 14. 行内元素 — 链接

### ✅ 已支持

#### 行内链接
- ✅ `[text](url)` 基本链接
- ✅ `[text](url "title")` 带双引号标题
- ✅ `[text](url 'title')` 带单引号标题
- ✅ `[text](url (title))` 带括号标题
- ✅ `[text](<url>)` 尖括号 URL（可含空格）
- ✅ `[text]()` 空 URL
- ✅ URL 中括号的平衡匹配
- ✅ 链接文本内嵌套行内元素（粗体、斜体、代码、图片等）
- ✅ 链接不能嵌套链接

#### 链接高级属性（扩展）
- ✅ `[text](url){rel="nofollow" target="_blank"}` 链接后属性块
- ✅ `[text](url){download="file.pdf"}` 下载属性
- ✅ 属性块支持 CSS class（`.classname`）、ID（`#idname`）、键值对（`key=value`）
- ✅ 属性块支持带引号的值（`key="value"` / `key='value'`）
- ✅ Link AST 节点携带 `attributes`、`cssClasses`、`cssId` 字段

> **备注**: 复用 `tryParseAttributes()` 解析器，在链接解析完成后检测并解析 `{...}` 属性块。与图片属性块共享同一解析逻辑。

#### 引用链接
- ✅ `[text][label]` 完整引用链接
- ✅ `[label][]` 折叠引用链接
- ✅ `[label]` 简写引用链接
- ✅ label 大小写不敏感匹配
- ✅ label 折叠连续空白

#### 自动链接
- ✅ `<url>` 尖括号自动链接（绝对 URI）
- ✅ `<email@example.com>` 邮箱自动链接
- ✅ GFM autolink（裸 URL 自动识别，如 `https://example.com`）
- ✅ GFM autolink（裸 `www.` 前缀自动识别）
- ✅ GFM autolink（裸邮箱自动识别）
- ✅ GFM autolink 尾部标点截断规则（不含尾部 `.`, `)`, `;` 等）
- ✅ URL 中特殊字符百分号编码（非尖括号 URL 自动编码，保留已有 `%XX` 序列）

**覆盖率**: 27/27 (100%)

---

## 15. 行内元素 — 图片

### ✅ 已支持
- ✅ `![alt](url)` 基本图片
- ✅ `![alt](url "title")` 带标题图片
- ✅ `![alt](<url>)` 尖括号 URL 图片
- ✅ `![alt][label]` 引用式图片
- ✅ `![alt][]` 折叠引用图片
- ✅ `![alt]` 简写引用图片
- ✅ alt 文本内行内元素解析（粗体、斜体、代码等）
- ✅ alt 文本中嵌套图片时提取纯文本
- ✅ 图片可嵌套在链接内 `[![alt](img-url)](link-url)`

#### 图片高级特性（扩展）
- ✅ `![alt](url =200x300)` 指定宽高（像素）
- ✅ `![alt](url =200x)` 仅指定宽度
- ✅ `![alt](url =x300)` 仅指定高度
- ✅ `![alt](url =200x300 "title")` 尺寸 + 标题
- ✅ `![alt](url){.class #id key=value}` 属性块语法
- ✅ 属性块支持 CSS class（`.classname`）、ID（`#idname`）、键值对（`key=value`）
- ✅ 属性块支持带引号的值（`key="value"` / `key='value'`）
- ✅ 尺寸 + 标题 + 属性块组合使用

> **备注**: `tryParseLinkTail()` 在图片模式下额外解析 `=WxH` 尺寸后缀。`tryParseAttributes()` 解析 `{...}` 属性块，支持 `.class`、`#id`、`key=value` 语法。Image AST 节点携带 `imageWidth`、`imageHeight`、`attributes` 字段。渲染器通过 `LocalImageRenderer` CompositionLocal 支持自定义图片加载组件注入。

**覆盖率**: 17/17 (100%)

---

## 16. 行内元素 — HTML

### ✅ 已支持
- ✅ 行内开标签 `<tag>` / `<tag attr="value">`（含属性）
- ✅ 行内闭标签 `</tag>`
- ✅ 自闭合标签 `<br />` / `<img />`
- ✅ 行内 HTML 注释 `<!-- comment -->`
- ✅ 行内处理指令 `<? ... ?>`
- ✅ 行内 CDATA `<![CDATA[ ... ]]>`
- ✅ 行内声明 `<!DECLARATION>`
- ✅ 行内 HTML 内不解析 Markdown 语法

**覆盖率**: 8/8 (100%)

---

## 17. 行内元素 — 转义与实体

### ✅ 已支持

#### 转义字符
- ✅ `\` + ASCII 标点符号转义（共 32 个可转义字符）：
  `` \` \~ \! \@ \# \$ \% \^ \& \* \( \) \- \_ \= \+ \[ \] \{ \} \| \\ \; \: \' \" \, \. \< \> \/ \? ``
- ✅ 转义在围栏代码块中无效
- ✅ 转义在行内代码中无效
- ✅ 转义在自动链接中无效
- ✅ `\` + 换行 = 硬换行

#### HTML 实体
- ✅ 命名实体（`&amp;` `&lt;` `&gt;` `&nbsp;` `&copy;` `&mdash;` `&hellip;` 等）
- ✅ 十进制数字实体（`&#123;` `&#9829;`）
- ✅ 十六进制数字实体（`&#x1F4A9;` `&#xA9;`）
- ✅ 无效实体按字面文本处理（`&foo;`）
- ✅ 实体在代码块/行内代码中不解析

**覆盖率**: 10/10 (100%)

---

## 18. 行内元素 — 换行

### ✅ 已支持
- ✅ 硬换行：行尾两个或更多空格 + 换行符
- ✅ 硬换行：行尾反斜杠 `\` + 换行符
- ✅ 软换行：普通换行符（渲染为空格或换行，取决于渲染器）
- ✅ 硬换行不在块级元素结尾生效（段落/标题最后一行无效）
- ✅ 硬换行不在行内代码中生效

**覆盖率**: 5/5 (100%)

---

## 19. 行内元素 — 扩展

### ✅ 已支持

#### 缩写（Abbreviation，扩展）
- ✅ `*[abbr]: Full Text` 缩写定义（块级解析，存储到 Document 节点）
- ✅ 正文中出现 `abbr` 时自动添加提示（tooltip）（词边界匹配替换）

#### 键盘按键（扩展）
- ✅ `<kbd>Ctrl</kbd>` 键盘按键标记（行内解析，优先于通用 HTML 标签）

#### 脚注引用（扩展）
- ✅ `[^label]` 脚注引用标记
- ✅ 脚注引用渲染为上标编号
- ✅ 点击脚注跳转到脚注定义

> **备注**: `InlineParser` 的 `appendCloseBracket()` 中已实现 `[^label]` 检测，当方括号内文本以 `^` 开头时创建 `FootnoteReference` 节点并自动分配索引编号；`markdown-renderer` 则通过 `BringIntoViewRequester` 将上标点击映射到对应的 `FootnoteDefinition`。

#### 行内数学（扩展）
- ✅ `$...$` 单美元符行内数学
- ✅ `$$...$$` 双美元符行内数学（不独占一行时为行内）
- ✅ 数学公式内不解析 Markdown 语法
- ✅ `$` 紧邻数字时不触发数学模式（避免 `$100` 误解析）

#### 高亮（扩展）
- ✅ `==text==` 双等号高亮标记
- ✅ 高亮内嵌套其他行内元素

#### 上标（扩展）
- ✅ `^text^` 上标标记
- ✅ `<sup>text</sup>` HTML 上标

#### 下标（扩展）
- ✅ `~text~` 下标标记（与删除线 `~~` 区分）
- ✅ `<sub>text</sub>` HTML 下标（作为行内 HTML 处理）

> **备注**: `appendTildeRun()` 已实现单 `~` 作为下标分隔符推入分隔符栈，`processEmphasis()` 中添加了 `Subscript` 创建分支，`delimsMatch()` 确保单 `~` 和双 `~~` 不混合匹配。

#### 插入文本（扩展）
- ✅ `++text++` 插入标记（渲染为下划线/插入样式）

#### 自定义行内样式（扩展）
- ✅ `[文本]{.red .bold}` CSS class 行内样式
- ✅ `[文本]{style="background:yellow"}` 内联 CSS 样式
- ✅ `[文本]{#myid}` 自定义 ID
- ✅ `[文本]{.class #id style="color:red"}` 混合属性
- ✅ StyledText AST 节点携带 `attributes`、`cssClasses`、`cssId`、`style` 字段
- ✅ 样式文本内支持嵌套行内元素（粗体、斜体、代码等）

> **备注**: `[text]{attrs}` 语法在 `appendCloseBracket()` 中当方括号后紧跟 `{` 且非链接/图片/脚注时触发。复用 `tryParseAttributes()` 解析属性块，生成 `StyledText` ContainerNode。

#### Emoji（扩展）
- ✅ `:emoji_name:` 短代码 Emoji（如 `:smile:` → 😄）
- ✅ Unicode Emoji 直接支持（如 `😀` 直接渲染）
- ✅ Emoji 短代码 → Unicode 自动映射（200+ 标准 Emoji）
- ✅ `:my-emoji:` 自定义 Emoji 别名映射（用户传入 `customEmojiMap`）
- ✅ 自定义映射优先于标准映射
- ✅ `:)` → 😊 ASCII 表情符号自动转换（40+ 种 ASCII 表情）
- ✅ ASCII 表情转换可通过 `enableAsciiEmoticons` 开关控制
- ✅ Emoji AST 节点携带 `unicode` 字段供渲染器直接使用

> **备注**: `STANDARD_EMOJI_MAP` 包含 200+ 标准短代码到 Unicode 的映射。`ASCII_EMOTICON_MAP` 包含 40+ 常见 ASCII 表情。`appendPossibleEmoji()` 和 `appendText()` 分别处理 `:` 前缀和非 `:` 前缀的表情匹配。参数通过 `MarkdownParser` → `StreamingParser` → `IncrementalEngine` → `InlineParser` 链路传递。

#### 剧透/折叠文本（Spoiler，扩展）
- ✅ `>!spoiler text!<` 剧透文本标记（Discord / Reddit 风格）
- ✅ 文字颜色与背景色相同实现遮挡效果
- ✅ 不跨行（遇到换行终止）
- ✅ `Spoiler` AST 节点（行内容器）
- ✅ 未闭合 `>!` 不解析（优雅降级为纯文本）

> **备注**: `InlineParser.appendSpoiler()` 在 `>!` 开始标记处触发，扫描到 `!<` 结束标记生成 `Spoiler` 容器节点。渲染器使用 `theme.spoilerColor` 同时设置文字颜色和背景色，实现视觉遮挡效果。

#### Wiki 链接（扩展）
- ✅ `[[page]]` 基础 Wiki 链接
- ✅ `[[page|显示文本]]` 带自定义显示文本的 Wiki 链接
- ✅ 链接目标支持空格和 CJK 字符
- ✅ `WikiLink` AST 节点（LeafNode，携带 `target` 和可选 `label`）
- ✅ 不跨行（遇到换行终止）
- ✅ 空 `[[]]` 不解析（优雅降级）

> **备注**: `InlineParser.tryAppendWikiLink()` 在检测到 `[[` 时触发，扫描到 `]]` 闭合标记生成 `WikiLink` 叶节点。支持 `|` 分隔的可选显示文本。渲染器使用 `LinkAnnotation.Clickable` 以 "wikilink" 标签触发 `onLinkClick` 回调。

#### Ruby 注音（扩展）
- ✅ `{漢字|かんじ}` 基础 Ruby 注音标注
- ✅ 中文拼音标注 `{中文|zhōngwén}`
- ✅ `RubyText` AST 节点（LeafNode，携带 `base` 和 `annotation`）
- ✅ 不跨行（遇到换行终止）
- ✅ 空 base 或空 annotation 不解析
- ✅ 不与 `{%` 短代码语法冲突

> **备注**: `InlineParser.appendPossibleRuby()` 在检测到 `{`（且下一字符非 `%`）时触发，扫描到 `}` 并检查 `|` 分隔符。渲染器通过 `InlineTextContent` 机制渲染为上方注音 + 下方基础文字的 Column 布局。

**覆盖率**: 50/50 (100%)

---

## 20. 流式解析引擎（StreamingParser）

### ✅ 已支持

#### 流式 API
- ✅ `beginStream()` 开始流式输入
- ✅ `append(chunk)` 追加文本块
- ✅ `endStream()` 结束流式输入（最终全量解析，无修复）
- ✅ `abort()` 中止流式输入
- ✅ `fullParse(input)` 非流式完整解析（向后兼容）

#### Append-Only 增量解析
- ✅ 尾部脏区域检测（从最后一个未关闭块到文本末尾）
- ✅ 稳定前缀块直接复用（不重新解析）
- ✅ 块稳定性分类（最后一个块始终视为"仍在构建中"）
- ✅ contentHash 比对优化（FNV-1a 哈希）

#### 按需内联解析（Lazy Inline）
- ✅ `ContainerNode` 扩展懒加载机制，块级解析完成后内联元素延迟到首次访问 `children` 时才解析
- ✅ `setLazyInlineContent()` 设置延迟解析内容和解析器引用
- ✅ `ensureInlineParsed()` 在首次访问子节点时触发内联解析

> **备注**: 懒加载机制通过 `_lazyInlineContent`、`_lazyInlineParser`、`_inlineParsed` 三个字段实现。`BlockParser.setupLazyInlineParsing()` 在 `finalizeBlock` 阶段为需要内联解析的容器节点设置延迟内容，避免一次性解析所有内联元素的性能开销。

#### 块级自动关闭（LLM 容错）
- ✅ 未关闭围栏代码块自动补结束符
- ✅ 未关闭数学块（`$$`）自动关闭
- ✅ 未关闭 Front Matter 自动关闭
- ✅ 未关闭 HTML 块自动关闭
- ✅ 递归处理 BlockQuote、ListBlock、ListItem、Table 内的未关闭结构

#### 行内自动修复（InlineAutoCloser）
- ✅ 栈式 O(n) 单遍扫描未关闭行内结构
- ✅ 未关闭粗体（`**`）自动补闭合
- ✅ 未关闭斜体（`*`/`_`）自动补闭合
- ✅ 未关闭行内代码（`` ` ``）自动补闭合
- ✅ 未关闭删除线（`~~`）自动补闭合
- ✅ 未关闭高亮（`==`）自动补闭合
- ✅ 未关闭行内数学（`$`/`$$`）自动补闭合
- ✅ 未关闭链接/图片 URL（`[text](`）自动补闭合
- ✅ 嵌套结构正确处理
- ✅ 转义字符跳过

**覆盖率**: 27/27 (100%)

---

## 21. 字符与编码

### ✅ 已支持
- ✅ UTF-8 编码支持
- ✅ U+0000（NULL）替换为 U+FFFD（REPLACEMENT CHARACTER）
- ✅ 制表符（Tab）展开为空格（对齐到 4 的倍数列）
- ✅ 行尾序列标准化（`\r\n`、`\r`、`\n` 统一为 `\n`）
- ✅ Unicode 标点符号识别（用于强调定界符规则）
- ✅ Unicode 空白字符识别（用于强调定界符规则）

#### CJK / 中文本地化优化
- ✅ CJK 字符检测（CJK Unified Ideographs、Extension A、Compatibility、Hiragana、Katakana、Bopomofo、Hangul）
- ✅ 全角标点符号识别（`。，、；：？！""''【】《》（）—…·` 及 `FF01-FF5E` 全角 ASCII 标点）
- ✅ 全角标点纳入 flanking delimiter run 规则（`*中文*。` 正确解析为斜体）
- ✅ 中文空格视为普通空格（不影响定界符判定）

> **备注**: `CharacterUtils` 扩展了 `isCJK()`、`isFullWidthPunctuation()`、`isCJKOrFullWidthPunctuation()` 方法。`InlineParser.appendDelimiterRun()` 将全角标点视为等效于 Unicode 标点，确保 `*中文*。`、`**粗体**，` 等场景正确解析强调。

**覆盖率**: 10/10 (100%)

---

## 22. HTML 生成器（HtmlRenderer）

### ✅ 已支持
- ✅ AST → HTML 完整转换（支持全部 41+ AST 节点类型）
- ✅ `HtmlRenderer.render(document)` 渲染已解析文档
- ✅ `HtmlRenderer.renderMarkdown(markdown, flavour)` 便捷 API（解析 + 渲染一步完成，可选 flavour 参数）
- ✅ `softBreak` 配置（软换行输出字符，默认 `\n`）
- ✅ `hardBreak` 配置（硬换行标签，默认 `<br />\n`）
- ✅ `escapeHtml` 配置（是否转义 HTML 特殊字符）
- ✅ `xhtml` 配置（是否输出 XHTML 自闭合标签）
- ✅ 块级属性 `blockAttributes` 输出为 HTML 属性（class、id、key=value）
- ✅ 标题自动 ID / 自定义 ID 输出
- ✅ 表格对齐属性（`style="text-align:..."`)
- ✅ 围栏代码块语言类名（`class="language-xxx"`）
- ✅ HTML 特殊字符转义（`<`、`>`、`&`、`"`）

> **备注**: `HtmlRenderer` 实现 `NodeVisitor<Unit>` 接口，通过 Visitor 模式遍历 AST 并生成标准 HTML。支持服务端 SSR 和 HTML 导出场景。参考 JetBrains Markdown 的 `HtmlGenerator` API 设计。

**覆盖率**: 12/12 (100%)

---

## 23. 语法验证 / Linting

### ✅ 已支持

#### 诊断模型
- ✅ `DiagnosticSeverity` 三级严重度（ERROR / WARNING / INFO）
- ✅ `DiagnosticCode` 枚举（13 种诊断码）
- ✅ `Diagnostic` 数据类（携带行号、严重度、诊断码、消息）
- ✅ `DiagnosticResult` 集合类（支持按严重度过滤、排序、toString 输出）
- ✅ 解析结果通过 `Document.diagnostics` 附带诊断信息

#### 检测规则
- ✅ 标题层级跳跃检测（如 h1 直接跳到 h3）
- ✅ 重复标题 ID 检测（独立计算 slug，不依赖 HeadingIdProcessor 的去重结果）
- ✅ 无效脚注引用检测（引用了不存在的脚注定义）
- ✅ 未使用脚注定义检测（定义了但从未引用）
- ✅ 空链接目标检测（`[text]()`）
- ✅ 图片缺失 alt 文本检测

#### WCAG 无障碍检测（扩展）
- ✅ `EMPTY_LINK_TEXT` 检测：链接文本为空时生成诊断（确保所有链接可被屏幕阅读器描述）
- ✅ `LINK_TEXT_NOT_DESCRIPTIVE` 检测：链接文本为 "click here"、"here"、"read more" 等非描述性文本时警告
- ✅ `MISSING_LANG_IN_CODE_BLOCK` 检测：围栏代码块缺少 info string 语言标识时提示（辅助技术依赖语言标注）
- ✅ `TABLE_MISSING_HEADER` 检测：表格缺少表头行时警告（屏幕阅读器依赖 `<th>` 建立数据关联）
- ✅ `LONG_ALT_TEXT` 检测：图片 alt 文本超过 125 字符时建议精简（WCAG 最佳实践）

> **备注**: WCAG 无障碍检测作为 `LintingPostProcessor` 的扩展规则集成，5 个新增诊断码均为 WARNING 级别。检测逻辑遵循 [WCAG 2.1 AA](https://www.w3.org/TR/WCAG21/) 标准，覆盖链接可访问性（1.1.1/2.4.4）、代码语义（1.3.1）、表格结构（1.3.1）和图片描述（1.1.1）。

#### 集成方式
- ✅ `MarkdownParser(enableLinting = true)` 开关控制
- ✅ `LintingPostProcessor`（优先级 900）作为后处理器运行
- ✅ 全量解析、增量编辑均附带诊断结果

> **备注**: `LintingPostProcessor` 实现 `PostProcessor` 接口，在所有其他后处理器完成后运行。通过递归遍历 AST 检测各类问题，诊断结果同时附加到 `Document.diagnostics` 和 `MarkdownParser.diagnostics`。

**覆盖率**: 19/19 (100%)

---

## 24. 短代码（Shortcodes，扩展）

### ✅ 已支持

#### 块级短代码
- ✅ `{% tag args %}...{% endtag %}` 块级短代码（独占行，内容可跨多行）
- ✅ `{% endtag %}` 闭合标记（标签名须与开启标记匹配）
- ✅ 块级短代码内支持嵌套 Markdown 块级元素解析

#### 行内短代码
- ✅ `{% tag args %}` 行内短代码（不独占行时作为行内元素解析）

#### 参数解析
- ✅ 位置参数（positional）：`{% youtube dQw4w9WgXcQ %}`
- ✅ 带引号参数（quoted）：`{% include "header.html" %}`
- ✅ 键值对参数（key=value）：`{% img src="/photo.jpg" width=300 %}`

#### AST 节点与渲染
- ✅ `ShortcodeBlock` AST 节点（块级短代码，携带 tag、arguments、body）
- ✅ `ShortcodeInline` AST 节点（行内短代码，携带 tag、arguments）
- ✅ `HtmlRenderer` 输出 `data-shortcode` 属性（`<div data-shortcode="tag">` / `<span data-shortcode="tag">`）

> **备注**: 短代码解析器（`ShortcodeStarter` 块级 + `InlineParser` 行内）识别 `{% ... %}` 语法，解析标签名和参数列表。参数解析器支持三种格式混用：裸字符串位置参数、双引号/单引号字符串、`key=value` 键值对。块级短代码通过 `{% endtag %}` 闭合，行内短代码为自闭合。渲染器通过 `data-shortcode` 属性将短代码语义传递给前端，便于 JS 插件进一步处理。

**覆盖率**: 8/8 (100%)

---

## 📊 总体覆盖率

| # | 类别 | 已支持 | 缺失 | 覆盖率 |
|---|------|--------|------|--------|
| 1 | 标题 | 17/17 | 0 | 100% |
| 2 | 段落与空行 | 5/5 | 0 | 100% |
| 3 | 代码块 | 17/17 | 0 | 100% |
| 4 | 块引用 | 8/8 | 0 | 100% |
| 5 | 列表 | 20/20 | 0 | 100% |
| 6 | 分隔线 | 6/6 | 0 | 100% |
| 7 | 表格（GFM） | 11/11 | 0 | 100% |
| 8 | HTML 块 | 10/10 | 0 | 100% |
| 9 | 链接引用定义 | 12/12 | 0 | 100% |
| 10 | 块级扩展 | 85/85 | 0 | 100% |
| 11 | 强调 | 13/13 | 0 | 100% |
| 12 | 删除线（GFM） | 4/4 | 0 | 100% |
| 13 | 行内代码 | 8/8 | 0 | 100% |
| 14 | 链接 | 27/27 | 0 | 100% |
| 15 | 图片 | 17/17 | 0 | 100% |
| 16 | 行内 HTML | 8/8 | 0 | 100% |
| 17 | 转义与实体 | 10/10 | 0 | 100% |
| 18 | 换行 | 5/5 | 0 | 100% |
| 19 | 行内扩展 | 50/50 | 0 | 100% |
| 20 | 流式解析引擎 | 27/27 | 0 | 100% |
| 21 | 字符与编码 | 10/10 | 0 | 100% |
| 22 | HTML 生成器 | 12/12 | 0 | 100% |
| 23 | 语法验证/Linting | 19/19 | 0 | 100% |
| 24 | 短代码（Shortcodes） | 8/8 | 0 | 100% |
| | **总计** | **410/410** | **0** | **100%** |

---

## 🧪 CommonMark Spec 0.31.2 Compliance

**652/652 (100%)** examples passing.

Tested via `CommonMarkSpecTest` which runs all 652 examples from the
[CommonMark Spec 0.31.2](https://spec.commonmark.org/0.31.2/) against
`HtmlRenderer.renderMarkdown(markdown, flavour = CommonMarkFlavour)`.

Run tests:
```bash
./gradlew :markdown-parser:jvmTest --tests "com.hrm.markdown.parser.CommonMarkSpecTest"
```

Results are written to `/tmp/commonmark-results.txt`.

---

## 🔀 Flavour System

`MarkdownFlavour` controls which syntax features are enabled:

| Property | CommonMark | GFM | MarkdownExtra | Extended (default) |
|----------|-----------|-----|---------------|---------------------|
| `blockStarters` | core only | core + table | core + table + footnote + deflist + fenced code | all |
| `postProcessors` | none | none | abbreviation | heading ID, abbreviation, etc. |
| `enableGfmAutolinks` | `false` | `true` | `false` | `true` |
| `enableExtendedInline` | `false` | `true` | `true` | `true` |
| `enableStrikethrough` | `false` | `true` | `true` | `true` |
| `enableEmphasisCoalescing` | `false` | `true` | `false` | `false` |

MarkdownExtra flavour (`MarkdownExtraFlavour` object):
- 表格（GFM 兼容）、脚注（`[^label]` 定义与引用）、定义列表（`: definition`）
- 缩写（`*[abbr]: Full Text`）、围栏代码块（`` ``` `` 和 `~~~`）
- 基于 [PHP Markdown Extra](https://michelf.ca/projects/php-markdown/extra/) 规范子集

Extended inline syntax (disabled in CommonMark):
- `~~strikethrough~~`, `==highlight==`, `++insert++`
- `^superscript^`, `$math$`, `:emoji:`

### Flavour 配置缓存

- ✅ `FlavourCache` 单例缓存（以 `MarkdownFlavour` 实例身份标识为 key）
- ✅ `BlockStarter` 列表不可变快照缓存
- ✅ `PostProcessor` 列表不可变快照缓存
- ✅ `BlockStarterRegistry` 预构建并冻结（`freeze()`），避免重复排序
- ✅ `BlockStarterRegistry.freeze()` 冻结机制，防止共享实例被意外修改
- ✅ `invalidate(flavour)` / `clearAll()` 手动缓存管理

> **备注**: `FlavourCache.of(flavour)` 为 `object` 单例方言（CommonMarkFlavour、GFMFlavour、ExtendedFlavour）全局缓存一份配置。`IncrementalEngine` 通过 `FlavourCache` 获取缓存的 `BlockStarterRegistry`（已冻结）和 `PostProcessorRegistry`（每次新建），消除高频解析场景下的重复初始化开销。

Usage:
```kotlin
// strict commonmark
val doc = MarkdownParser(CommonMarkFlavour).parse(input)

// default (all extensions enabled)
val doc = MarkdownParser().parse(input)

// one-shot html rendering with specific flavour
val html = HtmlRenderer.renderMarkdown(input, flavour = CommonMarkFlavour)
```

---

## 🎯 规范参考

| 规范 | 说明 | 涉及章节 |
|------|------|----------|
| [CommonMark Spec 0.31.2](https://spec.commonmark.org/0.31.2/) | Markdown 核心规范 | 1-9, 11, 13-18, 21 |
| [GFM Spec 0.29](https://github.github.com/gfm/) | GitHub Flavored Markdown | 5（任务列表）、7（表格）、8（禁止 HTML）、12（删除线）、14（自动链接扩展） |
| [Markdown Guide Extended Syntax](https://www.markdownguide.org/extended-syntax/) | 社区扩展语法参考 | 1（标题 ID）、10（定义列表/告示/TOC/Front Matter/自定义容器/图表块）、19（高亮/上标/下标/Emoji/缩写） |
| 扩展语法（社区约定） | 脚注、数学公式、插入文本、键盘、自定义容器、图表嵌入 | 10, 19 |

---

## 🔮 潜在可扩展能力

以下为业界主流 Markdown 处理器（Pandoc、kramdown、markdown-it、Obsidian、Typora、MkDocs Material 等）中存在但本项目尚未实现的扩展特性，按 **实用优先级 + 场景分类** 整理，可根据需求择机实现。

> **优先级说明**: P1 = 高优先级/核心场景，P2 = 中优先级/常用扩展，P3 = 低优先级/小众场景，P4 = 可选/边缘场景

### 一、块级结构扩展

> **备注**: 多列布局、目录高级配置、分页符已实现，详见第 10 章「块级结构 — 扩展」。

目前无待实现的块级结构扩展。

### 二、行内元素扩展

> **备注**: 链接高级属性、自定义行内样式、数学公式编号/引用、Emoji 增强已实现，详见第 14 章「链接」、第 19 章「行内扩展」、第 10 章「块级扩展 — 数学公式块」。

目前无待实现的行内元素扩展。

### 三、解析器能力增强

> **备注**: 自定义语法规则/短代码、多规范兼容（MarkdownExtra flavour）、代码块增强已实现，详见第 24 章「短代码」、第 3 章「代码块」和 Flavour System 章节。

| 优先级 | 特性 | 说明 |
|--------|------|------|
| **P3** | 解析缓存 | 对相同 Markdown 文本缓存解析后的 AST 节点树，避免重复解析，降低 CMS/文档系统等高频场景的 CPU/内存消耗 |
| **P3** | HTML 反向解析 | 将 HTML 文本反向解析为 Markdown（保留标题、列表、链接、图片等基础格式），满足富文本编辑器内容导入/迁移场景 |xz

### 四、特殊场景适配

> **备注**: 无障碍支持（WCAG）已实现，详见第 23 章「语法验证/Linting — WCAG 无障碍检测」。Wiki 链接、Figure 图片标题、Ruby 注音已实现，详见第 19 章「行内扩展」和第 10 章「块级扩展」。目录自动编号在渲染器层面实现（`MarkdownConfig.enableHeadingNumbering`）。

| 优先级 | 特性 | 语法示例 | 说明 |
|--------|------|----------|------|
| **P4** | 嵌入/Transclusion | `![[other-file]]` | 嵌入其他 Markdown 文件内容（Obsidian 风格） |
