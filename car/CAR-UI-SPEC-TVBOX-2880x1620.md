# 车机 UI 开发规范（基于 TVBoxOS 架构 · 2880×1620 / 17.3"）

> **状态：car.7 开发生效规范，运行验收结果单独记录。**
>
> - 适用 TVBoxOS 车机版本；既有 1280x720 / AndroidAutoSize MM 架构保留。
> - car.7 更新：背景与内容全屏铺满，安全带只限制操作热区，不再强制整个根布局留空。
> - 返回交互以用户提供的 `ivi_in_app_back_navigation_spec.md` 为依据；TVBox 的首页防退出及闲置时间兼容规则见本文件补充条款。

---

## 0. 核心架构原则

1. **保留项目的设计单位**：XML `mm` 由 AndroidAutoSize 的 `xdpi` 映射为设计坐标，不是屏幕真实物理毫米；无需修改车机系统 DPI。
2. **基准画布 1280x720（16:9）**：2880x1620 全屏时设计单位约为 2.25px。实际值必须通过最终 APK 的原生节点和截图核对。
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
| **顶部避让禁区 (Top Strip)** | **0 ~ 40 mm** | **0 ~ 90 px** | 0 ~ 12.0 mm | 不放按钮、Tab 或手势起始热区；允许壁纸、视频、海报背景及非交互信息连续延伸。 |
| **底部避让禁区 (Bottom Strip)** | **底边算起 55 mm** | **底边算起 124 px** | 16.5 mm | 不放可操作控制；视频、壁纸和非交互内容继续绘制，不添加纯黑占位条。 |
| **左右安全边距** | **各 35 mm** | **各 78.7 px** | 10.5 mm | 常规按钮与可点击卡片留在安全范围；滚动容器预留首尾留量。 |

