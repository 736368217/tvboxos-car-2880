# 音频与视频增强方案说明（car.4）

本文档以当前 `TVBoxOS 1.0.0-car.4` 源码为准，说明车机版的音效与臻彩实现方式、可调整参数、播放器接线、生命周期、兼容性、调优方法和验收命令。

## 1. 能力总览

| 能力 | 当前方案 | 用户可调方式 | 生效时机 |
|---|---|---|---|
| 音效 | Android `AudioEffect` 链：Equalizer + BassBoost + Virtualizer + LoudnessEnhancer | 设置页 6 档循环 | 当前内部播放会话立即重新绑定；新会话自动重新绑定 |
| 臻彩 | `GLSurfaceView` + `SurfaceTexture` + `GL_TEXTURE_EXTERNAL_OES` + GLSL 片元着色器 | 设置页开 / 关 | 下一次播放创建渲染视图时生效 |
| HDR 保护 | EXO 读取视频色彩元数据；IJK 读取 Dolby Vision 编码名 | 无单独开关 | HDR/Dolby Vision 时强制关闭臻彩 |

当前源码没有向用户暴露独立的 EQ 滑块、BassBoost 滑块、饱和度滑块或对比度滑块。用户界面只有“音效预设”和“臻彩增强”开关；更细的参数需要修改源码常量后重新构建。

## 2. 音效方案

### 2.1 信号链

源码：

- `app/src/main/java/com/github/tvbox/osc/car/AudioEffectController.java`
- `app/src/main/java/com/github/tvbox/osc/car/CarEnhance.java`
- `app/src/main/java/com/github/tvbox/osc/player/MyVideoView.java`
- `app/src/main/java/com/github/tvbox/osc/util/HawkConfig.java`
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/ModelSettingFragment.java`

运行链路：

```text
播放器准备完成
    |
    v
MyVideoView.prepared()
    |
    +-- view.getAudioSessionId()
    |       |
    |       +-- EXO: ExoPlayer.getAudioSessionId()
    |       +-- IJK: IjkMediaPlayer.getAudioSessionId()
    |
    v
CarEnhance.applyAudio(view)
    |
    v
AudioEffectController.apply(mode, audioSessionId)
    |
    +-- Equalizer
    +-- BassBoost
    +-- Virtualizer
    +-- LoudnessEnhancer
    |
    v
