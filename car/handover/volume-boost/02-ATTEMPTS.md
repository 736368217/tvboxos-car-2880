# 02 · 历次尝试与失败原因

> 每一轮都遵循同一个模式：**提出一个"应该能放大"的机制 → 用应用侧日志证明"我调用了" → 宣布修好**
> → 用户在车机上反馈"还是不行"。下表把每一轮的假设、实现、实际证据、以及**为什么这个证据不足以支撑结论**列清楚。

## 0. 时间线速览

| 轮次 | commit | 机制 | 车机结果 |
|---|---|---|---|
| 早期 | `6db5feec` / `7e4a08f4` | 应用内音量 0..1 + 持久化（>1 无实现） | 100% 与 200% 完全一样 |
| r36 | `aaeca99b` | 修手势累加 bug（数值正确了） | >100% 仍无增益 |
| r44 | `c590b1dc`（+ 诊断 `09c03cc7`） | 系统 `LoudnessEnhancer` | **无效** |
| r48 | `84effcdc` | ExoPlayer `AudioProcessor` PCM 增益 + 强制关 offload | **仍无效** |
| r55 | `bef4cf99` | 非 PCM 引擎上限压回 100% | 表现为「100% 就到顶」 |
| r56 | 本次 | 只加诊断，无行为改动 | 待车机取日志 |

---

## 1. 早期（`6db5feec`、`7e4a08f4`）—— 只有 0..1 是真的

- **假设**：把音量做成"应用内增益"，不碰系统音量，就够用了。
- **实现**：`HawkConfig.VOLUME_GAIN` 存 0..100；手势直接改它；播放器 `setVolume(0..1)`。
- **实际证据**：无（>1 根本没有实现路径）。
- **为什么不够**：ExoPlayer/IJK 的 `setVolume` 都把值钳到 1.0，**>100% 在设计上就不存在**。
  这一轮埋下了"200% 只是界面上的数字"这个坑。

## 2. r36（`aaeca99b`）—— 手势数值对了，增益还是零

- **假设**：用户"调不大"是因为手势映射坏了（旧写法每个 move 事件都累加一次累计值，几十像素就 0%→200%），
  所以数值对了用户就能感觉到。
- **实现**：`mVolumeStart` 快照 + `deltaY / height * VOLUME_SLIDE_RANGE`，`VOLUME_SLIDE_RANGE = 1.0f`；
  加 `volume_gesture start=…% dy=… h=… -> …%` 日志。
- **实际证据** `[日志]`：`volume_gesture start=100% dy=-96 h=1620 -> 106%`（数值线性、可复现）。
- **为什么不够**：这只修好了"数字"，**没有产生任何 >100% 的音频处理**。
  `clampVolume` 当时同样封在 1.0，所以就算滑到 200% 也只是数字。

## 3. r44（`c590b1dc`，诊断 `09c03cc7`）—— LoudnessEnhancer

- **假设**：>100% 的增益交给系统音效 `LoudnessEnhancer`（挂在播放器的 audio session 上），
  这样与解码方式无关。
- **实现**：`VolumeBoostMath.millibel(gain, factor)`（1.0→0 dB，2.0→+600 mB，25 mB 量化）；
  `AudioEffectController.apply(mode, session, boostMb)` 里 `new LoudnessEnhancer(session)` + 500 ms 淡入；
  接进 `CarEnhance.applyAudio`。
- **实际证据** `[日志]`（MuMu）：`loudness=600 boostMb=600 enabled=true`；
  另有 `fade_done loudness=… now=…`（`09c03cc7` 加的）。
- **为什么不够**：
  1. **MuMu 有 LoudnessEnhancer，车机不一定有。** `buildChain()` 里创建失败只打一行
     `loudness_unavailable session=…：设备没有 LoudnessEnhancer，>100% 音量无法放大` 然后 `loudness = null` ——
     **静默降级**，日志里如果不专门过滤这一行，就完全看不出来。
  2. `apply()` 在 `audioSessionId <= 0` 时**直接 release 掉整条链**。若引擎不暴露 session，增益永远为零。
  3. **从没听过**（MuMu 全程 `--mute`），所以 `enabled=true` 被当成"生效了"。
- **车机反馈**：无效。

