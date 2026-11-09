# r73：音量根因修正 —— 自动电平对齐（不动系统音量）

## 0. 用户要解决的到底是什么

> 「听歌的音量打开 App 看视频就感觉声音太小，调大了之后下次听歌声音又过大，
> 所以要反复调整。」

这不是「放大倍数不够」，而是**基准电平不一致**：用户在用**系统音量**补偿 TVBox，
而系统音量是音乐与视频共用的，于是来回调整。

## 1. 车机 ROM 报告能回答什么

用户提供的 `C-handoff-rom-gate-analysis-20261107.zip` 逐文件读过：全文是
「安装白名单闸门（`LeapApplicationInfoManager.filterPackage` / `whitelistUdisk` /
`installReason==64`）+ OTA 通道 + 车型适配」。全文搜 `音量/audio/volume/gain/
AudioFlinger/mixer/dB/衰减` 无任何音频结论。且包内是上一代 LEAP 3.0 平台，不是车主
B01 的 3.5a/4040 构建。**该报告不能用于诊断音量**，但它给出了车机 ADB 只读采集方式，
已转成 `09-R73-AUTO-LEVEL-ROM-CHECK.md`（备选，让车机侧 AI 验证 AudioPolicy 分区衰减）。

## 2. 硬证据：300% 其实生效了，问题在音轨太轻

r70 现场日志（`CarAudioGain`）：

```
queue n=21501 gain=3.0 enc=PCM_16BIT samples=4096 level_in=2948248 level_out=8844744
```

- `level_out / level_in = 3.000`：采样层确实放大 3 倍，**没有削顶**；
- `level_in / 4096 ≈ 720`，只占 16bit 满量程 32767 的 **2.2%（约 -33 dBFS）**；
- 3 倍后也只有 6.6% FS；而音乐母带通常接近 0 dBFS。

结论：视频音轨比音乐低十几 dB，×3 只补约 +9.5 dB，听感仍明显小。
继续提高倍数没用（会硬削顶失真），正确做法是**在 App 内把音轨基准对齐到音乐**。

## 3. 实现（App PCM 层，自己搞定，不需要 ROM 文件）

新增 `player/.../exo/AutoLevel.java`（纯 Java，可 host 测试）：

| 项 | 行为 |
|---|---|
| 测量 | 每个 PCM buffer 测峰值，约 1 秒窗口累计 |
| 目标 | 峰值向 `TARGET_PEAK=0.5`（约 -6 dBFS）靠拢 |
| 补增益 | 最多 ×4，ATTACK 0.02 / RELEASE 0.005 一阶平滑，避免抽气声 |
| 静音保护 | 峰值 ≤0.002 视为静音，不抬底噪 |
| 只补不压 | 本来就很响的音轨保持 1.0 |
| 组合 | `makeup × userGain × factor`，总增益封顶 4.0 |
| 软限幅 | 0.72 以上平滑压回 ±1，取代硬削顶（硬削顶正是「加大反而噪」的原因） |
| 生效条件 | **用户保持 100% 时**才对齐；用户主动调音量后自动让位，避免双重放大 |
| unity 直通 | `combined==1` 时逐位复制 PCM，不做浮点往返（32767 不再变 32766） |
| 0% 静音 | `combined<0.0001` 直接输出 0（fix 回归） |

接入 `AudioGainProcessor`：日志新增 `makeup / combined / peak` 字段，便于车机现场判定。
`AdvancedSettingsDialog` 新增「音量自动对齐 / 开启（推荐）」一行开关，默认开，可关。
仍**完全不改系统音量**、不引入无障碍/系统权限，300% 手势上限与 5% 档位不变。

## 4. 验证

### Host（全部 PASS）

| 测试 | 结果 |
|---|---|
| `AutoLevelHostTest` | 18 checks：低电平抬升、响音轨不压、静音不抬、组合封顶、软限幅单调、NaN |
| `AutoLevelProcessorHostTest` | 5 checks：真实 processor 跑 -33dBFS 音轨 |
| `AudioGainProcessorHostTest` | PASS（满量程断言改为软限幅语义） |
| `AudioVolumeRoutingHostTest` | **4928 checks**（含 gain=0/factor=0 静音、小增益衰减） |
| `VolumeBoostMathHostTest` 25 / `VolumeLimitHostTest` 18 / `GestureAxisHostTest` 15 / `EdgeHitPolicyHostTest` 18 / `CoverKeyHostTest` 49 / `OfficialManifestHostTest` 22 / `UpdateVersionHostTest` 95 | PASS |

`AutoLevelProcessorHostTest` 关键输出（**用户 100%，全靠自动对齐**）：

```
frames=96000 amp=720 level_in=44005575 level_out=46644211 makeup=1.06   ← 首个窗口开始抬
final level_in=1874294 level_out=7484024 ratio=3.9929829 makeup=3.9931426
passthrough diffs=0                                                     ← 关闭对齐后逐位直通
```

即：以前要手动拖到 300% 才得到 3 倍，现在**默认 100% 就得到约 4 倍且无削顶**。

### MuMu（r73）

- 安装 / 启动 / 设置页打开：**0 条 FATAL**；
- 「高级设置」可见「音量自动对齐 / 开启（推荐）」；
- 本机无可用点播源，未能在 MuMu 起播抓 `makeup` 日志 → **不编造现场听感**；
  字节级证据来自 host 的真实 processor 测试与 r70 车机现场日志。

## 5. 边界与后续

- **零跑 B01 真机听感 A/B 未做**：需要车主装 r73 后按
  `09-R73-AUTO-LEVEL-ROM-CHECK.md` 做「听歌 → 看视频 → 再听歌」三轮，不动系统音量。
- 自动对齐约 1 秒后逐步生效（平滑），不是瞬间跳变；这是刻意的，避免爆音。
- 若车机 AudioPolicy 确实按 usage 对 TVBox 额外衰减，采集清单会给出证据，
  更安全的是 App 侧适配 `AudioAttributes` 或再补一档固定增益，**不改系统**。
- 用户若不喜欢自动对齐，可在「高级设置」一键关闭，恢复纯手动 300%。

## 6. 交付

- `3.1.0-car.7-r73` / vc45，原 debug 证书；
- 官方通道 + GitHub Release，digest 与本地一致；
- 交接：`car/handover/volume-boost/09-R73-AUTO-LEVEL-ROM-CHECK.md`。
