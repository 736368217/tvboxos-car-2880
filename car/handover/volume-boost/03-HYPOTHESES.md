# 03 · 假设排序、证伪方法与修复选项

> 按「可能是根因」的概率排序。每条都给**一步就能证伪的具体动作**，不要靠猜。
> 记住：所有证伪都需要 r56 诊断包（或 r56 之后的版本），因为 r55 及以前**没有 `engine=` / `CarAudioGain` 日志**。

---

## H1（最高）· 车机实际用的不是 app 的 EXO 引擎

**假设**：播放时 `pl` 是 1（IJK），于是 `usesPcmGain()==false` → 走 `LoudnessEnhancer`
→ 车机没有/不生效 → 增益零；r55 起还会把上限压到 100%，表现就是「音量无法增大」。

**支持证据**（全是 `[代码]` 级）：
- `MyVideoView.usesPcmGain()` 仅对 `com.github.tvbox.osc.player.ExoPlayer` 返回 true（`MyVideoView.java:372`）。
- `PlayFragment.java:1381-1385`：`pl` 优先取 **该影片存下来的 `playerCfg`**，其次站点 `playerType`，再回退 Hawk。
- `PlayFragment.java:1671-1675`：**播放失败自动重试会改 `pl` 并写回 `mVodInfo.playerCfg`** → 持久化进历史记录。
- `LivePlayerManager` + `activity_live_play.xml:10`：直播用普通 `VideoView`，连 `CarEnhance` 都不经过。

**一步证伪**：

```
adb logcat -c && adb logcat -s CarAudio | findstr runtime_apply
# 播放中滑到 200%，看这两个字段
#   path=pcm      → H1 不成立（走的是 PCM 路径）
#   path=loudness → H1 成立
#   engine=…      → 直接告诉你引擎类名（r56 才有）
```

**若成立的对策**（见 §修复选项 A/C）。

---

## H2 · 处理器没进音频链路，或被编码拒绝

**假设**：`buildAudioSink` 覆写没有生效（工厂被换掉），或音频是直通格式（AC3/EAC3/DTS）
导致 `onConfigure` 抛 `UnhandledAudioFormatException` → 处理器被跳过。

**支持证据**：r55 及以前**处理器内部一行日志都没有**，两种失败都完全静默。

**一步证伪**（r56）：

```
adb logcat -c && adb logcat -s CarAudio CarAudioGain
# 播放开始后应出现：
#   CarAudio sink_built floatOut=… offloadArg=… processors=1
#   CarAudio renderers_factory=…SubtitleOffsetRenderersFactory
#   CarAudioGain configure n=1 enc=PCM_16BIT sampleRate=48000 channels=2 accepted=true gain=…
#   CarAudioGain queue n=1 gain=2.0 samples=… level_in=… level_out=…
```

判定：

| 现象 | 结论 |
|---|---|
| 没有 `sink_built` | 覆写没跑 → 工厂没被用（H2a，应用侧可修） |
| 有 `sink_built` 但没有 `configure` | sink 没被 configure（播放器没真正出声到该 sink / 换了 sink） |
| `configure … accepted=false` + `configure REJECTED enc=AC3` | 直通格式把处理器踢出链（H2b） |
| `configure` 与 `queue` 都有，`level_out ≈ 2 × level_in` | **H2 不成立**，代码路径没问题 → 转 H3 |

**若成立的对策**：H2a → 改注入方式（在 `initPlayer` 前 `setRenderersFactory`，或直接给
`SimpleExoPlayer.Builder` 传自定义 `AudioSink`）；H2b → 在 `onConfigure` 里返回
`new AudioFormat(sampleRate, channelCount, ENCODING_PCM_16BIT)` 把格式转成 PCM（而不是抛异常），
或对直通格式明确放弃增益并告知用户。

---

## H3 · 走到了，但设备侧音频通路把 +6 dB 吃掉了

**假设**：采样确实被 ×2 了，但车机音频链（后级 DSP、限幅器、音量键控、Dolby/音效后处理、
eARC/SPDIF 输出）把增益归一化，听感不变。

**支持证据**：`[未验]`。这是**目前唯一无法在应用侧证伪**的一类，必须靠 H2 的日志 + 客观测量排除。

**判定顺序**：先拿到 H2 的 `queue … level_out ≈ 2 × level_in`（证明采样被放大），
再做**客观测量**（见 §客观测量 A/B）。若采样放大成立但输出响度不变，基本可锁定 H3。

**若成立的对策**：
- 提高增益上限（+6 dB → +12/+18 dB），并加软限幅避免削波；
- 或改用「多段处理 + 动态范围压缩（DRC）」，让响度感知提升更明显；
- 若车机侧存在固定后处理，需要与车机侧约定（超出应用可改范围）。

---

## H4 · 状态被覆盖：`volumeFactor` / `appGain` 被别的路径改写

**假设**：`CarEnhance` 的增益状态是**进程级静态**，任何一路 VideoView 的
`setVolume` / `setMute` / `duckVolume` 都能把它改写；音频焦点闪避（CAN_DUCK → `duckVolume(0.1f)`）
之后若收不到 `AUDIOFOCUS_GAIN`，`volumeFactor` 会长期停留在 0.1 →
**200% 实际比 100% 还轻**。

**支持证据**（`[代码]` 级）：`CarEnhance.java:37/40`、`AudioFocusHelper.java:56-78`、
`MyVideoView.setVolume/setMute/duckVolume/restoreVolume`。

**一步证伪**（r56）：日志里已有 `factor=` 与 `applied=` 字段：

```
adb logcat -s CarAudio | findstr runtime_apply
# factor=1.0 applied=2.0  → 正常
# factor=0.1 applied=0.2  → H4 成立（此时 200% 反而更安静）
```

