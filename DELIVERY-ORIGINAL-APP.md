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
