# r90：预览小窗叠加、失焦暂停、换集音量

用户车机实拍 + 口述反馈三件事，全部修在 r90（vc62）。

## 一、预览小窗上的文字互相压字（截图里的白色叠层）

### 现象

详情页小窗（`DetailActivity` 的预览播放器）里，左上角同时出现：

- `1920 X 1080 → 超分 1057 X 594`（分辨率徽标，按 2880 宽设计）
- `片源 24.4 fps · 输出 54.86 fps`（帧率徽标）
- `CPU … · 应用 7%`（占用率徽标，右上）

小窗只有约 1057×594，徽标按整屏宽度排，结果三块白底互相压住，还盖住画面与字幕。

### 根因

`VodController` 的诊断文本更新（`myRunnable2` / `updateFrameRateDisplay`）与
`setPreviewMode` 无关：无论全屏还是小窗，每秒都刷新并 `setVisibility(VISIBLE)`。
`buildVideoSizeText` 还会把「超分目标尺寸」写成小窗的渲染尺寸，数值也没有意义。

### 修复

- 新增 `applyDiagnosticsVisibility()`：`previewMode` 时把分辨率 / 帧率徽标设为 `GONE`。
- `setPreviewMode()` 立即应用（进入/退出小窗即时生效）。
- `myRunnable2`：小窗不刷新分辨率文本、不生成占用率文本（顺带省掉每秒的采样拼串）。
- `updateFrameRateDisplay()`：徽标可见性跟随 `previewMode`。
- `buildVideoSizeText()`：小窗直接返回原始分辨率，不再追加「→ 超分 WxH」。

## 二、别的弹窗占到焦点，视频还在后台出声

### 根因

之前只在 `Activity.onPause`（页面切走）时暂停。系统权限框、安装器、通知栏、
其它应用的弹窗都是**独立窗口**：它们抢焦点但不会让 `DetailActivity` 走 `onPause`，
所以播放器继续在后台出声。

### 修复

- `DetailActivity.onWindowFocusChanged()`：失焦 → `playFragment.onWindowFocusLost()`，
  得焦 → `onWindowFocusGained()`。
- `PlayFragment`：失焦时若正在播放则暂停并记 `pausedByFocusLoss`；得焦时只续播这一次
  （用户手动暂停不会被自动续上）。
- `BaseDialog` 增加**可见弹窗计数**（`show()` 加、`dismiss()` 减，`isAnyVisible()`）：
  自家弹窗（选集、设置、切源、更新提示等继承 `BaseDialog` 的 20+ 个）不触发暂停，
  避免「点开选集就把视频停了」这种误伤。

## 三、刚打开 / 换集时声音很小，动一下音量条或等一会才变大

### 根因

两层叠加，都是 r87 那次「幂等短路」的副作用：

1. 新引擎实例的 `ExoPlayer.audioGain` 是字段默认值 **1.0**，只有 `setAudioGain()` 被调用
   才会变成用户档位；而 PCM 增益处理器是**动态读取** `owner::getAudioGain`，
   所以新引擎一开始就按 1.0 出声。
2. `CarEnhance.applyAudio()` 的幂等短路按「增益 / 闪避 / 音效 / session / 引擎类型」判断，
   新引擎的这几个值可能和上次完全相同（session 复用时），于是这次**必要**的下发被吞掉。
   等 AudioTrack 换 session、或用户动一下音量条（改变 gain）才补上 —— 正好对应
   「过一会儿自己变大 / 拉一下音量条就变大」。

### 修复

- `ExoPlayer.initPlayer()`：先用 `HawkConfig.VOLUME_GAIN` 播种 `audioGain`，
  新引擎建 sink 时就是用户档位（实测 `sink_built ... gain=5.0`）。
- `CarEnhance.applyAudio(view, force)`：新增强制通道，忽略幂等短路。
- `MyVideoView.setOptions()`（新起播）与 `setPlayerState(PREPARED/PLAYING)`（换集/恢复）
  都走 `force=true`，保证新引擎立即拿到正确增益。

## 验证

### 宿主机

新增 `car/tests/VolumeFocusRegressionHostTest.java`（21 项）守三条源码不变量：
预览态必须隐藏三类徽标、失焦必须暂停且自家弹窗放行、prepare/PLAYING 必须强制下发增益
且 `ExoPlayer` 必须播种 `audioGain`。完整 suite：`ALL_HOST_TESTS_PASS`。

### 设备（MuMu vm6，2880×1620/d160，r90 vc62）

| 验收点 | 证据 |
|---|---|
| 小窗不再叠字 | `car/reports/r90-preview-focus/01-preview-no-badges.png`（只剩视频与字幕） |
| 覆盖窗抢焦点暂停 | `playback_focus_pause` + `mCurrentFocus=NotificationShade` |
| 焦点回来续播 | `playback_focus_resume` + 焦点回到 `DetailActivity` |
| 新引擎增益播种 | `sink_built ... processors=1 gain=5.0` |
| 第一帧即满增益 | `queue n=1 user_gain=5.0 combined=5.0 measured_ratio=4.999987` |
| 倍率准确 | 500% × 补偿 1.538 = 7.69，实测 `measured_ratio=7.6919` |

原始日志抄录见 `car/reports/r90-preview-focus/evidence-notes.md`。

## 边界

- 「自家弹窗不暂停」靠宿主机不变量 + `BaseDialog` 计数保证；设备侧本轮未再单独回归
  （设备多次被管理端关停）。
- 失焦暂停只在 `DetailActivity`（唯一的播放宿主）实现；若将来新增播放页，需要同样接。
- 模拟器音频被池静音，听感仍无法主观验收；上面用 `measured_ratio` 与 `sink_built gain`
  作为客观证据。
