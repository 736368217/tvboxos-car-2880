# 00 · 问题描述与事实边界

## 1. 用户可见症状

用户在车机上（零跑 B01 车机，见下）反馈的原始说法：

> 「音量仍然无法增大」（在此之前已按「音量手势/应用内增益」改过好几轮）

展开成可检验的陈述：

1. 播放页把应用内音量从 100% 往上调（滑到 200%）**听不出变化**；
2. 低区间（0–100%）是有效的 —— 也就是「能变小、不能变大」；
3. 车机**系统音量**（方向盘/系统音量条）与这个应用内音量是两套东西（见下）。

第 1、2 条来自用户描述，属于 `[听感]` 级证据，但**采集过程没有留档**（没有录屏、没有前后对比录音）。
第 3 条是 `[代码]` 级事实。

## 2. 运行环境

| 项 | 值 | 证据 |
|---|---|---|
| 车机 | 零跑 B01，SA8295P，Android 12L / API 32，2880×1620 / d160 | 项目既有交接文档 |
| 应用 | Application ID `com.github.tvbox.osc.jun`，label `TVBox` | `app/build.gradle` |
| 迭代号 | 用户反馈时运行的是 r48–r55 之间的版本（**未实测确认**具体装的是哪个） | `[未验]` |
| 开发期模拟器 | MuMu 12（Android 15），每批次 `mumu-pool.py acquire --mute` | `04-PITFALLS.md` |
| 播放引擎默认 | EXO（`HawkConfig.PLAY_TYPE` 默认 2）；直播默认同样取该值 | `[代码]` |

## 3. 「应用内音量」的语义设计（这是全部混乱的源头）

应用内音量是一个 **0..2 的浮点**（100% = 1.0，200% = 2.0），拆成两段分别实现：

```
0..1   → 播放器自身的音量（ExoPlayer: SimpleExoPlayer.setVolume → AudioTrack 音量）
>1 的部分 → 必须直接放大解码后的 PCM 采样，因为 ExoPlayer/IJK 的 setVolume 都会把值钳到 1.0
```

- 持久化：`HawkConfig.VOLUME_GAIN`（百分比整数，`[DEX]` 在 r55 APK 里可搜到 `app_volume_gain` 键）；
- 手势：`BaseController.slideToChangeVolume()`，`VOLUME_SLIDE_RANGE = 1.0f`，基于 `mVolumeStart` 累计位移线性映射；
- 上限：r55 起 `BaseController.maxVolume()` = `canBoostVolume() ? 2f : 1f`，即**不支持的引擎只允许到 100%**。

> 注意：这与「系统音量」无关。改应用内音量**不会**改系统音量，也不会绕过系统音量上限。
> 车机系统音量本身已经很低时，应用内增益的听感差异会被系统音量掩蔽一部分（`[未验]` H5）。

## 4. 已确认事实（`[代码]`/`[日志]`/`[DEX]`）