Android AudioFlinger / 当前 AudioTrack
```

音效效果绑定的是**当前播放器的音频会话 ID**，不是全局系统音量，也不是系统混音器上的永久效果。播放器释放时，`MyVideoView.release()` 调用 `CarEnhance.releaseAudio()`，四个效果随会话释放。

### 2.2 支持范围

| 播放路径 | 是否支持 | 原因 |
|---|---:|---|
| 内置 EXO | 是 | `ExoPlayer.getAudioSessionId()` 返回真实会话 |
| 内置 IJK | 是 | `IjkMediaPlayer.getAudioSessionId()` 从 native IJK 获取会话 |
| 系统播放器/无会话输出 | 否 | 会话 ID 为 0，控制器会释放效果并跳过 |
| 外部 MX/Kodi/VLC/Reex 等 | 不在本链路 | 外部播放器不经过 `MyVideoView` 的内部音频会话绑定 |
| 直播页基类 `VideoView` | 当前不在范围内 | 当前直播基类没有接入 `MyVideoView` 的会话访问器 |

如果 `audioSessionId <= 0`，`AudioEffectController.apply()` 直接执行 `release()`，不会创建无目标会话的全局效果。

### 2.3 预设模式

模式常量定义：

```java
MODE_OFF      = 0; // 关闭
MODE_CINEMA   = 1; // 影院
MODE_BASS     = 2; // 重低音
MODE_SURROUND = 3; // 3D环绕
MODE_HIFI     = 4; // HiFi
MODE_VOCAL    = 5; // 人声
```

#### EQ 曲线频点

每个预设使用同一组 13 个参考中心频率，单位为 Hz：

```text
30, 60, 100, 150, 200, 250, 500, 1000, 2000, 5000, 8000, 16000, 32000
```

设备实际 Equalizer 的频段数量和中心频率可能不同。实现会读取设备实际频段，然后把每个实际频段映射到**最近的参考频点**。例如设备提供 63 Hz，会使用 60 Hz 的曲线值；设备提供 120 Hz，会在 100/150 Hz 中取最近值。

EQ 曲线数值在源码中以 dB 写入，随后乘以 100 转成 Android Equalizer 使用的 millibel：

```text
源码  +7.0  -> Equalizer +700 mB
源码  -1.5  -> Equalizer -150 mB
```

#### 完整预设表

| 模式 | EQ 曲线（依次对应 30…32000 Hz，单位 dB） | BassBoost | Virtualizer | LoudnessEnhancer |
|---|---|---:|---:|---:|
| 关闭 | 不创建/释放全部效果 | 0 | 0 | 0 dB |
| 影院 | `7, 8, 6, 3, 2, 1, 0, 0, 1, 2, 3, 3, 2` | 900/1000 = 90% | 850/1000 = 85% | +900 mB = +9 dB |
| 重低音 | `6, 7, 5, 4, 3, 2, 0, -1, -1, 0, 0, 0, 0` | 700/1000 = 70% | 150/1000 = 15% | +300 mB = +3 dB |
| 3D环绕 | `-2, -1, 0, 1, 2, 3, 2, 1, 2, 3, 4, 4, 3` | 450/1000 = 45% | 1000/1000 = 100% | +450 mB = +4.5 dB |
| HiFi | `1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 1` | 250/1000 = 25% | 300/1000 = 30% | +400 mB = +4 dB |
| 人声 | `-4, -3, -1.5, 0, 1, 2, 3, 4, 3, 2, 1, 0, -1` | 0 | 50/1000 = 5% | +750 mB = +7.5 dB |

说明：

- BassBoost 与 Virtualizer 的强度范围按 Android API 使用 `0..1000`。
- `getStrengthSupported()` 返回 false 时，BassBoost 或 Virtualizer 会被静默跳过，不影响播放。
- LoudnessEnhancer 使用目标增益 millibel；例如 `900` 就是约 `+9 dB`。
- Equalizer 的目标值会按设备返回的 `getBandLevelRange()` 做裁剪，不会超出硬件/系统允许范围。
- 人声模式没有低频 BassBoost，重点是削减低频、抬高中频和适度响度。

### 2.4 渐入与切换行为

每次新建效果链或切换预设，参数不是瞬间跳变，而是通过主线程 Handler 做渐入：

```text
FADE_STEPS = 16
FADE_STEP_MILLIS = 32 ms
名义时长约 500 ms，实际为 16 × 32 = 512 ms
```

如果仍是同一个音频会话，只切换预设，控制器会读取当前效果值作为起点，再插值到新目标；如果是新会话，则从 0 逐步到目标值。

`关闭` 模式会：

1. 停止尚未完成的渐入消息；
2. 禁用 Equalizer/BassBoost/Virtualizer/LoudnessEnhancer；
3. 调用 `release()` 释放对象；
4. 清空当前 session、模式和目标值。

### 2.5 音效持久化与 UI

持久化键：

```java
HawkConfig.HIFI_AUDIO = "hifi_audio";
```

默认值：

```text
0 = 关闭
```

设置页点击一次，按下面顺序循环：

```text
关闭 -> 影院 -> 重低音 -> 3D环绕 -> HiFi -> 人声 -> 关闭
```

设置页点击处理位于 `ModelSettingFragment`：

1. 读取当前 `CarEnhance.audioMode()`；
2. 计算 `(current + 1) % 6`；
3. 写入 `HawkConfig.HIFI_AUDIO`；
4. 更新文本和 Toast；
5. 如果当前 Activity 中存在 `mVideoView` 且为 `MyVideoView`，立即调用 `CarEnhance.applyAudio()`。

因此，音效开关与预设**可以影响当前内部播放**；如果当前没有内部播放会话，则会在下一次 `prepared()` 时应用。

### 2.6 可调参数位置

想改预设音色，主要修改：

```text
app/src/main/java/com/github/tvbox/osc/car/AudioEffectController.java
```

对应常量：

```java
CURVE_CINEMA
CURVE_BASS
CURVE_SURROUND
CURVE_HIFI
CURVE_VOCAL
BASS_STRENGTH
VIRT_STRENGTH
LOUD_STRENGTH
CURVE_FREQS
```

调参规则：

- `CURVE_*`：单位 dB，建议先在 `-6..+8 dB` 内调整；过大的 EQ 增益容易削波。
- `BASS_STRENGTH` / `VIRT_STRENGTH`：`0..1000`；1000 是 API 允许的最大强度，不代表每台设备听感相同。
- `LOUD_STRENGTH`：单位 millibel；建议先控制在 `0..900`，超过 +9 dB 需重点检查削波和车机功放余量。
- 修改后必须重新构建 APK；当前没有运行期 EQ 滑块。

## 3. 臻彩视频方案

### 3.1 渲染架构

源码：

- `app/src/main/java/com/github/tvbox/osc/car/CarEnhance.java`
- `app/src/main/java/com/github/tvbox/osc/util/PlayerHelper.java`
- `app/src/main/java/com/github/tvbox/osc/player/MyVideoView.java`
- `player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderView.java`
- `player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderViewFactory.java`

运行链路：

```text
视频源 / HLS / MP4
       |
       v
