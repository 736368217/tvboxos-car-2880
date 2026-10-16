# 车机应用内双模返回组件（In-App Back Navigation）AI 开发与设计规范说明文档

> **AI 研发助手指令（System Context for AI Coding Agent）** 本文档为 Android 车载信息娱乐系统（IVI / Android Automotive OS）应用内返回组件的完整设计与工程实现规范。当作为 AI 编程助手读取本文档时，请严格遵循以下核心约束进行代码生成与架构设计：
>
> 1. **零系统权限依赖（Zero System Permissions）**：严禁使用 `android.permission.SYSTEM_ALERT_WINDOW`（系统悬浮窗）或 `AccessibilityService`（无障碍全局返回）。所有视图必须通过 [Application.ActivityLifecycleCallbacks](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Freference%2Fandroid%2Fapp%2FApplication.ActivityLifecycleCallbacks) 自动挂载至当前 Activity 的 [Window.getDecorView()](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Freference%2Fkotlin%2Fandroid%2Fview%2FWindow) 顶层。
>
> 2. **双模式共存与切换（Dual-Mode Architecture）**：同时实现 **模式一（应用内磁吸悬浮返回球 `FloatingBackBallView`）** 与 **模式二（带边缘驻留导引小棍的拉出式流体返回抽屉 `EdgePullBackView`）**，支持通过配置项切换 `BALL_ONLY`、`EDGE_PULL_ONLY` 或 `DUAL_ENABLED`。
>
> 3. **局部热区防手势冲突（Scoped Touch Interception）**：容器层 `InAppBackHostLayout` 必须默认透传所有空白区域触摸事件（`onInterceptTouchEvent` 返回 `false`），模式二仅在“边缘导引小棍”所在的局部热区（`28dp × 120dp`）内拦截横向拉出手势，确保底层车载地图拖拽与横向列表滑动 100% 不受影响。

## 1. 业务背景与两种返回交互形态定义

在车机大屏（横屏、超宽带鱼屏或分屏模式）环境下，主机厂 ROM 往往隐藏或裁切系统导航栏（System Bar），导致应用进入二级页面后缺乏返回入口；同时车规级安全策略严控系统级悬浮窗与无障碍权限。本方案在 **应用进程内窗口（In-App Window）** 层面提供两套符合车载 HMI 人机工程学的返回交互：

