package com.hrm.markdown.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.rememberCoroutineScope
import com.hrm.markdown.renderer.rememberMarkdownTextSearchState
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.codehigh.renderer.CodeLayout
import com.hrm.codehigh.renderer.CodeTypography
import com.hrm.markdown.renderer.Markdown
import com.hrm.markdown.renderer.MarkdownBlockSlot
import com.hrm.markdown.renderer.MarkdownComponents
import com.hrm.markdown.renderer.MarkdownDefaults
import com.hrm.markdown.renderer.MarkdownImageClickEvent
import com.hrm.markdown.renderer.MarkdownInteractions
import com.hrm.markdown.renderer.MarkdownSoftBreakPolicy
import com.hrm.markdown.renderer.MarkdownTypography

internal val rendererExtensionsPreviewGroup = PreviewGroup(
    id = "renderer_extensions",
    title = "交互与标准组件插槽",
    description = "引用来源、图片图集、软换行和代码/表格操作栏",
    items = listOf(
        PreviewItem("document_text_find", "正文查找与定位", content = {
            val search = rememberMarkdownTextSearchState()
            val scope = rememberCoroutineScope()
            var visible by remember { mutableStateOf(true) }
            Column {
                if (visible) {
                    TextField(
                        value = search.query,
                        onValueChange = { search.setQuery(it) },
                        label = { Text("查找正文（试试：针叶林）") },
                        singleLine = true,
                    )
                    Row {
                        TextButton(enabled = search.matches.isNotEmpty(), onClick = {
                            val results = search.matches
                            if (results.isNotEmpty()) {
                                val current = results.indexOf(search.activeMatch)
                                val previous = if (current <= 0) results.lastIndex else current - 1
                                scope.launch { search.reveal(results[previous]) }
                            }
                        }) { Text("上一个") }
                        TextButton(enabled = search.matches.isNotEmpty(), onClick = {
                            val results = search.matches
                            if (results.isNotEmpty()) {
                                val next = (results.indexOf(search.activeMatch) + 1) % results.size
                                scope.launch { search.reveal(results[next]) }
                            }
                        }) { Text("下一个") }
                        Text("${search.matches.indexOf(search.activeMatch) + 1} / ${search.matches.size}")
                        TextButton(onClick = { search.clear(); visible = false }) { Text("关闭查找") }
                    }
                } else {
                    TextButton(onClick = { visible = true }) { Text("打开查找") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Markdown(
                        markdown = "# 正文查找\n\n针**叶**[林](https://example.invalid)可以跨行内样式匹配。" +
                            "\n\n" + "向下滚动后再次点击同一个结果也会重新定位。".repeat(20) +
                            "\n\n| 类型 | 描述 |\n| --- | --- |\n| 树林 | 针叶林 |" +
                            "\n\n> 引用中的针叶林也可以找到。\n\n`针叶林` 是 widget，不计入结果。",
                        enableScroll = false,
                        textSearchState = if (visible) search else null,
                    )
                }
            }
        }),
        PreviewItem("public_api_styles", "颜色、排版与代码尺寸", content = {
            var large by remember { mutableStateOf(false) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = { large = !large }) { Text("切换字号与代码内边距") }
                Markdown(
                    markdown = "# 独立样式配置\n\n正文与 `inline code`。\n\n```kotlin {title=\"api.kt\"}\nval answer = 42\nprintln(answer)\n```\n\n| 入口 | 职责 |\n| --- | --- |\n| interactions | 点击回调 |",
                    colors = MarkdownDefaults.colors(link = androidx.compose.ui.graphics.Color(0xFF0969DA)),
                    typography = MarkdownTypography(
                        text = TextStyle(fontSize = 16.sp, lineHeight = 28.sp),
                    ),
                    codeTypography = CodeTypography(
                        text = TextStyle(fontSize = if (large) 24.sp else 14.sp, lineHeight = if (large) 36.sp else 22.sp),
                        inlineCode = TextStyle(fontSize = if (large) 20.sp else 14.sp),
                    ),
                    codeLayout = CodeLayout(contentPadding = PaddingValues(if (large) 20.dp else 8.dp)),
                    enableScroll = false,
                )
            }
        }),
        PreviewItem("code_token_actions", "代码 Token 点击与回调更新", content = {
            var enabled by remember { mutableStateOf(true) }
            var revision by remember { mutableStateOf(1) }
            var selected by remember { mutableStateOf<String?>(null) }
            val label = "回调 $revision"
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    Button(onClick = { enabled = !enabled }) { Text(if (enabled) "关闭 Token 点击" else "启用 Token 点击") }
                    Button(onClick = { revision++ }) { Text("更新回调") }
                }
                Markdown(
                    markdown = "点击代码中的关键字、变量或数字。\n\n```kotlin\nval answer = 42\nprintln(answer)\n```",
                    enableScroll = false,
                    interactions = MarkdownInteractions(onCodeTokenClick = if (enabled) {
                        { token -> selected = "$label：${token.type} / ${token.text} / ${token.range}" }
                    } else null),
                )
                Text(selected ?: "点击代码 Token 查看类型、内容与块内位置")
            }
        }),
        PreviewItem("citation_actions", "引用点击与回调更新", content = {
            var selected by remember { mutableStateOf<String?>(null) }
            var label by remember { mutableStateOf("来源") }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = { label = if (label == "来源") "更新后的来源" else "来源" }) {
                    Text("更新回调，不重新解析正文")
                }
                Markdown(
                    markdown = "研究结论来自 [@paper]，相关数据来自 [@dataset]。",
                    enableScroll = false,
                    interactions = MarkdownInteractions(onCitationClick = { selected = "$label：${it.key}" }),
                )
            }
            selected?.let { source ->
                AlertDialog(onDismissRequest = { selected = null },
                    title = { Text("引用来源") }, text = { Text(source) },
                    confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } })
            }
        }),
        PreviewItem("image_gallery_actions", "图片点击与按需图集", content = {
            var selected by remember { mutableStateOf<MarkdownImageClickEvent?>(null) }
            val url = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png"
            Markdown(
                markdown = "第一处 ![图片一]($url =160x60)，第二处 ![图片二]($url =160x60)。",
                interactions = MarkdownInteractions(onImageClick = { selected = it }),
            )
            selected?.let { event ->
                val gallery = event.gallery
                var index by remember(event) { mutableStateOf(gallery.initialIndex.coerceAtLeast(0)) }
                AlertDialog(
                    onDismissRequest = { selected = null },
                    title = { Text("图片 ${index + 1} / ${gallery.images.size}") },
                    text = {
                        Column {
                            Markdown("![预览](${gallery.images[index].image.url})", enableScroll = false)
                            Row {
                                TextButton(onClick = { index-- }, enabled = index > 0) { Text("上一张") }
                                TextButton(onClick = { index++ }, enabled = index < gallery.images.lastIndex) { Text("下一张") }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { selected = null }) { Text("关闭") } },
                )
            }
        }),
        PreviewItem("soft_break_policy", "软换行显示策略", content = {
            var lineBreak by remember { mutableStateOf(false) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Button(onClick = { lineBreak = !lineBreak }) { Text(if (lineBreak) "当前：换行" else "当前：空格") }
                Markdown(
                    markdown = "第一行\n第二行\n**第三行\n第四行**",
                    softBreakPolicy = if (lineBreak) MarkdownSoftBreakPolicy.LineBreak else MarkdownSoftBreakPolicy.Space,
                    enableScroll = false,
                )
            }
        }),
        PreviewItem("standard_block_chrome", "代码与表格操作栏", content = {
            var action by remember { mutableStateOf("点击操作栏按钮") }
            // PreviewCard bounds this viewport; Markdown lays out its complete content below.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(action)
                Markdown(
                    markdown = "| 名称 | 值 |\n| --- | --- |\n| answer | 42 |\n\n> ```kotlin\n> 嵌套代码块也保留操作栏高度\n> ```",
                    enableScroll = false,
                    components = MarkdownComponents(
                        codeBlockHeader = MarkdownBlockSlot(48.dp) { code ->
                            TextButton(onClick = { action = "${code.language}：${code.code}" }) { Text("查看代码") }
                        },
                        tableToolbar = MarkdownBlockSlot(48.dp) { table ->
                            TextButton(onClick = { action = "导出 ${table.rowCount} 行 × ${table.columnCount} 列" }) { Text("导出表格") }
                        },
                    ),
                )
            }
        }),
    ),
)
