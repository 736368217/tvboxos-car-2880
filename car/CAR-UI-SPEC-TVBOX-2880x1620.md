# 车机 UI 开发规范（基于 TVBoxOS 架构 · 2880×1620 / 17.3"）

> **状态：草案（DRAFT，未生效）**
>
> - 本文件**不是** `AGENTS.md`、**不是** `.agents/*.md`，不会被任何 AI/Agent 自动加载或默默遵守。
> - **生效方式**：在对话中明确说出「按 `CAR-UI-SPEC-TVBOX-2880x1620` 开发」，本文件即作为该任务的核心开发与验收基准。
> - **设计来源**：以开源车载/TV 标杆项目 `q215613905/TVBoxOS`（AndroidAutoSize + Subunits.MM 体系）为技术基准，吸收畅流（畅☁流）的纯物理尺寸控制优点，并深度融入**17.3 英寸车载强光视距与上下系统组件避让**等车机人机工程学（Car HMI）。

---

## 0. 核心架构原则

1. **借道 `mm` 原生声明**：彻底弃用容易缩小的 `dp` 和 `sp`，全面使用 Android 原生支持的 `mm`（毫米）作为 XML 布局与字号单位。
2. **基准画布 1280×720（16:9）**：以车载/TV 工业成熟的 720P 为设计基准，天然 16:9 与目标屏（2880×1620）实现 **2.25 倍绝对无损等比缩放**。
3. **保留 Android 原生生态**：100% 采用标准 XML、`ConstraintLayout`、`TvRecyclerView`，享受原生视图回收、动画与**方向盘/旋钮焦点导航**。
4. **上下系统组件绝对避让**：顶部状态栏区与底部车控/Dock 栏区严禁放置任何可交互按钮，保障行车安全与盲操防挡。

---

## 1. 目标硬件与显示几何

| 物理参数 | 数值 | 换算推导 |
|---|---|---|
| **物理分辨率** | **2880 × 1620**（固定横屏，16:9） | 实机屏幕物理像素 |
| **屏幕尺寸** | **17.3 英寸**（对角线） | 发光区约 383 mm × 215 mm |
| **物理像素密度** | **191.0 PPI** | 1 物理像素 (px) ≈ 0.133 mm |
| **设计基准画布** | **1280 × 720**（单位：XML `mm`） | 16:9，与目标物理分辨率完全同比例 |
| **像素缩放因子** | **$S = 2.25$** | $2880 / 1280 = 1620 / 720 = 2.25$ |
| **单位换算恒等式** | **$1\text{ mm (XML)} = 2.25\text{ px} \approx 0.30\text{ mm (真实物理)}$** | 代码中写 1mm，上屏为 2.25 像素 |

> 💡 **为什么是 2.25？**
> 1280 × 720 与 2880 × 1620 具有完全一致的宽高比（16:9）。当把设计宽度定为 1280mm 时，在 2880 宽的屏上，每一毫米刚好放大 2.25 倍，高度 720mm 刚好放大到 1620px，**全屏无黑边、无变形、无拉伸**。

---

## 2. ★ 致命硬约束：上下系统组件避让禁区 (Exclusion Zones)

### 2.1 车机环境痛点
在 17.3 寸车机系统中：
- **屏幕顶部**：常驻系统状态栏、下拉通知呼出区或驾驶辅助提示条。
- **屏幕底部**：常驻空调/座椅控制栏、车机 Dock 栏、主页返回虚拟按键或手势滑动指示条。
如果在屏幕顶部或底部的边缘放置按钮，**会被系统组件直接遮挡，或者用户按上去触发车机空调/退回桌面**。

### 2.2 禁区数值定义

