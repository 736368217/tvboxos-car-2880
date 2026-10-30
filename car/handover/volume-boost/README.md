> **本文档对应的代码树**：`D:\github\tvboxos-upstream-feature-pack`（git 分支 `main`，r56 提交）。
> 与本文档所在工作区同远端（`736368217/tvboxos-car-2880`），但**不是同一个分支**：
> 若你手上是 `feature/car-ux-redesign-3k-fps`（`versionName 1.0.0-car.7`）等分支，
> 文中行号、以及 `AudioGainProcessor.java` / `CarEnhance.java` 这些文件都对不上，请先切到 `main`。

# 音量 >100% 失效 —— 专注交付包

> 交接对象：接手「应用内音量 >100% 在车机上听不出变化」这个具体问题的人。
> 所有结论都带**证据等级**，不要把它们混为一谈：
>
> - `[代码]` 读源码可验证的静态事实（含文件:行号）
> - `[日志]` 设备 logcat 里的实际输出
> - `[DEX]` 已发布 APK 内字节级存在性验证
> - `[听感]` 人耳确认 —— **本项目至今一条都没有**（见下方边界声明）
> - `[未验]` 未验证的推测
>
> 本目录只谈音量，不含返回组件/更新/封面等其它工作。

## 一句话现状

应用内音量 **0–100% 一直可用**（走播放器自身的 AudioTrack 音量）；
**>100% 的额外增益在车机上听不出来**。已经做过 4 轮实现：

| 轮次 | 做法 | 结果 |
|---|---|---|
| 早期 | 只把 0–100% 存 Hawk，>100% 无实现 | 100% 与 200% 完全一样 |
| r36 | 修手势累加 bug（0%→200% 只要几十像素） | 手势数值正确了，>100% 仍无增益 |
| r44 | 系统 `LoudnessEnhancer` | MuMu 上日志显示 `boostMb=600 enabled=true`；**车机上无效** |
| r48 | ExoPlayer `AudioProcessor` PCM 采样增益（不依赖系统音效） | 日志 `path=pcm gain=2.0`；**车机上仍听不出** |
| r55 | 非 PCM 引擎上限压回 100%，避免「假 200%」 | 车机上表现为「到 100% 就到顶」 |

**根因至今未定**。原因不是方案本身，而是**验证手段从头就是错的**：每一轮只用
「应用侧日志」证明「我调用了」，从来没有证明「采样值真的变大了」，更没有证明
「扬声器真的变响了」。见 `00-PROBLEM.md` 的边界声明。

## 最短定位路径（一次播放即可分流）

1. 装 r56 诊断包（规格见 `06-INSTRUMENTATION.md`），在车机播放一集，抓：

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
