package com.hrm.markdown.preview

import androidx.compose.runtime.Composable
import com.hrm.markdown.renderer.Markdown

internal val mathPreviewGroups = listOf(
    PreviewGroup(
        id = "inline_math",
        title = "行内公式",
        description = "行内 LaTeX 数学公式",
        items = listOf(
            PreviewItem(
                id = "inline_basic",
                title = "基础行内公式",
                content = {
                    Markdown(markdown = "质能方程 \$E = mc^2\$ 是物理学中最著名的公式之一。")
                }
            ),
            PreviewItem(
                id = "inline_multiple",
                title = "多个行内公式",
                content = {
                    Markdown(
                        markdown = "全量解析复杂度：\$O(n)\$，其中 \$n\$ 为文档总字符数。流式增量解析复杂度：\$O(k)\$，其中 \$k\$ 为尾部脏区域大小。"
                    )
                }
            ),
            PreviewItem(
                id = "inline_numeric_text_command",
                title = "数字开头行内公式",
                content = {
                    Markdown(
                        markdown = """
A battery does ${'$'}144\text{ J}${'$'} of work to move a specific amount of charge through a circuit with a potential difference of ${'$'}12\text{ V}${'$'}. Calculate the quantity of charge moved.

- A. ${'$'}12\text{ C}${'$'}
- B. ${'$'}132\text{ C}${'$'}
- C. ${'$'}156\text{ C}${'$'}
- D. ${'$'}1728\text{ C}${'$'}

Potential difference is the work done per unit charge moved.

${'$'}${'$'}
V = \frac{W}{Q}
${'$'}${'$'}

${'$'}${'$'}
Q = \frac{144}{12} = 12\text{ C}
${'$'}${'$'}
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "inline_tall_formula",
                title = "高行内公式行高",
                content = {
                    Markdown(
                        markdown = "这是一段带高行内公式的文本：\$\\frac{1}{\\sqrt{1+x^2}} + \\sum_{i=1}^{n} x_i^2\$，修复后该行应自动增高，避免与上下文本重叠。第二行的内容用于检测行高"
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "block_math",
        title = "块级公式",
        description = "块级 LaTeX 数学公式",
        items = listOf(
            PreviewItem(
                id = "quadratic",
                title = "求根公式",
                content = {
                    Markdown(
                        markdown = """
$$
\frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
$$
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "speedup",
                title = "加速比公式",
                content = {
                    Markdown(
                        markdown = """
$$
\text{Speedup} = \frac{T_{full}}{T_{incremental}} = \frac{O(n)}{O(k)} \approx \frac{n}{k}
$$
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "horizontal_scroll",
                title = "超长公式横向滚动",
                content = {
                    Markdown(
                        markdown = """
$$
\operatorname{score}(x)=\sum_{i=1}^{n}\frac{\alpha_i\beta_i\gamma_i\delta_i}{1+\exp\left(-\frac{x_i-\mu_i}{\sigma_i+\varepsilon}\right)}+\prod_{j=1}^{m}\left(1+\frac{\lambda_j^2}{\omega_j^2+\theta_j^2}\right)+\int_{0}^{T}\frac{\sin(\kappa t)+\cos(\rho t)}{\sqrt{1+t^2}}\,dt
$$
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "math_tag",
        title = "公式编号",
        description = "\\tag{N} 公式编号（LaTeX 原生渲染）",
        items = listOf(
            PreviewItem(
                id = "math_tag_basic",
                title = "基础公式编号",
                content = {
                    Markdown(
                        markdown = """
$$
E = mc^2 \tag{1}
$$

质能方程见公式(1)。
                        """.trimIndent()
                    )
                }
            ),
            PreviewItem(
                id = "math_tag_multiple",
                title = "多公式编号",
                content = {
                    Markdown(
                        markdown = """
$$
a^2 + b^2 = c^2 \tag{eq:pythagoras}
$$

$$
\frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{eq:quadratic}
$$

勾股定理见公式(eq:pythagoras)，求根公式见公式(eq:quadratic)。
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
    PreviewGroup(
        id = "math_in_context",
        title = "公式与文本混排",
        description = "公式嵌入在段落中",
        items = listOf(
            PreviewItem(
                id = "math_paragraph",
                title = "文本中的数学公式",
                content = {
                    Markdown(
                        markdown = """
流式增量解析的时间复杂度分析：

- 全量解析复杂度：${'$'}O(n)${'$'}，其中 ${'$'}n${'$'} 为文档总字符数
- 流式增量解析复杂度：${'$'}O(k)${'$'}，其中 ${'$'}k${'$'} 为尾部脏区域大小
- 稳定块复用率：通常 ${'$'}\frac{n - k}{n} \approx 95\%${'$'} 以上

块级公式 —— 增量解析加速比：

${'$'}${'$'}
\text{Speedup} = \frac{T_{full}}{T_{incremental}} = \frac{O(n)}{O(k)} \approx \frac{n}{k}
${'$'}${'$'}
                        """.trimIndent()
                    )
                }
            ),
        )
    ),
)
