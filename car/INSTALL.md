# INSTALL — TVBoxOS 1.0.0-car.5 Python 通用版

## 环境要求

- Android 5.0+、ARM 设备，APK 同时包含 `armeabi-v7a` 与 `arm64-v8a`。
- 无需 Root、无障碍授权或系统悬浮窗权限；应用内返回按钮是 Activity 窗口内的 View。
- 推荐车机画布：横屏 2880×1620（17.3″）。
- 包名和应用名必须保持 `com.github.tvbox.osc.jun` / `TVBox`，Spider JAR 会校验宿主身份。

## 安装

从 [v1.0.0-car.5 Release](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.5) 下载 `TVBox-1.0.0-car.5-python-signed.apk`，确认 SHA-256：

```text
6F43817EB9C4E2A97C8E30C190826244A2333E14CD46D064964F91AE45DF0C3B
```

同一测试签名的 car.4 可直接覆盖升级：

```powershell
adb install -r .\TVBox-1.0.0-car.5-python-signed.apk
```

如果设备中的同包名版本使用其他签名，先卸载旧包再安装：

```powershell
adb uninstall com.github.tvbox.osc.jun
adb install .\TVBox-1.0.0-car.5-python-signed.apk
```

## 配置订阅

首次进入设置 → **配置地址**，输入订阅 URL 后点“确定”。原订阅回归地址为：

```text
https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/123/xiaosa/api.json?v=3
```

car.5 继续支持 Java/JAR、QuickJS 和 Python Spider。Python 版不是把整个 Android 应用改写成 Python，而是把原工程的 Python flavor 构建进 APK；Java 页面和播放器仍然运行。

## 选择画面预设

设置 → **画面预设**，从列表选择以下任一档位；选择会持久化，并在下一次播放创建渲染视图时生效：

| ID | 名称 | 说明 |
|---|---|---|
| 0 | 原画 | 保留原始画面 |
| 1 | 臻彩 | 增强色彩与层次 |
| 2 | 自然 | 轻调色，接近原片 |
| 3 | 影院 | 温润色调，强化明暗 |
| 4 | 鲜艳 | 浓郁色彩，突出画面 |
| 5 | 柔和 | 降低反差，柔化色彩 |
| 6 | 明亮 | 提亮偏暗片源 |
| 7 | 暖色 | 暖色温，减少蓝色 |

列表可以上下滑动，当前项会高亮。旧版本的 `video_enhance=false/0` 迁移到原画，`true/1` 迁移到臻彩，损坏值回退原画。HDR/杜比视界按保护策略使用恒等调色；car.5 本轮没有 HDR 片源验收。

## 音效与返回按钮

- 设置 → **音效**：关闭、影院、重低音、3D环绕、HiFi、人声。内部 EXO/IJK 播放器会把 Equalizer、BassBoost、Virtualizer、LoudnessEnhancer 绑定到当前音频会话。
- 设置 → **应用内返回按钮**：窗口内返回按钮默认开启；默认闲置 4 秒隐藏，触摸后唤醒，可设 4/6/8 秒或常显。首页根级点击只提示，不退出应用；详情/分类/播放页按层级返回。

## 本轮验收结果

MuMu 6.8.4.0 / Android 15 / 2880×1620 上实际运行 Python 3.8.13。Python fixture 的 8 项依赖、HTTP、分类、卡片、详情和合成片播放均通过；QuickJS fixture 的主页、分类和详情通过；8 档画面均实际选择并播放，增强档均有 GL 参数和第 100 帧日志，截图像素比较通过。原订阅主页、分类和聚合搜索也已回归。

## 已知边界

- 本轮运行实测发生在 MuMu 的 ARM64 翻译环境；ARM32 ABI 已打包并经过 APK 内容检查，未在真实 ARM32 设备上单独运行。
- 目标真实车机的 OEM 全屏策略、真实 DPI、方控焦点和触摸驱动未实测；模拟器 2880×1620 不能替代车机验收。
- HDR/DoVi 自动关闭调色和 IJK 播放路径未在本轮重复运行；直播页和外部播放器不在当前音效会话链路范围内。
- 外部订阅源受网络、验证页和源自身解析影响。本轮 `潜伏6` 搜索为 21 条结果、78/78 源完成、3 个超时，日志也记录了部分验证页/解析错误，不能据此承诺所有外部站点永久可用。
- 应用弹窗是独立窗口，应用内返回按钮位于其下层；配置地址和预设选择使用弹窗自带按钮或系统返回键关闭。
