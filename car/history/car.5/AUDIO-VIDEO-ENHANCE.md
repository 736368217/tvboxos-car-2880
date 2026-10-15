# 音频与视频增强方案说明（car.5）

本文对应 `TVBoxOS 1.0.0-car.5` Python 通用版。car.5 把原来的二元“臻彩开关”扩展为 8 个稳定编号的 SDR 画面预设，同时保留 car.2–car.4 的 6 档音效、HDR 保护和主线程 Surface 修复。

## 1. 能力总览

| 能力 | 设置入口 | 生效时机 |
|---|---|---|
| 音效 | 关闭 / 影院 / 重低音 / 3D环绕 / HiFi / 人声 | 当前内部 EXO/IJK 播放会话重新绑定；新会话自动绑定 |
| 画面 | 原画 / 臻彩 / 自然 / 影院 / 鲜艳 / 柔和 / 明亮 / 暖色 | 下一次播放创建渲染视图时 |
| HDR 保护 | 无独立开关 | HDR/DoVi 强制使用恒等调色；本轮未用 HDR 片源实测 |

Python flavor 不会替换 Android 页面或播放器。APK 仍保留 Java、JAR、QuickJS 和原有 TVBox 业务链，只是 Python Spider 使用 Chaquopy 运行时。

## 2. 音效链

相关源码：

- `app/src/main/java/com/github/tvbox/osc/car/AudioEffectController.java`
- `app/src/main/java/com/github/tvbox/osc/car/CarEnhance.java`
- `app/src/main/java/com/github/tvbox/osc/player/MyVideoView.java`
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/ModelSettingFragment.java`

播放准备完成后，`MyVideoView.prepared()` 读取当前播放器的音频 session：

```text
EXO/IJK audio session
        |
        v
CarEnhance.applyAudio()
        |
        +-- Equalizer
        +-- BassBoost
        +-- Virtualizer
        +-- LoudnessEnhancer
        |
        v
