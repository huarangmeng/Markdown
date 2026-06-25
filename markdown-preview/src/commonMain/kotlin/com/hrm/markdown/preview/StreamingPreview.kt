package com.hrm.markdown.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hrm.markdown.renderer.Markdown
import androidx.compose.foundation.rememberScrollState
import com.hrm.markdown.renderer.MarkdownConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val streamingPreviewGroups = listOf(
    PreviewGroup(
        id = "streaming_demo",
        title = "流式渲染演示",
        description = "模拟 LLM 逐 token 输出，实时增量解析与渲染",
        items = listOf(
            PreviewItem(
                id = "streaming_full",
                title = "完整流式渲染",
                content = { StreamingMarkdownDemo() }
            )
        )
    ),
    PreviewGroup(
        id = "streaming_issues_19",
        title = "Issues",
        description = "问题复现与回归验证示例",
        items = listOf(
            PreviewItem(
                id = "streaming_issue_19_list_flicker",
                title = "Issue #19 列表闪烁回归",
                markdown = issue19MarkdownString(),
                content = { Issue19StreamingDemo() }
            ),
        )
    ),
    PreviewGroup(
        id = "streaming_issues_20",
        title = "Issues_20",
        description = "问题复现与回归验证示例",
        items = listOf(
            PreviewItem(
                id = "streaming_issue_20_list_flicker",
                title = "Issue #20 列表闪烁回归",
                markdown = issues20MarkdownString(),
                content = {
                    StringStreamingMarkdownDemo(
                        markdown = issues20MarkdownString(),
                        emptyHint = "该示例复现 Issue #20 "
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "streaming_diagram_stability",
        title = "Diagram 稳定性",
        description = "大量图表块在增量解析中的稳定性压力验证",
        items = listOf(
            PreviewItem(
                id = "streaming_many_diagrams",
                title = "大量 Diagram 增量解析",
                markdown = manyDiagramsMarkdownString(),
                content = {
                    StringStreamingMarkdownDemo(
                        markdown = manyDiagramsMarkdownString(),
                        emptyHint = "该示例会流式输出大量 mermaid / plantuml / dot 图表块，" +
                                "用于验证 diagram 组件在逐字符流式输入下的增量解析稳定性、滚动跟随和渲染一致性。",
                    )
                }
            ),
        )
    ),
)

@Composable
private fun StreamingMarkdownDemo() {
    StringStreamingMarkdownDemo(
        markdown = streamingMarkdownString(),
        emptyHint = "点击「开始流式生成」按钮，模拟 LLM 逐 token 输出 Markdown\n\n" +
                "Markdown 组件内置流式优化：\n" +
                "• 自动节流渲染，避免高频更新导致的布局抖动\n" +
                "• 流式期间不挂载选区 overlay，减少额外测量\n" +
                "• 流式结束后自动恢复文本选择能力",
    )
}

@Composable
private fun Issue19StreamingDemo() {
    StringStreamingMarkdownDemo(
        markdown = issue19MarkdownString(),
        emptyHint = "该示例复现 Issue #19 的输入形态：\n" +
                "使用单个 markdownString 按字符流式输出，\n" +
                "不再依赖 `List<String>` token 序列。\n\n" +
                "期望行为：流式过程中不应短暂显示为 SetextHeading。",
    )
}

@Composable
private fun StringStreamingMarkdownDemo(
    markdown: String,
    emptyHint: String,
) {
    var text by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    var streamFinished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var autoFollow by remember { mutableStateOf(true) }

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
                        text = ""
                        isRunning = true
                        streamFinished = false
                        scope.launch {
                            markdown.forEach { char ->
                                text += char.toString()
                                delay(8)
                            }
                            // 显式提交完整字符串，避免流式结束切换与最后几个字符落在同一帧时丢尾部。
                            text = markdown
                            isRunning = false
                            withFrameNanos { }
                            withFrameNanos { }
                            streamFinished = true
                        }
                    }
                },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isRunning) "生成中..."
                    else if (streamFinished) "重新生成"
                    else "开始流式生成"
                )
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
                    text = "模拟 markdownString 逐字符输出中...",
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (text.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp
                    )
                }
            } else {
                Markdown(
                    markdown = text,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    config = MarkdownConfig.LlmStreaming,
                    scrollState = scrollState,
                    isStreaming = isRunning
                )
            }
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect
        autoFollow = true

        launch {
            snapshotFlow { scrollState.isScrollInProgress to scrollState.value }
                .collect { (isScrolling, value) ->
                    if (isScrolling && value < scrollState.maxValue - 100) {
                        autoFollow = false
                    }
                }
        }

        launch {
            snapshotFlow { Triple(scrollState.value, scrollState.maxValue, autoFollow) }
                .collect { (value, maxValue, follow) ->
                    if (!follow && maxValue > 0 && value >= maxValue - 100) {
                        autoFollow = true
                    }
                }
        }

        launch {
            snapshotFlow { scrollState.maxValue to autoFollow }
                .collect { (maxValue, follow) ->
                    if (!follow) return@collect
                    withFrameNanos { }
                    if (kotlin.math.abs(scrollState.value - maxValue) > 6) {
                        scrollState.scrollTo(maxValue)
                    }
                }
        }
    }

    LaunchedEffect(streamFinished, text, markdown) {
        if (!streamFinished || text != markdown) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        scrollState.scrollTo(scrollState.maxValue)
        delay(50L)
        scrollState.animateScrollTo(scrollState.maxValue)
    }
}