| # | 事实 | 证据 |
|---|---|---|
| F1 | 默认引擎是 EXO，且 VOD 播放路径上 `play` 配置来自 `mVodPlayerCfg`，可被站点/自动重试改写成 IJK | `01-ARCHITECTURE.md` §3 |
| F2 | app 的 EXO 引擎（`com.github.tvbox.osc.player.ExoPlayer`）在构造时注入了自定义 `RenderersFactory`，且该工厂覆盖了 `buildAudioSink` | `ExoPlayer.java:58`、`:137`、`:524` |
| F3 | 该 sink 里塞进了 `AudioGainProcessor.INSTANCE`，并强制关掉 offload（`OFFLOAD_MODE_DISABLED`） | `ExoPlayer.java:528-532` |
| F4 | `AudioGainProcessor` 对 `PCM_16BIT`/`PCM_FLOAT` 做逐采样乘法；其它编码抛 `UnhandledAudioFormatException` | `AudioGainProcessor.java`，`javap` 已确认编译产物含 `onConfigure(AudioProcessor$AudioFormat)` / `queueInput(ByteBuffer)` |
| F5 | 音量链路完整连通：手势 → `BaseController` → `ControlWrapper.setVolume` → `MyVideoView.setVolume` → `CarEnhance.setAppGain` → `AudioGainProcessor.setGain` | `01-ARCHITECTURE.md` §2 |
| F6 | `usesPcmGain()` 只对 app 的 EXO 引擎返回 true；IJK 走 `LoudnessEnhancer`（上限 +12 dB，且 session ≤ 0 时静默 `release()`） | `MyVideoView.java:372`、`CarEnhance.java:71-74`、`AudioEffectController.java:73-84` |
| F7 | 直播播放用的是**普通 `VideoView`**（不是 `MyVideoView`）→ 直播永不进入 `CarEnhance`，且 r55 后直播上限就是 100% | `activity_live_play.xml:10`、`ControlWrapper.supportsVolumeBoost()` |
| F8 | `CarEnhance.appGain` 与 `volumeFactor` 是**进程级静态变量**，被所有 VideoView 共享；`volumeFactor` 会被音频焦点闪避压到 0.1 | `CarEnhance.java:37/40/105`、`AudioFocusHelper.java:73-76` |
| F9 | 日志 `CarAudio runtime_apply … path=pcm gain=2.0` **只证明 app 选择了 PCM 路径并调用了 `setGain`**，不证明处理器在链路里，更不证明采样被放大 | `CarEnhance.java:63-83`（日志就在 `setGain` 之前一行） |
| F10 | r55 已发布 APK 的 DEX 里确实含本音量代码（`runtime_apply`、`volume_gesture start=`、`volume_limited_by_player`、`loudness_unavailable`、`app_volume_gain`） | `[DEX]` 本目录 `05-REPRO.md` §5 的扫描脚本 |

## 5. 从未验证过的事（**这是本问题的核心边界**）

1. **从未做过听觉验证。** 本项目所有 MuMu 实例都以 `acquire --mute` 租用（宿主机与 guest 流双静音），
   所以「200% 比 100% 响」这句话**没有任何一次是被人耳或仪器确认过的**。
   历轮所有「已验证」都是 `[日志]` 级。
2. **从未验证过采样是否真的被放大。** r48 之前的 `AudioGainProcessor` 一行日志都没有，
   `path=pcm` 只是应用侧的选择记录（F9）。r56 才补上 `CarAudioGain` 采样级日志。
3. **从未确认用户装的是哪个版本。** 没有 `dumpsys package com.github.tvbox.osc.jun | grep versionName` 记录。
4. **从未确认播放时用的是哪个引擎。** 没有 `engine=` 记录（r56 才加进日志）。
5. **从未测量过设备输出。** 没有录音、没有 `AudioPlaybackCapture`、没有音频分析仪数据。

> 结论：这是一个**「症状确凿、根因零证据」**的问题。任何「已修复」的声明在拿到
> 上面 1–5 中至少 (1) 或 (2) 的证据之前都不成立。

## 6. 影响面矩阵

| 播放场景 | 引擎 | >100% 是否有实现 | 备注 |
|---|---|---|---|
| 点播（普通站点） | EXO（默认） | 有（PCM 增益） | 需要 H2/H3 排除 |
| 点播（站点 `pl=1` 或自动重试切到 IJK） | IJK | **无**（只靠 LoudnessEnhancer） | r55 起直接压回 100% |
| 点播（DASH/MPD 直链） | 强制 EXO | 有 | `PlayFragment.java:1011-1018` |
| 直播（LivePlayActivity） | EXO/IJK（`LIVE_PLAY_TYPE`） | **无** | 用的是普通 `VideoView` |
| 详情页小窗/预览 | 同一 MyVideoView | 有（但受 H7 静态共享影响） | `[未验]` |
| 调用外部播放器（Kodi/MX/VLC…） | 外部 app | **无** | `PlayerHelper.runExternalPlayer` |

## 7. 问题归属的三种可能（后续章节分别展开）

- **A. 我们没走到增益代码**（引擎不是 EXO / 处理器不在链路）→ 应用侧可修。
- **B. 走到了但车机音频通路把 +6 dB 吃掉了**（系统 DSP、限幅、后处理、音量键控）→ 应用侧难修，
  需要换思路（更大增益、多段处理、或与车机侧约定）。
- **C. 状态被覆盖**（静态 `volumeFactor`/`appGain` 被别的路径改写回 1.0 或 0.1）→ 应用侧可修，
  且这是**读代码就能改对**的一类（F8）。
