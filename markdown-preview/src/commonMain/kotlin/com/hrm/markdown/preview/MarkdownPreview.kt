package com.hrm.markdown.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 预览分类 — 第一层级
 */
data class PreviewCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: String = "📚",
    val groups: List<PreviewGroup>
)

/**
 * 预览分组 — 第二层级
 */
data class PreviewGroup(
    val id: String,
    val title: String,
    val description: String,
    val items: List<PreviewItem>
)

/**
 * 预览条目 — 第三层级
 */
data class PreviewItem(
    val id: String,
    val title: String,
    val markdown: String = "",
    val content: @Composable () -> Unit = {}
)

/**
 * 所有预览分类的汇总入口
 */
private fun consolidatedPreviewGroup(
    id: String,
    title: String,
    description: String,
    sourceGroups: List<PreviewGroup>,
): PreviewGroup = PreviewGroup(
    id = id,
    title = title,
    description = description,
    items = sourceGroups.flatMap { sourceGroup ->
        sourceGroup.items.map { item ->
            item.copy(
                title = if (item.title == sourceGroup.title) {
                    item.title
                } else {
                    "${sourceGroup.title} · ${item.title}"
                },
            )
        }
    },
)

val previewCategories: List<PreviewCategory> = listOf(
    PreviewCategory(
        id = "basics",
        title = "基础语法",
        description = "文本、标题、列表、引用、表格、链接与图片",
        icon = "📝",
        groups = listOf(
            consolidatedPreviewGroup(
                id = "text_and_headings",
                title = "文本与标题",
                description = "行内样式、标题层级、目录与编号",
                sourceGroups = textStylePreviewGroups + headingPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "structured_content",
                title = "结构化内容",
                description = "列表、引用、Admonition 与表格",
                sourceGroups = listPreviewGroups + blockquotePreviewGroups + tablePreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "links_and_media",
                title = "链接与媒体",
                description = "普通链接、自动链接、Wiki 链接、图片与 Figure",
                sourceGroups = linkImagePreviewGroups,
            ),
        ),
    ),
    PreviewCategory(
        id = "rich_content",
        title = "富内容",
        description = "代码高亮、数学公式和图表渲染",
        icon = "🧮",
        groups = listOf(
            consolidatedPreviewGroup(
                id = "code_and_highlighting",
                title = "代码与高亮",
                description = "行内代码、围栏代码块、多语言高亮与代码属性",
                sourceGroups = codeBlockPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "math_and_diagrams",
                title = "数学与图表",
                description = "LaTeX 公式、Mermaid 与 PlantUML",
                sourceGroups = mathPreviewGroups + diagramPreviewGroups,
            ),
        ),
    ),
    PreviewCategory(
        id = "extensions",
        title = "扩展与配置",
        description = "扩展语法、本地化、指令插件和方言配置",
        icon = "🧩",
        groups = listOf(
            consolidatedPreviewGroup(
                id = "extended_and_cjk",
                title = "扩展语法与本地化",
                description = "脚注、定义列表、Emoji、容器、CJK 强调与 Ruby 注音",
                sourceGroups = extendedPreviewGroups + cjkPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "directives_and_plugins",
                title = "指令与插件",
                description = "块级/行内指令、输入转换与自定义渲染",
                sourceGroups = directivePreviewGroups + directivePluginPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "flavour_configuration",
                title = "方言配置",
                description = "CommonMark、GFM、Extended 与自定义 Emoji 对比",
                sourceGroups = flavourConfigPreviewGroups,
            ),
        ),
    ),
    PreviewCategory(
        id = "rendering",
        title = "渲染与质量",
        description = "主题排版、流式更新、性能压测和语法诊断",
        icon = "⚙️",
        groups = listOf(
            consolidatedPreviewGroup(
                id = "appearance_and_layout",
                title = "主题与排版",
                description = "主题模式、窄容器换行与复杂行内布局回归",
                sourceGroups = themePreviewGroups + inlineLayoutPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "streaming_rendering",
                title = "流式渲染",
                description = "LLM 增量输出、列表稳定性与大量图表更新",
                sourceGroups = streamingPreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "performance_validation",
                title = "性能压测",
                description = "超长 Markdown 的解析、渲染、滚动与内存验证",
                sourceGroups = performancePreviewGroups,
            ),
            consolidatedPreviewGroup(
                id = "syntax_diagnostics",
                title = "语法校验",
                description = "标题层级、脚注引用和重复标题 ID 诊断",
                sourceGroups = lintingPreviewGroups,
            ),
        ),
    ),
)

/**
 * Markdown 预览入口 Composable — 三层导航
 */
@Composable
fun MarkdownPreview() {
    var selectedCategory by remember { mutableStateOf<PreviewCategory?>(null) }
    var selectedGroup by remember { mutableStateOf<PreviewGroup?>(null) }

    when {
        selectedGroup != null -> {
            PreviewItemListScreen(
                title = selectedGroup!!.title,
                items = selectedGroup!!.items,
                onBack = { selectedGroup = null }
            )
        }

        selectedCategory != null -> {
            PreviewGroupListScreen(
                title = selectedCategory!!.title,
                groups = selectedCategory!!.groups,
                onBack = { selectedCategory = null },
                onGroupClick = { selectedGroup = it }
            )
        }

        else -> {
            CategoryListScreen(
                categories = previewCategories,
                onCategoryClick = { category ->
                    if (category.groups.size == 1) {
                        selectedCategory = category
                        selectedGroup = category.groups.first()
                    } else {
                        selectedCategory = category
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryListScreen(
    categories: List<PreviewCategory>,
    onCategoryClick: (PreviewCategory) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markdown 预览") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryCard(category = category, onClick = { onCategoryClick(category) })
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: PreviewCategory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.icon,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "${category.groups.size} 个分组",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewGroupListScreen(
    title: String,
    groups: List<PreviewGroup>,
    onBack: () -> Unit,
    onGroupClick: (PreviewGroup) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                GroupCard(group = group, onClick = { onGroupClick(group) })
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: PreviewGroup,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = group.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = group.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "${group.items.size} 个示例",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