private fun issues20MarkdownString(): String = $$"""
# Markdown KMP 测试

这是一个 **MarkdownKmpText** 组件的测试页面。


已知投掷实心球的轨迹是抛物线，高度$y$关于水平距离$x$的函数为二次函数，设函数为
$$ y = a(x - h)^2 + k $$
其中，$(h, k)$ 为抛物线顶点坐标。

为什么抛物线的函数可以表示为 y = a(x - h)^2 + k ？

二次函数的解析式有3个参数，顶点式中顶点提供了两个参数，知道另一个点即可确定唯一的函数。

题目中已知最高点（顶点）坐标为$(3, 4.5)$，起点高度为1.5，起点的水平距离为0，即函数过点$(0, 1.5)$。
代入函数表达式：
$$ y = a(x - 3)^2 + 4.5 $$
将点$(0, 1.5)$代入：
$$ 1.5 = a(0 - 3)^2 + 4.5 \implies 1.5 = 9a + 4.5 $$
解得：
$$ 9a = 1.5 - 4.5 = -3 \implies a = -\frac{1}{3} $$
因此，函数解析式为：
$$ y = -\frac{1}{3}(x - 3)^2 + 4.5 $$

根据评分标准，满分条件是实心球从起点到落点的水平距离不小于4米。
当实心球落地时，高度为0，令函数等于0：
$$ 0 = -\frac{1}{3}(x - 3)^2 + 4.5 \implies (x - 3)^2 = 13.5 $$
解得两根：
$$ x = 3 \pm \sqrt{13.5} \approx 3 \pm 3.67 $$
水平距离分别为：
$$ x_1 \approx -0.67 $$（舍掉，因不合理）
$$ x_2 \approx 6.67 $$
实际水平距离为：
$$ 6.67 - 0 = 6.67 \text{米} $$
因为6.67大于4米，说明实心球水平距离满足评分标准。
但是题目解析中判断该生不能得满分，可能因为题中评分标准或条件不同，请仔细核对后给出结论。
（注：解析中落地点求值误差或阅读需谨慎）

第二次投掷的函数为：
$$ y = -\frac{1}{4}x^2 + 2 $$
落地点时，令高度为0：
$$ 0 = -\frac{1}{4}x^2 + 2 \implies x^2 = 8 \implies x = \pm 2\sqrt{2} $$
水平距离为两根的差的绝对值：
$$ 2\sqrt{2} - (-2\sqrt{2}) = 4\sqrt{2} \approx 5.66 \text{米} $$

（1）第一次投掷抛物线解析式为
$$ y = -\frac{1}{3}(x - 3)^2 + 4.5 $$
（2）根据函数，实心球落地的水平距离约为6.67米，满足满分要求，因此理论上应得满分。
（3）第二次投掷的水平距离为约5.66米。

## 功能特性

- 支持完整的 CommonMark 规范
- 支持 LaTeX 数学公式: $E = mc^2$
- 支持代码块:

```kotlin
fun main() {
println("Hello, World!")
}
```

## 列表示例

1. 第一项
2. 第二项
3. 第三项

> 这是一个引用块

[链接示例](https://github.com/huarangmeng/Markdown)
""".trimIndent()

