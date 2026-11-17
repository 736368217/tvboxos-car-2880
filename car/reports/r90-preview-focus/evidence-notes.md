# r90 设备实测原始记录

以下为本次设备会话（MuMu vm6，2880×1620/d160，r90 vc62）中实际抓到的 logcat 行，
按时间顺序原样抄录；截图见同目录 `01-preview-no-badges.png`。

## 1. 预览小窗不再显示诊断徽标

截图 `01-preview-no-badges.png`：详情页小窗只剩视频与字幕，分辨率 / 帧率 / 占用率
三块徽标都不再出现（对照用户反馈的截图：同一位置曾出现三层白底徽标互相压字）。

## 2. 别的窗口抢焦点 → 暂停；焦点回来 → 续播

```
11-17 22:06:00.019  2002  2002 I CarFeedback: playback_focus_pause
   （此时 dumpsys window: mCurrentFocus=Window{6d7ad u0 NotificationShade}）

11-17 22:07:57.962  2002  2002 I CarFeedback: playback_focus_resume
   （此时 dumpsys window: mCurrentFocus=Window{1a39507 u0 ...DetailActivity}）
```

覆盖窗是 SystemUI 的通知栏：它抢焦点但不让 Activity 走 onPause，正是「弹窗盖住画面、
视频还在后台出声」的那类窗口。

## 3. 新引擎/换集后音量立即到位

同一会话重启进程后重新起播：

```
11-17 22:16:44.790 53157 53157 I CarAudio: sink_built floatOut=false atParams=false
        offloadArg=false processors=1 gain=5.0

11-17 22:16:45.434 53157 53269 I CarAudioGain: queue n=1 user_gain=5.0 makeup=1.0
        makeup_locked=false locked_value=1.0 source_dbfs=-18.68822 combined=5.0
        peak=0.11630249 samples=2048 frames=1024
        level_in=2785089 level_out=13925409 measured_ratio=4.999987

11-17 22:16:45.435 53157 53269 I CarAudioGain: queue n=2 user_gain=5.0 makeup=1.0
        ... combined=5.0 ... measured_ratio=5.0
```

即：新引擎在**建 sink 时就是 5.0**（不是默认 1.0），**第 1 个缓冲**的输出倍率已经是
`4.99999`，不存在「先小声、动一下音量条或等一会才变大」的窗口。

## 4. 播放中的增益倍率（对照）

```
11-17 22:04:11.246 I CarAudioGain: queue n=10001 user_gain=5.0 makeup=1.5384037
        combined=7.6920185 ... measured_ratio=7.6919007
```

用户档位 500% × 自动补偿 1.538 = 7.69，实测 7.6919，一致。

## 说明

- 设备在本轮多次被管理端关停（`shell.log` 里是 `action:close`，不是 APK 崩溃），
  最后一段 logcat 未能导成文件；上面的行抄自同一次会话的实时输出，时间戳可对照。
- 「自家弹窗不暂停」由 `VolumeFocusRegressionHostTest` 守住源码不变量
  （`BaseDialog.isAnyVisible()` + 失焦回调里的放行判断），未再单独做设备回归。