```
+-------------------------------------------------------------------------+
|    [顶部避让禁区 TOP_STRIP] 40 mm (90 px / 物理约 12 mm)                |  <- 严禁放置任何可点击按钮/返回键/Tab
+-------------------------------------------------------------------------+
|                                                                         |
|                       【安全可交互区 SAFE_CANVAS】                       |
|                 高度: 625 mm (1406 px / 物理约 187 mm)                  |  <- 所有卡片、操作按钮、列表、滑块严格在此区域
|                                                                         |
+-------------------------------------------------------------------------+
|    [底部避让禁区 BOTTOM_STRIP] 55 mm (124 px / 物理约 16.5 mm)           |  <- 严禁放置播放进度条、底栏按键、翻页键
+-------------------------------------------------------------------------+
```

| 区域 | XML 尺寸 (`mm`) | 屏幕像素 (`px`) | 物理高度 (`mm`) | 规范要求 |
|---|---|---|---|---|
| **顶部避让禁区 (Top Strip)** | **0 ~ 40 mm** | **0 ~ 90 px** | 0 ~ 12.0 mm | **严禁放置任何可点击控件**（返回键、搜索键、Tab 均须下移至 40mm 之下）。仅允许背景色延伸或居中无点击纯文本。 |
| **底部避让禁区 (Bottom Strip)** | **底边算起 55 mm** | **底边算起 124 px** | 16.5 mm | **严禁放置任何可点击控件**（播放控制条、分类按钮、底栏 Tab 严禁贴底）。 |
| **左右安全边距** | **各 35 mm** | **各 78.7 px** | 10.5 mm | 防车机圆角与外边框遮挡，内容区左右缩进。 |

### 2.3 根布局落地标准
所有 Activity 的 XML 根布局或公共模板（如 `layout_base_activity.xml`）必须设置安全 Padding：
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:paddingTop="40mm"
    android:paddingBottom="55mm"
    android:paddingLeft="35mm"
    android:paddingRight="35mm"
    android:clipToPadding="false"
    android:orientation="vertical">
    <!-- 具体内容区 -->
</LinearLayout>
```

---

## 3. 核心机制落地（TVBoxOS 借道 `mm` 原理与实现）

### 3.1 原理剖析
Android Framework 在解析 XML 中的 `mm` 单位时，底层调用：
```java
// TypedValue.java 原生代码
case COMPLEX_UNIT_MM:
    return value * metrics.xdpi * (1.0f / 25.4f);
```
当我们将当前 Activity 的 `DisplayMetrics.xdpi` 动态赋值为：
$$\text{targetXdpi} = \frac{\text{屏幕实际宽度 (2880)}}{1280} \times 25.4 = 2.25 \times 25.4$$
计算实际像素时：
$$\text{px} = \text{value} \times (2.25 \times 25.4) \times \frac{1}{25.4} = \text{value} \times 2.25$$
$25.4$ 完全抵消！**我们直接用系统原生代码实现了无损等比缩放，且完全不改动 `density`，不影响系统弹窗与第三方 SDK！**

### 3.2 方案 A：零依赖自实现（推荐，仅需 5 行原生代码）
无需引入任何外部第三方库，在 `BaseActivity.java` 或 `BaseActivity.kt` 中写入：

```kotlin
open class BaseCarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        adaptCarScreen(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        adaptCarScreen(this) // 应对倒车、切源或分屏归来
    }

    companion object {
        fun adaptCarScreen(activity: Activity) {
            val dm = activity.resources.displayMetrics
            // 设计宽度 1280：xdpi 动态赋值，使得 1mm 在 2880 宽上精确输出 2.25px
            dm.xdpi = (dm.widthPixels / 1280f) * 25.4f
        }
    }
}
```

### 3.3 方案 B：沿用 TVBoxOS 原版 `me.jessyan:autosize`
在 `build.gradle` 引入 `implementation 'me.jessyan:autosize:1.2.1'`。
在 `App.java` 的 `onCreate()` 中写入（与 upstream 完全一致）：
```java
AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
        .setSupportDP(false)            // 彻底关闭 dp 拦截
        .setSupportSP(false)            // 彻底关闭 sp 拦截
        .setSupportSubunits(Subunits.MM); // 仅开启系统 mm 拦截
