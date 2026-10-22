# 交付说明 · TVBox 白盒原版 + 交接包功能（original-app-feature-handover）

本文件记录本轮交付的分支、产物身份、已实现功能、验证证据与已知边界。
所有结论均来自本机可复现的命令与设备实测，未验证项在「已知边界」中如实标注。

---

## 1. 交付物

| 项 | 值 |
| :--- | :--- |
| 仓库 / 工作树 | `D:\github\tvboxos-car-2880.git` 的 worktree `D:\github\tvboxos-upstream-feature-pack` |
| 分支 | `feature/original-app-feature-handover` |
| 分支基线 | `upstream/main` = `ab11d289`（三倍速位置微调） |
| APK 路径 | `%PI_SCRATCH_DIR%\delivery\TVBox-Original-Feature-1.0.0-car.7.apk`（不入 Git） |
| APK 大小 | 33,322,589 bytes |
| APK SHA-256 | `00A0B196F3572C4EED88D74CBF9F60FD823E7F2B50AF74912CF4DA527ED164B0` |
| 签名 | v1 + v2 + v3 通过；证书 SHA-256 `2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C`（Android Debug keystore，与 car.7 测试证书同源） |

> 该 APK 为**测试签名**产物，用于装机验证；不是商店发布包。仓库未提供 release signing 配置，故在构建后用 `zipalign` + `apksigner` 手动签名。

## 2. 应用身份（保持不变）

| 项 | 值 |
| :--- | :--- |
| Application ID | `com.github.tvbox.osc.jun` |
| 应用名 | `TVBox` |
| versionCode / versionName | `8` / `1.0.0-car.7` |
| minSdk / targetSdk / compileSdk | 19 / 28 / 33 |
| Launcher | `com.github.tvbox.osc.ui.activity.HomeActivity` |

身份由 `aapt2 dump badging` 在交付 APK 上直接读出（非源码推断）。
`versionCode`/`versionName` 由白盒原版的 `1` / `1.0.0` 改为交接目标身份；`applicationId` 原版已是目标值，未改动。

## 3. 已合入功能

### 3.1 返回杠（对应 `01-back-bar.md`）

- 边缘竖向窄杠（非悬浮球），默认 4 秒自动隐藏，不提供永久显示。
- 不申请 `SYSTEM_ALERT_WINDOW`、无无障碍服务、无全局按键权限；宿主为 Activity DecorView 内的全屏透明 `FrameLayout`，宿主本身不消费触摸。
- 隐藏态首次触摸只唤醒、不误触返回；短按 ≤300ms 才判定为点击，多指、超时、滑动均拒绝。
- 返回分发与系统 Back 同序：`BackInterceptor` → 聚焦 WebView `goBack()` → Fragment back stack → `Activity.onBackPressed()`。
- 关键类：`com.github.tvbox.osc.backbar.{BackBarController, BackBarHost, BackBarView, BackBarInteractionPolicy}`；挂载点 `App#install` + `BaseActivity#onResume/dispatchTouchEvent/dispatchKeyEvent`。
- 设置页新增「返回杠」开关行（`fragment_model.xml#llBackBar` / `tvBackBarOpen`），持久化键 `HawkConfig.IN_APP_BACK`（`inapp_back`，默认开启）。

### 3.2 播放增强四项（对应 `02`–`05`）

画面预设 11 个（ID 0–10）、超分 SGSR、时域平滑（相邻帧混合，非 MEMC）、9 个音效模式；四项相互独立。

- `VideoEnhancePreset` / `VideoEnhanceConfig` / `GlEnhanceRenderView` / `GlEnhanceRenderViewFactory` / `VideoShaders` / `VideoPostProcessPolicy`（取自交接文档所述的最新版本：`VideoPostProcessPolicy` SHA `73E915F0…42B8`、`VideoShaders` SHA `DC8D435D…1AED`）。
- 音效绑定当前播放器 audio session；模式 0 或无效 session 会释放（`AudioEffectController` / `AudioEffectPreset`）。
- 播放器控件行：`画面 / 超分·开|关 / 平滑·开|关 / 音效 · 模式`（`player_vod_control_view.xml` + `VodController`）。
- 不虚假宣称：输出 FPS 不复制片源 FPS；补帧文案为「时域平滑」。

