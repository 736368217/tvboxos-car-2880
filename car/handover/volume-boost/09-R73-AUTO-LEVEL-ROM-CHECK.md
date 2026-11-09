# 车机侧 AI 执行单：验证「系统是否对 TVBox 音量做额外衰减」（只读，不改系统）

> 背景：App 侧已确认 PCM 采样层 `gain=3.0` 时 `level_out/level_in=3.0`（确实放大 3 倍、
> 未削顶），但视频音轨基准只有约 -33 dBFS，而音乐接近 0 dBFS。r73 已在 App PCM 层加入
> 自动电平对齐（100% 默认即可把轻音轨抬到约 ×4），**不需要改车机系统**。
> 本清单仅用于排查「是否还有 ROM 层按 usage/stream 的额外衰减」这一剩余变量。
> 全部命令只读；遵守 A/B/C 包里的 R0–R4 规约，不提权、不写 system、不改音量。

## 1. 采集目标

判断车机 `AudioPolicy` 是否对 TVBox（`com.github.tvbox.osc.jun`）使用的
`USAGE_MEDIA / STREAM_MUSIC` 有与音乐 App 不同的衰减曲线（厂商音量表/功放映射）。

## 2. 只读命令（Wi-Fi ADB，`adb -s <SERIAL> shell ...`）

```bash
# 2.1 当前各 stream 音量与范围（TVBox 播放中、音乐 App 播放中各采一次）
dumpsys audio | grep -iE 'STREAM_MUSIC|streamVolume|indexMin|indexMax|device|VolumeStreamState'
cmd media_session dump | head -80
dumpsys media.audio_flinger | grep -iE 'active|track|usage|volume|mixer' | head -80

# 2.2 厂商音量曲线/分区配置（关键）
ls -l /vendor/etc/ /system/etc/ | grep -iE 'audio|volume|policy|mixer|gain'
# 常见文件名：audio_policy_configuration.xml、audio_policy_volumes.xml、
#            default_volume_tables.xml、mixer_paths*.xml、*volume*curve*.xml
# 找到后逐个 cat / adb pull，重点看 <volume stream="AUDIO_STREAM_MUSIC" ...> 的 <point> 表

# 2.3 车载音频分区（零跑常见 CarAudioService 动态路由）
dumpsys car_service | grep -iE 'audio|volume|group|zone|context|usic|edia' | head -120
dumpsys audio | grep -iE 'car|zone|group|context|bus|address' | head -120

# 2.4 确认 TVBox 播放时实际 usage / AudioAttributes
dumpsys media.audio_flinger | grep -A4 -B2 'com.github.tvbox.osc.jun'
dumpsys audio | grep -A6 -B2 'com.github.tvbox.osc.jun'
# 记录：usage / content_type / flags / stream type / device / volume left,right

# 2.5 播放瞬间 logcat（同时采 App 的 CarAudioGain 作对照）
adb logcat -v threadtime -s AudioFlinger AudioPolicyManager CarAudioService CarAudioGain AudioTrack 2>&1
```

## 3. 对照实验（车主手动，AI 只记录，不改音量）

1. 用音乐 App 播一首熟悉的歌，**不动系统音量**，记 `dumpsys audio` 的 STREAM_MUSIC index；
2. 退出音乐，打开 TVBox r73 播同一段视频，**仍不动系统音量**，再记一次 index；
3. 两次都抓 `CarAudioGain` 的 `gain / makeup / combined / peak / level_in / level_out`。

判据：

| 现象 | 结论 |
|---|---|
| 两次 STREAM_MUSIC index 相同，且 TVBox `makeup` 收敛到 3–4、`level_out/level_in≈makeup` | **ROM 无额外衰减**，App 侧对齐已生效，无需改系统 |
| TVBox 被路由到不同 device/bus/zone，且该 zone 的 volume point 明显更低 | ROM 有分区衰减，见 §4 建议 |
| TVBox 的 usage 不是 `USAGE_MEDIA`（例如被识别成 `USAGE_NOTIFICATION` 或其它） | App 侧 `AudioAttributes` 需要对齐，我来改 |
| `level_out/level_in` 明显小于 `makeup` | 还有后级 DSP/功放限幅，需结合 mixer_paths 判断 |

## 4. 若确有 ROM 衰减，给车机 AI 的建议（不要直接改，先回报）

- 只报告：哪个 XML、哪一行、哪个 device/bus、哪个 zone、与音乐 App 的差值 dB；
- **不要**改 `audio_policy_volumes.xml` / 刷机 / 提权（R4）；
- 更安全的方向是 App 侧适配：把 TVBox 的 `AudioAttributes.usage` 对齐到音乐 App，
  或在 App PCM 层按测得的差值再补一档固定增益（我这边做，不动系统）。

## 5. 回传格式（贴给主会话即可）

```
构建号 / 车型：
STREAM_MUSIC index（音乐 / TVBox）：
TVBox usage / device / zone：
相关 XML 路径与 volume point 片段：
CarAudioGain 最后 5 行（gain/makeup/combined/peak/level_in/level_out）：
结论（ROM 有/无额外衰减）：
```

> 不需要回传完整 ROM 镜像或 services.jar；本任务只要上面几段文本输出。