### 2.3 背景与热区分离
根布局铺满窗口，不统一加四边安全 Padding。壁纸由窗口完整绘制，根布局不得用不透明色覆盖壁纸形成空带。仅操作层应用安全 Margin / Padding：
```xml
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <!-- 视频或图片层铺满；不添加黑色占位条。 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:paddingTop="40mm"
        android:paddingBottom="55mm"
        android:paddingLeft="35mm"
        android:paddingRight="35mm"
        android:orientation="vertical" />
</FrameLayout>
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

### 9.1 MuMu 显示链路验收门槛

模拟器操作必须先通过全局实例池的租约检查，并全部经 `hawchou.mumu-player` 插件执行。MuMu 配置必须是 `resolution_mode=custom`、宽 2880、高 1620、dpi 160，host 静音开启。

**不能只检查截图宽高或 `wm size` 的 Override 值。** 2026-10-16 曾出现截图为 2880x1620、底层却仍按竖屏缩放到 `Rect(0,984 - 1620,1895)` 的无效状态；该状态不得用于车机验收。

在已持有租约的实例中，通过 `mumu_shell` 的系统 namespace 执行：

```sh
wm size reset
wm density reset
wm scaling auto
settings put system accelerometer_rotation 0
settings put system user_rotation 1
wm user-rotation lock 1
wm fixed-to-user-rotation enabled
```

以上仅用于已配置 physical density 160 的模拟器，禁止对真实车机执行 DPI 或尺寸修改。自然方向报告 `Physical size: 1620x2880` 本身不代表失败，必须同时满足下列条件：

- 没有手工 `Override size`；系统旋转是横屏，`mCurrentOrientation=1`。
- `dumpsys display` 的 `mCurrentDisplayRect=Rect(0,0 - 2880,1620)`，画面完整铺满。
- `dumpsys input` 的逻辑与物理 viewport 均为 `[0,0,2880,1620]`，`deviceSize=[2880,1620]`，不存在缩放或偏移。
- `dumpsys window displays` 的 `cur`、`app`、`DisplayFrames` 均为 2880x1620；应用配置为 `land`、`160dpi`。
- 桌面、应用原生 PNG 均为 2880x1620，重启实例后重新检查仍满足上述条件。

MuMu 6.8.7.0 实测会在实例重启后将旋转锁恢复为 `free/default`，即使桌面仍恰好横屏。因此每次启动后都要重新锁定，再运行只读门禁 `car/tests/verify_car_display.sh`；只有输出 `CAR_DISPLAY_PASS` 才能开始验收。锁定不持久不得被写成重启后配置全通过。
真实车机可用 ADB 只读采集同类信息与截图；模拟器不能代替 OEM 窗口管理或真实硬件性能验收。

### 9.2 图像像素验收核对标尺

打开生成的 `check-car-2880.png`（必须为 **2880 × 1620** 分辨率）：

1. **顶部禁区校验**：画面顶部 **0 ~ 90 px** 内是否有任何按钮？（必须为**无**，只有背景延伸）。
2. **底部禁区校验**：画面底部 **1496 ~ 1620 px**（底边 124 px）内是否有任何按钮或进度条？（必须为**无**）。
3. **正文字号校验**：用画图/量尺测量列表汉字高度，字高必须在 **$36 \sim 45\text{ px}$** 之间。
4. **主按钮高度校验**：测量主操作按钮（如播放、确定、分类）高度，必须在 **$112 \sim 120\text{ px}$** 之间。
5. **左右安全区校验**：最左侧按钮左边缘距屏幕左边界 $\ge 78\text{ px}$。

---

## 10. 生效与协作流程

1. **当前状态**：本规范已由用户要求启用，适用于 car.7 及后续车机版；不是待激活草稿。
2. **激活口令**：当用户明确下达：
   > 「按 `CAR-UI-SPEC-TVBOX-2880x1620` 开发」
   > 或
   > 「采用 TVBoxOS 规范改造当前项目」
   AI 必须严格执行本文件 §2 的上下避让禁区、§3 的 mm 转换引擎与 §4 的尺寸数值表。
3. **后续调优留档**：如果实车上下一条的物理遮挡高度有细微变动，只需在 §2.2 中修改 `40mm` 与 `55mm` 的基准值，全应用通过 `dimens.xml` 自动全局重算生效。

## 11. car.7 返回与触摸适配

- 模式：悬浮球、边缘拉出、双模共存。应用内 View 挂载到 Activity decor；不用系统悬浮窗、无障碍或新增权限。
- 悬浮球采用原稿56/64、72/80的参考单位比例，而非直接使用Android dp。按 `BACK-CAR-SCALE.md` 适配后，2880x1620窗口的标准可视球为189px、热区243px，大号216/270px；8参考单位拖拽阈值同尺度换算。保留速度辅助选边、0.75/400弹簧、闲置alpha0.38、外移35%与120ms唤醒。
- TVBox 兼容：保留 `inapp_back` 总开关和 `hide_seconds` 的常驻/4/6/8 秒选择，默认 4 秒；因此不采用蓝图固定 3 秒。首页根状态保留防退出，轻点只提示，绝不由组件退出应用。
- 边缘胶囊按同一参考单位缩放：本车屏导引条20.25x256.5px，局部热区94.5x405px，实际拉动162px就绪。保留横向优势1.5和 `72*tanh(rawDx/120)` 的参考单位阻尼；抬手返回，短拉、轻点和CANCEL不返回。水滴轮廓按原SVG比例展开、跨度上限160参考单位；胶囊独立布局文字与圆形箭头钮。
- 长按 400ms 调整胶囊高度，独立于球的位置保存。支持左、右或双侧；旋转后归一化坐标重新夹紧；销毁时清理动画和回调。
- 返回组件的视觉和手势使用车机窗口参考单位比例，普通安全边界仍取原生dp下限与车机MM操作边距的较大值，不修改整机DPI。该适配有意修正原稿在本车屏上过小的原始dp尺寸；原稿保持不变，差异与占屏比例见 `BACK-CAR-SCALE.md`。最终尺寸与图形必须在d160实测，d240只作补充对照。
- 空白透传；仅组件本身和局部胶囊热区处理事件，不占整条边缘。Dialog 属于独立窗口，通过自身外点取消，不能宣称 decor Z 值能跨窗口覆盖。
- 首页快捷入口使用水平滚动容器；分类点击必须能切页，不能只响应焦点。推荐标题独立于海报底部，双行固定区域并可滚到最后一列。

## 12. car.7 验收基线

- 主环境：2880x1620、density 160；density 240 仅作不劣化对照，不代替实机等效环境。不得修改真实车机 DPI。
- 必须先通过 §9.1 的显示链路门槛；PNG 宽高或 `wm size` 覆盖值不能单独证明环境合格。旧的竖屏承载横屏缩放状态证据只作排查，不计入最终通过。
- 证据绑定最终 APK 的 SHA-256 和实际安装 versionCode/name。构建成功、插件安装返回成功、截图存在均不等于功能通过。
- 检查按钮 bbox 不进入上下安全带；背景连续到窗口边缘；最右入口、最后卡片及其双行标题可达；横拖不触发返回。
- 检查球拖动/吸边/闲置/唤醒/跨页持久化；边缘短拉、达阈值、反悔取消、长按移位、局部外区透传及双模切换。
- 弹窗内点击不误取消，外部轻点取消且不保存输入、不执行确认；CANCEL 不派发返回或确认。
- 当前视频切换画面、SR、补帧和音效后立即观察当前 renderer/session；覆盖全关与重新开启、暂停和 EXO/IJK 前后台恢复。
- 如未执行 500 次跳转内存测试、真实车机性能测试或全部 context-loss 注入，报告中明确未覆盖，不将静态实现写作实测通过。