private fun issue19MarkdownString(): String = """

截至2025年5月，根据我的知识，DeepSeek 是中国一家专注于通用人工智能（AGI）研究与应用的科技公司。以下是我了解的一些关键信息：

1. **公司背景**  
   - DeepSeek 由国内顶尖的AI研究团队创立，核心成员来自高校、科研机构或知名科技企业，致力于突破大模型与AGI领域的技术边界。 

2. **主营业务与产品**  
   - 专注于开发高性能、低成本的大语言模型（LLM）及多模态模型，曾推出多个开源或商用模型系列（如DeepSeek-VL、DeepSeek-Coder等），在代码生成、逻辑推理、长文本理解等领域表现突出。  
   - 提供API服务、企业级解决方案及垂直领域定制化应用（如金融、科研、教育等）。 

3. **技术特点**  
   - 强调算法与工程协同优化，在模型架构创新、训练效率提升、数据质量控制等方面有较多实践。  
   - 积极参与开源社区，部分模型在国际权威评测（如MMLU、GSM8K等）中位列前茅。 

4. **行业影响**  
   - 被视为中国AGI领域的重要参与者之一，与国内其他头部AI机构共同推动技术发展。  
   - 注重AI安全与伦理研究，探索大模型在产业落地中的可靠性方案。 

**需要说明的是**：  
- 我的信息可能无法涵盖2025年5月之后的最新动态，如需了解该公司近期进展（如新模型发布、合作伙伴、融资情况等），建议查阅最新权威报道或官方渠道。  
- 如果你有具体的技术细节、商业模式或某款产品想深入讨论，我可以结合已有知识进一步交流。 
""".trimIndent()

private fun streamingMarkdownString(): String = """
# Kotlin Multiplatform 完全指南

本文将全面介绍 **Kotlin Multiplatform**（KMP）的核心概念、架构设计、最佳实践以及在生产环境中的实际应用。无论你是刚接触 KMP 的新手，还是经验丰富的跨平台开发者，都能从中获得有价值的参考。

## 1. 什么是 Kotlin Multiplatform

Kotlin Multiplatform 是 JetBrains 推出的跨平台开发技术，允许在 **Android**、**iOS**、**Desktop**、**Web** 等平台之间共享业务逻辑代码，同时保留各平台的原生 UI 开发体验。

与其他跨平台方案（如 Flutter、React Native）不同，KMP 采用了 ***共享逻辑、原生 UI*** 的哲学：

- **共享层**：网络请求、数据库操作、业务逻辑、状态管理
- **平台层**：各平台原生 UI 框架（Jetpack Compose、SwiftUI、Compose for Desktop）
- **桥接层**：`expect/actual` 机制实现平台特定功能

### 1.1 expect/actual 机制

这是 KMP 最核心的特性之一。通过 `expect` 声明公共接口，通过 `actual` 提供平台实现：

```kotlin
expect class PlatformInfo() {
    val name: String
    val version: String
    fun getDeviceId(): String
}
```

## 2. 项目架构设计

一个良好的 KMP 项目架构是成功的关键。以下是推荐的模块化架构方案：

| 模块 | 职责 | 平台 | 依赖 |
|:---|:---|:---:|:---|
| `core-model` | 数据模型定义 | Common | 无 |
| `core-network` | 网络层封装 | Common | Ktor |
| `core-database` | 本地数据库 | Common | SQLDelight |
| `core-domain` | 业务逻辑 | Common | core-model |

## 3. 网络层实践

**Ktor** 是 KMP 生态中最成熟的网络库。以下展示如何构建一个健壮的网络层。

## 4. 总结

Kotlin Multiplatform 已经从实验性功能发展为生产就绪的跨平台解决方案。

> 提示：你可以继续扩展这个 demo，模拟更复杂的消息流。
""".trimIndent()