### 3.3 其他

- `assets/licenses/snapdragon-gsr.txt`：SGSR 相关许可声明。
- `app/src/debug/`：仅调试构建的播放探针（`FeatureProbeActivity` + `ProbeLaunchReceiver`）。**已确认不在 release APK 中**（见 §4.4）。
- `car/tests/`：4 个纯 JVM host 测试（无需设备）。

## 4. 验证证据

### 4.1 Host 测试（JVM，全部通过）

```
javac -encoding UTF-8 -d <out> -sourcepath "app/src/main/java;player/src/main/java" car/tests/*.java
java -cp <out> BackBarInteractionPolicyHostTest   → PASS: 16 Back-bar gesture/timer checks
java -cp <out> VideoEnhancePresetTest             → PASS: 11 presets, stable IDs, legacy migration, corrupt preference fallback, distinct immutable curves
java -cp <out> Car6PostProcessTest                → PASS: exact old/new audio and video curves, immutable data, linked SGSR sharpness, temporal cut/pause/seek, 4K policy and switches
java -cp <out> Car7RuntimeConfigTest              → PASS: 44 runtime configurations, 88 SDR/HDR cases, 1936 transition comparisons, independent switches and immutable publication
```

另：`git diff --check` 无输出（无空白错误）。

### 4.2 车机显示门禁（MuMu 实例 1）

`verify_car_display.sh` 输出 `CAR_DISPLAY_PASS`：physical density 160、rotation lock 1、fixed-to-user-rotation enabled、`accelerometer_rotation=0`、`mCurrentDisplayRect=Rect(0,0-2880,1620)`、input logical/physicalFrame `[0,0,2880,1620]`、deviceSize `[2880,1620]`、DisplayFrames `w=2880 h=1620 r=1`。所有设备操作均在真实 2880×1620 / d160 基准下进行。

### 4.3 返回杠像素级证据（release APK）

截图统一为 2880×1620，用逐像素 rgb24 差分：

| 场景 | 结果 |
| :--- | :--- |
| 隐藏 → 触摸唤醒（左边缘热区 x=15,y=810） | 左带差异 **4864 px**，bbox `(3,714)-(28,905)` |
| 隐藏 → 中屏触摸唤醒（x=1440,y=1600） | 同上，**4864 px**，bbox `(3,714)-(28,905)`；与左边缘唤醒截图逐字节相同 |
| 唤醒后超过自动隐藏时间 | 与隐藏基线 **SHA-256 完全相同**（`1170CDF2…64F7`） |
| 关闭开关后触摸左边缘热区 | 前后截图 **逐字节完全相同**，无杠、无拦截、未发生返回 |

交互语义（两次点击链）：第一次触摸只唤醒（`topResumedActivity` 不变），第二次点击同一位置执行返回（`SettingActivity` → `HomeActivity`）。
`HomeActivity` 与 `SettingActivity` 上返回杠形状与数值一致（同为 4864 px、同一 bbox）。

### 4.4 交付 APK 内容核对

| 检查 | 结果 |
| :--- | :--- |
| `aapt2 dump badging` | package / versionCode 8 / versionName 1.0.0-car.7 / label TVBox / launcher HomeActivity |
| `apksigner verify` | v1、v2、v3 全部通过 |
| dex 字面量（不受 R8 混淆影响） | `BackBar`、`inapp_back`、`SGSR`、`GlEnhance` 存在于 `classes.dex`；`GlEnhance` 亦在 `classes2.dex` |
| 调试探针排除 | `FeatureProbe`、`probe.properties` 在 release dex 中**不存在** |
| 权限 | 无 overlay / 无障碍相关权限 |
| 安装与运行 | 安装后 APK SHA 与交付包一致；冷启动进 `HomeActivity`；崩溃缓冲区无本应用 `FATAL EXCEPTION`（仅有 `uiautomator` 自身退出时的 SIGSEGV，属测试工具，与应用无关） |