* **模式一：应用内磁吸悬浮返回球（In-App Assistive Floating Ball）** 挂载于当前 Activity 顶层容器，支持单指轻点立即返回、在安全边距内自由拖拽、松手后基于弹簧物理模型（[SpringAnimation](https://www.google.com/search?q=https%3A%2F%2Fdeveloper.android.com%2Fdevelop%2Fui%2Fviews%2Fanimations%2Fspring-animation)）平滑磁吸至屏幕左/右边缘，并在停止操作 `3000ms` 后自动降低不透明度且向屏幕边缘外侧缩进半隐藏。

* **模式二：带边缘驻留导引小棍的拉出式流体返回抽屉（Edge Pill Handle + Bezier Pull-Out Back Drawer）** 将“边缘小棍标识位置 + 拖动拉出组件触发返回”规范定义为 **边缘胶囊导引条（Edge Pill Indicator）+ 贝塞尔流体水滴耳/胶囊抽屉（Bezier Wave Teardrop / Capsule Tab）**。常态下在屏幕边缘驻留一根 `6dp × 76dp` 的圆角胶囊“小棍”作为显性视觉锚点（支持长按上下调整驻留高度）；手指按住小棍向屏幕内侧拉动时，平滑拉出三阶贝塞尔流体底座（或圆角胶囊抽屉），拉出距离达到 `48dp` 阈值后高亮锁定并触发震动/车载提示音反馈，松手即刻派发返回事件。

## 2. 模式一：应用内磁吸悬浮返回球设计规范（`FloatingBackBallView`）

### 2.1 几何尺寸与视觉 Token

| 参数项 | 规范数值 | 说明与车载 HMI 依据 | 
| ----- | ----- | ----- | 
| **可视球体直径 (`ballSizeDp`)** | 默认 `56dp`（大号模式 `64dp`） | 兼顾主驾余光辨识度与低画面遮挡面积 | 
| **外扩盲操热区 (`touchBoxSizeDp`)** | `72dp × 72dp` | 在球体外围扩展 `8dp` 透明点击热区，适应行车颠簸盲操 | 
| **安全拖拽边距 (`SafeAreaInsets`)** | 顶 `36dp` / 底 `56dp` / 左右 `12dp` | 避让车机顶部状态栏与底部空调/Dock 导航栏 | 
| **底盘填充与描边（日间/夜间）** | 底色 `#E60F172A`（Alpha 90%），描边 `2dp #38BDF8` | 深空灰磨砂底盘搭配高对比度冰川青左向箭头 `<` | 
| **拖拽判定阈值 (`touchSlopDp`)** | `8dp`（或系统 `ViewConfiguration.scaledTouchSlop`） | 位移 `< 8dp` 且按下时长 `< 200ms` 判定为单击返回 | 
| **弹簧磁吸参数 (`SpringForce`)** | `DAMPING_RATIO = 0.75f`, `STIFFNESS = 400f` | 低弹跳平滑阻尼，杜绝生硬线性动画 | 
| **闲置半隐藏参数 (`IdleState`)** | 延时 `3000ms`；Alpha 降至 `0.38f`；向外缩进 `35%` 宽度 | 闲置时隐藏约三分之一球体，触碰瞬间 `120ms` 恢复全显 | 

### 2.2 四阶段状态机流转（ASCII 逻辑图与 SVG 矢量源码）

```
[状态 1: 贴边激活态 Active] (Alpha = 0.90, 贴靠左/右安全边距 12dp)
   ├── 单指轻点 (位移 < 8dp, 时长 < 200ms) ──> 触发 UnifiedBackDispatcher.performBack()
   ├── 无操作满 3000ms ────────────────────> [状态 4: 闲置半隐藏态 Idle] (Alpha = 0.38, 向屏幕外缩进 35% 宽度)
   │                                              └── 任意 ACTION_DOWN 触碰 ──> 120ms 恢复 [状态 1: 贴边激活态]
   └── 按下并滑动位移 >= 8dp ──────────────> [状态 2: 自由拖拽态 Dragging] (Scale = 1.08x, 实时跟随手指并限制在 SafeArea 内)
                                                  └── 手指抬起 ACTION_UP ──> [状态 3: 弹簧贴边磁吸 Snapping]
                                                       (按球心 X 是否越过 ScreenWidth/2 及水平初速度 vx 判定贴左或贴右，动画结束后进入状态 1)

```

以下为模式一的原生 SVG 矢量示意图源码（AI 可直接解析其坐标结构或用于在前端/文档中渲染预览）：

```
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 920 336" width="100%" height="auto">
  <rect x="2" y="2" width="916" height="332" rx="12" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>
  <text x="24" y="28" font-family="sans-serif" font-size="14" font-weight="bold" fill="#0F172A">方案一：应用内磁吸悬浮返回球（In-App Floating Back Ball）四态交互与安全区示意图</text>
  <rect x="24" y="58" width="530" height="260" rx="10" fill="#0F172A" stroke="#334155" stroke-width="2.5"/>
  <rect x="38" y="90" width="502" height="190" rx="6" fill="none" stroke="#38BDF8" stroke-width="1.2" stroke-dasharray="5,5"/>
  <line x1="289" y1="90" x2="289" y2="280" stroke="#64748B" stroke-width="1.2" stroke-dasharray="4,4"/>
  <!-- 状态1：主驾侧贴边激活态 (可视 56dp + 外扩热区 72dp) -->
  <rect x="42" y="138" width="56" height="56" rx="10" fill="#38BDF8" fill-opacity="0.12" stroke="#38BDF8" stroke-dasharray="3,3"/>
  <circle cx="70" cy="166" r="21" fill="#1E293B" stroke="#38BDF8" stroke-width="2.5"/>
  <path d="M 75 158 L 65 166 L 75 174" fill="none" stroke="#38BDF8" stroke-width="2.8" stroke-linecap="round"/>
  <!-- 状态2：自由拖拽态 (1.08x) 与 状态3：SpringAnimation 贴边轨迹 -->
  <path d="M 192 162 Q 145 135 98 156" fill="none" stroke="#38BDF8" stroke-width="2" stroke-dasharray="5,3"/>
  <circle cx="222" cy="172" r="24" fill="#1E293B" stroke="#F59E0B" stroke-width="2" stroke-dasharray="3,2"/>
  <path d="M 227 163 L 216 172 L 227 181" fill="none" stroke="#FBBF24" stroke-width="2.8" stroke-linecap="round"/>
  <!-- 状态4：3秒闲置半隐藏态 (Alpha=38%, 向右边缘外缩进 35%) -->
  <g opacity="0.45">
    <circle cx="544" cy="196" r="22" fill="#1E293B" stroke="#94A3B8" stroke-width="2"/>
    <path d="M 537 188 L 528 196 L 537 204" fill="none" stroke="#E2E8F0" stroke-width="2.5" stroke-linecap="round"/>
  </g>
</svg>

```

## 3. 模式二：带边缘导引小棍的拉出式流体返回抽屉规范（`EdgePullBackView`）

### 3.1 核心设计理念：为什么需要“边缘导引小棍（Edge Pill Handle）”？

1. **显性视觉锚点（Visual Affordance）**：车机屏幕外框较宽或带有物理内凹台阶，常驻在屏幕边缘的 `6dp × 76dp` 圆角胶囊小棍让驾驶员通过眼角余光即可准确定位手势起点。

2. **局部热区收敛防手势冲突（Scoped Hit-Box）**：仅在小棍所在的 `28dp（宽）× 120dp（高）` 局部矩形热区内拦截 `ACTION_DOWN` 并检测横向拉出；屏幕左右边缘其余 85% 以上的高度区域完全不拦截触摸事件，彻底消除与车载地图拖拽、横向列表（`ViewPager2` / `RecyclerView`）的滑动冲突。

3. **支持驻留高度上下调节**：长按导引小棍满 `400ms` 触发一次轻震反馈后，手指可沿屏幕边缘上下拖动小棍，调节至驾驶员最顺手的黄金高度并自动持久化保存。

### 3.2 三阶段非线性阻尼拉出交互（3-Stage Pull Interaction）

| 阶段名称 | 水平拉出位移 `Δx` | 视觉形态与阻尼公式 | 触感/音效与松手行为 | 
| ----- | ----- | ----- | ----- | 
| **阶段 1：常态边缘驻留 (`RESTING`)** | `Δx = 0dp` | 贴边显示 `6dp × 76dp` 圆角胶囊小棍（圆角 `3dp`，常态不透明度 `55%`，冰川青 `#38BDF8`）。外围透明热区为 `28dp × 120dp`。 | 常态静默；长按 `400ms` 解锁上下垂直移位模式。 | 
| **阶段 2：阻尼拉出预备 (`PULLING_UNARMED`)** | `0dp < Δx < 48dp` | 手指在热区内向屏幕内侧横拉（要求 `|dx| > 1.5 × |dy|`）。小棍平滑演变为三阶贝塞尔流体水滴底座（上下总跨度 `160dp`）或圆角胶囊抽屉。视觉拉出高度采用双曲正切阻尼映射：`pullWidth = 72dp × tanh(Δx / 120dp)`。底色为深灰半透明 `#E01E293B`，内部左箭头随进度渐显但未点亮。 | **支持中途反悔取消**：此阶段松开手指（`ACTION_UP`），底座在 `180ms` 内弹性回缩归零，**不触发返回**。 | 
| **阶段 3：阈值锁定触发 (`ARMED_READY`)** | `Δx ≥ 48dp` | 当实际手指拉出距离越过 `48dp` 阈值线时，底座瞬间切换为高亮青蓝渐变（`#0284C7 → #38BDF8`），内部返回箭头放大至 `1.2x` 并点亮高亮圆环背板（胶囊抽屉皮肤同步显示“松手返回”文案）。 | 越过 `48dp` 瞬间触发一次 `HapticFeedbackConstants.CONFIRM` 震动（或 `SoundPool` 车载清脆滴声）；**此时松手立即执行返回**并回缩底座。 | 

### 3.3 模式二三阶段演进 SVG 矢量示意图源码

```
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 920 390" width="100%" height="auto">
  <!-- 阶段 1：常态边缘驻留 (6dp x 76dp 胶囊小棍 + 28dp x 120dp 局部热区) -->
  <rect x="24" y="62" width="206" height="308" rx="10" fill="#0F172A" stroke="#334155" stroke-width="2"/>
  <rect x="32" y="156" width="44" height="124" rx="8" fill="#38BDF8" fill-opacity="0.12" stroke="#38BDF8" stroke-dasharray="4,3"/>
  <rect x="36" y="182" width="7" height="72" rx="3.5" fill="#38BDF8" opacity="0.88"/>
  <!-- 阶段 2：阻尼拉出预备 (0dp < Δx < 48dp，三阶贝塞尔流体底座未达阈值，松手可反悔回缩) -->
  <rect x="244" y="62" width="206" height="308" rx="10" fill="#0F172A" stroke="#334155" stroke-width="2"/>
  <line x1="316" y1="126" x2="316" y2="306" stroke="#F59E0B" stroke-width="1.2" stroke-dasharray="4,4"/>
  <path d="M 252 146 C 252 174, 288 188, 288 220 C 288 252, 252 266, 252 294 Z" fill="#1E293B" stroke="#94A3B8" stroke-width="1.8"/>
  <path d="M 276 212 L 267 220 L 276 228" fill="none" stroke="#CBD5E1" stroke-width="2.4" stroke-linecap="round"/>
  <!-- 阶段 3：阈值锁定触发 (Δx >= 48dp，底座高亮发光 + 箭头放大 1.2x + 震动/滴声，松手即返回) -->
  <rect x="464" y="62" width="214" height="308" rx="10" fill="#0F172A" stroke="#0284C7" stroke-width="2.5"/>
  <path d="M 472 136 C 472 172, 542 186, 542 220 C 542 254, 472 268, 472 304 Z" fill="#0284C7" stroke="#7DD3FC" stroke-width="2.2"/>
  <circle cx="510" cy="220" r="16" fill="#0F172A" fill-opacity="0.45" stroke="#E0F2FE" stroke-width="1.5"/>
  <path d="M 515 211 L 504 220 L 515 229" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round"/>
  <!-- 可选皮肤：圆角胶囊抽屉样式 (Capsule Drawer Skin) -->
  <rect x="708" y="114" width="172" height="84" rx="8" fill="#0F172A"/>
  <rect x="708" y="126" width="5" height="60" rx="2.5" fill="#38BDF8"/>
  <rect x="713" y="134" width="96" height="44" rx="22" fill="#1E293B" stroke="#38BDF8" stroke-width="2"/>
</svg>

```

## 4. 零系统权限应用内技术架构设计

### 4.1 视图层级挂载与多级返回分发链路

```
Application.onCreate()
  └── InAppBackManager.init(application, config)
        └── registerActivityLifecycleCallbacks()
              ├── onActivityPostCreated(activity):
              │     获取 (activity.window.decorView as ViewGroup)
              │     创建并挂载 InAppBackHostLayout (FrameLayout, translationZ = 1000f)
              │       ├── 子视图 1: FloatingBackBallView (模式一：应用内磁吸悬浮球)
              │       └── 子视图 2: EdgePullBackView     (模式二：边缘导引小棍 + 贝塞尔流体抽屉)
              └── onActivityResumed(activity):
                    1. 从 BackNavStateRepository 同步最新归一化坐标 (normX, normY, edgeSide)
                    2. RootPageVisibilityDetector 检查是否为首页根状态：
                       若 (activity.isTaskRoot && fragmentBackStack == 0 && !hasCustomBackCallback) -> 自动平滑隐藏
                       否则 -> 自动平滑显示

用户触发返回事件 (UnifiedBackDispatcher.performBack(activity)) 四级责任链：
  [优先级 1] InAppOverlayInterceptor ──> 若页面存在开启的应用内自定义全屏浮层/语音面板，优先关闭浮层
  [优先级 2] WebViewHistoryBack      ──> 递归查找当前可见且 focused 的 WebView，若 webView.canGoBack() 则执行 webView.goBack()
  [优先级 3] OnBackPressedDispatcher ──> 若 activity is ComponentActivity，调用 onBackPressedDispatcher.onBackPressed()（兼容 Fragment 回退栈与 Jetpack Compose BackHandler）
  [优先级 4] Activity.finish()       ──> 若非任务栈根 Activity (!activity.isTaskRoot)，执行 activity.finish()

```

## 5. 核心工程代码实现蓝图（Kotlin 完整骨架）

AI 开发助手可直接按以下模块结构在工程中创建对应类文件。

### 5.1 配置模型与跨页面状态仓库（`BackNavConfig.kt` & `BackNavStateRepository.kt`）

```
package com.carapp.navigation.back

import android.content.Context
import android.content.SharedPreferences

enum class BackNavMode {
    BALL_ONLY,       // 仅启用模式一：应用内磁吸悬浮球
    EDGE_PULL_ONLY,  // 仅启用模式二：边缘导引小棍 + 拉出式流体抽屉
    DUAL_ENABLED     // 双模同时启用
}

enum class EdgePullSkin {
    BEZIER_TEARDROP, // 皮肤 A：三阶贝塞尔流体水滴耳（默认推荐）
    CAPSULE_DRAWER   // 皮肤 B：带“松手返回”文字的圆角胶囊抽屉
}

data class BackNavConfig(
    val mode: BackNavMode = BackNavMode.DUAL_ENABLED,
    val edgeSkin: EdgePullSkin = EdgePullSkin.BEZIER_TEARDROP,
    val enableDualEdgesForWideScreen: Boolean = false, // 超宽带鱼屏是否左右两侧同时驻留小棍
    val autoHideOnRootPage: Boolean = true,            // 首页根界面且无子栈时自动隐藏
    val ballSizeDp: Float = 56f,                       // 悬浮球可视直径 (支持 56dp / 64dp)
    val ballTouchBoxDp: Float = 72f,                   // 悬浮球外扩透明盲操热区
    val pillWidthDp: Float = 6f,                       // 边缘小棍可视宽度
    val pillHeightDp: Float = 76f,                     // 边缘小棍可视高度
    val pillTouchWidthDp: Float = 28f,                 // 边缘小棍局部拦截热区宽度
    val pillTouchHeightDp: Float = 120f,               // 边缘小棍局部拦截热区高度
    val pullTriggerThresholdDp: Float = 48f            // 拉出触发返回的水平位移阈值
)

/**
 * 标记接口：若某个特殊 Activity（如启动闪屏页 SplashActivity）不需要注入返回组件，实现此接口即可跳过。
 */
interface IgnoreInAppBack

/**
 * 跨 Activity 内存+持久化状态仓库，保证页面跳转时悬浮球与边缘小棍坐标零闪烁同步。
 */
object BackNavStateRepository {
    private const val PREFS_NAME = "ivi_in_app_back_prefs"
    private lateinit var prefs: SharedPreferences

    var config: BackNavConfig = BackNavConfig()

    // 悬浮球归一化坐标 (0.0f ~ 1.0f) 与贴边方向
    var ballNormY: Float = 0.52f
        private set
    var ballDockedLeft: Boolean = true
        private set

    // 边缘导引小棍归一化高度 (0.15f ~ 0.85f) 与驻留侧
    var pillNormY: Float = 0.55f
        private set
    var pillDockedLeft: Boolean = true
        private set

    fun init(context: Context, initialConfig: BackNavConfig) {
        config = initialConfig
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ballNormY = prefs.getFloat("ball_norm_y", 0.52f)
        ballDockedLeft = prefs.getBoolean("ball_docked_left", true)
        pillNormY = prefs.getFloat("pill_norm_y", 0.55f)
        pillDockedLeft = prefs.getBoolean("pill_docked_left", true)
    }

    fun updateBallPosition(normY: Float, dockedLeft: Boolean) {
        ballNormY = normY.coerceIn(0.08f, 0.88f)
        ballDockedLeft = dockedLeft
        prefs.edit().putFloat("ball_norm_y", ballNormY).putBoolean("ball_docked_left", dockedLeft).apply()
    }

    fun updatePillPosition(normY: Float, dockedLeft: Boolean) {
        pillNormY = normY.coerceIn(0.12f, 0.86f)
        pillDockedLeft = dockedLeft
        prefs.edit().putFloat("pill_norm_y", pillNormY).putBoolean("pill_docked_left", dockedLeft).apply()
    }
}

```

### 5.2 统一返回分发责任链（`UnifiedBackDispatcher.kt`）

```
package com.carapp.navigation.back

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.fragment.app.FragmentActivity

object UnifiedBackDispatcher {

    private val customInterceptors = mutableListOf<(Activity) -> Boolean>()

    fun registerInterceptor(interceptor: (Activity) -> Boolean) {
        customInterceptors.add(interceptor)
    }

    fun performBack(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        // 优先级 1：应用内自定义浮层/全屏弹窗拦截器
        for (interceptor in customInterceptors) {
            if (interceptor.invoke(activity)) return
        }

        // 优先级 2：若当前页面内存在可见且可回退的 WebView，优先回退网页历史
        val decorView = activity.window?.decorView as? ViewGroup
        val webView = decorView?.let { findCanGoBackWebView(it) }
        if (webView != null) {
            webView.goBack()
            return
        }

        // 优先级 3：通过 Jetpack OnBackPressedDispatcher 分发（自动兼容 Fragment 回退栈与 Compose BackHandler）
        if (activity is ComponentActivity) {
            if (activity.onBackPressedDispatcher.hasEnabledCallbacks() || !activity.isTaskRoot) {
                activity.onBackPressedDispatcher.onBackPressed()
                return
            }
        }

        // 优先级 4：非根 Activity 兜底关闭
        if (!activity.isTaskRoot) {
            activity.finish()
        }
    }

    /**
     * 判断当前 Activity 是否处于“无可回退的根首页状态”（用于自动隐藏返回组件）
     */
    fun isAtRootWithNoBackStack(activity: Activity): Boolean {
        if (!activity.isTaskRoot) return false
        val decorView = activity.window?.decorView as? ViewGroup
        if (decorView != null && findCanGoBackWebView(decorView) != null) return false
        if (activity is FragmentActivity) {
            if (activity.supportFragmentManager.backStackEntryCount > 0) return false
        }
        if (activity is ComponentActivity) {
            if (activity.onBackPressedDispatcher.hasEnabledCallbacks()) return false
        }
        return true
    }

    private fun findCanGoBackWebView(root: ViewGroup): WebView? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child is WebView && child.canGoBack()) return child
            if (child is ViewGroup) {
                val found = findCanGoBackWebView(child)
                if (found != null) return found
            }
        }
        return null
    }
}

```

### 5.3 模式一视图实现：应用内磁吸悬浮返回球（`FloatingBackBallView.kt`）

```
package com.carapp.navigation.back

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs
import kotlin.math.hypot

class FloatingBackBallView(
    context: Context,
    private val onTriggerBack: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val config get() = BackNavStateRepository.config

    private val ballRadiusPx get() = (config.ballSizeDp * density) / 2f
    private val touchBoxSizePx get() = (config.ballTouchBoxDp * density).toInt()
    private val safeMarginLeftPx = 12f * density
    private val safeMarginRightPx = 12f * density
    private val safeMarginTopPx = 36f * density
    private val safeMarginBottomPx = 56f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60F172A")
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPath = Path()

    private var downRawX = 0f
    private var downRawY = 0f
    private var startViewX = 0f
    private var startViewY = 0f
    private var downTimestamp = 0L
    private var isDragging = false
    private var isIdleHalfHidden = false
    private var velocityTracker: VelocityTracker? = null
    private var springAnimX: SpringAnimation? = null

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable { enterIdleHalfHide() }

    init {
        alpha = 0.90f
        scheduleIdleTimer()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(touchBoxSizePx, touchBoxSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = ballRadiusPx

        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r, strokePaint)

        // 绘制中心左向返回箭头 `<`
        val arrowSpan = r * 0.38f
        arrowPath.reset()
        arrowPath.moveTo(cx + arrowSpan * 0.45f, cy - arrowSpan)
        arrowPath.lineTo(cx - arrowSpan * 0.55f, cy)
        arrowPath.lineTo(cx + arrowSpan * 0.45f, cy + arrowSpan)
        canvas.drawPath(arrowPath, arrowPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parentView = parent as? View ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                springAnimX?.cancel()
                wakeUpFromIdle()
                downRawX = event.rawX
                downRawY = event.rawY
                startViewX = x
                startViewY = y
                downTimestamp = System.currentTimeMillis()
                isDragging = false
                velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                animate().scaleX(1.08f).scaleY(1.08f).setDuration(100).start()
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                }
                if (isDragging) {
                    val minX = safeMarginLeftPx
                    val maxX = (parentView.width - width - safeMarginRightPx).coerceAtLeast(minX)
                    val minY = safeMarginTopPx
                    val maxY = (parentView.height - height - safeMarginBottomPx).coerceAtLeast(minY)
                    x = (startViewX + dx).coerceIn(minX, maxX)
                    y = (startViewY + dy).coerceIn(minY, maxY)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val clickDuration = System.currentTimeMillis() - downTimestamp
                if (!isDragging && clickDuration < 250L && event.actionMasked == MotionEvent.ACTION_UP) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTriggerBack.invoke()
                    snapToNearestEdge(parentView, 0f)
                } else {
                    snapToNearestEdge(parentView, vx)
                }
                scheduleIdleTimer()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToNearestEdge(parentView: View, velocityX: Float) {
        val centerX = x + width / 2f
        val dockLeft = when {
            abs(velocityX) > 650f -> velocityX < 0f
            else -> centerX < parentView.width / 2f
        }
        val targetX = if (dockLeft) {
            safeMarginLeftPx
        } else {
            (parentView.width - width - safeMarginRightPx).coerceAtLeast(safeMarginLeftPx)
        }

        val normY = if (parentView.height > 0) y / parentView.height.toFloat() else 0.5f
        BackNavStateRepository.updateBallPosition(normY, dockLeft)

        springAnimX = SpringAnimation(this, DynamicAnimation.X, targetX).apply {
            spring = SpringForce(targetX).apply {
                dampingRatio = 0.75f
                stiffness = 400f
            }
            start()
        }
    }

    fun syncPositionFromRepo(parentWidth: Int, parentHeight: Int) {
        if (parentWidth <= 0 || parentHeight <= 0) return
        val dockLeft = BackNavStateRepository.ballDockedLeft
        val baseTargetX = if (dockLeft) {
            safeMarginLeftPx
        } else {
            (parentWidth - width - safeMarginRightPx).coerceAtLeast(safeMarginLeftPx)
        }
        val minY = safeMarginTopPx
        val maxY = (parentHeight - height - safeMarginBottomPx).coerceAtLeast(minY)
        x = baseTargetX
        y = (parentHeight * BackNavStateRepository.ballNormY).coerceIn(minY, maxY)
    }

    private fun scheduleIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        idleHandler.postDelayed(idleRunnable, 3000L)
    }

    private fun enterIdleHalfHide() {
        val parentView = parent as? View ?: return
        isIdleHalfHidden = true
        val dockLeft = BackNavStateRepository.ballDockedLeft
        val hiddenOffset = width * 0.35f * if (dockLeft) -1f else 1f
        val baseTargetX = if (dockLeft) safeMarginLeftPx else (parentView.width - width - safeMarginRightPx)
        animate().alpha(0.38f).x(baseTargetX + hiddenOffset).setDuration(220).start()
    }

    private fun wakeUpFromIdle() {
        idleHandler.removeCallbacks(idleRunnable)
        if (isIdleHalfHidden) {
            isIdleHalfHidden = false
            val parentView = parent as? View ?: return
            val dockLeft = BackNavStateRepository.ballDockedLeft
            val baseTargetX = if (dockLeft) safeMarginLeftPx else (parentView.width - width - safeMarginRightPx)
            animate().alpha(0.90f).x(baseTargetX).setDuration(120).start()
        }
    }
}

```

### 5.4 模式二视图实现：边缘导引小棍 + 贝塞尔流体拉出抽屉（`EdgePullBackView.kt`）

```
package com.carapp.navigation.back

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.tanh

class EdgePullBackView(
    context: Context,
    private val onTriggerBack: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val config get() = BackNavStateRepository.config
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val pillWidthPx get() = config.pillWidthDp * density
    private val pillHeightPx get() = config.pillHeightDp * density
    private val hitBoxWidthPx get() = config.pillTouchWidthDp * density
    private val hitBoxHeightPx get() = config.pillTouchHeightDp * density
    private val triggerThresholdPx get() = config.pullTriggerThresholdDp * density
    private val maxVisualPullPx = 72f * density
    private val waveHalfSpanPx = 80f * density // 贝塞尔水滴总高 160dp

    private var currentPullDx = 0f       // 实际手指水平拉出位移
    private var visualPullWidth = 0f     // 经 tanh 阻尼映射后的视觉拉出高度
    private var isPullingBack = false    // 是否处于横向拉出状态
    private var isVerticalAdjust = false // 是否处于长按上下调节小棍高度状态
    private var isArmed = false          // 是否已越过 48dp 阈值锁定

    private var downX = 0f
    private var downY = 0f
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!isPullingBack) {
            isVerticalAdjust = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            invalidate()
        }
    }

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C38BDF8")
        style = Paint.Style.FILL
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val waveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        textSize = 11f * density
        FakeBoldText = true
    }

    private val pillRect = RectF()
    private val capsuleRect = RectF()
    private val wavePath = Path()
    private val arrowPath = Path()

    /**
     * 关键防冲突机制：仅当触摸点落在边缘导引小棍的 28dp × 120dp 局部热区内时才处理事件，
     * 屏幕边缘其余区域返回 false，100% 透传给底层车载地图或 ViewPager2/RecyclerView。
     */
    private fun isPointInPillHitBox(x: Float, y: Float): Boolean {
        if (width <= 0 || height <= 0) return false
        val centerY = height * BackNavStateRepository.pillNormY
        val inVerticalRange = y in (centerY - hitBoxHeightPx / 2f)..(centerY + hitBoxHeightPx / 2f)
        if (!inVerticalRange) return false

        val isLeft = BackNavStateRepository.pillDockedLeft
        val inLeftHitBox = x <= hitBoxWidthPx
        val inRightHitBox = x >= (width - hitBoxWidthPx)
        return if (config.enableDualEdgesForWideScreen) {
            inLeftHitBox || inRightHitBox
        } else {
            if (isLeft) inLeftHitBox else inRightHitBox
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isPointInPillHitBox(event.x, event.y)) {
                    return false // 非小棍热区，直接放行给底层视图
                }
                downX = event.x
                downY = event.y
                isPullingBack = false
                isVerticalAdjust = false
                isArmed = false
                // 若开启双侧小棍，根据按下位置自动判断当前操作哪一侧
                if (config.enableDualEdgesForWideScreen) {
                    val touchLeft = event.x < width / 2f
                    BackNavStateRepository.updatePillPosition(BackNavStateRepository.pillNormY, touchLeft)
                }
                longPressHandler.postDelayed(longPressRunnable, 400L)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val isLeft = BackNavStateRepository.pillDockedLeft
                val inwardDx = if (isLeft) dx else -dx

                if (isVerticalAdjust) {
                    // 长按后上下拖动调整小棍驻留高度
                    val newNormY = (event.y / height.toFloat()).coerceIn(0.12f, 0.86f)
                    BackNavStateRepository.updatePillPosition(newNormY, isLeft)
                    invalidate()
                    return true
                }

                if (!isPullingBack) {
                    if (inwardDx > touchSlop && abs(dx) > abs(dy) * 1.3f) {
                        isPullingBack = true
                        longPressHandler.removeCallbacks(longPressRunnable)
                    } else if (abs(dy) > touchSlop * 1.5f) {
                        // 用户沿边缘快速上下滑动列表，取消长按并放行
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                }

                if (isPullingBack) {
                    currentPullDx = inwardDx.coerceAtLeast(0f)
                    // 双曲正切阻尼映射：visualPullWidth = MaxPull * tanh(Δx / 120dp)
                    val normPull = (currentPullDx / (120f * density)).toDouble()
                    visualPullWidth = (maxVisualPullPx * tanh(normPull)).toFloat()

                    val nowArmed = currentPullDx >= triggerThresholdPx
                    if (nowArmed && !isArmed) {
                        isArmed = true
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    } else if (!nowArmed && isArmed) {
                        isArmed = false
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (isPullingBack) {
                    val shouldTrigger = isArmed && event.actionMasked == MotionEvent.ACTION_UP
                    animateRelease {
                        if (shouldTrigger) onTriggerBack.invoke()
                    }
                }
                isPullingBack = false
                isVerticalAdjust = false
                isArmed = false
                return true
            }
        }
        return false
    }

    private fun animateRelease(onEnd: () -> Unit) {
        val startVisual = visualPullWidth
        ValueAnimator.ofFloat(startVisual, 0f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                visualPullWidth = anim.animatedValue as Float
                invalidate()
            }
            onEnd.invoke()
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val centerY = height * BackNavStateRepository.pillNormY
        val isLeft = BackNavStateRepository.pillDockedLeft

        // 1. 绘制常态驻留导引“小棍” (Edge Pill Indicator)
        drawPillIndicator(canvas, isLeft, centerY)
        if (config.enableDualEdgesForWideScreen) {
            drawPillIndicator(canvas, !isLeft, centerY)
        }

        // 2. 若正在向内拉出，根据皮肤配置绘制三阶贝塞尔流体水滴或圆角胶囊抽屉
        if (visualPullWidth > 1f) {
            if (config.edgeSkin == EdgePullSkin.BEZIER_TEARDROP) {
                drawBezierTeardrop(canvas, isLeft, centerY, visualPullWidth)
            } else {
                drawCapsuleDrawer(canvas, isLeft, centerY, visualPullWidth)
            }
        }
    }

    private fun drawPillIndicator(canvas: Canvas, isLeft: Boolean, centerY: Float) {
        val marginEdge = 3f * density
        val left = if (isLeft) marginEdge else (width - marginEdge - pillWidthPx)
        val top = centerY - pillHeightPx / 2f
        pillRect.set(left, top, left + pillWidthPx, top + pillHeightPx)
        pillPaint.color = if (isVerticalAdjust) Color.parseColor("#FBBF24") else Color.parseColor("#8C38BDF8")
        val radius = pillWidthPx / 2f
        canvas.drawRoundRect(pillRect, radius, radius, pillPaint)
    }

    private fun drawBezierTeardrop(canvas: Canvas, isLeft: Boolean, centerY: Float, pullW: Float) {
        val startX = if (isLeft) 0f else width.toFloat()
        val dir = if (isLeft) 1f else -1f
        val peakX = startX + dir * pullW

        wavePath.reset()
        wavePath.moveTo(startX, centerY - waveHalfSpanPx)
        wavePath.cubicTo(
            startX, centerY - waveHalfSpanPx * 0.45f,
            peakX,  centerY - waveHalfSpanPx * 0.35f,
            peakX,  centerY
        )
        wavePath.cubicTo(
            peakX,  centerY + waveHalfSpanPx * 0.35f,
            startX, centerY + waveHalfSpanPx * 0.45f,
            startX, centerY + waveHalfSpanPx
        )
        wavePath.close()

        if (isArmed) {
            wavePaint.shader = LinearGradient(
                startX, centerY, peakX, centerY,
                Color.parseColor("#F20284C7"), Color.parseColor("#E038BDF8"),
                Shader.TileMode.CLAMP
            )
            waveStrokePaint.color = Color.parseColor("#7DD3FC")
        } else {
            wavePaint.shader = null
            wavePaint.color = Color.parseColor("#E61E293B")
            waveStrokePaint.color = Color.parseColor("#94A3B8")
        }

        canvas.drawPath(wavePath, wavePaint)
        canvas.drawPath(wavePath, waveStrokePaint)

        // 绘制水滴内部返回箭头 `<`
        val progress = (currentPullDx / triggerThresholdPx).coerceIn(0.25f, 1.0f)
        val arrowCenterX = startX + dir * (pullW * 0.56f)
        val scale = if (isArmed) 1.2f else (0.85f + 0.15f * progress)
        val span = 8f * density * scale

        arrowPaint.alpha = (255 * progress).toInt()
        arrowPath.reset()
        arrowPath.moveTo(arrowCenterX + span * 0.4f, centerY - span)
        arrowPath.lineTo(arrowCenterX - span * 0.5f, centerY)
        arrowPath.lineTo(arrowCenterX + span * 0.4f, centerY + span)
        canvas.drawPath(arrowPath, arrowPaint)
    }

    private fun drawCapsuleDrawer(canvas: Canvas, isLeft: Boolean, centerY: Float, pullW: Float) {
        val capsuleH = 44f * density
        val capsuleW = (pullW * 1.45f).coerceAtLeast(capsuleH)
        val left = if (isLeft) 6f * density else (width - 6f * density - capsuleW)
        capsuleRect.set(left, centerY - capsuleH / 2f, left + capsuleW, centerY + capsuleH / 2f)

        wavePaint.shader = null
        wavePaint.color = if (isArmed) Color.parseColor("#F20284C7") else Color.parseColor("#E61E293B")
        waveStrokePaint.color = if (isArmed) Color.parseColor("#7DD3FC") else Color.parseColor("#38BDF8")

        canvas.drawRoundRect(capsuleRect, capsuleH / 2f, capsuleH / 2f, wavePaint)
        canvas.drawRoundRect(capsuleRect, capsuleH / 2f, capsuleH / 2f, waveStrokePaint)

        if (isArmed && capsuleW > 84f * density) {
            val textX = if (isLeft) capsuleRect.left + 14f * density else capsuleRect.right - 64f * density
            canvas.drawText("松手返回", textX, centerY + 4f * density, textPaint)
        }
    }
}

```

### 5.5 全局生命周期自动注入管理器（`InAppBackManager.kt`）

```
package com.carapp.navigation.back

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat

object InAppBackManager : Application.ActivityLifecycleCallbacks {

    private const val HOST_VIEW_TAG = "TAG_IVI_IN_APP_BACK_HOST"

    fun init(application: Application, config: BackNavConfig = BackNavConfig()) {
        BackNavStateRepository.init(application, config)
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is IgnoreInAppBack) return
        activity.window?.decorView?.post {
            attachHostIfNeeded(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is IgnoreInAppBack) return
        val host = attachHostIfNeeded(activity) ?: return
        host.syncStateAndVisibility(activity)
    }

    private fun attachHostIfNeeded(activity: Activity): InAppBackHostLayout? {
        val decorView = activity.window?.decorView as? ViewGroup ?: return null
        val existing = decorView.findViewWithTag<InAppBackHostLayout>(HOST_VIEW_TAG)
        if (existing != null) return existing

        val hostLayout = InAppBackHostLayout(activity).apply {
            tag = HOST_VIEW_TAG
            ViewCompat.setTranslationZ(this, 1000f)
        }
        decorView.addView(
            hostLayout,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        return hostLayout
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}

class InAppBackHostLayout(private val hostActivity: Activity) : FrameLayout(hostActivity) {

    private val floatingBallView = FloatingBackBallView(hostActivity) {
        UnifiedBackDispatcher.performBack(hostActivity)
    }
    private val edgePullView = EdgePullBackView(hostActivity) {
        UnifiedBackDispatcher.performBack(hostActivity)
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(
            edgePullView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        addView(
            floatingBallView,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        )
    }

    fun syncStateAndVisibility(activity: Activity) {
        val cfg = BackNavStateRepository.config
        if (cfg.autoHideOnRootPage && UnifiedBackDispatcher.isAtRootWithNoBackStack(activity)) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE

        floatingBallView.visibility = when (cfg.mode) {
            BackNavMode.BALL_ONLY, BackNavMode.DUAL_ENABLED -> View.VISIBLE
            BackNavMode.EDGE_PULL_ONLY -> View.GONE
        }
        edgePullView.visibility = when (cfg.mode) {
            BackNavMode.EDGE_PULL_ONLY, BackNavMode.DUAL_ENABLED -> View.VISIBLE
            BackNavMode.BALL_ONLY -> View.GONE
        }

        post {
            floatingBallView.syncPositionFromRepo(width, height)
            edgePullView.invalidate()
        }
    }
}

```

## 6. 开发排期与质量验收标准（Checklist for Review & QA）

| 阶段里程碑 | 预计工期 | 核心研发交付内容 | 验收标准（DoD） | 
| ----- | ----- | ----- | ----- | 
| **M1：基础容器与自动注入框架** | Day 1 – Day 2 | 实现 `InAppBackManager`、`BackNavStateRepository`、`UnifiedBackDispatcher` 及 `DecorView` 自动挂载。 | 无需申请任何悬浮窗或无障碍权限；Activity 跳转坐标零闪烁；一级根首页自动隐藏。 | 
| **M2：模式一（磁吸悬浮球）开发** | Day 3 – Day 4 | 实现 `FloatingBackBallView`、`72dp` 外扩盲操热区、安全边距限制、`SpringAnimation` 贴边磁吸与 3 秒闲置半隐藏。 | 满帧拖拽无掉帧；松手贴边具备自然弹簧阻尼；闲置 3 秒准时降透缩进 `35%`。 | 
| **M3：模式二（边缘小棍+拉出抽屉）** | Day 5 – Day 6 | 实现 `EdgePullBackView` 常驻导引小棍、长按 `400ms` 上下移位、`28dp × 120dp` 局部热区拦截、三阶贝塞尔流体水滴与胶囊抽屉双皮肤、`48dp` 阈值锁定反馈。 | 在小棍热区外滑动车载地图/横向列表 100% 无冲突；拉出 `< 48dp` 松手可反悔取消；`≥ 48dp` 触发震动/滴声并准确返回。 | 
| **M4：车载多屏适配与设置面板** | Day 7 – Day 8 | 实现调试/设置开关面板、日夜模式（`UiModeManager`）配色切换、车机分屏与旋转重算（`onSizeChanged`）。 | 分屏/旋转后悬浮球与边缘小棍按归一化比例自动重定位；连续跳转 500 次无内存泄漏。 | 