EXO 或 IJK 解码器
       |
       v
Surface（car.4 在主线程绑定）
       |
       v
SurfaceTexture（缓冲区显式设为 videoWidth × videoHeight）
       |
       v
GL_TEXTURE_EXTERNAL_OES
       |
       v
GLSL 片元着色器
       |
       v
GLSurfaceView 屏幕输出
```

`PlayerHelper.updateCfg()` 会先选择内部播放器和默认渲染视图：

- `PLAY_TYPE=1`：IJK；
- `PLAY_TYPE=2`：EXO；
- `PLAY_RENDER=0`：TextureRenderView；
- `PLAY_RENDER=1`：SurfaceRenderView。

当 `CarEnhance.useEnhanceRenderView(videoView)` 返回 true 时，渲染工厂被替换为 `GlEnhanceRenderViewFactory`。因此臻彩是**替换渲染视图**，不是在普通 View 上再叠一层半透明滤镜。

### 3.2 GLSL 处理公式

顶点着色器把四边形绘制到 NDC 全屏区域，并使用 `SurfaceTexture.getTransformMatrix()` 修正外部纹理坐标。

片元着色器的核心逻辑：

```glsl
raw = texture2D(uTexture, vTexCoord);
color = raw.rgb * uRgb;
color = (color - 0.5) * uCon + 0.5 + uBri;
l = dot(color, vec3(0.2126, 0.7152, 0.0722));
output = clamp(mix(vec3(l), color, uSat), 0.0, 1.0);
```

参数含义：

| 参数 | 当前开启值 | 当前关闭值 | 作用 |
|---|---:|---:|---|
| `uSat` | `1.35` | `1.0` | 饱和度；1.0 为原始，>1 增强，<1 去饱和 |
| `uCon` | `1.12` | `1.0` | 对比度；围绕中灰 0.5 拉伸/压缩 |
| `uBri` | `+0.03` | `0.0` | 亮度偏移 |
| `uRgb` | `(1,1,1)` | `(1,1,1)` | RGB 通道增益，目前固定为恒等值 |
| `uTexMatrix` | SurfaceTexture 提供 | SurfaceTexture 提供 | 旋转、裁剪、缩放后的纹理坐标矩阵 |

调色步骤顺序是：

1. 采样外部 OES 视频纹理；
2. 进行 RGB 通道乘法；
3. 以 0.5 为中心做对比度变换；
4. 加亮度偏移；
5. 计算亮度 `l`；
6. 在灰度 `vec3(l)` 与彩色 `color` 之间按 `uSat` 混合；
7. 最后 clamp 到 `[0,1]`，避免输出越界。

### 3.3 HDR / Dolby Vision 策略

臻彩不应对已经经过 HDR 映射的画面再次做 SDR 风格调色。当前逻辑：

#### EXO

`ExoPlayer.isHdrVideo()` 检查当前选中的视频轨：

- `sampleMimeType` 包含 `dolby-vision`；
- `codecs` 以 `dvhe`、`dvh1`、`dovi` 开头或包含 `dolby`；
- `Format.colorInfo` 存在且 `ColorInfo.isTransferHdr()` 为 true。

#### IJK

当前 fork 暴露的 Java 元数据有限，只检查 `MediaInfo.mVideoDecoder` 是否包含：

```text
dvhe / dvh1 / dovi / dolby
```

IJK 当前不能可靠识别所有 HLG/PQ/HDR10 传输特性，代码中已明确注明这是启发式检测。

#### 强制关闭行为

`MyVideoView.prepared()`：

```java
if (isHdrVideo()) {
    setVideoEnhanceMode(0);
    Toast: HDR 视频已自动关闭臻彩
} else if (CarEnhance.videoEnhanceEnabled()) {
    setVideoEnhanceMode(1);
}
```

如果 HDR 元数据在创建渲染视图之后才出现，渲染视图仍可能是 GL 类型，但会被切换到恒等参数：

```text
sat=1.0, con=1.0, bri=0.0
```

### 3.4 car.4 修复的线程与缓冲区问题

car.3 的黑屏根因：

```text
GLThread -> player.setSurface(surface) -> EXO 线程检查失败
```

car.4 改成：

```text
GLThread 创建 SurfaceTexture / Surface
    |
    v
