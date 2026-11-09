# r72：边缘返回重做（对标光芒拉动）+ 失效修复

## 0. 先回答「光芒返回去哪了 / 怎么被删了」

对标作品是 `Swipe Navigation Gesture 3.0`（`com.swipetoback.swipeback`），分析资料在
`C:\Users\WIN10\Downloads\pi\apk\swipe-navigation-gesture-3.0-analysis\docs\REPORT.md`。
它的「光芒」不是一段动画，而是：

- 全高 `TYPE_ACCESSIBILITY_OVERLAY` 边缘带（触发宽度 50px）；
- `SNG_CornerStretchView.onDraw()` 用 `Path.cubicTo()` 画从屏幕边缘鼓向手指的**拉伸手柄**；
- 叠加 `swipe_stretch_left.png / right / bottom` 发光贴图与箭头 Bitmap，随拖动距离渐显；
- `settingLayoutParams()` 拖动时扩大窗口宽度，松手缩回。

本仓库历史里：

- `9d43f57a` 把 `app/.../backbar/` 四件套（BackBarController/Host/View/InteractionPolicy）删掉，
  换成 `car-ux-redesign-3k-fps` 的应用内返回组件。旧 `BackBarView` **只是一根圆角竖杠 + 小箭头**，
  `git grep -i glow 9d43f57a^ -- backbar` 零命中——那套实现里本来就没有光芒。
- `bef4cf99`（r55）又删掉 `BackBarSettingsDialog`。
- 现有的 `car/EdgePullBackView` 后来做过 teardrop/capsule 水滴，但**没有发光层**，静态是一根
  粗圆角杠（用户说「长得丑、太丑、不够简约」），且命中区只有小棍附近 36×140dp 一小块。

所以准确说法是：**对标作品的光芒拉伸从未被搬进 main 的返回组件**；r72 现在按逆向报告的
`cubicTo` + 发光贴图思路，用纯 Canvas 渐变重画回来了，不引入第三方图片资源。

## 1. 视觉重做（本轮重点）

| 状态 | 新设计 |
|---|---|
| 静态 | 一根 **9px 宽**的半透明冷色细导引线 + 中心一点微光，不再是粗圆角杠；不写文字、不挡内容 |
| 拉动 | `Path.cubicTo` 水滴状拉伸手柄 + `RadialGradient` 径向光芒 + `LinearGradient` 冷色本体 + 白色箭头，越接近阈值越亮 |
| 达阈值 | 显示「松手返回」，边框转亮白 |
| 松手未达 | 220ms 弹性收回 |
| 隐藏 | 4/6/8 秒后淡出，边缘带再拉可立即唤出 |

对标作品用的是 `swipe_stretch_*.png` 发光贴图；这里用 `RadialGradient` 重画，效果等价且能随
`progress` 连续变化，避免把第三方资源打进 APK。**光芒跟随手指 y**（`pullY`），不是固定画在
导引线中心——这是首轮实机验证时发现的偏差，已修正。

## 2. 边缘返回失效：两个真实原因

1. **冷却吞掉手势**：r71 的 `InAppBackHostLayout.wakeAt()` 有全局 1 秒冷却，隐藏后
   1 秒内的边缘 DOWN 被直接 `return`，`wakeFromEdge` 根本不执行 → 拉不出来。
2. **热区太窄**：旧 `BackNavPolicy.edgeHit` 只认「导引小棍中心 ±70dp、向内 36dp」的小方框。
   播放页/详情页/全屏页手指稍偏，`hitSide==0`，`onTouchEvent` 直接 `return false`。
3. 附带：全屏 `swipe` 子 View 覆盖在 `edge` 之上，系统按子 View 逆序派发 DOWN 时可能不落到
   `edge`；`applyVisibility()` 的 early-return 也可能留下「逻辑上 show、视图 inactive」。

### 修法

- 新增纯逻辑 `car/EdgeHitPolicy.java`：**整条屏幕边缘带**命中（x 在边缘宽度内、y 覆盖可用
  高度），与对标作品的全高 Overlay 一致；页面中央仍完全穿透。
- `EdgeHitPolicy.allowWake()`：冷却只抑制普通「点亮」，**边缘手势无条件放行**。
- `InAppBackHostLayout.dispatchTouchEvent()` 对边缘 DOWN 直接把完整手势流转发给
  `edge.onTouchEvent()`（含 MOVE/UP 捕获），不再依赖系统子 View 派发顺序。
- `applyVisibility()` 的 early-return 分支补一次 `VISIBLE` 兜底，防止显示状态没变但视图被关掉。
- `wake()` 只负责点亮；`EdgePullBackView.onTouchEvent` 独立处理手势，隐藏态也能一拉即出。

## 3. 实机验证（MuMu idx0 / 2880x1620 / r72）

| 判据 | 结果 |
|---|---|
| 静态简约 | 刚启动时左侧条带青蓝像素 **262 个、仅 9 列 x**（细线，不是粗杠） |
| 拉动光芒 @y=180 | changed 4814、**cyan_glow 4328** |
| 拉动光芒 @y=810 | changed 4259、**cyan_glow 4021** |
| 拉动光芒 @y=1450 | changed 4445、**cyan_glow 4089** |
| 隐藏态一拉即出 | changed 4665、**cyan_glow 4314** |
| 启动崩溃 | `AndroidRuntime/FATAL` 0 条 |

三个高度此前（固定 `centerY` 版本）只有中间有效，上下两端为 0；改为 `pullY` 跟随手指后
三处全部出现光芒 → 整条边缘带 + 跟随手指都成立。

## 4. 回归

- `:app:assemblePythonRelease` BUILD SUCCESSFUL；`git diff --check` 干净；无重复 `@Override`。
- `EdgeHitPolicyHostTest` **18 checks PASS**（上/中/下命中、旧热区盲区、中央穿透、
  单侧配置、冷却放行）；`GestureAxisHostTest` 15 checks PASS（r71 手势轴未回退）。
- 签名仍是原 debug 证书 `2FEB63C5…FA9C`；版本号 `3.1.0-car.7-r72` / vc44。

## 5. 边界（如实说明）

- 对标作品基于无障碍全局 Overlay，本项目按既定约束仍只用应用内 DecorView，
  **不会跨应用生效**；这是产品约束，不是本次遗漏。
- 未在零跑 B01 车机触摸屏做现场拉动；MuMu 用 `input swipe` 模拟，证据为像素统计。
- 静态导引线在 4/6/8 秒后会自动淡出；若用户设了「常驻（0 秒）」则一直显示细线，不会
  出现 r71 那种粗杠长驻。
- 本轮只改边缘返回与返回杠视觉；悬浮球、播放手势、音量等 r71 修复未回退。