> release 开启了 `minifyEnabled true`，类名被 R8 重命名，因此按类名检索会落空；上表改用能保留的字面量核对。`player` 模块的 `GlEnhanceRenderView` 仍以原名存在，说明其 keep 规则生效。

### 4.5 播放增强运行态（debug 构建，同源代码）

通过 `app/src/debug` 探针在真机运行态验证（release APK 不含探针）：

| 场景 | 结果 |
| :--- | :--- |
| EXO + SR + 平滑 + 音效1 | `GlEnhanceRenderView`，`srActive=true`，`interpActive=true`，sourceFps≈30，outputFps≈59.6；PixelCopy 四象限 `tl=255,0,0 tr=0,129,0 bl=0,0,255 br=254,255,255` |
| EXO 全关 | `TextureRenderView`，无 GL，播放正常 |
| EXO + 滤镜7 + SR | 白色测试图 `br` 由 255,255,255 → 255,255,243（蓝色下降），滤镜生效 |
| IJK + 滤镜9 + SR + 平滑 + 音效6 | `GlEnhanceRenderView`，audioActive=true（`动漫·标准`），outputFps=60.0 |
| IJK 全关 | identity 渲染，sr/interp 均 false，像素正确 |
| 音效 sweep（模式 1–8） | 全部 `active=true`；模式 0 `active=false`（正确释放） |
| 全程 | 无 `FATAL EXCEPTION` |

## 5. 构建与复现

```powershell
$env:JAVA_HOME      = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME = 'D:\gradle-cache'
# 该环境 wrapper 的 URL 校验不通过，直接使用已缓存的 Gradle
& 'D:\gradle-cache\wrapper\dists\gradle-7.5-bin\1bmvna2xxjhfv7365tgdvoqbr\gradle-7.5\bin\gradle.bat' :app:assembleJavaRelease
# 产物在 app/build/outputs/apk/java/release/，随后：
#   build-tools\34.0.0\zipalign.exe -p -f 4 <in.apk> <aligned.apk>
#   build-tools\34.0.0\apksigner.bat sign --ks %USERPROFILE%\.android\debug.keystore --ks-key-alias androiddebugkey <aligned.apk>
#   （口令使用本机 Android 调试密钥库既有口令，不在此文件记录）
```

设备批次（已结束）：`mumu-pool.py` owner `tvbox-original-handover-5748b969e2c7`、index 1，已 `release`（实例关机、主 MuMu 程序保留、静音已恢复）。

## 6. 已知边界（未验证项，勿当作已验证）

1. **release APK 播放增强未经运行态验证**：R8 会重命名/内联，运行态插桩只在 debug 构建上完成；release 上验证的是身份、冷启动、设置页与返回杠。若需 release 运行态证据，需要可用的点播源或把探针以 `src/release` 形式临时注入。
2. **未做真车验收**：验证环境是 MuMu（API 35），非零跑 B01 实车（SA8295P / Android 12L / API 32）；未验证 OEM 安装器过滤、真实 HDR 输出与物理音质。
3. **远端源可用性未纳入本轮范围**：点播/直播源与聚合搜索依赖外部服务，本轮未做站点连通性验证，不能表述为「各站点均已通过」。
4. **签名是测试证书**：可安装可运行，但不构成可发布产物的签名合规结论。

---

## 7. r2 修订（按用户反馈重做）

### 7.1 返回组件改为移植 car-ux-redesign-3k-fps 的方案