Handler(Looper.getMainLooper()).post(...)
    |
    v
主线程 player.setSurface(surface)
```

同时在已知视频尺寸后调用：

```java
surfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
```

当前测试片日志为：

```text
setVideoSize 640x360
surfaceTexture buffer size -> 640x360
drawFrame #0 ...
drawFrame #100 ...
```

`GLSurfaceView` 使用 `RENDERMODE_WHEN_DIRTY`：只有收到 `SurfaceTexture.onFrameAvailable()` 时请求绘制，因此不会无条件占用满速 GPU 循环。

### 3.5 臻彩持久化与生效时机

持久化键：

```java
HawkConfig.VIDEO_ENHANCE = "video_enhance";
```

取值：

```text
0 = 关闭
1 = 开启
```

`CarEnhance.videoEnhanceEnabled()` 同时兼容旧式 Boolean 存储值，并把非零数字视为开启。

设置页行为：

1. 点击 `臻彩增强`；
2. 反转 `video_enhance`；
3. 写入 Hawk；
4. Toast 显示“画质增强：臻彩（下次播放生效）”或“画质增强：关闭”。

之所以是“下次播放生效”，是因为渲染工厂在播放器创建前选择。当前设置页不会把正在播放的 TextureView 动态替换成 GLSurfaceView。

### 3.6 可调参数位置

要调整当前臻彩强度，修改：

```text
player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderView.java
```

当前代码位置的核心字段：

```java
private volatile float mSat = 1.0f;
private volatile float mCon = 1.0f;
private volatile float mBri = 0.0f;
private volatile float mRgbR = 1.0f;
private volatile float mRgbG = 1.0f;
private volatile float mRgbB = 1.0f;
```

开启模式目前写死为：

```java
mSat = 1.35f;
mCon = 1.12f;
mBri = 0.03f;
```

当前关闭/开启只有二元模式，没有独立调节 UI。建议调参范围（经验值，不是代码硬限制）：

| 参数 | 建议试验范围 | 过大后的风险 |
|---|---|---|
| 饱和度 `uSat` | `1.0..1.5` | 色彩削顶、肤色过浓、色块失真 |
| 对比度 `uCon` | `1.0..1.15` | 暗部丢细节、亮部削顶 |
| 亮度 `uBri` | `-0.02..+0.05` | 黑位抬高或高光溢出 |
| RGB 增益 `uRgb` | 每通道约 `0.95..1.05` | 色偏、白平衡失真 |

注意：shader 最后执行 `clamp()`，所以参数过大时不会出现浮点越界，但会出现不可逆的颜色削顶。调试时应使用低饱和测试片，而不是已经接近满饱和的色条片。

如果要把参数开放到设置页，建议新增：

```text
video_sat      float
video_con      float
video_bri      float
video_rgb_r    float
video_rgb_g    float
video_rgb_b    float
```

并把 `setVideoEnhanceMode(int)` 拆成 `setVideoEnhanceParams(...)`，在 GL 线程通过 `queueEvent()` 更新 uniform。不要直接在 UI 线程调用 GLES API。

## 4. 设置键与源码入口速查

| 项目 | 键 / 方法 | 文件 |
|---|---|---|
| 音效预设 | `hifi_audio` | `HawkConfig.java` |
| 臻彩开关 | `video_enhance` | `HawkConfig.java` |
| 音效名称/循环 | `AudioEffectController.name()`、`ModelSettingFragment.llCarAudio` | `AudioEffectController.java` / `ModelSettingFragment.java` |
| 音效绑定 | `CarEnhance.applyAudio()` | `CarEnhance.java` |
| 音效释放 | `CarEnhance.releaseAudio()` | `CarEnhance.java` / `MyVideoView.release()` |
| HDR 判断 | `ExoPlayer.isHdrVideo()`、`IjkMediaPlayer.isDolbyVision()` | `ExoPlayer.java` / `IjkMediaPlayer.java` |
| 渲染视图选择 | `PlayerHelper.updateCfg()` | `PlayerHelper.java` |
| GPU 参数 | `setVideoEnhanceMode()` | `GlEnhanceRenderView.java` |
| 渲染 Surface 绑定 | `bindSurfaceToPlayer()` | `GlEnhanceRenderView.java` |
| 外部纹理帧回调 | `onFrameAvailable()` | `GlEnhanceRenderView.java` |

## 5. 运行期验证命令

### 5.1 推送本地测试片并起播

```powershell
adb push cartest-lowsat.mp4 /sdcard/Movies/
adb forward tcp:19978 tcp:9978

