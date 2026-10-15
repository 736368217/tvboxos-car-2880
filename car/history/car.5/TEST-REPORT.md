# TEST-REPORT — TVBoxOS 1.0.0-car.5 Python 通用版

验收日期：2026-10-15。测试对象 `TVBox-1.0.0-car.5-python-signed.apk`，52,627,205 字节，SHA-256 `6F43817EB9C4E2A97C8E30C190826244A2333E14CD46D064964F91AE45DF0C3B`。

## 1. 环境与证据

- MuMu **6.8.4.0** / Android 15；横屏逻辑画布 2880×1620，density 240。
- 原生 ABI 列表包含 x86_64 与 arm64-v8a；本轮 Python 与 QuickJS 实际经 ARM64 翻译运行。ARM32 仅完成打包核验。
- 独占租用实例 1，未操作其他实例；整批静音，结束后 release，仅停止租用实例，MuMu 主程序保留。
- 无障碍：`enabled_accessibility_services=null`，`accessibility_enabled=0`。
- 静音：`volume_music=0`，`sound_effects_enabled=0`，并启用 MuMu 主机静音。
- 显式重启前 PID **4480**，重启后至结束 PID **16125**。最终 PID 日志未检出 `FATAL EXCEPTION` 或 EXO `Player is accessed on the wrong thread`。

本轮原生 XML、8 档截图、运行日志摘要、音效会话和像素数据保存在 [`evidence/car.5/`](evidence/car.5/)。第三方站点请求的大段返回内容未复制进仓库。

MuMu `sh` 调用 Android Java shell 工具需要进入 `system_server` 的 mount namespace 并导入 BOOTCLASSPATH 等变量；步骤由共享目录 LF 脚本经插件执行。该系统的 UiAutomator 会在写出 XML 后发生工具进程退出/139；每次删除旧 XML、检查新文件并核对应用 PID。一次外部详情采集报 `could not get idle state`，未把旧 XML 当新证据。

## 2. 构建与 APK 核验

| 项目 | 实际结果 |
|---|---|
| Java 8 host 预设测试 | 通过：8 档稳定 ID、参数不同、原画恒等、旧 Boolean/0/1、字符串与损坏值、列表不可变 |
| Java flavor 编译检查 | `:app:compileJavaReleaseJavaWithJavac` 通过 |
| Python 通用构建 | `:app:assemblePythonRelease` exit 0，约 155 秒 |
| APK 身份 | `com.github.tvbox.osc.jun` / `TVBox` / versionCode 6 / versionName 1.0.0-car.5 |
| native ABI | `armeabi-v7a` + `arm64-v8a` |
| Python / QuickJS 库 | 两个 ABI 均有 `libpython3.8.so`、`libchaquopy_java.so`、`libquickjs-android-wrapper.so` |
| 签名 | v1/v2/v3 true；沿用测试证书 |
| zipalign | 4 字节对齐通过 |
| 升级安装 | car.4 后覆盖安装 car.5 成功 |

旧设置迁移的**host 测试已通过**；本轮没有在升级前通过 UI 写入旧臻彩 ON，再覆盖升级并读取，因而不声称设备上的旧值迁移完整实测。

## 3. Python 与 QuickJS 实际执行

本地验收配置只用于测试，未写入 APK 默认订阅。配置及 fixture 在 `tests/fixtures/`，经应用自带 `:9978/file/TVBoxCar5/` 服务读取。

### Python

真实运行时日志：

```text
CAR5_PY_RUNTIME version=3.8.13 dependencies=8 network=OK
CAR5_PY_PLAY url=http://127.0.0.1:9978/file/TVBoxCar5/lowsat.mp4
```

`car5_python_probe.py` 实际执行并断言：lxml XPath、ujson、pyquery、requests 本地 HTTP、jsonpath、cachetools、Crypto SHA256、BeautifulSoup。原生节点显示 `Python验收分类`、`Python运行成功 3.8.13` 和 `PYTHON-EXECUTED-8-DEPS`；进入分类→详情→播放器，实际播放 640×360 SDR 合成片。

### QuickJS

原生节点显示 `JS验收分类`、`QuickJS运行成功`、`QUICKJS-EXECUTED`，随后详情页显示 `来源：QuickJS兼容验收`、本地播放地址；当前 PID 16125 日志记录 `JSLoader … car5_js` 和 GL `drawFrame #100`。这证明模块已运行并参与分类、详情和播放，而非仅检查 APK 中存在 QuickJS 库。

证据：`py-fixed.xml`、`py-category.xml`、`py-detail.xml`、`js-home.xml`、`js-category.xml`、`js-detail.xml`、`runtime-excerpts.txt`。

## 4. 8 档画面预设与音效

通过设置 UI 逐项选择：原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色；明亮和暖色通过列表滚动到达。每档均从 Python 分类卡片进入详情播放同一静态低饱和片，ID 1–7 均有实际参数与第 100 帧日志。原画走默认渲染路径，无需 GL 调色日志。