Android AudioFlinger / 当前 AudioTrack
```

6 档模式 ID：

```text
0 关闭
1 影院
2 重低音
3 3D环绕
4 HiFi
5 人声
```

### 音效参数（沿用 car.4）

EQ 使用 13 个参考中心频率（Hz）：`30, 60, 100, 150, 200, 250, 500, 1000, 2000, 5000, 8000, 16000, 32000`。设备频段映射到最近参考频点，dB 乘 100 转换为 Android millibel，并按设备 `getBandLevelRange()` 裁剪。

| 模式 | EQ 曲线（依次对应 30…32000 Hz，dB） | BassBoost | Virtualizer | LoudnessEnhancer |
|---|---|---:|---:|---:|
| 关闭 | 释放全部效果 | 0 | 0 | 0 dB |
| 影院 | `7, 8, 6, 3, 2, 1, 0, 0, 1, 2, 3, 3, 2` | 900 | 850 | +900 mB |
| 重低音 | `6, 7, 5, 4, 3, 2, 0, -1, -1, 0, 0, 0, 0` | 700 | 150 | +300 mB |
| 3D环绕 | `-2, -1, 0, 1, 2, 3, 2, 1, 2, 3, 4, 4, 3` | 450 | 1000 | +450 mB |
| HiFi | `1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1` | 250 | 300 | +400 mB |
| 人声 | `-4, -3, -1.5, 0, 1, 2, 3, 4, 3, 2, 1, 0, -1` | 0 | 50 | +750 mB |

BassBoost / Virtualizer 的 Android 强度范围为 0–1000；LoudnessEnhancer 的 `900 mB` 约为 +9 dB。音效偏好键 `hifi_audio`，默认 0。设置页按 0→1→2→3→4→5→0 循环，当前 Activity 若没有内部播放器，会在下一次 `prepared()` 绑定。

调参常量：`CURVE_*`、`CURVE_FREQS`、`BASS_STRENGTH`、`VIRT_STRENGTH`、`LOUD_STRENGTH`。控制增益并检查削波；不同音频 HAL 的支持程度和实际听感不同。


效果链绑定当前会话，不修改全局系统音量。关闭或 `audioSessionId <= 0` 时释放效果。新建或切换效果链使用 16 × 32ms 的渐入，名义时长约 512ms。若设备不支持 BassBoost/Virtualizer 强度控制，则只跳过该效果，不阻塞播放。

支持范围：内置 EXO 和 IJK；系统播放器、外部 MX/Kodi/VLC、当前直播基类 `VideoView` 不在这条绑定链路中。

## 3. 8 档画面预设

源码：

- `player/src/main/java/xyz/doikki/videoplayer/render/VideoEnhancePreset.java`
- `player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderView.java`
- `player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderViewFactory.java`
- `app/src/main/java/com/github/tvbox/osc/car/CarEnhance.java`
- `app/src/main/java/com/github/tvbox/osc/util/PlayerHelper.java`
- `app/src/main/java/com/github/tvbox/osc/player/MyVideoView.java`

所有参数是 immutable snapshot，设置页和 GL renderer 共用同一张表：

| ID | 名称 | saturation | contrast | brightness | RGB 增益 |
|---:|---|---:|---:|---:|---|
| 0 | 原画 | 1.00 | 1.00 | 0.000 | 1.000 / 1.000 / 1.000 |
| 1 | 臻彩 | 1.35 | 1.12 | +0.030 | 1.000 / 1.000 / 1.000 |
| 2 | 自然 | 1.08 | 1.04 | +0.010 | 1.000 / 1.000 / 1.000 |
| 3 | 影院 | 1.08 | 1.16 | -0.015 | 1.030 / 1.000 / 0.970 |
| 4 | 鲜艳 | 1.50 | 1.16 | +0.015 | 1.000 / 1.000 / 1.000 |
| 5 | 柔和 | 0.90 | 0.94 | +0.020 | 1.015 / 1.000 / 0.985 |
| 6 | 明亮 | 1.12 | 1.02 | +0.080 | 1.000 / 1.000 / 1.000 |
| 7 | 暖色 | 1.06 | 1.04 | +0.020 | 1.060 / 1.000 / 0.920 |

ID 0 和 1 保留旧 car.2–car.4 语义。设置值支持旧 Boolean、数字和字符串：`false/0` 为原画，`true/1` 为臻彩，越界或损坏值回退原画。偏好键仍为 `HawkConfig.VIDEO_ENHANCE = "video_enhance"`，只是现在存稳定预设 ID。

### GLSL 处理顺序

```glsl
raw = texture2D(uTexture, vTexCoord);
color = raw.rgb * uRgb;
color = (color - 0.5) * uCon + 0.5 + uBri;
l = dot(color, vec3(0.2126, 0.7152, 0.0722));
output = clamp(mix(vec3(l), color, uSat), 0.0, 1.0);
```

渲染使用 `GL_TEXTURE_EXTERNAL_OES` 和 `SurfaceTexture`。GL 线程负责创建纹理，播放器 `setSurface()` 通过主线程 Handler 执行；视频尺寸已知后显式调用 `setDefaultBufferSize(videoWidth, videoHeight)`。`RENDERMODE_WHEN_DIRTY` 只在新帧到达时请求绘制，暂停时保留上一帧供重绘。

car.4 修复的跨线程黑屏问题仍然保留：EXO 不再从 GL 线程调用 `player.setSurface()`，日志会记录 `player surface bound (main thread)`、`setVideoSize`、`drawFrame` 和 shader 错误。

## 4. HDR 策略

EXO 检查 `dolby-vision`、`dvhe`、`dvh1`、`dovi`、`dolby` 及 `ColorInfo.isTransferHdr()`；IJK 使用 codec name 启发式检查 `dvhe/dvh1/dovi/dolby`。检测到 HDR/DoVi 时，`MyVideoView.prepared()` 让 renderer 使用 ID 0 的恒等参数，并提示用户。IJK 不能完整识别所有 HLG/PQ/HDR10 传输特性。

## 5. car.5 运行期验收

MuMu 6.8.4.0 / Android 15 / 横屏 2880×1620 / density 240：

- Python 3.8.13 实际执行 `lxml`、`ujson`、`pyquery`、`requests`、`jsonpath`、`cachetools`、`Crypto`、`beautifulsoup4` 与本地 HTTP 断言。
- Python 和 QuickJS fixture 均实际返回分类、卡片、详情；Python 详情播放合成 640×360 片。
- 8 档逐项通过设置 UI 选择并走分类→详情→播放；ID 1–7 均记录对应 `preset applied` 和 `drawFrame #100`。
- `evidence/car.5/pixel-comparison.json`：以原画为基准，7 个增强档视频区域均有非零平均差异；抽样 UI 区域差异为 0；鲜艳档平均饱和度提高，明亮档平均亮度提高。
- 设置重启后保留：最终 UI 读取到 `臻彩`；`音效`读取到 `影院`。
- 当前 EXO 播放 session 89 挂载 4 个效果：Equalizer、Dynamic Bass Boost、Loudness Enhancer、Virtualizer。

## 6. 调参入口

画面参数集中在 `VideoEnhancePreset.java`，不需要在 renderer 中维护多组 volatile float。音效曲线和强度集中在 `AudioEffectController.java`。修改后应重新构建，并用低饱和测试片做像素比较；shader 最后的 `clamp()` 会避免越界，但不能避免高光或色彩削顶。

## 7. 边界

真实车机 OEM 全屏、真实 DPI、ARM32 独立运行、HDR/DoVi 和 IJK 本轮未覆盖；外部播放器和直播页不在音效会话链路范围内。外部订阅源的可用性受网络、验证页、源自身实现和超时影响，不能由一次搜索承诺所有源永久正常。
