package com.hrm.markdown.preview

import com.hrm.markdown.renderer.Markdown

private val issue31LongMathMarkdown = """
# 方程 cosx＝x 是否有解析解？

**作者**: 农夫三拳有点疼

**简介**: 俩果篦儿加鸡蛋葱花酱豆腐不要辣子

---

更新：知乎小透明第一次拥有这么多赞，受宠若惊，感谢大家的点赞。笔者目前只是个学生，没有足够能力独自解决这个问题，在参考了C. E. Siewert和E. E. Burniston两位大佬的paper[^1]后才写下这篇回答。至于评论区提到的关于答案化简的问题，请参考酱紫君大佬的这篇文章 [^2]，里面包含了若干种其他形式的解析解以及相应的数值验证，在此贴出，以飨各位读者。

---

## 一、介绍

${'$'}${'$'}x=\cos x\Rightarrow x-\frac{\pi}{2}=\cos({x-\frac{\pi}{2}})=\sin x${'$'}${'$'}

这个方程的一般形式叫做**开普勒方程 ( Kepler’s equation )**[^3]**，**开普勒方程是天体力学研究的基础，因此几个世纪以来一直备受关注。对于椭圆轨道，开普勒方程通常写成： 

${'$'}${'$'}e \sin E=E-M \tag{1}\\${'$'}${'$'}

对于双曲轨道，开普勒方程通常写成： 

${'$'}${'$'}e \sinh F=F+N \tag{2} \\${'$'}${'$'}

![这里应该配个图](https://pic1.zhimg.com/v2-989fe69b40dc0d607ddadec809aa94cf)

其中，辅助圆轨道上假想质点所转过的角度${'$'}\angle M${'$'}被称为**平近点角**[^4]**，**将椭圆上的质点向上作延长线,交辅助圆于${'$'}x${'$'}点所形成的角${'$'}\angle E${'$'}被称为**偏近点角, **总之就是一些天体力学里面的参数。 而我们要求解的那个方程，恰好就是 ${'$'}e=1${'$'} 的特殊情况。 对于方程（1），首先令 

${'$'}${'$'}E=M+\frac{e}{z}\\ \omega=\frac{1}{e}\tag{3}\\ \xi=\frac{M}{e} \\${'$'}${'$'}

显然，方程（1）的求解也等价于寻找下面这个函数的零点：

${'$'}${'$'}\Lambda (z)=1+\xi z-\omega z \sin^{-1}(1/z)\tag{4}\\${'$'}${'$'}

我们采用复分析当中一个常见的操作，将复平面沿着实轴从-1到1剪开，来保证 

${'$'}${'$'}\sin^{-1}(1/z)=k\pi +(-1)^k\left[\frac{\pi}{2}-i \log \left[f(z)+\frac{1}{z}\right]\right],k=0,\pm1,\pm2,...\tag{5}\\${'$'}${'$'}

在每一支上面是解析的，其中 ${'$'}\log${'$'} 取主值，当 ${'$'}k=1${'$'} 时 ${'$'}\sin^{-1}(-z)=-\sin^{-1}(z)${'$'} ，并且 

${'$'}${'$'}f(z)=\sqrt{\frac{1}{z^2}-1},f(\infty)=i\tag{6}\\${'$'}${'$'}

函数

${'$'}${'$'}\Lambda _k(z)=1+\xi z-\omega z  \left[k\pi+(-1)^k \frac{\pi}{2}-i(-1)^k \log \left[f(z)+\frac{1}{z}\right]\right],\\k=0,\pm1,\pm2,...\tag{7}\\${'$'}${'$'}

的每一个零点 ${'$'}z_{kx}\notin (-1,1)${'$'} ，都对应了一个开普勒方程的解（椭圆形式）。

 同理，函数 ${'$'}\hat \Lambda _k(z)=1+\zeta z-\omega z \left[k\pi+(-1)^k \frac{\pi}{2}-i(-1)^k \log\left[f(z)+\frac{1}{z} \right]\right],\\k=0,\pm1,\pm2,...\tag{8}\\${'$'}

的每一个零点 ${'$'}z_{kx}${'$'} ，通过换元

${'$'}${'$'}F=-N+i\frac{e}{z}\\ \omega=\frac{1}{e}\\ \zeta=i\frac{N}{e}\tag{9}\\${'$'}${'$'}

都对应了一个开普勒方程的解（双曲形式）。 

## 二、分析 

观察（7）式，当 ${'$'}z${'$'} 分别从上岸 （+）和下岸（-）两个方向靠近实轴上面-1到1的部分时，我们可以得到边界值

${'$'}${'$'}\Lambda ^\pm_k(z)=1+t[\xi-\omega \pi \Delta(k)]+(-1)^k\omega \frac{\pi}{2} \left| t\right| \mp i(-1)^k \omega t C(t) \tag{10}\\${'$'}${'$'}

其中， ${'$'}\Delta(k)=k+(-1)^k\tag{11}\\${'$'}

${'$'}${'$'}C(t)=\log\left[f(t)+\frac{1}{\left| t \right|}\right] \tag{12}\\${'$'}${'$'}

引入函数

${'$'}${'$'}\Omega_k(z)=\Lambda_k(z) \Lambda_k(-z) \tag{13} \\${'$'}${'$'}

 考虑由边界条件

${'$'}${'$'}\Phi_k^+(t)=G_k(t) \Phi_k^-(t),t \in(0,1) \tag{14}\\${'$'}${'$'}

确定的**黎曼问题（Riemann problem）**，其中

${'$'}${'$'}G_k(t)=\frac{\Phi_k^+(t)}{\Phi_k^-(t)}=\exp[2i \arg \Phi_k^+(t)] \tag{15}\\${'$'}${'$'}

这里我们找到一个函数 ${'$'}\Phi_k(z)${'$'} 满足：在沿着实轴从0到1剪开的复平面上解析，且不为0。因为 ${'$'}G_k(t)${'$'} 是连续的，且当 ${'$'}t \in(0,1)${'$'} 时 ${'$'}G_k(t) \ne 0${'$'} ，因此所需的解可以写成：

${'$'}${'$'}\Phi_k(z)=(1-z)^{-\aleph_k} \exp\left[\frac{1}{\pi} \int_0^1 \arg \Phi_k^+(t)\frac{\mathrm{d} t}{t-z}\right] \tag{16}\\${'$'}${'$'}

其中 ${'$'}\arg \Phi_k^+(0)=0${'$'} 。

回到对方程（1）的求解（实数解），我们有 ${'$'}e\in(0,1)${'$'} 和 ${'$'}M\in[0,2\pi]${'$'} ，接下来只考虑方程（7）中使得 ${'$'}\Lambda_k(z)${'$'} 有实数根的那些 ${'$'}k${'$'} 值， ${'$'}k${'$'} 的取值取决于参数 ${'$'}e${'$'} 和 ${'$'}M${'$'} 。

对于固定的 ${'$'}e${'$'} 和 ${'$'}M${'$'} ，可以用辐角原理确定 ${'$'}\Omega_k(z)${'$'} 在割开的平面上的零点个数，这个个数可以被表示为 ${'$'}2(\aleph_k+1)${'$'} 。 注意到，考虑所有的 ${'$'}k${'$'} ， ${'$'}\aleph_k${'$'} 可以是-1、0、1或2。

对于 ${'$'}\aleph_k=-1${'$'} ， ${'$'}\Omega_k(z)${'$'} 在割开的复平面上没有实根，舍；对于 ${'$'}\aleph_k=0${'$'} ，${'$'}\Omega_k(z)${'$'} 在割开的复平面上有两个实根；对于 ${'$'}\aleph_k=1${'$'} ， ${'$'}\Omega_k(z)${'$'} 在割开的复平面上没有实根，舍；还必须考虑 ${'$'}\aleph_k=2${'$'} 的情况。

总结起来，可以分为三个部分：

${'$'}${'$'}\{e,M\}\in R_1 \Rightarrow k=1 ,\aleph_1=2\\ \{e,M\}\in R_2 \Rightarrow k=0 ,\aleph_0=0\\ \{e,M\}\in R_3 \Rightarrow k=3 ,\aleph_3=2\\${'$'}${'$'}

 其中，

${'$'}${'$'}R_1:e<\frac{\pi}{2}-M\\R_2:e>\frac{\pi}{2}-M \wedge e>M-\frac{3\pi}{2}\\R_3:e<M-\frac{3\pi}{2}${'$'}${'$'}

另外，观察 ${'$'}\Omega_k(z)${'$'} 发现：

${'$'}${'$'}\Omega_k(z)=\Omega_k(-z),\Omega_k(z)=\overline {\Omega_k(\bar z)}\tag{17}\\${'$'}${'$'}

显然 ${'$'}\Omega_k(z) \Phi_k^{-1}(-z)${'$'} 也是由（14）式确定的黎曼问题的解。

从而有 ${'$'}\Omega_k(z) \Phi_k^{-1}(-z)=\Phi_k(z)P_k(z)\tag{18}\\${'$'}

其中 ${'$'}P_k(z)${'$'} 是个多项式。

因为 ${'$'}\Phi_k(z)${'$'} 非零，所以我们可以推出：

${'$'}${'$'}P_k(z)=B_k^2 \prod_{\alpha=1}^{\aleph_k+1} [z_{k\alpha}^2-z^2]\tag{19}\\${'$'}${'$'}

其中，

${'$'}${'$'}B_k=\xi-\omega \pi \Delta(k) \tag{20}\\${'$'}${'$'}

因此式（18）可以改写为

${'$'}${'$'}\Omega_k(z)=\Phi_k(z)\Phi_k(-z)B_k^2 \prod_{\alpha=1}^{\aleph_k+1} [z_{k\alpha}^2-z^2]\tag{21}\\${'$'}${'$'}

同理，对于双曲型式的方程（2），我们可以用类似的操作得到：

${'$'}${'$'}\hat \Omega_1(z)=\hat \Lambda_1(z) \hat \Lambda_1(-z) \tag{22}\\${'$'}${'$'}

上式可以分解为， ${'$'}\hat \Omega_1(z)=\hat \Phi_1(z) \hat \Phi_1(-z)\zeta^2 \prod_{\alpha=1}^{\hat \aleph_1+1} [z_{1\alpha}^2-z^2]\tag{23}\\${'$'}

${'$'}${'$'}\hat \Phi_1(z)=(1-z)^{-\hat\aleph_1}\exp\left[\frac{1}{\pi}\int_0^1\arg \hat\Omega_1^+(t)\frac{\mathrm{d}t}{t-z}\right]\tag{24}\\${'$'}${'$'}

 其中， ${'$'}\arg\hat \Phi_1^+(0)=0${'$'} 。观察方程（2）可以发现，我们只用考虑方程（8）中 ${'$'}k=1${'$'} 的情形，具体写出来是：

${'$'}${'$'}\{e,N\}\in\hat R_1 \Rightarrow \hat \aleph_1=0\\\{e,N\}\in\hat R_2 \Rightarrow \hat \aleph _1=2${'$'}${'$'}

其中 ，${'$'}\hat R_1:e\cosh N>\frac{\pi}{2}\\\hat R_2:e\cosh N<\frac{\pi}{2}\\${'$'}

考虑 ${'$'}e${'$'} 和 ${'$'}N${'$'} 的特殊值 ${'$'}\{e,N\}\in\hat R_s \Rightarrow e \cosh N =\frac{\pi}{2}${'$'} ，此时边界 ${'$'}\hat \Omega_1^+(t)${'$'} 在割线 ${'$'}[-1,1]${'$'} 上有零点。对于 ${'$'}\{e,N\}\in\hat R_s${'$'} ， ${'$'}\hat G_k(t)=\frac{\hat \Phi_k^+(t)}{\hat \Phi_k^-(t)} \tag{26}${'$'}

的辐角在由 ${'$'}\hat \Phi_1^+(t)=\hat G_1(t)\hat\Phi_1^-(t),t\in(0,1)\tag{27}\\${'$'}

确定的黎曼问题中，在

${'$'}${'$'}t=t_0=\frac{2e}{\pi}\tag{28}${'$'}${'$'}

处是间断的。

则方程（2）存在解 ${'$'}F=-N\pm i \frac{\pi}{2},\{e,N\}\in R_s \tag{29}${'$'}

还是那句话，我们需要找的是实解，由方程（27）可以得到

${'$'}${'$'}\hat \Phi_1(z)=(t_0-z)^{-1}\exp\left[\frac{1}{\pi}\int_0^1\arg \hat\Omega_1^+(t)\frac{\mathrm{d}t}{t-z}\right]，\{e,N\}\in \hat R_s\tag{30}\\${'$'}${'$'}

值得注意的是，对于 ${'$'}\{e,N\}\in \hat R_s${'$'} ， ${'$'}\arg \hat \Omega_1^+(t)${'$'} 在 ${'$'}t=t_0${'$'} 处不连续， ${'$'}\arg \hat \Omega_1^+(1)=0${'$'} 。此外，方程（30）可以用来表示 ${'$'}\hat \Omega_1(z)${'$'} ：

${'$'}${'$'}\hat \Omega_1(z)=\hat \Phi_1(z)\hat \Omega_1(-z) \zeta^2[t_0^2-z^2][z_{11}^2-z^2],\{e,N\}\in R_s \tag{31}${'$'}${'$'}

##  三、求解

正片开始，接下来着手求解方程（1）。我们从最最简单情况入手，即 ${'$'}\{e,M\}\in R_2${'$'} ，注意到此时 ${'$'}k=0${'$'} 且 ${'$'}\aleph_0=0${'$'} ，通过解方程（21）可以得到

${'$'}${'$'}z_{01}^2=z^2+\Omega_0(z)[B_0^2\Phi_0(z)\Phi_0(-z)]^{-1},\{e,M\}\in R_2\tag{32}\\${'$'}${'$'}

而 ${'$'}\pm z_{01}${'$'} 分别是 ${'$'}\Lambda_0(z)${'$'} 和 ${'$'}\Lambda_0(-z)${'$'} 的实根。

作换元 ${'$'}z=iy${'$'} ，结合（7）式和（13）式，可以得到：

${'$'}${'$'}e^2\Omega_k(iy)=\left[e+(-1)^ky \log\left[\sqrt{\frac{1}{y^2}+1}+\frac{1}{y}\right]\right]^2+y^2\left[M-\pi\Delta(k)\right]^2\tag{33}\\${'$'}${'$'}

定义：

${'$'}${'$'}E_k(iy)=\exp\left[-\frac{1}{\pi}\int_0^1t\arg \Omega_k^+(t)\frac{\mathrm{d}t}{t^2+y^2}\right]\tag{34}\\${'$'}${'$'}

${'$'}${'$'}\arg \Omega_k^+(t)=\tan^{-1}\left[\frac{2(-1)^{k+1}tC(t)\left[e+(-1)^k\frac{\pi}{2}\left| t \right| \right]}{\left[e+(-1)^k\frac{\pi}{2}\left| t \right|\right]^2-t^2[M-\pi\Delta(k)]^2-t^2C^2(t)}\right] \tag{35}\\${'$'}${'$'}

 其中，${'$'}\arg \Omega_k^+(0)=0${'$'} 。

> 事实上，现在已经可以看见答案的影子了。

式（32）现在可以通过变换（3）得到期望的方程（1）的解，即

${'$'}${'$'}E=M-e(M-\pi)[e^2\Omega_0(iy)E_0^2(iy)-y^2(M-\pi)^2]^{-1/2},\{e,M\}\in R_2 \tag{36}\\${'$'}${'$'}

显然，不同的 ${'$'}y${'$'} 对应不同的结果，如果令 ${'$'}y=0${'$'} ，则

${'$'}${'$'}E=M-(M-\pi)\exp\left[\frac{1}{\pi}\int_0^1\arg\Omega_0^+(t)\frac{\mathrm{d}t}{t}\right],\{e,M\}\in R_2 \tag{37}\\${'$'}${'$'}

如果令 ${'$'}y\rightarrow \infty${'$'} , 则

${'$'}${'$'}\boxed{E=M-e(M-\pi)\left[(e+1)^2-(M-\pi)^2\frac{2}{\pi}\int_0^1t\arg \Omega_0^+(t)\mathrm{d}t\right]^{-1/2},\{e,M\}\in R_2 \tag {38}}${'$'}${'$'}

接下来考虑 ${'$'}\{e,M\}\in R_1,R_3${'$'} 的情况，这两种情况的 ${'$'}\aleph_k=2${'$'} ,所以我们在三个不同的点 ${'$'}z=iy${'$'} ， ${'$'}y=\alpha,\beta,\gamma${'$'} 求解方程（21），得到：

${'$'}${'$'}F_k(i\alpha)=[z_{k1}^2+\alpha^2][z_{k2}^2+\alpha^2][z_{k3}^2+\alpha^2]\tag {39a}\\${'$'}${'$'}

${'$'}${'$'}F_k(i\beta)=[z_{k1}^2+\beta^2][z_{k2}^2+\beta^2][z_{k3}^2+\beta^2]\tag {39b}\\${'$'}${'$'}

${'$'}${'$'}F_k(i\gamma)=[z_{k1}^2+\gamma^2][z_{k2}^2+\gamma^2][z_{k3}^2+\gamma^2]\tag {39c}\\${'$'}${'$'}

 其中， ${'$'}F_k(iy)=\frac{e^2\Omega_k(iy)[1+y^2]^2E_k^2(iy)}{[M-\pi\Delta(k)]^2}\tag{40}\\${'$'}

方程组（39）可以用消元法化简，从而可以进一步求出解析解。

方程（1）的解可表示为：

${'$'}${'$'}E=M+e(2-k)\left[S_{1k}(\alpha,\beta,\gamma)+S_{2k}(\alpha,\beta,\gamma)-\frac{1}{3}A_{2k}(\alpha,\beta,\gamma)\right]^{-1/2},\\\{e,M\}\in R_k,k=1 \ or \ 3 \tag{41}\\${'$'}${'$'}

其中， 

${'$'}${'$'}S_{jk}(\alpha,\beta,\gamma)=[D_k(\alpha,\beta,\gamma)-(-1)^j\left[D_k^2(\alpha,\beta,\gamma)+Q_k^3(\alpha,\beta,\gamma)\right]^{1/2}]^{1/3} \tag{42}${'$'}${'$'}

${'$'}${'$'}D_k(\alpha,\beta,\gamma)=\frac{1}{6}\left[A_{1k}(\alpha,\beta,\gamma)A_{2k}(\alpha,\beta,\gamma)-3A_{0k}(\alpha,\beta,\gamma)\right]-[\frac{1}{3}A_{2k}(\alpha,\beta,\gamma)]^3 \tag{43}\\${'$'}${'$'}

${'$'}${'$'}Q_{k}(\alpha,\beta,\gamma)=\frac{1}{3}A_{1k}(\alpha,\beta,\gamma)-[\frac{1}{3}A_{2k}(\alpha,\beta,\gamma)]^2 \tag{44}\\${'$'}${'$'}

${'$'}${'$'}A_{0k}(\alpha,\beta,\gamma)=\alpha^2\beta^2\gamma^2+\beta^2\gamma^2(\beta^2-\gamma^2)TF_k(i\alpha)+\\\gamma^2\alpha^2(\gamma^2-\alpha^2)TF_k(i\beta)+\alpha^2\beta^2(\alpha^2-\beta^2)TF_k(i\gamma)\tag{45a}\\${'$'}${'$'}

${'$'}${'$'}A_{1k}(\alpha,\beta,\gamma)=\alpha^2\beta^2+\beta^2\gamma^2+\gamma^2\alpha^2+(\beta^4-\gamma^4)TF_k(i\alpha)+\\(\gamma^4-\alpha^4)TF_k(i\beta)+(\alpha^4-\beta^4)TF_k(i\gamma)\tag{45b}\\${'$'}${'$'}

${'$'}${'$'}A_{2k}(\alpha,\beta,\gamma)=\alpha^2+\beta^2+\gamma^2+(\beta^2-\gamma^2)TF_k(i\alpha)+\\(\gamma^2-\alpha^2)TF_k(i\beta)+(\alpha^2-\beta^2)TF_k(i\gamma) \tag{45c}\\${'$'}${'$'}

${'$'}${'$'}T=[(\alpha^2-\beta^2)(\beta^2-\gamma^2)(\gamma^2-\alpha^2)]^{-1} \tag{46}\\${'$'}${'$'}

式（41）中 ${'$'}\alpha,\beta,\gamma${'$'} 的取值是有讲究的，一个合理的取值方案是 ${'$'}\alpha=0,\beta=1,\gamma=2${'$'} ，这时（45）将化简为：

${'$'}${'$'}A_{0k}(0,1,2)=-F_k(0) \tag{47a}\\${'$'}${'$'}

${'$'}${'$'}A_{1k}(0,1,2)=4-\frac{5}{4}F_k(0)+\frac{4}{3}F_k(i)-\frac{1}{12}F_k(2i) \tag{47b}${'$'}${'$'}

${'$'}${'$'}A_{2k}(0,1,2)=5-\frac{1}{4}F_k(0)+\frac{1}{3}F_k(i)-\frac{1}{12}F_k(2i) \tag{47c}\\${'$'}${'$'}

 用式（47）可以写出方程（1）的解析解：

${'$'}${'$'}E=M+e(2-k)\left[S_{1k}(0,1,2)+S_{2k}(0,1,2)-\frac{1}{3}A_{2k}(0,1,2)\right]^{-1/2},\\ \{e,M\}\in R_k,k=1 \ or\ 3 \tag{48}\\${'$'}${'$'}

为了写成与（38）统一的格式，我们用 ${'$'}\alpha t, \beta t, \gamma t${'$'} 来代替（41）中的 ${'$'}\alpha,\beta,\gamma${'$'} ，再令 ${'$'}t \rightarrow \infty${'$'} ，可以得到：

${'$'}${'$'}\boxed{E=M+e(2-k)\left[S_{1k}+S_{2k}-\frac{1}{3}A_{2k}\right]^{-1/2}, \{e,M\}\in R_k,k=1 \ or\ 3 \tag{49}\\}${'$'}${'$'}

其中， ${'$'}S_{jk}=[D_k-(-1)^j[D_k^2+Q_k^3]^{1/2}]^{1/3}\tag{50}\\${'$'}

${'$'}${'$'}D_k=\frac{1}{6}[A_{1k}A_{2k}-3A_{0k}]-[\frac{1}{3}A_{2k}]^3 \tag{51}\\${'$'}${'$'}

${'$'}${'$'}Q_{k}=\frac{1}{3}A_{1k}-[\frac{1}{3}A_{2k}]^2 \tag{52}\\${'$'}${'$'}

${'$'}${'$'}A_{0k}=\frac{4}{3}I_{1k}^3+4I_{1k}I_{3k}+2I_{5k}+J(k)[M-\pi\Delta(k)]^{-2} \tag{53a}\\${'$'}${'$'}

${'$'}${'$'}A_{1k}=2I_{1k}^2+2I_{3k}+[-2(e-1)^2I_{1k}+\frac{1}{3}(e-1)][M-\pi\Delta(k)]^{-2} \tag{53b}\\${'$'}${'$'}

${'$'}${'$'}A_{2k}=2I_{1k}-(e-1)^2[M-\pi\Delta(k)]^{-2} \tag{53c}\\${'$'}${'$'}

${'$'}${'$'}J(k)=-2(e-1)^2[I_{1k}^2+I_{3k}]+(e-1)[\frac{2}{3}I_{1k}+\frac{3}{20}]-\frac{1}{36} \tag{54}\\${'$'}${'$'}

${'$'}${'$'}I_{\alpha k}=\frac{1}{\pi} \int_0^1t^{\alpha}\arg \Omega_k^+(t) \mathrm{d}t-\frac{2}{\alpha+1} \tag{55}\\${'$'}${'$'}

式（36）（37）（38）（41）（48）（49）就是方程（1）最终的解。尽管方程（1）中的 ${'$'}e${'$'} 和 ${'$'}M${'$'} 受物理意义的约束，有一定的取值范围，但事实上这个方法可以适用于所有取值，无论是实数还是复数。同样，我们只求得了（1）的实解，事实上可以从（39）式出发，在 ${'$'}k=1${'$'} 和 ${'$'}k=3${'$'} 对应的两支中找到两个复数解。对于方程（2）的求解大同小异，读者不妨尝试自行证明。

回到题目给的方程， ${'$'}x=\cos x\Rightarrow x-\frac{\pi}{2}=\cos({x-\frac{\pi}{2}})=\sin x${'$'} ，显然，取 ${'$'}e=1${'$'} ， ${'$'}M=\frac{\pi}{2}${'$'} 的特例可得到：

${'$'}${'$'}\boxed{x=\frac{\pi}{2}+\frac{\pi}{2}\exp \left( \frac{1}{\pi}\int_0^1 \tan^{-1}\left(\frac{t\log \left( \frac{\sqrt{1-t^2}+1}{t}\right)(\pi t+2)}{t^2 \log^2 \left(\frac{\sqrt{1-t^2}+1}{t}\right)-\pi t-1}\right)\frac{\mathrm{d}t}{t}\right)}\\${'$'}${'$'}

再减去 ${'$'}\frac{\pi}{2}${'$'} 即可得到原题目的解。

[^1]: An exact analytical solution of Kepler's Equation[https://link.springer.com/article/10.1007/BF01231473](https://link.springer.com/article/10.1007/BF01231473)

[^2]: x = cos x 的解析形式[https://zhuanlan.zhihu.com/p/36297534](https://zhuanlan.zhihu.com/p/36297534)

[^3]: 开普勒方程[https://zh.wikipedia.org/zh-hans/%E9%96%8B%E6%99%AE%E5%8B%92%E6%96%B9%E7%A8%8B](https://zh.wikipedia.org/zh-hans/%E9%96%8B%E6%99%AE%E5%8B%92%E6%96%B9%E7%A8%8B)

[^4]: 平近点角[https://baike.baidu.com/item/%E5%B9%B3%E8%BF%91%E7%82%B9%E8%A7%92/5984710](https://baike.baidu.com/item/%E5%B9%B3%E8%BF%91%E7%82%B9%E8%A7%92/5984710)
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
