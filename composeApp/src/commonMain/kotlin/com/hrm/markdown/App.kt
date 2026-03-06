package com.hrm.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.parser.MarkdownParser
import com.hrm.markdown.parser.ast.Document
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownTheme
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    MaterialTheme {
        var selectedTab by remember { mutableStateOf(0) }
        val tabs = listOf("普通渲染", "流式渲染")

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .safeContentPadding()
                .fillMaxSize()
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> NormalMarkdownDemo()
                1 -> StreamingMarkdownDemo()
            }
        }
    }
}

@Composable
private fun NormalMarkdownDemo() {
    Markdown(
        markdown = normalDemoMarkdown,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        theme = MarkdownTheme(),
        onLinkClick = { url ->
            println("Clicked link: $url")
        }
    )
}

@Composable
private fun StreamingMarkdownDemo() {
    val parser = remember { MarkdownParser() }
    var document by remember { mutableStateOf<Document?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    var streamFinished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (!isRunning) {
                        isRunning = true
                        streamFinished = false
                        document = null
                        scope.launch {
                            parser.beginStream()
                            for (token in streamingTokens) {
                                // 每次 append 后立即更新 document 状态，触发 UI 重组
                                document = parser.append(token)
                                // 慢速模式使用固定 200ms 延迟，便于逐帧观察
                                delay(token.streamDelay())
                            }
                            document = parser.endStream()
                            isRunning = false
                            streamFinished = true
                        }
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isRunning) "生成中..." else if (streamFinished) "重新生成" else "开始流式生成")
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isRunning) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "模拟 LLM 逐 token 输出中...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (streamFinished) {
                Text(
                    text = "生成完毕",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

        }

        if (document == null && !isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "点击「开始流式生成」按钮，模拟 LLM 逐 token 输出 Markdown\n\n" +
                            "StreamingParser 会实时增量解析，并自动修复未关闭的语法结构\n" +
                            "（如未闭合的代码块、粗体、链接等）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
        }

        val doc = document
        if (doc != null) {
            Markdown(
                document = doc,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                theme = MarkdownTheme(),
                scrollState = scrollState,
                isStreaming = isRunning,
                onLinkClick = { url ->
                    println("Clicked link: $url")
                }
            )

            // 流式滚动策略：
            // 流式生成期间：自动滚动跟随内容增长，直接滚动到底部。
            // 用户上滑 → 退出自动跟随；用户滚回底部 → 恢复自动跟随。
            // 流式结束后：确保最终滚动到底部。
            var autoFollow by remember { mutableStateOf(true) }

            LaunchedEffect(isRunning) {
                if (!isRunning) return@LaunchedEffect
                autoFollow = true

                // 检测用户主动上滑 → 退出自动跟随
                launch {
                    snapshotFlow { scrollState.isScrollInProgress to scrollState.value }
                        .collect { (isScrolling, value) ->
                            if (isScrolling && value < scrollState.maxValue - 100) {
                                autoFollow = false
                            }
                        }
                }

                // 检测用户滚动回底部附近 → 重新进入自动跟随
                launch {
                    snapshotFlow { scrollState.value to scrollState.maxValue }
                        .collect { (value, maxValue) ->
                            if (!autoFollow && maxValue > 0 && value >= maxValue - 100) {
                                autoFollow = true
                            }
                        }
                }

                // 匀速滚动循环：每帧检查目标位置，平滑追赶到底部
                while (true) {
                    delay(16L) // ~60fps
                    if (!autoFollow) continue

                    val maxValue = scrollState.maxValue
                    val currentValue = scrollState.value
                    val remaining = maxValue - currentValue

                    if (remaining > 0) {
                        if (remaining <= 10) {
                            scrollState.scrollTo(maxValue)
                        } else {
                            val step = (remaining * 0.35f).toInt().coerceAtLeast(1)
                            scrollState.scrollTo(currentValue + step)
                        }
                    }
                }
            }

            // 流式结束后，确保滚动到底部
            LaunchedEffect(streamFinished) {
                if (!streamFinished) return@LaunchedEffect
                delay(50L)
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}

/**
 * 根据 token 内容决定延迟，模拟真实 LLM 输出节奏
 */
private fun String.streamDelay(): Long = when {
    this == "\n" || this == "\n\n" -> 30L
    this.startsWith("#") -> 15L
    this.startsWith("```") -> 20L
    this.length > 10 -> 10L
    else -> 25L
}

/**
 * 模拟 LLM 流式输出的 token 序列
 * 每个 token 模拟真实 LLM 的输出粒度（通常按词/符号级别）
 * 覆盖各种 Markdown 语法结构，内容足够长以展示滚动和增量渲染效果
 */
private val streamingTokens = listOf(
    // ═══ 一级标题 ═══
    "# ",
    "Kotlin ",
    "Multiplatform ",
    "完全",
    "指南",
    "\n\n",

    // ═══ 引言段落 ═══
    "Kotlin ",
    "Multiplatform",
    "（简称 ",
    "**KMP**）",
    "是由 ",
    "JetBrains ",
    "开发的",
    "一项",
    "革命性",
    "技术，",
    "允许",
    "开发者",
    "使用",
    "同一份",
    " Kotlin ",
    "代码",
    "同时",
    "构建 ",
    "Android、",
    "iOS、",
    "Desktop、",
    "Web ",
    "等多个",
    "平台的",
    "应用。",
    "本文将",
    "从基础",
    "概念到",
    "高级实践，",
    "全方位",
    "介绍 ",
    "KMP ",
    "的核心",
    "知识。",
    "\n\n",

    // ═══ 二级标题 - 项目结构 ═══
    "## ",
    "1. ",
    "项目",
    "结构",
    "概览",
    "\n\n",

    "一个",
    "典型的 ",
    "KMP ",
    "项目",
    "包含",
    "以下",
    "模块：",
    "\n\n",

    // 无序列表
    "- ",
    "**`shared`**",
    " — 共享",
    "业务",
    "逻辑",
    "模块，",
    "包含 ",
    "`commonMain`、",
    "`androidMain`、",
    "`iosMain` ",
    "等源集",
    "\n",
    "- ",
    "**`androidApp`**",
    " — Android ",
    "平台",
    "入口，",
    "使用 ",
    "Jetpack ",
    "Compose",
    "\n",
    "- ",
    "**`iosApp`**",
    " — iOS ",
    "平台",
    "入口，",
    "使用 ",
    "SwiftUI ",
    "或 ",
    "UIKit",
    "\n",
    "- ",
    "**`desktopApp`**",
    " — 桌面",
    "平台",
    "入口，",
    "使用 ",
    "Compose ",
    "for ",
    "Desktop",
    "\n",
    "- ",
    "**`webApp`**",
    " — Web ",
    "平台",
    "入口，",
    "编译为 ",
    "JavaScript ",
    "或 ",
    "WasmJS",
    "\n\n",

    // ═══ 代码块 - Gradle 配置 ═══
    "### ",
    "Gradle ",
    "配置",
    "示例",
    "\n\n",

    "以下是 ",
    "`build.gradle.kts` ",
    "的典型",
    "配置：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "kotlin {\n",
    "    android",
    "Target()\n",
    "    ios",
    "X64()\n",
    "    ios",
    "Arm64()\n",
    "    ios",
    "Simulator",
    "Arm64()\n",
    "    jvm",
    "(\"desktop\")\n",
    "    \n",
    "    source",
    "Sets {\n",
    "        val ",
    "commonMain ",
    "by getting",
    " {\n",
    "            ",
    "dependencies {\n",
    "                ",
    "implementation",
    "(\"org.jetbrains",
    ".kotlinx:",
    "kotlinx-coroutines",
    "-core:",
    "1.7.3\")\n",
    "                ",
    "implementation",
    "(\"org.jetbrains",
    ".kotlinx:",
    "kotlinx-serialization",
    "-json:",
    "1.6.0\")\n",
    "                ",
    "implementation",
    "(\"io.ktor:",
    "ktor-client",
    "-core:",
    "2.3.5\")\n",
    "            }\n",
    "        }\n",
    "        val ",
    "androidMain ",
    "by getting",
    " {\n",
    "            ",
    "dependencies {\n",
    "                ",
    "implementation",
    "(\"io.ktor:",
    "ktor-client",
    "-android:",
    "2.3.5\")\n",
    "            }\n",
    "        }\n",
    "        val ",
    "iosMain ",
    "by getting",
    " {\n",
    "            ",
    "dependencies {\n",
    "                ",
    "implementation",
    "(\"io.ktor:",
    "ktor-client",
    "-darwin:",
    "2.3.5\")\n",
    "            }\n",
    "        }\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 二级标题 - expect/actual ═══
    "## ",
    "2. ",
    "平台",
    "适配",
    "机制：",
    "`expect`",
    "/`actual`",
    "\n\n",

    "KMP ",
    "的核心",
    "机制是 ",
    "`expect`",
    "/`actual` ",
    "声明。",
    "在 ",
    "`commonMain` ",
    "中定义",
    "接口",
    "契约，",
    "在各平台",
    "源集中",
    "提供",
    "具体",
    "实现：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "// commonMain\n",
    "expect ",
    "fun ",
    "getPlatform",
    "Name(): ",
    "String\n",
    "\n",
    "expect ",
    "class ",
    "Platform",
    "Logger() ",
    "{\n",
    "    fun ",
    "log(",
    "tag: String",
    ", message",
    ": String)\n",
    "}\n",
    "```",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "// androidMain\n",
    "actual ",
    "fun ",
    "getPlatform",
    "Name(): ",
    "String = ",
    "\"Android ",
    "\${'$'}{Build",
    ".VERSION",
    ".SDK_INT}\"\n",
    "\n",
    "actual ",
    "class ",
    "Platform",
    "Logger ",
    "actual ",
    "constructor() ",
    "{\n",
    "    actual ",
    "fun log(",
    "tag: String",
    ", message",
    ": String) ",
    "{\n",
    "        android",
    ".util.Log.d",
    "(tag, ",
    "message)\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "// iosMain\n",
    "actual ",
    "fun ",
    "getPlatform",
    "Name(): ",
    "String = ",
    "UIDevice",
    ".currentDevice",
    ".systemName ",
    "+ \" \" + ",
    "UIDevice",
    ".currentDevice",
    ".systemVersion\n",
    "\n",
    "actual ",
    "class ",
    "Platform",
    "Logger ",
    "actual ",
    "constructor() ",
    "{\n",
    "    actual ",
    "fun log(",
    "tag: String",
    ", message",
    ": String) ",
    "{\n",
    "        NSLog",
    "(\"[\${'$'}tag] ",
    "\${'$'}message\")\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 提示 Admonition ═══
    "> [!NOTE]\n",
    "> `expect`",
    "/`actual` ",
    "声明",
    "的匹配",
    "在编译期",
    "检查，",
    "如果某",
    "平台",
    "缺少 ",
    "`actual` ",
    "实现，",
    "编译器",
    "会直接",
    "报错。",
    "这保证了",
    "跨平台",
    "代码的",
    "类型",
    "安全性。",
    "\n\n",

    // ═══ 二级标题 - 网络层 ═══
    "## ",
    "3. ",
    "网络",
    "请求",
    "实践",
    "\n\n",

    "使用 ",
    "**Ktor** ",
    "作为",
    "跨平台 ",
    "HTTP ",
    "客户端，",
    "配合 ",
    "**kotlinx.",
    "serialization** ",
    "进行 ",
    "JSON ",
    "序列化：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "@Serializable\n",
    "data class ",
    "User(\n",
    "    val id: ",
    "Long,\n",
    "    val name: ",
    "String,\n",
    "    val email: ",
    "String,\n",
    "    val avatar",
    "Url: String",
    "? = null,\n",
    "    val created",
    "At: String",
    " = \"\",\n",
    ")\n",
    "\n",
    "@Serializable\n",
    "data class ",
    "ApiResponse",
    "<T>(\n",
    "    val code: ",
    "Int,\n",
    "    val message: ",
    "String,\n",
    "    val data: ",
    "T? = null,\n",
    ")\n",
    "\n",
    "class Api",
    "Client {\n",
    "    private ",
    "val client ",
    "= HttpClient",
    " {\n",
    "        install",
    "(ContentNegotiation)",
    " {\n",
    "            json",
    "(Json {\n",
    "                ",
    "ignoreUnknown",
    "Keys = true\n",
    "                ",
    "prettyPrint ",
    "= true\n",
    "            })\n",
    "        }\n",
    "        install",
    "(Logging)",
    " {\n",
    "            level",
    " = LogLevel",
    ".HEADERS\n",
    "        }\n",
    "        default",
    "Request {\n",
    "            url",
    "(\"https://",
    "api.example",
    ".com/v1/\")\n",
    "        }\n",
    "    }\n",
    "\n",
    "    suspend",
    " fun ",
    "getUsers():",
    " List<User>",
    " {\n",
    "        val ",
    "response = ",
    "client.get",
    "(\"users\")",
    ".body<Api",
    "Response<List",
    "<User>>>()\n",
    "        return ",
    "response.data",
    " ?: empty",
    "List()\n",
    "    }\n",
    "\n",
    "    suspend",
    " fun ",
    "getUserById",
    "(id: Long):",
    " User? {\n",
    "        return ",
    "client.get",
    "(\"users/",
    "\${'$'}id\")",
    ".body<Api",
    "Response<User",
    ">>().data\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 二级标题 - Compose UI ═══
    "## ",
    "4. ",
    "Compose ",
    "Multiplatform ",
    "UI ",
    "开发",
    "\n\n",

    "Compose ",
    "Multiplatform ",
    "让你用",
    "声明式 ",
    "UI ",
    "编写",
    "跨平台",
    "界面。",
    "以下是",
    "一个",
    "用户",
    "列表",
    "页面的",
    "完整",
    "示例：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "@Composable\n",
    "fun User",
    "ListScreen(",
    "\n",
    "    viewModel",
    ": UserList",
    "ViewModel ",
    "= remember",
    " { UserList",
    "ViewModel() }",
    "\n",
    ") {\n",
    "    val users",
    " by viewModel",
    ".users",
    ".collectAs",
    "State()",
    "\n",
    "    val isLoading",
    " by viewModel",
    ".isLoading",
    ".collectAs",
    "State()",
    "\n",
    "    val error",
    " by viewModel",
    ".error",
    ".collectAs",
    "State()",
    "\n\n",
    "    Scaffold(",
    "\n",
    "        top",
    "Bar = {\n",
    "            Top",
    "AppBar(",
    "\n",
    "                title",
    " = { Text(",
    "\"用户列表\")",
    " },\n",
    "                actions",
    " = {\n",
    "                    Icon",
    "Button(",
    "onClick = ",
    "{ viewModel",
    ".refresh() }",
    ") {\n",
    "                        Icon",
    "(Icons.Default",
    ".Refresh, ",
    "\"刷新\")\n",
    "                    }\n",
    "                }\n",
    "            )\n",
    "        }\n",
    "    ) { padding",
    " ->\n",
    "        when {\n",
    "            is",
    "Loading -> ",
    "Loading",
    "Indicator(",
    "Modifier.fill",
    "MaxSize())\n",
    "            error",
    " != null -> ",
    "ErrorView(",
    "error!!, ",
    "onRetry = ",
    "{ viewModel",
    ".refresh() })\n",
    "            else -> ",
    "LazyColumn(",
    "modifier = ",
    "Modifier.padding",
    "(padding)) ",
    "{\n",
    "                items",
    "(users) { ",
    "user ->\n",
    "                    User",
    "Card(",
    "user = user)\n",
    "                }\n",
    "            }\n",
    "        }\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 表格 - 平台对比 ═══
    "## ",
    "5. ",
    "平台",
    "能力",
    "对比",
    "\n\n",

    "不同",
    "平台在 ",
    "KMP ",
    "中的",
    "支持",
    "程度：",
    "\n\n",

    "| 功能",
    " | Android",
    " | iOS",
    " | Desktop",
    " | Web",
    " | 说明 |\n",
    "|:---",
    "|:---:",
    "|:---:",
    "|:---:",
    "|:---:",
    "|:--- |\n",
    "| Compose UI",
    " | ✅",
    " | ✅",
    " | ✅",
    " | ✅",
    " | 全平台",
    "统一 UI",
    " |\n",
    "| 协程",
    " | ✅",
    " | ✅",
    " | ✅",
    " | ✅",
    " | kotlinx",
    ".coroutines",
    " |\n",
    "| 网络请求",
    " | ✅",
    " | ✅",
    " | ✅",
    " | ✅",
    " | Ktor",
    " Client",
    " |\n",
    "| 数据库",
    " | ✅",
    " | ✅",
    " | ✅",
    " | ⚠️",
    " | SQLDelight",
    " / Room",
    " |\n",
    "| 文件系统",
    " | ✅",
    " | ✅",
    " | ✅",
    " | ❌",
    " | okio",
    " |\n",
    "| 蓝牙/NFC",
    " | ✅",
    " | ✅",
    " | ⚠️",
    " | ❌",
    " | 需平台",
    "特定实现",
    " |\n",
    "| 推送通知",
    " | ✅",
    " | ✅",
    " | ❌",
    " | ❌",
    " | Firebase",
    " / APNs",
    " |\n\n",

    // ═══ 二级标题 - 状态管理 ═══
    "## ",
    "6. ",
    "跨平台",
    "状态",
    "管理",
    "\n\n",

    "推荐",
    "使用 ",
    "`StateFlow` ",
    "结合 ",
    "`ViewModel` ",
    "模式",
    "管理",
    "应用",
    "状态：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "class Counter",
    "ViewModel {\n",
    "    private ",
    "val _count ",
    "= Mutable",
    "StateFlow(0)\n",
    "    val count: ",
    "StateFlow",
    "<Int> = ",
    "_count.as",
    "StateFlow()\n",
    "\n",
    "    private ",
    "val _history ",
    "= Mutable",
    "StateFlow(",
    "emptyList<",
    "String>())\n",
    "    val history: ",
    "StateFlow",
    "<List<String>> ",
    "= _history",
    ".asStateFlow()\n",
    "\n",
    "    fun ",
    "increment() ",
    "{\n",
    "        _count",
    ".value++\n",
    "        addHistory",
    "(\"Increment ",
    "to \${'$'}{",
    "_count.value}\")\n",
    "    }\n",
    "\n",
    "    fun ",
    "decrement() ",
    "{\n",
    "        _count",
    ".value--\n",
    "        addHistory",
    "(\"Decrement ",
    "to \${'$'}{",
    "_count.value}\")\n",
    "    }\n",
    "\n",
    "    fun ",
    "reset() {\n",
    "        _count",
    ".value = 0\n",
    "        addHistory",
    "(\"Reset\")\n",
    "    }\n",
    "\n",
    "    private ",
    "fun addHistory",
    "(action: ",
    "String) {\n",
    "        _history",
    ".value = ",
    "_history.value",
    " + action\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 引用块 ═══
    "> ",
    "💡 **提示：**",
    "在 ",
    "Compose ",
    "Multiplatform ",
    "中，",
    "你可以",
    "直接",
    "使用 ",
    "`collectAsState()` ",
    "将 ",
    "`StateFlow` ",
    "转换为 ",
    "Compose ",
    "的 ",
    "`State`，",
    "实现",
    "响应式 ",
    "UI ",
    "更新。",
    "这与 ",
    "Android ",
    "上的 ",
    "Jetpack ",
    "Compose ",
    "完全",
    "一致。",
    "\n\n",

    // ═══ 二级标题 - 测试 ═══
    "## ",
    "7. ",
    "跨平台",
    "测试",
    "策略",
    "\n\n",

    "KMP ",
    "支持在 ",
    "`commonTest` ",
    "中编写",
    "共享",
    "测试，",
    "这些测试",
    "会在",
    "所有",
    "目标",
    "平台上",
    "执行：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "class Api",
    "ClientTest ",
    "{\n\n",
    "    @Test\n",
    "    fun ",
    "should_parse",
    "_user_response",
    "_correctly()",
    " {\n",
    "        // Arrange\n",
    "        val json ",
    "= \"\"\"\n",
    "            {\"code\":",
    "200, \"message\":",
    "\"ok\", \"data\":",
    "{\"id\":1, ",
    "\"name\":\"Alice\",",
    " \"email\":",
    "\"alice@example",
    ".com\"}}\n",
    "        \"\"\".trim",
    "Indent()\n",
    "\n",
    "        // Act\n",
    "        val response ",
    "= Json.decode",
    "FromString<",
    "ApiResponse<User>>",
    "(json)\n",
    "\n",
    "        // Assert\n",
    "        assertEquals(",
    "200, response.code)\n",
    "        assertNotNull",
    "(response.data)\n",
    "        assertEquals(",
    "\"Alice\", ",
    "response.data",
    "?.name)\n",
    "    }\n",
    "\n",
    "    @Test\n",
    "    fun ",
    "should_handle",
    "_error_response",
    "_gracefully()",
    " {\n",
    "        val json ",
    "= \"\"\"{\"code\":",
    "404, \"message\":",
    "\"Not Found\"}\"\"\"\n",
    "\n",
    "        val response ",
    "= Json.decode",
    "FromString<",
    "ApiResponse<User>>",
    "(json)\n",
    "\n",
    "        assertEquals(",
    "404, response.code)\n",
    "        assertNull(",
    "response.data)\n",
    "    }\n",
    "\n",
    "    @Test\n",
    "    fun ",
    "should_serialize",
    "_user_to_json()",
    " {\n",
    "        val user ",
    "= User(id = 1,",
    " name = \"Bob\",",
    " email = ",
    "\"bob@test.com\")\n",
    "        val json ",
    "= Json.encode",
    "ToString(user)\n",
    "\n",
    "        assert",
    "Contains(",
    "\"Bob\", json)\n",
    "        assert",
    "Contains(",
    "\"bob@test.com\",",
    " json)\n",
    "    }\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 有序列表 ═══
    "### ",
    "测试",
    "执行",
    "命令",
    "\n\n",

    "1. ",
    "**全平台测试**：",
    "`./gradlew ",
    "allTests` ",
    "— 在 JVM、",
    "JS、",
    "WasmJS、",
    "iOS ",
    "上全部",
    "执行",
    "\n",
    "2. ",
    "**仅 JVM**：",
    "`./gradlew ",
    ":shared:",
    "jvmTest` ",
    "— 速度",
    "最快，",
    "适合",
    "快速",
    "验证",
    "\n",
    "3. ",
    "**仅 iOS**：",
    "`./gradlew ",
    ":shared:",
    "iosSimulator",
    "Arm64Test` ",
    "— 需要 ",
    "Xcode ",
    "环境",
    "\n",
    "4. ",
    "**仅 JS**：",
    "`./gradlew ",
    ":shared:",
    "jsBrowserTest` ",
    "— 需要 ",
    "Chrome/",
    "Chromium",
    "\n\n",

    // ═══ 警告 Admonition ═══
    "> [!WARNING]\n",
    "> iOS ",
    "测试",
    "需要在 ",
    "macOS ",
    "上运行，",
    "并确保 ",
    "Xcode ",
    "命令行",
    "工具",
    "已安装。",
    "在 CI ",
    "环境中，",
    "建议",
    "使用 ",
    "GitHub ",
    "Actions ",
    "的 ",
    "`macos-latest` ",
    "runner。",
    "\n\n",

    // ═══ 二级标题 - 依赖注入 ═══
    "## ",
    "8. ",
    "依赖",
    "注入",
    "方案",
    "\n\n",

    "在 KMP ",
    "中推荐",
    "使用 ",
    "**Koin** ",
    "作为",
    "轻量级",
    "依赖",
    "注入",
    "框架：",
    "\n\n",

    "```",
    "kotlin",
    "\n",
    "// 定义模块\n",
    "val app",
    "Module = module",
    " {\n",
    "    single",
    "Of<Api",
    "Client> { ",
    "ApiClient() }\n",
    "    single",
    "Of<UserRepo",
    "sitory> { ",
    "UserRepository",
    "(get()) }\n",
    "    factory",
    "Of<UserList",
    "ViewModel> { ",
    "UserList",
    "ViewModel(",
    "get()) }\n",
    "}\n",
    "\n",
    "// 初始化\n",
    "fun init",
    "Koin() {\n",
    "    startKoin {\n",
    "        modules(",
    "appModule)\n",
    "    }\n",
    "}\n",
    "\n",
    "// 在 Composable 中使用\n",
    "@Composable\n",
    "fun User",
    "Screen() {\n",
    "    val viewModel",
    " = koinInject",
    "<UserList",
    "ViewModel>()\n",
    "    // ...\n",
    "}\n",
    "```",
    "\n\n",

    // ═══ 任务列表 ═══
    "## ",
    "9. ",
    "开发",
    "检查",
    "清单",
    "\n\n",

    "新功能",
    "上线前，",
    "请确认",
    "以下",
    "事项：",
    "\n\n",

    "- [x] ",
    "共享模块",
    "单元",
    "测试",
    "全部",
    "通过",
    "\n",
    "- [x] ",
    "Android ",
    "端 UI ",
    "适配",
    "完成",
    "\n",
    "- [x] ",
    "iOS ",
    "端 UI ",
    "适配",
    "完成",
    "\n",
    "- [ ] ",
    "Desktop ",
    "端",
    "功能",
    "验证",
    "\n",
    "- [ ] ",
    "Web ",
    "端",
    "兼容性",
    "测试",
    "\n",
    "- [x] ",
    "API ",
    "接口",
    "文档",
    "更新",
    "\n",
    "- [ ] ",
    "性能",
    "基准",
    "测试",
    "（解析 ",
    "10k 行",
    "用时 ",
    "< 100ms）",
    "\n",
    "- [ ] ",
    "CI/CD ",
    "流水线",
    "集成",
    "\n\n",

    // ═══ 数学公式 ═══
    "## ",
    "10. ",
    "性能",
    "分析",
    "\n\n",

    "流式",
    "增量",
    "解析",
    "的时间",
    "复杂度",
    "分析：",
    "\n\n",

    "- 全量",
    "解析",
    "复杂度：",
    "${'$'}O(n)${'$'}",
    "，其中 ",
    "${'$'}n${'$'} ",
    "为文档",
    "总字符数",
    "\n",
    "- 流式",
    "增量",
    "解析",
    "复杂度：",
    "${'$'}O(k)${'$'}",
    "，其中 ",
    "${'$'}k${'$'} ",
    "为尾部",
    "脏区域",
    "大小",
    "\n",
    "- 稳定",
    "块",
    "复用率：",
    "通常 ",
    "${'$'}\\frac{n - k}{n} \\approx 95\\%${'$'}",
    " 以上",
    "\n\n",

    "块级",
    "公式 —— ",
    "增量解析",
    "加速比：",
    "\n\n",

    "${'$'}${'$'}",
    "\n",
    "\\text{Speedup}",
    " = \\frac{T_{full}}",
    "{T_{incremental}}",
    " = \\frac{O(n)}",
    "{O(k)} ",
    "\\approx \\frac{n}{k}",
    "\n",
    "${'$'}${'$'}",
    "\n\n",

    "当文档",
    "达到 ",
    "10,000 行时，",
    "每次 ",
    "token ",
    "追加",
    "仅需",
    "重新",
    "解析",
    "最后 ",
    "~50 行，",
    "**加速比约 ",
    "200 倍**。",
    "\n\n",

    // ═══ 嵌套引用 ═══
    "> **总结：**",
    "KMP ",
    "让跨平台",
    "开发变得",
    "前所未有",
    "的简单。",
    "\n>\n",
    "> > *\"Write ",
    "once, ",
    "run ",
    "anywhere\"*",
    " —— 这不再是",
    "口号，",
    "而是 ",
    "Kotlin ",
    "Multiplatform ",
    "的现实。",
    "\n>\n",
    "> 结合 ",
    "Compose ",
    "Multiplatform ",
    "的声明式 ",
    "UI，",
    "开发者",
    "可以用 ",
    "**一套代码** ",
    "覆盖 ",
    "**所有主流",
    "平台**，",
    "大幅提升",
    "开发效率。",
    "\n\n",

    // ═══ 二级标题 - 常见问题 ═══
    "## ",
    "11. ",
    "常见",
    "问题 ",
    "FAQ",
    "\n\n",

    "### ",
    "Q: KMP ",
    "和 Flutter ",
    "有什么",
    "区别？",
    "\n\n",

    "| 对比维度",
    " | KMP",
    " | Flutter |\n",
    "|:---|:---|:---|\n",
    "| 语言",
    " | Kotlin",
    " | Dart |\n",
    "| UI 方案",
    " | 原生 UI ",
    "或 Compose",
    " | 自绘引擎 |\n",
    "| 共享范围",
    " | 业务逻辑",
    " + 可选 UI",
    " | 全部 |\n",
    "| 原生互操作",
    " | 优秀",
    "（直接调用）",
    " | 需要 ",
    "Platform ",
    "Channel |\n",
    "| 包体积影响",
    " | 小",
    " | 较大 |\n",
    "| 学习曲线",
    " | 低（Kotlin 开发者）",
    " | 中等 |\n\n",

    "### ",
    "Q: iOS ",
    "开发者",
    "能接受 ",
    "KMP ",
    "吗？",
    "\n\n",

    "是的！",
    "KMP ",
    "生成的",
    "是标准 ",
    "iOS ",
    "Framework，",
    "Swift ",
    "代码",
    "可以",
    "像调用",
    "普通 ",
    "Swift ",
    "库一样",
    "调用 ",
    "KMP ",
    "模块。",
    "对于 ",
    "iOS ",
    "开发者",
    "来说，",
    "共享模块",
    "就是一个",
    "普通的",
    "依赖，",
    "不需要",
    "学习 ",
    "Kotlin。",
    "\n\n",

    "### ",
    "Q: 如何",
    "处理",
    "平台",
    "特有的 ",
    "UI？",
    "\n\n",

    "有两种",
    "策略：",
    "\n\n",

    "1. ",
    "**共享 UI**：",
    "使用 ",
    "Compose ",
    "Multiplatform，",
    "所有平台",
    "使用",
    "相同的 ",
    "UI 代码",
    "\n",
    "2. ",
    "**原生 UI**：",
    "只共享",
    "业务",
    "逻辑，",
    "各平台",
    "用原生",
    "方式",
    "（Jetpack Compose、",
    "SwiftUI）",
    "写 UI",
    "\n\n",

    // ═══ TIP Admonition ═══
    "> [!TIP]\n",
    "> 对于",
    "新项目，",
    "推荐",
    "直接使用 ",
    "Compose ",
    "Multiplatform ",
    "共享 UI。",
    "对于",
    "已有",
    "原生 App ",
    "的项目，",
    "建议先",
    "共享",
    "业务",
    "逻辑层，",
    "逐步",
    "迁移 UI。",
    "\n\n",

    // ═══ 二级标题 - 资源链接 ═══
    "## ",
    "12. ",
    "推荐",
    "资源",
    "\n\n",

    "以下是",
    "学习 ",
    "KMP ",
    "的优质",
    "资源：",
    "\n\n",

    "- ",
    "[Kotlin ",
    "Multiplatform ",
    "官方文档](",
    "https://",
    "kotlinlang.org",
    "/docs/multiplatform",
    ".html)",
    "\n",
    "- ",
    "[Compose ",
    "Multiplatform ",
    "入门](",
    "https://",
    "www.jetbrains.com",
    "/lp/compose",
    "-multiplatform/)",
    "\n",
    "- ",
    "[KMP ",
    "Awesome ",
    "库列表](",
    "https://",
    "github.com",
    "/AAmirr",
    "/kmp-awesome)",
    "\n",
    "- ",
    "[Touchlab ",
    "KMP ",
    "实践指南](",
    "https://",
    "touchlab.co",
    "/kotlin",
    "-multiplatform/)",
    "\n",
    "- ",
    "[Philipp ",
    "Lackner ",
    "KMP 教程](",
    "https://",
    "youtube.com",
    "/@PhilippLackner)",
    "\n\n",

    // ═══ 分隔线 + 结尾 ═══
    "---",
    "\n\n",

    "以上就是 ",
    "Kotlin ",
    "Multiplatform ",
    "的完整",
    "指南。",
    "从项目",
    "结构、",
    "平台",
    "适配、",
    "网络",
    "请求、",
    "UI ",
    "开发、",
    "测试",
    "策略",
    "到性能",
    "分析，",
    "涵盖了",
    "实际",
    "开发中",
    "的方方面面。",
    "\n\n",

    "**本文",
    "由 ",
    "`StreamingParser` ",
    "实时",
    "增量",
    "解析",
    "并渲染。**",
    "每个 ",
    "token ",
    "到达后",
    "立即",
    "解析，",
    "未关闭的",
    "语法",
    "结构",
    "自动",
    "修复，",
    "前缀",
    "稳定块",
    "直接",
    "复用 —— ",
    "这就是",
    "流式",
    "解析的",
    "魅力所在。",
    "\n\n",

    "*Powered",
    " by ",
    "Streaming",
    "Parser ",
    "for ",
    "Compose ",
    "Multiplatform ",
    "| ",
    "Built with ",
    "Kotlin ",
    "& Love*",
    " ❤️",
    "\n"
)

// ─── 普通渲染 Demo 数据 ───

private val normalDemoMarkdown = """
# Markdown Renderer Demo

## 基础文本样式

这是一个段落，包含 **粗体**、*斜体*、***粗斜体***、~~删除线~~、`行内代码` 和 ==高亮文本== 等样式。

还支持 H~2~O 下标和 x^2^ 上标，以及 ++插入文本++。

---

## 链接与图片

这是一个 [链接](https://kotlinlang.org "Kotlin 官网")。自动链接：<https://kotlinlang.org>

### 图片高级特性

基本图片：![Google Logo](https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png)

指定尺寸：![风景](https://images.unsplash.com/photo-1472214103451-9374bd1c798e?w=200&h=100&fit=crop =200x100)

仅指定宽度：![山脉](https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=300&h=200&fit=crop =300x)

带标题和尺寸：![城市夜景](https://images.unsplash.com/photo-1444723121867-7a241cacace9?w=150&h=150&fit=crop =150x150 "城市夜景")

带属性：![自然风光](https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=400&h=250&fit=crop){.rounded .shadow loading=lazy}

尺寸 + 属性：![海滨日落](https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=400&h=200&fit=crop =400x200){.hero-image #main-banner align=center}

## 引用

> 这是一段引用。
> 
> 引用中可以包含 **粗体** 和 *斜体*。
> 
> > 嵌套引用也是支持的。

## 列表

### 无序列表
- 项目一
- 项目二
  - 嵌套项目 A
  - 嵌套项目 B
- 项目三

### 有序列表
1. 第一步
2. 第二步
3. 第三步

### 任务列表
- [x] 已完成的任务
- [ ] 待完成的任务
- [x] 另一个已完成的任务

## 代码块

```kotlin
fun main() {
    val message = "Hello, Markdown!"
    println(message)
    
    val numbers = listOf(1, 2, 3, 4, 5)
    numbers.filter { it % 2 == 0 }
           .map { it * it }
           .forEach { println(it) }
}
```

## 表格

| 功能 | 状态 | 说明 |
|:-----|:----:|-----:|
| 标题 | ✅ | ATX & Setext |
| 代码块 | ✅ | 围栏 & 缩进 |
| 列表 | ✅ | 有序 & 无序 & 任务 |
| 数学公式 | ✅ | 行内 & 块级 |

## 数学公式

行内公式：质能方程 ${'$'}E = mc^2${'$'} 是物理学中最著名的公式之一。

块级公式：

${'$'}${'$'}
\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
${'$'}${'$'}

## Admonition

> [!NOTE]
> 这是一个提示信息，用于展示 Admonition 渲染效果。

> [!WARNING]
> 这是一个警告信息。

> [!TIP]
> 这是一个技巧提示。

## 脚注

这是一段带有脚注的文本[^1]。

[^1]: 这是脚注的内容。

## 定义列表

Kotlin
: 一种现代的编程语言，运行在 JVM 上。

Compose Multiplatform
: JetBrains 的跨平台 UI 框架。

  支持 Android、iOS、Desktop、Web 多平台开发。

  ```kotlin
  @Composable
  fun Greeting(name: String) {
      Text("Hello, ${'$'}name!")
  }
  ```

  - 声明式 UI
  - 跨平台复用

## Emoji

支持 Emoji 短代码：:smile: :heart: :rocket:

## 键盘按键

使用 <kbd>Ctrl</kbd>+<kbd>C</kbd> 复制，<kbd>Ctrl</kbd>+<kbd>V</kbd> 粘贴。

## 缩写

*[HTML]: Hyper Text Markup Language
*[CSS]: Cascading Style Sheets

HTML 和 CSS 是 Web 开发的基础技术。HTML 定义页面结构，CSS 负责样式。

## 自定义容器

::: note "提示信息"
这是一个使用 `:::` 围栏语法创建的自定义容器。

支持**完整的 Markdown 语法**，包括列表：
- 项目一
- 项目二
:::

::: warning
未指定标题时，容器类型名作为默认标题显示。
:::

::::card
:::note
容器支持**嵌套**，外层使用更多冒号（`::::`）。
:::
::::

## 图表块（Mermaid）

```mermaid
flowchart TD
    A[Markdown 文本] --> B[BlockParser]
    B --> C[AST 节点树]
    C --> D[InlineParser]
    D --> E[完整 AST]
    E --> F[Compose 渲染]
```

```mermaid
flowchart LR
    A[输入] -->|解析| B(Parser)
    B -->|生成| C{判断类型}
    C -->|块级| D[BlockNode]
    C -->|行内| E[InlineNode]
```

```plantuml
@startuml
actor User
User -> Parser : 输入 Markdown
Parser -> AST : 生成节点树
AST -> Renderer : 渲染 UI
@enduml
```

---

*Powered by Markdown Renderer for Compose Multiplatform*
""".trimIndent()