**若成立的对策**：把 `appGain` / `volumeFactor` 改成**按 View 实例持有**（或按
`AudioGainProcessor` 的调用方分组），并给闪避加超时兜底（例如 30 s 后强制恢复）。

---

## H5 · 用户在调系统音量，而不是应用内音量

**假设**：车机方向盘/音量条改的是系统 `STREAM_MUSIC`，与应用内增益无关。

**一步证伪**：让用户描述操作路径，或看 `dumpsys audio`：

```
adb shell dumpsys audio | findstr /i "STREAM_MUSIC"
```

同时确认播放页手势确实改了应用内音量：r56 有 `volume_gesture start=…% dy=… -> …%` 与
`CarAudio app_gain request=200%`。

**若成立的对策**：不需要改代码，需要改交互预期；或（若用户确实想让音量键作用于系统）另行设计。

---

## H6 · 用户装的是旧版本

**一步证伪**：

```
adb shell dumpsys package com.github.tvbox.osc.jun | findstr versionName
# 期望：3.1.0-car.7-r56
```

---

## H7 · 单例/双实例互相覆盖

**假设**：`AudioGainProcessor.INSTANCE` 是全局单例，而详情页小窗/预览与全屏播放
各自持有 sink；后创建的 sink 与新写入的 gain 可能对不上（或两个 sink 共享同一个
`BaseAudioProcessor` 实例导致内部缓冲状态互相污染）。

**一步证伪**：r56 日志里 `runtime_apply … player=<hash> pcmStored=… pcmQueued=…`：
若 `pcmStored` 与期望一致但 `pcmQueued` 长时间不增长 → 说明这个 sink 没在跑（另一个实例在跑）。

**若成立的对策**：`buildAudioSink` 里为每个 sink `new AudioGainProcessor()`，
把 gain 存在进程级 holder 里（每次 `configure`/`queueInput` 读取），
即「一 sink 一实例 + 共享增益值」。

---

## H8 · 车机系统音量本身很低，差异被掩蔽

`[未验]`。应用内 +6 dB 在系统音量 5% 时几乎听不出来。
**一步证伪**：把系统音量调到 50% 以上再对比 100%/200%。

---

## 客观测量方案（把「听感」变成数字）

> 这是本次交付**最缺的东西**。下面三种任选一种，都能彻底闭环。

### A. `AudioPlaybackCapture`（推荐，Android 10+，车机是 12L）

1. 装一个能录"应用音频"的录音 App（或自写 20 行：`MediaProjection` +
   `AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX)` 风格配置，
   `AudioPlaybackCaptureConfiguration` + `setAllowedCapturePolicy`）；
2. TVBox 默认的 `AudioAttributes` 是 `USAGE_MEDIA` 且没有 `ALLOW_CAPTURE_BY_NONE`，**默认可被捕获**；
3. 同一片段分别录 100% 与 200% 各 15 秒；
4. 计算 RMS / 峰值：

```
ffmpeg -i a100.wav -af astats=metadata=1:reset=0 -f null - 2>&1 | findstr /i "RMS_level"
ffmpeg -i a200.wav -af astats=metadata=1:reset=0 -f null - 2>&1 | findstr /i "RMS_level"
```

5. **期望**：200% 的 RMS 比 100% 高 **≈6 dB**；若持平 → H3（设备侧）或 H2（没走到）成立。

### B. 宿主录音（不用装 App）

在车机上播放，用手机/录音笔在扬声器前录音，同样比 RMS。样本噪声更大，但足以区分 0 dB 与 +6 dB。

### C. 有 root 时查看音频栈

```
adb shell dumpsys media.audio_flinger | findstr /i "Track stream volume active offload"
adb shell dumpsys audio | findstr /i "focus volume"
```

看是否出现 offload/直通、以及 audio focus 是否被别的 App 抢走（H4）。

---

## 修复选项（按性价比排序）

| 选项 | 动作 | 收益 | 风险/成本 |
|---|---|---|---|
| **A** | 当应用内音量 > 100% 时**强制 EXO**（在 `PlayerHelper` 里覆盖 `pl`，或 `updateCfg` 传 `forcePlayerType=2`），并在设置里显示当前引擎 | 立刻把 H1 覆盖掉，用户马上能听到 PCM 增益（若 H2/H3 不成立） | 某些片源 EXO 解不出来才需要 IJK；强制可能让这些片放不了 → 需保留"自动重试"并提示 |
| **B** | 给 IJK 也做 PCM 级增益（IJK 无采样 hook，需要自建 audio pipeline 或用 `AudioTrack` 自渲染） | 彻底覆盖 IJK | 工作量大，涉及 IJK 内部 |
| **C** | 把上限从 +6 dB 提到 +12 dB 并加软限幅；或改用 DRC | 对 H3 有效，用户感知更强 | 可能削波/破音 |
| **D** | 设置页/播放页明确显示「当前引擎：EXO（支持放大）/ IJK（不支持）」 | 消除"假 200%"的认知差 | 无 |
| **E** | 把 `appGain`/`volumeFactor` 改为按实例持有 + 闪避超时兜底 | 消除 H4/H7 | 无 |

## 验收标准（缺一不可）

1. `CarAudioGain configure` + `queue` 行存在，且 `level_out ≈ gain × level_in`；
2. **客观测量**（方案 A/B）显示 200% 比 100% 的 RMS 高 ≈6 dB（或按新上限的对应值）；
3. 车机上人耳确认 200% 明显更响；
4. 100% 以下行为不回归（0→100% 线性、静音、音频焦点闪避仍有效）；
5. 直播页与 IJK 场景**明确说明是否支持**，不允许出现"数字能调但没效果"。
