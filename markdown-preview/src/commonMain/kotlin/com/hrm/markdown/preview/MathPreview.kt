package com.hrm.markdown.preview

import com.hrm.markdown.renderer.Markdown

private val issue31LongMathMarkdown = """
# Issue #31 长 LaTeX 直渲染回归

这个 preview 用来复现 `长文本 + 大量块级公式` 的打开性能问题。

需求边界：

- 直接渲染完整 markdown
- 不走流式输入
- 不做增量更新
- 重点观察首屏打开、滚动和公式排版稳定性

下面的内容整理自 issue 中引用的长回答，保留了足够长的正文和高密度公式区段，用于做性能回归。

## 一、介绍

${'$'}${'$'}
x=\cos x \Rightarrow x-\frac{\pi}{2}=\cos \left(x-\frac{\pi}{2}\right)=\sin x
${'$'}${'$'}

这个方程的一般形式叫做开普勒方程。对于椭圆轨道，常写作：

${'$'}${'$'}
e \sin E = E - M \tag{1}
${'$'}${'$'}

对于双曲轨道，常写作：

${'$'}${'$'}
e \sinh F = F + N \tag{2}
${'$'}${'$'}

为了把问题转成复变函数上的零点问题，引入代换：

${'$'}${'$'}
E = M + \frac{e}{z}, \quad \omega = \frac{1}{e}, \quad \xi = \frac{M}{e}
${'$'}${'$'}

这样一来，方程 (1) 的求解等价于求下面函数的零点：

${'$'}${'$'}
\Lambda(z) = 1 + \xi z - \omega z \sin^{-1}(1/z) \tag{3}
${'$'}${'$'}

为了让反三角函数在分支上解析，需要把复平面沿着实轴上的区间 ${'$'}(-1,1)${'$'} 剪开，并约定：

${'$'}${'$'}
\sin^{-1}(1/z)=k\pi+(-1)^k\left[\frac{\pi}{2}-i\log\left(f(z)+\frac{1}{z}\right)\right]
${'$'}${'$'}

其中

${'$'}${'$'}
f(z)=\sqrt{\frac{1}{z^2}-1}, \quad f(\infty)=i
${'$'}${'$'}

于是可得每个分支上的解析函数：

${'$'}${'$'}
\Lambda_k(z)=1+\xi z-\omega z\left[k\pi+(-1)^k\frac{\pi}{2}-i(-1)^k\log\left(f(z)+\frac{1}{z}\right)\right] \tag{4}
${'$'}${'$'}

## 二、边界值与黎曼问题

当 ${'$'}z${'$'} 从割线的上下两侧逼近实轴时，可以得到边界值：

${'$'}${'$'}
\Lambda_k^{\pm}(t)=1+t[\xi-\omega\pi\Delta(k)]+(-1)^k\omega\frac{\pi}{2}|t|\mp i(-1)^k\omega t C(t) \tag{5}
${'$'}${'$'}

其中

${'$'}${'$'}
\Delta(k)=k+(-1)^k, \qquad C(t)=\log\left[f(t)+\frac{1}{|t|}\right]
${'$'}${'$'}

进一步引入偶函数：

${'$'}${'$'}
\Omega_k(z)=\Lambda_k(z)\Lambda_k(-z) \tag{6}
${'$'}${'$'}

并考虑满足边界条件的黎曼问题：

${'$'}${'$'}
\Phi_k^+(t)=G_k(t)\Phi_k^-(t), \qquad
G_k(t)=\exp\left[2i\arg \Phi_k^+(t)\right] \tag{7}
${'$'}${'$'}

对应的一个标准解可写成：

${'$'}${'$'}
\Phi_k(z)=(1-z)^{-\aleph_k}\exp\left[\frac{1}{\pi}\int_0^1 \arg \Phi_k^+(t)\frac{dt}{t-z}\right] \tag{8}
${'$'}${'$'}

此时又有

${'$'}${'$'}
\Omega_k(z)=\Phi_k(z)\Phi_k(-z)B_k^2\prod_{\alpha=1}^{\aleph_k+1}\left[z_{k\alpha}^2-z^2\right] \tag{9}
${'$'}${'$'}

这里

${'$'}${'$'}
B_k=\xi-\omega\pi\Delta(k)
${'$'}${'$'}

## 三、实解的表示

对于常见的实数参数范围，可把问题分成三个区域：

${'$'}${'$'}
\{e,M\}\in R_1 \Rightarrow k=1,\aleph_1=2
${'$'}${'$'}

${'$'}${'$'}
\{e,M\}\in R_2 \Rightarrow k=0,\aleph_0=0
${'$'}${'$'}

${'$'}${'$'}
\{e,M\}\in R_3 \Rightarrow k=3,\aleph_3=2
${'$'}${'$'}

其中

${'$'}${'$'}
R_1:e<\frac{\pi}{2}-M,\qquad
R_2:e>\frac{\pi}{2}-M \wedge e>M-\frac{3\pi}{2},\qquad
R_3:e<M-\frac{3\pi}{2}
${'$'}${'$'}

当 ${'$'}\{e,M\}\in R_2${'$'} 时，可以得到一组更直接的表达式。先定义：

${'$'}${'$'}
e^2\Omega_k(iy)=\left[e+(-1)^ky\log\left(\sqrt{\frac{1}{y^2}+1}+\frac{1}{y}\right)\right]^2+y^2[M-\pi\Delta(k)]^2 \tag{10}
${'$'}${'$'}

以及

${'$'}${'$'}
E_k(iy)=\exp\left[-\frac{1}{\pi}\int_0^1 t \arg \Omega_k^+(t)\frac{dt}{t^2+y^2}\right] \tag{11}
${'$'}${'$'}

则有

${'$'}${'$'}
E=M-e(M-\pi)\left[e^2\Omega_0(iy)E_0^2(iy)-y^2(M-\pi)^2\right]^{-1/2} \tag{12}
${'$'}${'$'}

取 ${'$'}y\to\infty${'$'} 后，可写成：

${'$'}${'$'}
E=M-e(M-\pi)\left[(e+1)^2-(M-\pi)^2\frac{2}{\pi}\int_0^1 t\arg\Omega_0^+(t)\,dt\right]^{-1/2} \tag{13}
${'$'}${'$'}

对于 ${'$'}R_1${'$'} 和 ${'$'}R_3${'$'}，还需要通过三个采样点 ${'$'}i\alpha${'$'}、${'$'}i\beta${'$'}、${'$'}i\gamma${'$'} 做消元，得到

${'$'}${'$'}
F_k(i\alpha)=[z_{k1}^2+\alpha^2][z_{k2}^2+\alpha^2][z_{k3}^2+\alpha^2]
${'$'}${'$'}

${'$'}${'$'}
F_k(i\beta)=[z_{k1}^2+\beta^2][z_{k2}^2+\beta^2][z_{k3}^2+\beta^2]
${'$'}${'$'}

${'$'}${'$'}
F_k(i\gamma)=[z_{k1}^2+\gamma^2][z_{k2}^2+\gamma^2][z_{k3}^2+\gamma^2]
${'$'}${'$'}

再定义

${'$'}${'$'}
S_{jk}=[D_k-(-1)^j(D_k^2+Q_k^3)^{1/2}]^{1/3}
${'$'}${'$'}

${'$'}${'$'}
D_k=\frac{1}{6}[A_{1k}A_{2k}-3A_{0k}]-\left(\frac{1}{3}A_{2k}\right)^3
${'$'}${'$'}

${'$'}${'$'}
Q_k=\frac{1}{3}A_{1k}-\left(\frac{1}{3}A_{2k}\right)^2
${'$'}${'$'}

最后得到统一形式：

${'$'}${'$'}
E=M+e(2-k)\left[S_{1k}+S_{2k}-\frac{1}{3}A_{2k}\right]^{-1/2} \tag{14}
${'$'}${'$'}

## 四、回到原问题

原题 ${'$'}x=\cos x${'$'} 可写为

${'$'}${'$'}
x-\frac{\pi}{2}=\sin x
${'$'}${'$'}

只要取开普勒方程中的特例 ${'$'}e=1${'$'}、${'$'}M=\frac{\pi}{2}${'$'}，就能得到一个解析表示。整理后可写成：

${'$'}${'$'}
x=\frac{\pi}{2}+\frac{\pi}{2}\exp\left(
\frac{1}{\pi}\int_0^1
\tan^{-1}\left(
\frac{
t\log\left(\frac{\sqrt{1-t^2}+1}{t}\right)(\pi t+2)
}{
t^2\log^2\left(\frac{\sqrt{1-t^2}+1}{t}\right)-\pi t-1
}
\right)\frac{dt}{t}
\right)
${'$'}${'$'}

减去 ${'$'}\frac{\pi}{2}${'$'} 即可得到原方程的实根。

## 五、性能观察点

下面这些点用于手动观察 issue #31：

1. 首次进入页面时，长公式是否导致明显卡顿。
2. 块级公式连续出现时，排版和滚动是否稳定。
3. 段落与公式混排时，行高、间距和换页是否正常。
4. 非增量、直接渲染路径下，长文是否仍能完整打开。

再补一段混排文本，增加连续解析压力：在同一段里放入行内公式 ${'$'}\Omega_k(z)${'$'}、${'$'}\Phi_k(z)${'$'}、${'$'}\int_0^1 f(t)\,dt${'$'} 与普通中文说明，确保既覆盖块级公式密集场景，也覆盖行内公式高频出现的情况。
""".trimIndent()

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
    PreviewGroup(
        id = "math_performance_regression",
        title = "性能回归",
        description = "长文本与高密度公式的直接渲染样例",
        items = listOf(
            PreviewItem(
                id = "issue_31_long_latex_direct",
                title = "Issue #31 长 LaTeX 直渲染",
                markdown = issue31LongMathMarkdown,
                content = {
                    Markdown(markdown = issue31LongMathMarkdown)
                }
            ),
        )
    ),
)