上一版自写的 backbar（单条竖向杠）已删除，改为逐字移植该分支的实现：

| 文件 | 作用 |
| :--- | :--- |
| car/InAppBackButton.java | 入口：DecorView 挂载、生命周期、返回分发、配置读写 |
| car/InAppBackHostLayout.java | 全屏宿主，安全边距、token 缩放、显隐与置顶 |
| car/EdgePullBackView.java | 边缘导引小棍 + 贝塞尔拉出抽屉（默认模式） |
| car/FloatingBackBallView.java | 磁吸悬浮球（拖拽/弹簧贴边/闲置半隐藏） |
| car/BackNavPolicy.java | 纯逻辑：命中、阈值、阻尼、磁吸、几何 |

配套：HawkConfig.BACK_* 全量配置键、dimens.xml 的 car_top/bottom/side_padding、
BaseActivity 的 attach/wake/release 与 onBackButtonStepUp()/onBackButtonAtRoot() 钩子、
HomeActivity 的两处覆写。设置页新增「应用内返回」开关与「返回模式」循环项。

### 7.2 播放页遥测

- 左上角分辨率：宽 X 高；超分运行时追加 → 超分 <渲染视口宽> X <高>（视口尺寸来自 GL 渲染视图，未生效时不追加）。
- 移除帧率行里的 SR 生效 / 平滑 生效 提示，只保留 片源 … fps · 输出 … fps。
- 右上角下载速率改为字节口径：B/s、KB/s、MB/s（新增 PlayerHelper.getDisplaySpeedBytes），不再用 Mbps。
- 右上角新增系统负载徽标：CPU 37% · 内存 62%；CPU 取 /proc/stat 相邻采样差值（整机口径），
  内存取 ActivityManager.MemoryInfo；任一项 ≥90% 时文字转红。

### 7.3 本轮验证

| 项 | 结果 |
| :--- | :--- |
| Host 测试 | BackNavPolicyHostTest PASS: 738 项返回导航策略检查；另 3 个既有测试仍全绿 |
| 编译 | :app:assembleJavaRelease BUILD SUCCESSFUL |
| 显示门禁 | CAR_DISPLAY_PASS（2880×1620 / d160 / rotation lock / input 几何） |
| 挂载 | 运行日志确认 InAppBackHostLayout 已创建，宿主 2880×1620，tokenScale 3.375，球体 243px，位置 (78,695) |
| 渲染 | 唤醒后截屏在 (160–280, 720–880) 检出青色描边像素，确认球体实际绘制 |
| 命中/返回 | 点击球体触发 performBack()：首页根状态弹出「已在首页」Toast（Window{… Toast}，owner uid 为本应用） |
| 设置 | tvBackBarOpen=开启，tvBackMode 可在 边缘拉出 / 悬浮球 间切换并即时生效 |

### 7.4 本轮未完成 / 需下一轮确认

1. release APK 上未复现点击球体的 Toast：debug 构建（同源代码、未混淆）已复现；release 上两次点击未触发，
   尚不能断定是 R8 影响还是球体处于闲置半隐藏位置导致未命中，需下一轮用 release 复测。
2. 边缘拉出手势未在横屏下实测成功：本轮早期设备被重置为竖屏（1620×2880, ROTATION_0）导致几何错位，
   纠正横屏后仅完成悬浮球路径验证；边缘拉出手势需补测。
3. 播放页四项 UI 未做真机视觉验收：依赖真实播放（需要可用点播源或把播放探针接入 VodController），
   本轮只完成代码与设备端格式前提核对（/proc/stat 字段结构、ActivityManager 可用）。
4. 交付 APK（r2）SHA-256：F2DACE98971A5B00566B6FE97B54023D4133836E8383046A28F1A3E11E231460，33,334,877 bytes。

---

## 8. r3：Python 数据源（本次修复）

### 8.1 根因

