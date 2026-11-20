# 历史音量排查档案（r56–r73）

现役增强/原声开关与动态处理见 [音量 A/B 说明](../R92-AUDIO-AB.md)，设备和构建边界见 [项目 README](../../../README.md)。

> 以下保存当时“音量 >100% 失效”的排查与证据，不把旧路径、旧安装建议或未验证项当作现状。
> 所有结论都带**证据等级**，不要把它们混为一谈：
>
> - `[代码]` 读源码可验证的静态事实（含文件:行号）
> - `[日志]` 设备 logcat 里的实际输出
> - `[DEX]` 已发布 APK 内字节级存在性验证
> - `[听感]` 人耳确认 —— 本档案没有此类验收证据
> - `[未验]` 未验证的推测
>
> 本目录只谈音量，不含返回组件/更新/封面等其它工作。

## r56–r58 当时的排查状态

应用内音量 **0–100% 一直可用**（走播放器自身的 AudioTrack 音量）；
**>100% 的额外增益在车机上听不出来**。已经做过 4 轮实现：

| 轮次 | 做法 | 结果 |
|---|---|---|
| 早期 | 只把 0–100% 存 Hawk，>100% 无实现 | 100% 与 200% 完全一样 |
| r36 | 修手势累加 bug（0%→200% 只要几十像素） | 手势数值正确了，>100% 仍无增益 |
| r44 | 系统 `LoudnessEnhancer` | MuMu 上日志显示 `boostMb=600 enabled=true`；**车机上无效** |
| r48 | ExoPlayer `AudioProcessor` PCM 采样增益（不依赖系统音效） | 日志 `path=pcm gain=2.0`；**车机上仍听不出** |
| r55 | 非 PCM 引擎上限压回 100%，避免「假 200%」 | 车机上表现为「到 100% 就到顶」 |

**r58 已修复源码可确认的失败路径**：每 sink 独立 processor、关闭 passthrough、增益状态按 View 隔离，
以及 IJK 点播 >100% 时保留 URL/headers/位置切到 EXO。详见 `07-R58-FIX.md`。
当时未完成车机听觉/RMS 对照；这份档案不构成当前听感结论，也不再建议安装 r58。当前音频实现与验证入口见页首链接。

## 历史定位路径（r56 诊断流程）

1. 以下为 r56 诊断包当时的取证步骤（规格见 `06-INSTRUMENTATION.md`），不据此安装旧包；历史日志入口为：

```
adb logcat -c
adb logcat -s CarAudio CarAudioGain TVBox-runtime
```

2. 按下表分流（完整判读表见 `06-INSTRUMENTATION.md`）：

| 观察到的现象 | 结论 | 下一步 |
|---|---|---|
| `CarAudio runtime_apply … path=loudness` | 车机根本没走 PCM 增益路径（**第一嫌疑**） | `03-HYPOTHESES.md` H1 |
| `path=pcm`，但**完全没有** `CarAudioGain configure` 行 | 处理器没进音频链路 | H2 |
| 有 `CarAudioGain configure` 但 `enc=AC3/E_AC3/…` 且 `accepted=false` | 直通格式，处理器被跳过 | H2 |
| 有 `configure` + `queue n=…` 且 `level_out ≈ 2 × level_in` | 代码路径没问题，问题在设备侧音频通路 | H3 |
| `CarAudio runtime_apply … factor=0.1` | 音频焦点闪避没恢复，200% 反而更小 | H4 |

## 文件清单

| 文件 | 内容 |
|---|---|
| `00-PROBLEM.md` | 症状、设备环境、已确认/未确认边界、证据等级定义 |
| `01-ARCHITECTURE.md` | 音量链路全景图 + 关键文件精确行号 |
| `02-ATTEMPTS.md` | r28/r36/r44/r48/r55 历次尝试：假设、实现、实测证据、为何不够 |
| `03-HYPOTHESES.md` | 按可能性排序的假设 + 每条的具体证伪方法 + 修复选项 |
| `04-PITFALLS.md` | 环境/工具/日志坑，含「为什么这轮白折腾」 |
| `05-REPRO.md` | 构建/签名/安装/抓日志逐条命令，含客观测量方案 |
| `06-INSTRUMENTATION.md` | r56 诊断插桩规格、日志判读表、与 r55 的精确差异 |
| `07-R58-FIX.md` | r58 的三类源码修复、保留进度的 IJK→EXO 切换、本机验证与车机未验收边界 |
| `08-R64-LIFECYCLE-FIX.md` | r64 播放生命周期与增益恢复档案 |
| `09-R73-AUTO-LEVEL-ROM-CHECK.md` | r73 自动电平与 ROM 路由排查档案 |
| `10-R73-AUTO-LEVEL.md` | r73 自动电平实现档案，非现役算法 |