```
在 `AndroidManifest.xml` 中加入：
```xml
<meta-data
    android:name="design_width_in_dp"
    android:value="1280" />
<meta-data
    android:name="design_height_in_dp"
    android:value="720" />
```

---

## 4. 尺寸与字号规范速查表

> 在 XML 布局中，**直接书写下表的 XML 数值（带 `mm` 单位）**，或引用 `dimens.xml` 中的对应别名。

| 业务角色 | XML 书写数值 (`mm`) | 上屏像素 (`px`) | 物理真实大小 (`mm`) | 车机易用性评价 |
|---|---|---|---|---|
| **次要文字 / 标签** | `14mm` | 31.5 px | 4.2 mm | 辅助信息下限，清晰不刺眼 |
| **正文 / 列表项标题** | **`16mm ~ 18mm`** | **36 ~ 40.5 px** | **4.8 ~ 5.4 mm** | **车机正文舒适区，60~80cm 扫视清晰** |
| **小标题 / 按钮文字** | `20mm ~ 22mm` | 45 ~ 49.5 px | 6.0 ~ 6.6 mm | 按钮、弹窗标题标准字号 |
| **大标题 / 分类 Tab** | `26mm ~ 30mm` | 58.5 ~ 67.5 px | 7.8 ~ 9.0 mm | 顶栏分类、大卡片标题 |
| **大数字 (速度/集数)** | `40mm ~ 60mm` | 90 ~ 135 px | 12.0 ~ 18.0 mm | 醒目突出 |
| **最小触摸热区** | **`40mm × 40mm`** | **90 × 90 px** | **12.0 × 12.0 mm** | **车机点按绝对硬下限，低于此值必误触** |
| **常规按钮高度** | **`50mm`** | **112.5 px** | **15.0 mm** | **主操作按键推荐高度** |
| **大卡片按钮高度** | `70mm ~ 90mm` | 157.5 ~ 202.5 px | 21.0 ~ 27.0 mm | 首页大入口（点播/直播/历史） |
| **相邻热区间距** | **`12mm ~ 15mm`** | **27 ~ 33.7 px** | **3.6 ~ 4.5 mm** | **防行车颠簸连带误触安全间距** |
| **焦点高亮边框线宽** | `2mm` | 4.5 px | 0.6 mm | TVBox 遥控器/方向盘聚焦高亮边框 |
| **圆角弧度** | `6mm ~ 10mm` | 13.5 ~ 22.5 px | 1.8 ~ 3.0 mm | 适度圆角，现代车载质感 |

### `res/values/dimens.xml` 推荐常备表
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 字号类 TextSize (ts_*) -->
    <dimen name="ts_14">14mm</dimen>
    <dimen name="ts_16">16mm</dimen>
    <dimen name="ts_18">18mm</dimen>
    <dimen name="ts_20">20mm</dimen>
    <dimen name="ts_24">24mm</dimen>
    <dimen name="ts_28">28mm</dimen>
    <dimen name="ts_32">32mm</dimen>

    <!-- 尺寸与间距 ViewSize (vs_*) -->
    <dimen name="vs_4">4mm</dimen>
    <dimen name="vs_8">8mm</dimen>
    <dimen name="vs_12">12mm</dimen>
    <dimen name="vs_16">16mm</dimen>
    <dimen name="vs_20">20mm</dimen>
    <dimen name="vs_30">30mm</dimen>
    <dimen name="vs_40">40mm</dimen>
    <dimen name="vs_50">50mm</dimen>
    <dimen name="vs_70">70mm</dimen>
    <dimen name="vs_100">100mm</dimen>
    <dimen name="vs_180">180mm</dimen>

    <!-- 避让区尺寸 -->
    <dimen name="car_top_strip_padding">40mm</dimen>
    <dimen name="car_bottom_strip_padding">55mm</dimen>
    <dimen name="car_side_padding">35mm</dimen>
</resources>
```

