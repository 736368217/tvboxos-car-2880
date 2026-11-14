# r87：调音量卡顿 + 音量增强是否真的生效

## 一、调音量卡顿

### 现象

播放中调节应用内音量（右侧上下滑），视频会卡顿，用户反馈「百分百触发」。

### 根因（两条，同时存在）

**① 音频渲染线程每次增益变化都写 logcat**

`AudioGainProcessor.queueInput()` 的日志条件是：

```java
boolean report = queueCalls < 3 || queueCalls % 500 == 0
        || Math.abs(combined - lastAppliedGain) > .04f;
```

音量手势每档 5%（0.05），必然超过 0.04 阈值，于是**每一档**都在音频线程上拼一个
三百多字符的字符串并写 logcat。logcat 是跨进程写，音频线程被拖慢就会欠载 —— 听感/观感
就是「调音量时卡顿」。

**② 主线程每次增益变化都做同步文件 IO**

音量手势链路是 `BaseController.slideToChangeVolume()` → `mControlWrapper.setVolume()`
→ `CarEnhance.applyAudio()`。旧版在**每一档**都执行：

- `Hawk.put(HawkConfig.VOLUME_GAIN, ...)` —— 每次一档就写一次偏好
- `DiagLog.event("user_volume=...")` → `writeMarker()` → `append()`：
  **打开文件 → 写 → 关闭 + 两次目录扫描**（`parts(dir)`），且 `append` 是 `synchronized`
- `CarEnhance.applyAudio()` 无条件拼一个含 `identityHashCode`、`getAudioSessionId`、
  `getPcmGainQueueCount()` 的大字符串并 `Log.i`

一次长滑一秒二三十档，就等于主线程每秒做几十次磁盘操作 + 几十次大字符串拼接。

### 修复

| 位置 | 改动 |
|---|---|
| `AudioGainProcessor.queueInput` | 日志限频：前 3 个缓冲 + 每 500 个缓冲 + 增益变化后**最多每秒一条**（`REPORT_MIN_INTERVAL_MS=1000`）。音频线程不再按「增益变了」写日志。 |
| `DiagLog.event` | 改为**入队 + 单线程异步写盘**；队列上限 512，满了丢弃而不是阻塞调用方。格式化与文件 IO 全部移出主线程。 |
| `CarEnhance.applyAudio` | 增加**幂等短路**：增益/闪避系数/音效模式/audio session/引擎类型都没变就直接返回，不再重复下发与拼字符串；快照日志改为增益跨过 20% 才打。 |
| `BaseController` | 滑动过程中**只做实时生效 + 刷新数字**；`Hawk.put` 与诊断打点改为**手势结束统一提交一次**（`commitVolumePreference()`），打点最小间隔 400ms。 |
| `MyVideoView.setVolume` | `app_gain` 日志阈值 1% → 25%。 |

### 验证（r87，vc59，MuMu vm6，2880×1620/d160）

同一台设备、同样 3 次长滑：

| 指标 | 修复前（r86） | 修复后（r87） |
|---|---|---|
| 每档一条 `CarAudioGain: queue` | 每档一条（≈20+/次滑） | 3 次滑共 **4 条**（仅周期日志） |
| `CarAudio: runtime_apply` | 每档一条 | 3 次滑共 **6 条** |
| `CarAudio: app_gain` | 每档一条 | **5 条** |
| `CarFeedback: user_volume` | 每档一条 | **6 条**（400ms 限频） |
| `Hawk` 写入 | 每档一次 | 手势结束 **1 次**（`volume_committed`） |
| 主线程每档磁盘 IO | 有（DiagLog 同步写） | **无**（异步队列） |
| 掉帧 | — | **2846 帧 / Janky 0 / Missed Vsync 0**（`dumpsys gfxinfo`） |

证据：`car/reports/r87-volume/gfxinfo.txt`、`volume-runtime.log`、`01-playing-volume.png`。

## 二、音量增强到底还生效吗

### 结论：生效，且是精确倍率。此前「听感不对」不是增益没作用。

用 PCM 电平对比（处理器内部 `level_out/level_in`，即真实输出/输入幅度比）实测：

| 用户档位 | 自动补偿 makeup | 组合增益 | 实测 `measured_ratio` |
|---|---|---|---|
| 125% | 1.0 | 1.25 | **1.2499603** |
| 360% | 1.0 | 3.60 | **3.5999365** |
| 385% | 1.0 | 3.85 | **3.8498833** |
| 455% | 1.0 | 4.55 | **4.5498314** |
| 360% | 4.0（该片源很轻，−18.95 dBFS） | 14.4 | **14.399849** |
| 500% | 4.0 | 20.0 | **20.0** |

即 `measured_ratio` 与 `user_gain × makeup` 完全一致，输出确实按倍率变大。
另外：

- `sink_built floatOut=false atParams=false offloadArg=false processors=1 gain=1.0`
  —— PCM 增益处理器确实挂在音频链上（`processors=1`），offload 已关闭。
- 重启进程后从偏好恢复：`user_gain=3.6` 自动生效，`measured_ratio=3.5999`。

### 那为什么「听起来像没增强」？三个真实原因

1. **自动补偿与用户增益是相乘的**。很轻的片源会被补到 4×，再乘用户档位，最高到 20×；
   而响的片源补偿是 1×。同一档位在不同片源上听感差异很大 —— 容易被读成「时灵时不灵」。
2. **防削波软限幅在起作用**。`combined` 超过 1 时，样本幅度 > 0.72 会被 `softClip` 压回，
   所以响的段落再往上拉，音量提升会被限幅吃掉一部分（实测某段 `measured_ratio` 从 14.4
   降到 13.45 就是触发了限幅）。这是刻意的，不是失效。
3. **非 PCM 引擎（IJK/系统播放器）无法真正放大**。此时代码会把上限压回 100% 并提示
   「当前播放器不支持放大」。只有 EXO（默认）走 PCM 增益。

本次没有改这三条行为：它们决定音色与削波策略，需要你按听感拍板，改错会变成「更响但失真」。

## 三、顺带确认的功能回归（r87）

| 项 | 结果 |
|---|---|
| 播放 / PCM 增益 | 正常，`measured_ratio` 与档位一致 |
| 切后台 | `playback_pause_saved position=215848` + `playback_lifecycle_pause was_playing=true` |
| 切回 | `playback_restore_reuse_live_page` + `playback_lifecycle_resume position=215858`（连续） |
| 返回 | `collapse_fullscreen` → `leave_detail session_active=false` → 首页 |
| 历史 | 「河豚[py]」「片库[py]」真实来源，无「搜」 |
| 进程被杀后恢复 | `playback_restore id=598666` 直接回到详情页 |
| Host 测试 | `ALL_HOST_TESTS_PASS`（含 VolumeBoostMath / AudioGainProcessor） |

## 四、边界

- 掉帧数据来自 MuMu（x86_64）模拟车机；真车 SoC 更慢，但本次改动把每档的主线程磁盘 IO
  与音频线程日志都去掉了，慢硬件上只会更稳，不会更差。
- 模拟器音频被池静音（`guest_streams: ok`），无法做主观听感验收；「放大多少」用
  `measured_ratio` 客观测量，不用听感代替。
- 自动补偿×用户增益的相乘策略、以及 0.72 起的软限幅阈值，本次保持原样；如需调音色另议。