$u = 'http://127.0.0.1:9978/file/Movies/cartest-lowsat.mp4'
$b = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($u)).Replace('+','-').Replace('/','_')
Invoke-WebRequest "http://127.0.0.1:19978/push/b64:$b" -UseBasicParsing
```

### 5.2 验证音效

必须在片子仍在播放时执行，因为播放结束后控制器会释放效果：

```powershell
adb shell dumpsys media.audio_flinger | Select-String 'Effect Chains|effects for session|- name:'
adb shell dumpsys audio | Select-String 'com.github.tvbox.osc.jun|AudioTrack|USAGE_MEDIA'
```

期望看到：

```text
4 effects for session <非零 session>
- name: Equalizer
- name: Dynamic Bass Boost
- name: Loudness Enhancer
- name: Virtualizer
```

### 5.3 验证臻彩

```powershell
adb logcat -d | Select-String 'GlEnhance'
```

期望看到：

```text
player surface bound (main thread)
surfaceTexture buffer size -> 640x360
setVideoSize 640x360
drawFrame #100 sat=1.35 con=1.12 bri=0.03
```

像素验证应使用 `cartest-lowsat.mp4`：

1. 设置臻彩开启，起播并截图；
2. 设置臻彩关闭，起播并截图；
3. 对相同视频区域计算饱和度/亮度；
4. UI 区域应保持一致，视频区 ON 应高于 OFF。

## 6. 已知边界

- 系统播放器、外部播放器、直播页基类 `VideoView` 不在当前音效会话绑定范围内。
- IJK 只能通过 codec name 粗略识别 Dolby Vision，不能完整覆盖 HLG/PQ/HDR10 元数据。
- HDR/Dolby Vision 片源会强制关闭臻彩；这是保护策略，不是故障。
- 臻彩切换在下一次播放创建渲染视图时生效；音效预设可以对当前内部播放会话立即重新应用。
- `AudioEffect` 是否真正可用取决于设备音频 HAL；代码会检查 BassBoost/Virtualizer 的 `getStrengthSupported()`，不支持时只跳过对应效果。
- 音效效果应在播放期间查看；播放结束后 `MyVideoView.release()` 会释放效果链，`dumpsys media.audio_flinger` 会回到 0 个效果链。