---

## 5. 车载人机工程学（Car HMI）易用性专属规范

### 5.1 视距与单手盲操（Drive Ergonomics）
- 车机安装在中控台，离驾驶员视距约为 **60 ~ 80 cm**（是手机的 2.5 倍），副驾驶视距约 **70 ~ 90 cm**。
- **行车文字单行长度限制**：
  - 标题或说明文字一行不得超过 30 个汉字（最大容器宽不得超过 `600mm`），严禁横向拉满整屏导致视线横扫移位。

### 5.2 焦点导航与车载物理控制（D-Pad / 方控 / 旋钮）
这是 TVBoxOS 原生架构的最大优势：
1. **所有可点控件强制声明 `android:focusable="true"`**：保证车机旋钮或方向盘上下左右按键能够自然选定。
2. **焦点高亮状态（Focus State）必须极度鲜明**：
   - 必须使用 `@drawable/selector_item_focus`：获焦时具有高亮描边（`2mm`）或整体缩放动画（1.05x 放大），背景变亮。
   - 焦点态与普通态的对比度必须 $\ge 3:1$。
3. **禁用无限级微细滑动条（SeekBar）**：
   - 音量、亮度调节使用大步进按钮（+ / -，步长 10%），避免在颠簸路面进行细粒度拖拽。

### 5.3 强光反光与夜间防眩（Contrast & Lighting）
- 车内白天存在太阳直射反光，夜间存在暗光眩光。
- **全系采用暗色沉浸主题**：
  - 根背景色：深灰黑 `#101318`（严禁纯黑 `#000000` 导致 OLED 纯黑边缘割裂，严禁大面积浅色白天刺眼）。
  - 卡片背景：浅灰黑 `#1E222B`。
  - 主文字色：高亮米白 `#EDEDED`（正文对比度达到 11:1，抗阳光反光）。
  - 强调色（Focus/选中）：车载护眼蓝 `#2979FF` 或 金橙 `#FF9100`。

---

## 6. 视频播放器层专项规范（针对影视仓/播放类项目）

1. **画面渲染层（SurfaceView / TextureView）独立**：
   - 视频渲染层由底层解码器负责按 16:9 原生铺满，**严禁对视频渲染层施加任何业务缩放**。
2. **播放器覆盖层（Player Controller Overlay）严格避让**：
   - 顶部返回按钮与视频标题：顶部 Margin 必须 $\ge 40\text{mm}$（防顶部状态栏）。
   - 底部播放/暂停、快进快退、集数选择、清晰度切换：底部 Margin 必须 $\ge 60\text{mm}$（防空调与 Dock 栏）。
   - 进度条（ProgressBar）高度 $\ge 8\text{mm}$，拖动热区 $\ge 45\text{mm}$。
3. **触控手势防误触**：
   - 左侧上下滑动调亮度、右侧上下滑动调音量：有效识别区域限定在中间 $150\text{mm} \sim 570\text{mm}$ 高度区间，上下一律各留出 $40\text{mm}$ 和 $55\text{mm}$ 手势死区。

---

## 7. AndroidManifest.xml 与工程级配置

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="你的包名">

    <!-- 硬件特性一律 required="false"，杜绝车机安装过滤 -->
    <uses-feature android:name="android.software.leanback" android:required="false" />
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.location" android:required="false" />

    <application
        android:name=".App"
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:resizeableActivity="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.NoActionBar">

        <!-- TVBoxOS 720P 设计基准（采用方案 B 时使用，采用方案 A 时可留作标识） -->
        <meta-data
            android:name="design_width_in_dp"
            android:value="1280" />
        <meta-data
            android:name="design_height_in_dp"
            android:value="720" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:resizeableActivity="true"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|uiMode|keyboardHidden|fontScale">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## 8. 禁止清单（Do Not - 违例即打回）