此前交付的是 java 风味。该风味里 src/normal/java/.../pyLoader.java 是显式桩：
日志写着 java flavor: Python is not supported，getSpider() 直接返回 SpiderNull，
所以 py 源只会静默变空，只有 js 源可用。

### 8.2 修复

1. 改打 :app:assemblePythonRelease（python 风味）：含 Chaquopy 12.0.1 的 Python 3.8
   运行时（arm64-v8a、armeabi-v7a）、stdlib 镜像、真实 pyLoader。
2. pyramid/build.gradle：buildPython 由写死的 D:/Programs/Python/Python38/python.exe
   改为 -PbuildPython / TVBOX_BUILD_PYTHON / python3.8 依次回退。
3. 锁定 Chaquopy 的 Android wheel 版本 lxml==4.6.3、ujson==1.35、pycryptodome==3.9.4；
   不锁定时旧 pip 会解析新版 sdist 的 pyproject.toml 而失败。
4. 补齐缺失的 Python 模块 pyramid/src/python/tvbox_http.py（主分支有、白盒没有）。
   没有它时 app.py 第 12 行 from tvbox_http import install_fallback 抛
   ModuleNotFoundError，Python 侧整体起不来 —— 这是 py 源不可用的直接原因。
5. 同步主分支的 pyLoader（pendingConfig 延迟下发）、PythonHttp（base64 体/超时/重定向/
   header_pairs/二进制响应）、app.py（改为调用 tvbox_http）。

### 8.3 端到端证据（真实 py 源）

用 debug 探针（app/src/debug，仅调试构建）经生产路径 pyLoader.getSpider() 加载本机
最小 py 源，日志：

```
PyLoader: echo-getSpider url: http://127.0.0.1:8899/spider.py
python.stdout: plugin downloaded spider.py
PyLoader: echo-init path: /data/user/0/com.github.tvbox.osc.jun/cache/py/spider.py
PythonLoader: pytest: 下載插件成功！
PyLoader: echo-getSpider 加载spider: pytest
PyProbe : spider=com.undcover.freedom.pyramid.PythonSpider
PythonLoader: homeContent-pytest ... {"class": [{"type_id": "1", "type_name": "PY源"}], "list": [{"vod_id": "1", "vod_name": "PY源可用", "vod_pic": ""}]}
PyProbe : homeContent={"class": [{"type_id": "1", "type_name": "PY源"}], "list": [{"vod_id": "1", "vod_name": "PY源可用", "vod_pic": ""}]}
```

即：下载 .py → Chaquopy 拉起 Python → PythonSpider 实例化 → Python 侧 homeContent
执行并返回 JSON。同一轮里播放探针仍显示 srActive=true interpActive=true outFps≈59
（640×360 源，viewport 2880×1620），说明播放增强未被影响。

### 8.4 r3 产物

| 项 | 值 |
| :--- | :--- |
| 文件 | TVBox-Original-Feature-1.0.0-car.7-python-r2.apk |
| 大小 | 52,659,997 bytes |
| SHA-256 | 1C117C4A3358ABB629B4D89C60274BEC2182473A7A19EB05A4126CC5719FE80B |
| 包内容 | lib/{arm64-v8a,armeabi-v7a}/libpython3.8.so、assets/chaquopy/app.imy（17,978 B）、stdlib 镜像 |
| R8 检查 | PythonHttp / PythonLoader / PythonSpider / pyLoader 均以原名保留（Python→Java 桥安全） |
| 设备检查 | 安装后 primaryCpuAbi=arm64-v8a，/data/app/.../lib/arm64/libpython3.8.so 就位，冷启动进 HomeActivity 无崩溃 |

### 8.5 仍需注意

- 真车（arm64 原生）表现应优于模拟器：MuMu 是 x86_64 + ARM 转译，Python 走转译执行。
- 本轮只验证了「py 源能加载并执行」，未逐个验证第三方 py 源的业务逻辑与站点连通性。