| ID | 名称 | 实际 sat / con / bri | RGB |
|---:|---|---|---|
| 0 | 原画 | 1 / 1 / 0（默认渲染） | 1 / 1 / 1 |
| 1 | 臻彩 | 1.35 / 1.12 / 0.03 | 1 / 1 / 1 |
| 2 | 自然 | 1.08 / 1.04 / 0.01 | 1 / 1 / 1 |
| 3 | 影院 | 1.08 / 1.16 / -0.015 | 1.03 / 1 / 0.97 |
| 4 | 鲜艳 | 1.50 / 1.16 / 0.015 | 1 / 1 / 1 |
| 5 | 柔和 | 0.90 / 0.94 / 0.02 | 1.015 / 1 / 0.985 |
| 6 | 明亮 | 1.12 / 1.02 / 0.08 | 1 / 1 / 1 |
| 7 | 暖色 | 1.06 / 1.04 / 0.02 | 1.06 / 1 / 0.92 |

本轮继续验证 car.4 修复：`player surface bound (main thread)`、缓冲区/视频尺寸 640×360、连续绘帧，不再出现 GL 线程调用 EXO `setSurface()` 的异常。

### 像素比较

640×360 静态低饱和片循环为 32 秒，采集 2880×1620 PNG。视频 ROI `[250,180,1050,650]`；UI ROI `[1250,100,2700,700]`。HSV 的 S/V 采用 Pillow 0–255 标度，视频平均绝对差异采用 RGB 0–255 标度。

| 名称 | 平均 S | 平均 V | 视频相对原画平均绝对差异 | 抽样 UI 差异 |
|---|---:|---:|---:|---:|
| 原画 | 111.825 | 111.175 | 0.000 | 0.000 |
| 臻彩 | 125.800 | 123.346 | 10.650 | 0.000 |
| 自然 | 119.378 | 114.317 | 2.694 | 0.000 |
| 影院 | 123.915 | 109.835 | 8.354 | 0.000 |
| 鲜艳 | 139.031 | 123.712 | 14.908 | 0.000 |
| 柔和 | 80.764 | 116.017 | 7.915 | 0.000 |
| 明亮 | 91.217 | 131.990 | 17.950 | 0.000 |
| 暖色 | 121.243 | 118.178 | 5.219 | 0.000 |

7 个增强档均产生可测量的视频变化；鲜艳平均饱和度提高、明亮平均亮度提高；抽样 UI 区域逐像素一致。指标只描述该测试片/ROI，不等同于每种影片的观感评价。数据与分析脚本：`pixel-comparison.json`、`tests/compare_presets.py`。

### 持久化与音效

显式关闭再启动后，设置页仍显示 `臻彩`、`影院`；证据 `persistence-settings.xml`。影院音效在当前 session **89** 挂载四效果：Equalizer、Dynamic Bass Boost、Loudness Enhancer、Virtualizer，见 `audio-session-89.txt`。只对影院做了这次效果器挂载回归，没有声称逐档做听感或频响测量。

应用内返回按钮多次用于详情→分类、分类→主页；无障碍未开启。既有逐页面完整返回/空闲隐藏矩阵属于 car.1–car.4 验收，本轮没有重新覆盖每个页面。

## 5. 原订阅 JAR 回归

原地址保持不变：

```text
https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/123/xiaosa/api.json?v=3
```

通过设置 → 配置地址 → 确认导入，最终恢复为此订阅。

```text
JarLoader: echo-load Init.init success
JarLoader: load success key=main
JarLoader: getSpider success key=main豆瓣 / 热播影视 / 一起影视 / 咕噜 / 布布 …
```

- 首页分类和真实推荐卡片可见。
- `热门电影` 分类返回希望、潜伏6、人为何要写情书、火柴盒大电影等卡片。
- 以 `潜伏6` 发起聚合，最终 UI 为 **搜索完成 21 / 源 78/78 · 超时 3**，有咕噜、剧圈、天堂、一起等结果。
- 部分外部源返回验证页、空响应或解析异常。没有逐个验证全部站点的分类、详情或播放。
- 点击一个外部详情后应用仍存活，但 UiAutomator 报无法取得 idle，故本轮未将该外部详情/线路记为通过。合成片播放闭环由 Python/QuickJS 验收源证明。

car.3 修复解决的是白壳保护型 JAR 误判和初始化不回退；不等于所有外部站点永久可用。此前报告中的“全部站点可用”表述范围过大，本报告以可观察结果为准。

## 6. 未覆盖与独立问题

- 目标真实车机的 OEM 全屏、真实 DPI、方控/D-Pad 焦点、触摸驱动。
- ARM32 独立运行、Android 旧版本兼容性。
- HDR/DoVi 自动关闭调色、IJK 同路径完整回归；直播页和外部播放器不在本音效链范围内。
- 旧设置 ON→覆盖升级→设备读取的完整迁移；只有 host 迁移测试与 car.5 自身重启持久化通过。
- 全局包名台账已补登记 TVBoxOS 原身份及所有现有路径；最新检查被另一任务新增的 `flclash-carhd/ref/FlClash-0.9.0-android-arm64-v8a.apk` 未登记挡住。本应用无未登记路径，该独立文件未改动。

Python 通用 APK、8 档预设和本轮回归证据已具备交付条件。真实车机与上述范围继续列为未实测，不冒充通过。