private fun manyDiagramsMarkdownString(): String = """
# Diagram 增量解析稳定性压力预览

该文档用于验证在大量图表块持续追加时，增量解析、图表识别与渲染能否保持稳定。

- 图表类型覆盖：`mermaid`、`plantuml`、`dot`
- 验证重点：不闪烁、不串图、不丢尾部、不出现明显卡死

## Mermaid Flowchart 1

```mermaid
flowchart TD
    M1A[输入 1] --> M1B[解析 1]
    M1B --> M1C{识别图表类型}
    M1C -->|mermaid| M1D[DiagramBlock]
    M1C -->|fallback| M1E[Code Fallback]
    M1D --> M1F[diagram-render]
```

## PlantUML Sequence 1

```plantuml
@startuml
actor User1
participant Parser1
participant Renderer1
User1 -> Parser1 : append chunk 1
Parser1 -> Renderer1 : incremental update 1
Renderer1 --> User1 : stable frame 1
@enduml
```

## DOT Graph 1

```dot
digraph Incremental1 {
  rankdir=LR;
  Input1 -> Parse1;
  Parse1 -> Detect1;
  Detect1 -> Render1;
  Render1 -> Stable1;
}
```

段落 1：当前批次用于覆盖多种图表类型交错出现时的增量解析路径。

## Mermaid Flowchart 2

```mermaid
flowchart TD
    M2A[输入 2] --> M2B[解析 2]
    M2B --> M2C{识别图表类型}
    M2C -->|mermaid| M2D[DiagramBlock]
    M2C -->|fallback| M2E[Code Fallback]
    M2D --> M2F[diagram-render]
```

## PlantUML Sequence 2

```plantuml
@startuml
actor User2
participant Parser2
participant Renderer2
User2 -> Parser2 : append chunk 2
Parser2 -> Renderer2 : incremental update 2
Renderer2 --> User2 : stable frame 2
@enduml
```

## DOT Graph 2

```dot
digraph Incremental2 {
  rankdir=LR;
  Input2 -> Parse2;
  Parse2 -> Detect2;
  Detect2 -> Render2;
  Render2 -> Stable2;
}
```

段落 2：当前批次用于覆盖多种图表类型交错出现时的增量解析路径。

## Mermaid Flowchart 3

```mermaid
flowchart TD
    M3A[输入 3] --> M3B[解析 3]
    M3B --> M3C{识别图表类型}
    M3C -->|mermaid| M3D[DiagramBlock]
    M3C -->|fallback| M3E[Code Fallback]
    M3D --> M3F[diagram-render]
```

## PlantUML Sequence 3

```plantuml
@startuml
actor User3
participant Parser3
participant Renderer3
User3 -> Parser3 : append chunk 3
Parser3 -> Renderer3 : incremental update 3
Renderer3 --> User3 : stable frame 3
@enduml
```

## DOT Graph 3

```dot
digraph Incremental3 {
  rankdir=LR;
  Input3 -> Parse3;
  Parse3 -> Detect3;
  Detect3 -> Render3;
  Render3 -> Stable3;
}
```

段落 3：当前批次用于覆盖多种图表类型交错出现时的增量解析路径。

## Mermaid Flowchart 4

```mermaid
flowchart TD
    M4A[输入 4] --> M4B[解析 4]
    M4B --> M4C{识别图表类型}
    M4C -->|mermaid| M4D[DiagramBlock]
    M4C -->|fallback| M4E[Code Fallback]
    M4D --> M4F[diagram-render]
```

## PlantUML Sequence 4

```plantuml
@startuml
actor User4
participant Parser4
participant Renderer4
User4 -> Parser4 : append chunk 4
Parser4 -> Renderer4 : incremental update 4
Renderer4 --> User4 : stable frame 4
@enduml
```

## DOT Graph 4

```dot
digraph Incremental4 {
  rankdir=LR;
  Input4 -> Parse4;
  Parse4 -> Detect4;
  Detect4 -> Render4;
  Render4 -> Stable4;
}
```

段落 4：当前批次用于覆盖多种图表类型交错出现时的增量解析路径。
""".trimIndent()