| # | 禁令 | 违规后果 | 修正方案 |
|---|---|---|---|
| **1** | **严禁在顶部 0~40mm / 底部 0~55mm 放置按钮** | **被车机系统状态栏、空调条、Dock 栏遮挡无法点击** | **根布局加 `paddingTop="40mm"` 与 `paddingBottom="55mm"`** |
| **2** | **严禁在 XML 中使用 `dp` 或 `sp`** | **在 240/160 DPI 车机上严重缩小，变成无法辨析的小字** | **一律使用 `mm`，如 `16mm`、`50mm`** |
| **3** | **严禁直接修改全局 `DisplayMetrics.density`** | **污染系统通知、第三方 SDK、系统键盘与输入法** | **只拦截 `xdpi`（见 §3），严禁触碰 `density` 与 `scaledDensity`** |
| **4** | **严禁最小点击热区小于 `40mm × 40mm`** | **行车颠簸时按不到或严重误触** | **所有按钮最小宽高写死 $\ge 40\text{mm}$** |
| **5** | **严禁相邻热区间距小于 `12mm`** | **手指粗按导致同时触发两个按钮** | **按钮间距 Margin $\ge 12\text{mm}$** |
| **6** | **严禁正文字号小于 `14mm`** | **驾驶员视距下必须眯眼查看，产生行车安全隐患** | **正文 $\ge 16\text{mm}$，辅助说明文字 $\ge 14\text{mm}$** |
| **7** | **严禁强制竖屏 `screenOrientation="portrait"`** | **车机强制横屏下画面撕裂或被系统加宽黑边 letterbox** | **强制横屏 `sensorLandscape`** |
| **8** | **严禁在视频 SurfaceView 上直接叠加全局缩放** | **视频画面比例被扭曲拉伸，出现非等比变形** | **渲染层原生 16:9，控制 UI 单独套安全区** |

---

## 9. 验收规范（真机 / 模拟器标准作业程序）

### 9.1 ADB 自动化截屏与尺寸验证
```powershell
$adb = "C:\Users\WIN10\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$s   = "<设备序列号>"

# 1. 验证目标分辨率基线
& $adb -s $s shell wm size
# 期望：Physical size: 2880x1620 (或 Override size: 2880x1620)

# 2. 启动并获取原生分辨率截图
& $adb -s $s shell am start -W -n <包名>/.MainActivity
Start-Sleep -Seconds 3
cmd /c "`"$adb`" -s $s exec-out screencap -p > check-car-2880.png"
```

### 9.2 图像像素验收核对标尺

打开生成的 `check-car-2880.png`（必须为 **2880 × 1620** 分辨率）：

1. **顶部禁区校验**：画面顶部 **0 ~ 90 px** 内是否有任何按钮？（必须为**无**，只有背景延伸）。
2. **底部禁区校验**：画面底部 **1496 ~ 1620 px**（底边 124 px）内是否有任何按钮或进度条？（必须为**无**）。
3. **正文字号校验**：用画图/量尺测量列表汉字高度，字高必须在 **$36 \sim 45\text{ px}$** 之间。
4. **主按钮高度校验**：测量主操作按钮（如播放、确定、分类）高度，必须在 **$112 \sim 120\text{ px}$** 之间。
5. **左右安全区校验**：最左侧按钮左边缘距屏幕左边界 $\ge 78\text{ px}$。

---

## 10. 生效与协作流程

1. **默认状态**：本文档存盘为草稿，任何通用 Agent 不会自动套用此规则。
2. **激活口令**：当用户明确下达：
   > 「按 `CAR-UI-SPEC-TVBOX-2880x1620` 开发」
   > 或
   > 「采用 TVBoxOS 规范改造当前项目」
   AI 必须严格执行本文件 §2 的上下避让禁区、§3 的 mm 转换引擎与 §4 的尺寸数值表。
3. **后续调优留档**：如果实车上下一条的物理遮挡高度有细微变动，只需在 §2.2 中修改 `40mm` 与 `55mm` 的基准值，全应用通过 `dimens.xml` 自动全局重算生效。
