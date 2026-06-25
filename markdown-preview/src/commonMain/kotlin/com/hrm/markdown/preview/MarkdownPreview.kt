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
val previewCategories: List<PreviewCategory> = listOf(
    PreviewCategory(
        id = "appearance",
        title = "主题/外观",
        description = "强制暗色模式等主题展示",
        icon = "🌙",
        groups = themePreviewGroups
    ),
    PreviewCategory(
        id = "streaming",
        title = "流式渲染",
        description = "模拟 LLM 逐 token 输出的流式增量解析与渲染",
        icon = "⚡",
        groups = streamingPreviewGroups
    ),
    PreviewCategory(
        id = "text_styles",
        title = "文本样式",
        description = "粗体、斜体、删除线、高亮、上下标等行内样式",
        icon = "✏️",
        groups = textStylePreviewGroups
    ),
    PreviewCategory(
        id = "inline_layout",
        title = "行内排版回归",
        description = "窄容器内长中文、混合标点和嵌套列表的换行边界",
        icon = "↔️",
        groups = inlineLayoutPreviewGroups
    ),
    PreviewCategory(
        id = "headings",
        title = "标题",
        description = "ATX 标题（1-6 级）、目录自动编号",
        icon = "📑",
        groups = headingPreviewGroups
    ),
    PreviewCategory(
        id = "lists",
        title = "列表",
        description = "无序列表、有序列表、任务列表、嵌套列表",
        icon = "📋",
        groups = listPreviewGroups
    ),
    PreviewCategory(
        id = "code",
        title = "代码块",
        description = "围栏代码块、语法高亮（Kotlin/Python/JSON 等）",
        icon = "💻",
        groups = codeBlockPreviewGroups
    ),
    PreviewCategory(
        id = "table",
        title = "表格",
        description = "GFM 表格、对齐方式",
        icon = "📊",
        groups = tablePreviewGroups
    ),
    PreviewCategory(
        id = "blockquote",
        title = "引用与 Admonition",
        description = "引用块、嵌套引用、NOTE/WARNING/TIP 提示框",
        icon = "💬",
        groups = blockquotePreviewGroups
    ),
    PreviewCategory(
        id = "math",
        title = "数学公式",
        description = "行内公式与块级公式",
        icon = "🔢",
        groups = mathPreviewGroups
    ),
    PreviewCategory(
        id = "links_images",
        title = "链接与图片",
        description = "链接、自动链接、Wiki 链接、图片、Figure 图片标题",
        icon = "🔗",
        groups = linkImagePreviewGroups
    ),
    PreviewCategory(
        id = "extended",
        title = "扩展语法",
        description = "脚注、定义列表、Emoji、键盘按键、缩写、自定义容器",
        icon = "🧩",
        groups = extendedPreviewGroups
    ),
    PreviewCategory(
        id = "directives",
        title = "指令",
        description = "块级/行内指令：{% tag args %}...{% endtag %}",
        icon = "🔧",
        groups = directivePreviewGroups
    ),
    PreviewCategory(
        id = "plugins",
        title = "插件扩展",
        description = "外部特殊语法转换 + directive 原生渲染",
        icon = "🔌",
        groups = directivePluginPreviewGroups
    ),
    PreviewCategory(
        id = "diagram",
        title = "图表",
        description = "Mermaid 流程图、PlantUML 时序图",
        icon = "📈",
        groups = diagramPreviewGroups
    ),
    PreviewCategory(
        id = "linting",
        title = "语法验证/Linting",
        description = "解析时检测无效语法并返回诊断信息：标题层级跳跃、重复标题 ID、无效脚注引用等",
        icon = "🔍",
        groups = lintingPreviewGroups
    ),
    PreviewCategory(
        id = "cjk",
        title = "中文本地化优化",
        description = "全角标点定界符识别、CJK 强调解析优化、Ruby 注音标注",
        icon = "🇨🇳",
        groups = cjkPreviewGroups
    ),
    PreviewCategory(
        id = "flavour_config",
        title = "方言配置",
        description = "通过 MarkdownConfig 切换不同 Flavour（CommonMark / GFM / Extended），对比渲染差异",
        icon = "🎛️",
        groups = flavourConfigPreviewGroups
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
