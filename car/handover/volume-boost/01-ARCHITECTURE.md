# 01 · 音量链路与架构全景

> 行号以本目录所属提交（r56 诊断版）为准。r55 及之前：`MyVideoView` 相关行号 −5、
> `ExoPlayer.java` 中 `buildAudioSink` 相关行号 −8、`CarEnhance.applyAudio` 日志行 −1。

## 1. 全景图

```
 用户滑动 / 方向键
      │
      ▼
 BaseController.slideToChangeVolume(deltaY)          app/.../player/controller/BaseController.java
      │  mVolumeStart + deltaY/H * VOLUME_SLIDE_RANGE(=1.0)  → clampVolume()
      │  clampVolume → maxVolume() = canBoostVolume() ? 2f : 1f
      ▼
 mAppVolume (0..2)  ──持久化──▶  HawkConfig.VOLUME_GAIN (百分比 int)
      │
      ▼
 ControlWrapper.setVolume(v)                          player/.../controller/ControlWrapper.java:99
      │  v = clamp(v, 0, 2);  然后 ((VideoView) mPlayerControl).setVolume(v, v)
      ▼
 MyVideoView.setVolume(v1, v2)                        app/.../player/MyVideoView.java:341
      │  gain = max(v1, v2)
      │  base = VolumeBoostMath.playerVolume(gain) = min(gain, 1)
      ├─ super.setVolume(base, base) ──▶ VideoView.setVolume ──▶ mMediaPlayer.setVolume
      │        └─ ExoMediaPlayer.setVolume((l+r)/2) ──▶ SimpleExoPlayer.setVolume  (0..1, AudioTrack 音量)
      └─ CarEnhance.setAppGain(this, gain)            app/.../car/CarEnhance.java:91
               │  appGain = clamp(gain, 0, 2)          ← ★ 进程级 static
               ▼
          CarEnhance.applyAudio(view)                  :63
               │  pcm = view.usesPcmGain()  = (mMediaPlayer instanceof com...player.ExoPlayer)
               ├─ pcm  → view.applyPcmGain(appGain * volumeFactor)   →  AudioGainProcessor.INSTANCE.setGain(..)
               │           └─ AudioGainProcessor.queueInput: 逐采样 ×gain，饱和到 16bit/float 范围
               └─ !pcm → VolumeBoostMath.millibel(...)  →  AudioEffectController.apply(mode, session, boostMb)
                           └─ LoudnessEnhancer / Equalizer / BassBoost / Virtualizer（系统音效，上限 +12 dB）
               ▼
          Log.i("CarAudio", "runtime_apply mode=… session=… gain=… factor=… applied=… path=pcm|loudness …")
```

`path=pcm` 只说明 `usesPcmGain()` 为真、`setGain()` 被调用（在日志的下一行），
**不代表处理器在 sink 链上**。见 `00-PROBLEM.md` F9。

## 2. 逐段事实（含行号）

| 环节 | 文件:行 | 要点 |
|---|---|---|
| 手势累加 | `BaseController.java` `slideToChangeVolume()`（≈441-478） | `VOLUME_SLIDE_RANGE = 1.0f`；基于 `mVolumeStart`，**不是**每个 move 事件累加（r36 修的 bug） |
| 上限 | `BaseController.java` `canBoostVolume()` / `maxVolume()` / `clampVolume()`（≈127-137） | r55 新增；非 PCM 引擎封顶 1.0 |
| 起点加载 | `BaseController.java` `loadAppVolume()`（≈139-141） | 读 `HawkConfig.VOLUME_GAIN`，默认 100 |
| 状态回落 | `BaseController.java` `setPlayState()` → `applyVolumeLimit()`（≈249-268） | 切播放器后把已存的 180% 压回 100%，日志 `volume_limited_by_player` |
| 控制器 | `ControlWrapper.java:99-103` | `setVolume` 钳 0..2；`supportsVolumeBoost()` 在 `:113-117` |
| View | `player/.../player/VideoView.java:1050-1056` | `setVolume(v1,v2)` 存 `mVolumeLeft/Right` 并转发给播放器 |
| View（车机） | `MyVideoView.java:341-352` | 拆成 base + gain；`usesPcmGain()` 在 `:372`、`supportsVolumeBoost()` 在 `:378`、`applyPcmGain()` 在 `:383` |
| 音频总控 | `CarEnhance.java:63-83` | `applyAudio`：main 线程、`pcm` 分流、打印 `CarAudio` 日志 |
| 静态状态 | `CarEnhance.java:37`（`appGain`）、`:40`（`volumeFactor`） | ★ 进程级共享；`setVolumeFactor()` 在 `:105` |
| 系统音效 | `AudioEffectController.java:73-84`、`:136-143` | `apply()` 在 `session <= 0` 时直接 `release()`；`LoudnessEnhancer` 创建失败只打 `loudness_unavailable` |
| PCM 增益 | `player/.../exo/AudioGainProcessor.java` | 见 §5 |
| sink 注入 | `app/.../player/ExoPlayer.java:524-541` | `buildAudioSink` 覆写：`setAudioProcessors(INSTANCE)` + `OFFLOAD_MODE_DISABLED` |
| 工厂注入 | `ExoPlayer.java:58` + `:137-156`；`ExoMediaPlayer.java:73-82` | 构造函数先 `setRenderersFactory`，`initPlayer()` 里 `mRenderersFactory != null` 才用它 |
| 焦点闪避 | `AudioFocusHelper.java:56-78` | `CAN_DUCK → duckVolume(0.1f)`；`GAIN → restoreVolume()`（**必须收到 GAIN 事件**） |