## 4. r48（`84effcdc`）—— ExoPlayer PCM 采样增益（当前主力方案）

- **假设**：LoudnessEnhancer 依赖设备音效实现，不可靠；改成**直接放大解码后的 PCM 采样**，
  与系统音效无关，逻辑上也最硬。
- **实现**（全部为 `[代码]` 级事实）：
  1. `player/.../exo/AudioGainProcessor.java`：`BaseAudioProcessor` 子类，`INSTANCE` 单例，
     `PCM_16BIT`/`PCM_FLOAT` 逐采样 ×gain，饱和；
  2. `ExoPlayer.java:58` 构造时 `setRenderersFactory(buildRenderersFactory(context))`；
  3. `ExoPlayer.java:524-541` 覆写 `buildAudioSink` → `setAudioProcessors(INSTANCE)` +
     `setOffloadMode(OFFLOAD_MODE_DISABLED)`（offload 会绕过处理器）；
  4. `CarEnhance.applyAudio`：`pcm = view.usesPcmGain()` → `view.applyPcmGain(appGain × volumeFactor)`；
  5. 同一轮还修了"设置改动不即时生效"（`BaseDialog.getContext()` 是 `ContextThemeWrapper`，
     `ownerActivity()` 判 `instanceof Activity` 恒为 false）。
- **实际证据** `[日志]`（MuMu 2880×1620）：`CarAudio runtime_apply … gain=1.80 … gain=1.98 … gain=2.0 path=pcm`。
- **为什么仍然不够（关键）**：
  1. **`path=pcm` 不具备证明力。** 这行日志位于 `setGain()` 调用**之前一行**，
     打印的是 `usesPcmGain()` 的判断结果。它只说明"我决定走 PCM 路径"，
     **没有任何信息说明 `AudioGainProcessor` 在 sink 链上、也没有说明采样被放大**。
  2. **offload 只关了一半的面。** 强制关 offload 只影响 ExoPlayer 自己的直通决策；
     若音频格式本身是 passthrough（AC3/EAC3/DTS），`onConfigure` 会抛
     `UnhandledAudioFormatException`，处理器被跳过 —— 而当时**处理器里一行日志都没有**，无从发现。
  3. **仍然没听过**（MuMu `--mute`）。所有"验证"都是日志。
- **车机反馈**：仍无效。

## 5. r55（`bef4cf99`）—— 上限按播放器能力收敛（止血，不是修复）

- **假设**：至少不要给用户一个"假 200%"——不支持的引擎就不该允许调到 200%。
- **实现**：`VolumeBoostCapable` 接口 + `ControlWrapper.supportsVolumeBoost()`；
  `BaseController.canBoostVolume()/maxVolume()/clampVolume()`；`setPlayState` → `applyVolumeLimit()`；
  到顶再上滑提示「音量100%（当前播放器不支持放大）」。
- **实际证据**：`VolumeLimitHostTest` 16 项 PASS `[代码]`；MuMu EXO 上滑 `volume_gesture 127% → 197% → 200%`，
  切到非支持引擎时 `volume_limited_by_player -> 100%` `[日志]`。
- **为什么不够**：这是**止血**。它把"假装能放大"变成了"明确不能放大"，
  用户看到的现象就固定成了「**音量无法增大**」—— 正是本次反馈的原话。
  真正的 >100% 增益在车机上依旧没有落地证据。

## 6. 为什么四轮都没打中（复盘）

| 失效模式 | 具体表现 | 代价 |
|---|---|---|
| 用"调用日志"代替"效果验证" | `path=pcm`、`enabled=true` 都被当成"生效" | 4 轮返工 |
| 用静音模拟器做音效验证 | MuMu 全程 `--mute`，从来没听过 | 所有结论都不成立 |
| 没有在处理器内部埋点 | 处理器是否被配置、采样是否被乘，全都不可见 | 无法区分"没走到"和"走到了没用" |
| 没有确认实际引擎 | 没有 `engine=` 日志；`pl` 还能被自动重试改成 IJK 并持久化 | 可能一直在测错路径 |
| 静默降级 | `LoudnessEnhancer` 创建失败、`session<=0`、编码被拒 → 都不报错 | 故障不可见 |

**r56 的存在意义就是把上表后三行全部变成可观测。**