---

## 9. r3（本机 UX 修复）：小窗放大 / 返回杠可配置 / CPU 缺省不显示

### 9.1 详情页小窗单击 = 放大

- VodController 新增 OnPreviewTapListener：previewMode 下 onSingleTapConfirmed
  不再 toggleShowState() 弹控制条，而是回调宿主。
- PlayFragment.setOnPreviewTapListener() 转发；DetailActivity 在设置预览模式的两处
  接到 enterFullPreview()。小窗里那排过小的按钮不再出现，点一下直接全屏。

### 9.2 返回杠

- 点按即返回：BackNavPolicy.EdgeGesture.end() 把「按下未移动即抬起」(WAITING) 视为返回，
  与拖到 48dp 阈值等效；纵向调位、斜向/向外滑动、CANCEL 仍不返回。
- 热区放大：默认 28x120dp → 32x140dp，并随杠尺寸自适应（宽 x3、高 +56dp）。
- 落点修正：edge.syncBounds() 不再额外加 waveHeight/2，导引小棍回到安全区 55% 处
  （此前被整体下移半个波形高度，视觉与点击区一起偏下）。
- 外观可配置（设置页 4 行）：

| 设置项 | 取值 |
| :--- | :--- |
| 返回杠大小 | 细 6x64 / 中 12x96 / 粗 18x140 dp |
| 返回杠颜色 | 青 #38BDF8 / 白 / 琥珀 #FBBF24 / 绿 #34D399 / 粉 #F472B6 |
| 返回杠透明度 | 40% / 60% / 80% / 100%（默认 55%） |
| 自动隐藏 | 4 秒 / 6 秒 / 8 秒 / 常驻 |

新增键 BACK_BAR_WIDTH_DP、BACK_BAR_HEIGHT_DP、BACK_BAR_COLOR、BACK_BAR_ALPHA；
EdgePullBackView 与 FloatingBackBallView 均按这些值绘制，改完立即 refresh() 生效。

### 9.3 性能徽标

- SystemUsage.describeAvailable()：取不到的指标不再输出 --；CPU 缺失只显示内存，
  两项都取不到则隐藏整个徽标（可见性跟随下载速率徽标）。

### 9.4 验证状态

| 项 | 结果 |
| :--- | :--- |
| Host 测试 | BackNavPolicyHostTest PASS 740 项（新热区/点按语义），其余 3 个测试全绿 |
| 编译 | :app:assemblePythonRelease BUILD SUCCESSFUL |
| 产物 | TVBox-Original-Feature-1.0.0-car.7-python-r3.apk，52,664,093 bytes，SHA-256 8E9720CFAFBE1E9F9E21CE6894D6FCB27547004B1B761DD1561D78DB0D73F82A |

未完成：本轮改动未做真机点测。设备池两次 acquire 均返回
no instance could be leased（vm0 被其他会话的 com.carweb.tvb 占用）。需要一次实机确认：
设置页 4 个新行可循环、导引小棍点按返回、小窗单击放大、CPU 徽标按预期隐藏。

---

## 10. r4：返回组件设置收口 + 实机复测

### 10.1 设置结构

原来 6 行（开关/模式/大小/颜色/透明度/隐藏）压成**一行**「返回组件」，右侧显示当前模式。
点开是 `BackBarSettingsDialog`（BaseDialog 子类，纯代码布局，TV 可聚焦）：
开关、返回模式、返回杠大小、返回杠颜色、返回杠透明度、自动隐藏，逐行点击循环、即时生效。

### 10.2 实机复测结果（vm1，2880x1620 / d160）