## 3. 「到底用了哪个引擎」——最容易翻车的一环

```
PlayFragment.startPlay()                            app/.../ui/fragment/PlayFragment.java
  ├─ pl 来源（:1376-1402）：
  │     mVodPlayerCfg = new JSONObject(mVodInfo.playerCfg)      ← 该影片上次存下的配置（历史/收藏里持久化！）
  │     没有 "pl" → sourceBean.getPlayerType()（站点指定）→ 再回退 Hawk(PLAY_TYPE, 2)
  │     pl == 0  → 强制 2（EXO）
  ├─ DASH/MPD 直链 → updateCfg(view, cfg, 2) 强制 EXO（:1011-1018）
  ├─ 普通链接 → updateCfg(view, cfg)（:1020）→ PlayerHelper 按 pl 建工厂
  └─ 播放失败自动重试（:1671-1675）：
        pl 被改写成另一个引擎，并写回 mVodInfo.playerCfg ← ★ 会持久化到历史记录里
```

判定结论：

- **VOD 默认是 EXO**（`HawkConfig.PLAY_TYPE` 默认 2，`PlayerHelper.java:38`），因此在默认路径上
  PCM 增益**应该**生效。
- 但 **`pl` 会被站点配置或自动重试改成 1（IJK），而且写进历史记录**。一旦某部片被切到 IJK，
  之后每次播这部片都还是 IJK —— 此时 `path=loudness`，r55 起上限 100%，
  用户看到的现象正是「音量无法增大」。
- 直播（`LivePlayerManager`）用的是**普通 `VideoView`**（`activity_live_play.xml:10`），
  完全不经过 `MyVideoView`/`CarEnhance` → 直播永远没有应用内增益。

## 4. 三层音量，别混

| 层 | 谁控制 | 应用能否改 | 影响 |
|---|---|---|---|
| 系统音量 / 车机音量键 | 系统 `AudioManager` STREAM_MUSIC | 否（除非申请改系统音量，本项目没做） | 全链路的最终上限，应用够不着 |
| 播放器音量（AudioTrack） | `SimpleExoPlayer.setVolume(0..1)` | 是（0..100% 这一段） | 线性的 0–100% |
| 应用内 PCM 增益 | `AudioGainProcessor` | 是（100–200%） | 采样级 ×2（+6 dB），**与系统音量无关** |

> 如果用户在调**系统音量**（方向盘/音量条），应用内增益完全帮不上忙 —— 这是 H5。

## 5. `AudioGainProcessor` 的行为与限制

- 单例：`public static final AudioGainProcessor INSTANCE`，被 `buildAudioSink` 静态塞进 sink。
- `setGain` 钳 0..2，`queueInput` 内逐 buffer 读取（改动一个 buffer 内生效）。
- **只接受 `ENCODING_PCM_16BIT` 与 `ENCODING_PCM_FLOAT`**；其它编码抛 `UnhandledAudioFormatException`
  → 在 `DefaultAudioSink.configure()` 里会变成 `ConfigurationException`，处理器（乃至整条音频链）被跳过或播放报错。
  直通（passthrough AC3/EAC3/DTS）走的就是这条路。
- offload 被强制关闭（`DefaultAudioSink.OFFLOAD_MODE_DISABLED`），因为 offload 会绕过 `AudioProcessor`。
- **sink 是通过覆写 `DefaultRenderersFactory.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, enableOffload)` 注入的**；
  只要 `ExoPlayer` 用的是自己那个 `SubtitleOffsetRenderersFactory`，注入就成立（F2/F3）。
- `flush()` / `reset()` 在 `BaseAudioProcessor` 里是 **final**，无法挂钩（r56 编译期实测），
  所以「会话是否重启过」只能靠 `configure` 日志推断。

## 6. 已知的结构性弱点（读代码即可确认，无需设备）

1. **静态共享**：`CarEnhance.appGain` / `volumeFactor` 是进程级，任何一路 VideoView 的
   `setVolume` / `setMute` / `duckVolume` 都会改写全局值，而 `AudioGainProcessor.INSTANCE` 又是全局单例。
   → 详情页小窗、返回播放页、切集等场景存在互相覆盖的可能（H7）。
2. **闪避不恢复**：`volumeFactor` 被 `duckVolume(0.1f)` 压到 0.1 后，只有收到 `AUDIOFOCUS_GAIN`
   且 `!isMute()` 才恢复；在此期间 PCM 增益 = `appGain × 0.1`，**200% 会比 100% 更轻**。
3. **直播没有增益**：直播用普通 `VideoView`，连 `CarEnhance` 都不经过。
4. **IJK 没有 PCM 增益**：`IjkPlayer.setVolume` 直接把 0..1 转给 ijk，没有采样级 hook
   （`player/.../ijk/IjkPlayer.java:193-195`）。IJK 唯一可能的放大手段是系统音效（LoudnessEnhancer）。
5. **`path=pcm` 自证不足**：日志点位于 `setGain` 之前一行，永远无法证明处理器在链上（F9）。
