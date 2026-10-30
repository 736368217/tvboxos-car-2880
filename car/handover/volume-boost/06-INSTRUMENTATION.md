# 06 · r56 诊断插桩规格（本次唯一新增内容）

> **r56 不改变任何行为**，只加可观测性。它的存在意义：把「没走到增益」和「走到了没用」分开。
> 版本：`versionCode 29` / `versionName 3.1.0-car.7-r56`。
> 产物 SHA-256 `02FAD567D31D2DD8B17BEC7FEDF234961807960AB74BB4FC12288686C1E854D4`。

## 1. 相对 r55 的全部改动

| 文件 | 改动 | 目的 |
|---|---|---|
| `player/.../exo/AudioGainProcessor.java` | 新增 `CarAudioGain` 日志：`configure` / `queue`（含 `level_in`/`level_out`）/ `set_gain` / `configure REJECTED`；新增 `appliedGain()`、`queueCount()`；所有日志走 `log()` 包装（吞异常） | **证明处理器是否在链上、采样是否被放大** |
| `app/.../player/ExoPlayer.java` | `buildRenderersFactory` 打 `renderers_factory=<类名>`；`buildAudioSink` 打 `sink_built floatOut=… atParams=… offloadArg=… processors=1 gain=…` | **证明 sink 覆写真的被执行** |
| `app/.../player/MyVideoView.java` | `setVolume` 打 `app_gain request=N% playerVolume=… pcmCapable=… engine=…`（0.01 变化触发） | 证明手势→View 的链路与引擎身份 |
| `app/.../car/CarEnhance.java` | `runtime_apply` 增加 `factor=`、`applied=`、`engine=`、`pcmStored=`、`pcmQueued=` | 一次拿到应用侧全部状态 |
| `app/build.gradle` | `versionCode 29` / `versionName 3.1.0-car.7-r56` | 版本可识别 |

**没有改动**：任何音量计算、上限、播放器选择、UI 行为。

## 2. 日志规格

### 2.1 `CarAudioGain`（新 tag，全部来自处理器内部）

| 日志 | 触发 | 关键字段 |
|---|---|---|
| `set_gain <old> -> <new> (queue_calls=N)` | gain 变化 ≥0.05 | 增益是否被写进来 |
| `configure n=N enc=PCM_16BIT sampleRate=48000 channels=2 accepted=true gain=G` | 每次 configure（前 20 次） | **处理器在链上**；编码 |
| `configure REJECTED enc=AC3 -> gain cannot be applied` (W) | 编码不可处理 | **直通导致增益失效** |
| `queue n=N gain=G enc=… samples=S level_in=A level_out=B` | 前 3 次 + 每 500 次 | **采样真的被乘了**（`B ≈ G × A`） |

> `flush()` / `reset()` 在 `BaseAudioProcessor` 里是 **final**，无法挂钩，所以计数是**单调累加**的；
> 「会话切换」只能靠 `configure n=` 的连续性推断。

### 2.2 `CarAudio`（在 r55 基础上扩充）

```
runtime_apply mode=<音效模式> session=<audioSessionId> gain=<appGain> factor=<volumeFactor>
              applied=<appGain×factor> path=pcm|loudness boostMb=<0|LoudnessEnhancer 目标>
              enabled=<音效链是否挂上> state=<播放状态> player=<hash>
              engine=<引擎类名> pcmStored=<处理器当前 gain> pcmQueued=<处理器已处理 buffer 数>
sink_built floatOut=<bool> atParams=<bool> offloadArg=<bool> processors=1 gain=<当前 gain>
renderers_factory=<工厂类名>
app_gain request=<N>% playerVolume=<0..1> pcmCapable=<bool> engine=<引擎类名>
```

## 3. 判读矩阵（拿到车机日志后照这个走）

| 观察 | 结论 | 去处 |
|---|---|---|
| `runtime_apply … path=loudness` | 没走 PCM 路径（引擎不是 app 的 EXO，多半是 IJK） | **H1** |
| `path=pcm` 但**没有** `sink_built` | `buildAudioSink` 覆写没执行 | **H2a** |
| 有 `sink_built`，**没有** `CarAudioGain configure` | 处理器没被 configure（sink 未被使用 / 换了 sink） | **H2a/H7** |
| `configure … accepted=false` + `configure REJECTED …` | 直通格式（AC3/EAC3/DTS），处理器被跳过 | **H2b** |
| 有 `configure` + `queue`，且 `level_out ≈ gain × level_in` | 采样确实被放大 → 问题在设备侧通路 | **H3** |
| 有 `configure` 但 `queue` 不增长，且 `runtime_apply` 的 `pcmQueued` 也不涨 | 该 sink 没在跑（另一个播放器实例在出声） | **H7** |
| `runtime_apply … factor=0.1` | 音频焦点闪避没恢复 → 200% 反而更小 | **H4** |
| `app_gain request=200%` 但 `engine=<某个 IjkMediaPlayer>` | 用户在 IJK 上测 | **H1** |
| 完全没有 `CarAudio` 任何行 | 播放根本没经过 `MyVideoView`（例如直播页 = 普通 VideoView） | 见 `01-ARCHITECTURE.md` §3 |

## 4. 如果还要继续加插桩（经验）

1. **不要覆写 `flush()`/`reset()`**（final，编译期就会失败）。
2. `android.util.Log` 必须包 `try/catch(Throwable)`：`car/tests/AudioGainProcessorHostTest`
   会在纯 JVM 上直驱这个类，`Log` 是 `Stub!` 实现会抛异常。
3. 日志要**节流**：音量拖动会连续触发 `setVolume`（每次 move 一个事件），
   不节流会瞬间刷几千行。
4. 想证明"放大真的发生"，唯一可靠的指标是**输入/输出采样幅度比**（本插件用的 `level_in/level_out`），
   而不是"我调用了 setGain"。
5. 若要在**设备侧**再加一层证据，可以在 `ExoPlayer.buildAudioSink` 里
   打印 `DefaultAudioSink` 的实际输出参数（`AudioTrack` 的 sampleRate/encoding/通道），
   或在 sink 外层包一个 `AudioSink` 代理记录 `handleBuffer` 调用次数。

## 5. 已知不足（交给下一位）

- 插桩**只在 EXO 路径**有；IJK（`LoudnessEnhancer`）路径依然只有 `runtime_apply` 一行，
  建议后续给 `AudioEffectController.apply()` 的每个分支都加显式日志（尤其 `session<=0` 与
  `loudness == null` 两个静默降级点）。
- 只有 `queue` 的前 3 次 + 每 500 次会打印，**长时间播放时看不到实时增益**；
  调试时可以把这两个阈值临时调小。
- 没有把增益写进播放页 UI（用户看不到"当前生效值"），
  若要彻底消除"数字能调但没效果"的困惑，建议在播放页显示实际生效的 `applied=`。