| 项 | 结果 |
| :--- | :--- |
| 设置页结构 | 只有一行「返回组件 / 边缘拉出」 |
| 弹窗 | 六项齐全；连点后变 粗 / 白 / 80% / 6 秒，并持久化 |
| 外观生效 | 截图左边缘出现 y=144..614、宽约 60px、颜色 (204,205,207) 的竖条 = 18dp×140dp / 白 / 80% |
| 边缘拉动返回 | swipe (3,566)->(700,566)，日志 `UP back=true`，SettingActivity -> HomeActivity |
| 上下拖动 | 长按 400ms 进入 REPOSITIONING，松手不误返回 |

### 10.3 环境坑（已定位）

模拟器偶发把应用布局成 1620x2880 竖屏（显示却是横屏），此时导引小棍落在 y≈1400，
按横屏预期去拉必然"拉不动"。锁定横屏并确认 `dumpsys activity top` 的
`w2880dp h1620dp land` 后，拉动/拖动/点按全部正常。真车不会出现这种错位。

### 10.4 仍未验证

详情页小窗单击放大：只完成代码接线（VodController.OnPreviewTapListener ->
PlayFragment -> DetailActivity.enterFullPreview），本机没有可播源，未能真机点测。

产物：`TVBox-Original-Feature-1.0.0-car.7-python-r4.apk`，52,664,093 bytes，
SHA-256 `D5A76353667D463D09C68CA80A32680DC75A1D1EB8834D8D0A075F2BE66A022B`。

---

## 11. r5：拖动范围修复 / 设置实时生效 / 分支收敛

### 11.1 导引小棍只能拖到中间 —— 是 bug

`syncGeometry()` 里计算可停范围时把「波形高度(160dp)」当成底部预留：

```
availableY = h - waveHeight      // 1620 - 540 = 1080
maxY       = availableY - bottom // 1080 - 346 = 734
```

所以中心点只能在 [252, 734] 之间移动（15%–45% 高度），往下自然"卡在中间"。
改为整段安全区：

```
minY = top            // 252
maxY = h - bottom     // 1274  (15%–78%)
```

波形半高只有 270px，在最大位置 1274+270=1544 仍在屏内，不会画到屏幕外。

### 11.2 外观设置"没生效"的处理

代码路径本身是通的（`setBarXxx → put() → refresh(activity) → host.refresh() → configure + invalidate`），
本轮再补两点：

1. **悬浮球也跟随「返回杠大小」**：原来球只有 `isLarge` 两档，改粗细对球无效；
   现在 细/中/粗 → 48/56/68dp。
2. **二次刷新**：弹窗刚打开时宿主可能还没测量完，第一次 configure 会拿到旧尺寸；
   改为 `decorView.post(() -> refresh(activity))`，下一帧再刷一次。

### 11.3 分支收敛（已执行）

| 项 | 值 |
| :--- | :--- |
| 旧 main | `494b989d`（已丢弃） |
| 新 main | `475a325f`（本分支内容，force 推送） |
| 保留分支 | `main` + `feature/car-ux-redesign-3k-fps`（远端仅此 2 个） |
| 已删除 | `feature/original-app-feature-handover`、`feature/car-ux-redesign-3k`、`feature/upstream-feature-pack` |

### 11.4 订阅源（文档记录）

历史文档里记录的两个订阅：

- `https://clun.top/box.json`（当日 97 站 / 44 个 PY 站）
- `https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json`

本轮**未能实际用它们跑通**：分到的 vm0 上另一个会话的 `com.carweb.tvb` 反复抢占前台，
且该实例的配置是"车机界面验收（本地素材）"（首页只有素材网格、没有设置入口），
无法在应用内改订阅，也无法稳定点测。下一轮换干净实例再补。

### 11.5 产物

`TVBox-Original-Feature-1.0.0-car.7-python-r5.apk`，52,664,093 bytes，
SHA-256 `2789E0A60C585BDECAB28F7433F8B9BCCB21EEDB2ACE4234B1373C39C717C9B1`。
已核对 dex 含「返回组件 / 返回杠大小 / 返回杠颜色」字面量，确认新弹窗在包内。
