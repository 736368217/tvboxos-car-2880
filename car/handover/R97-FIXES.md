# r97 · 日志全自动上传 + 播放器写死 EXO

版本 `3.1.0-car.7-r97` / versionCode 69。只针对 B01。

## 1. 诊断日志：全自动，不再依赖按钮

r96 已把 `DiagLog` 的传输从停用的 `dl.z736.vip` 改到 `dl.736.de5.net/logs/tvboxos-car-2880/`，
但节奏仍是 20 s 首传 + 120 s 间隔，且只有设置页那个按钮能立刻触发。r97 改为：

| 项 | 值 |
| :--- | :--- |
| 首次上传 | 启动后 **5 s**（原 20 s） |
| 周期上传 | **60 s**（原 120 s） |
| 切后台 | `App` 的 `onActivityStopped`（最后一个 Activity）立刻 `uploadNow()` |
| 批次读满 | 单批 ≥ 半量时 `drainNow`，下一个 tick 继续 |
| 手动按钮 | 保留，但只是「额外手段」，不再是被依赖的路径 |

**实测**（MuMu，r97 装机后不点任何按钮）：云端陆续出现
`tvbox-…-b-5.12204-5.256051-….json.gz`、`…-b-5.256051-6.25946-….json.gz`、`…-b-6.25946-6.37925-….json.gz`
三个批次，说明启动、周期与后台三条路径都在自动上传。

## 2. 播放器：写死 EXO，不再能切换

IJk 没有 PCM 增益：>100% 音量不可用、动态处理（`CarAudioDynamicsEngine`）也不生效，而 r96 之前
为了让音量突破 100% 还要中途切换引擎——正是「调音量把影片调重播」的来源。r97 直接钉死：

- `App.initParams()`：`PLAY_TYPE` 与 `LIVE_PLAY_TYPE` 一律写 2（EXO），历史值不再保留；
- `PlayerHelper.updateCfg(...)`：站源配置里的 `pl < 10` 全部改写为 2；`pl ≥ 10` 是「调外部播放器」，
  不属于内置引擎切换，保持原样；
- 设置页「播放器」行显示 `EXO（固定）`，选择器里只剩 EXO 一项。

因此 `PREFER_VOLUME_BOOST_EXO` 这类「下次起播换引擎」的偏好不再需要，`requestVolumeBoost`
在 EXO 下也不会被调用（`supportsVolumeBoost()` 恒为 true）。

**实测**（MuMu，r97）：推一路 HTTP mp4 播放，日志只有
`I/ExoPlayerImpl: Init …` 与逐秒 `I/CarAudioGain: queue n=… mode=CAR_BALANCED …`，
没有任何 `IjkMediaPlayer` 初始化记录。

## 3. 验证

| 检查 | 结果 |
| :--- | :--- |
| `:app:assemblePythonRelease :app:lintPythonRelease` | 成功 |
| `car/ci/verify-b01-apk.py` | `B01_APK_PASS`，`minSdk=32`、`arm64-v8a`、65020790 B、`5a237f72…38c2` |
| MuMu 装机 | `versionCode=69 / versionName=3.1.0-car.7-r97` |
| 引擎 | 仅 `ExoPlayerImpl` + PCM 增益日志，无 IJk |
| 自动上传 | 未点按钮即产生 3 个新批次对象 |
| R2 / GitHub | `latest.json` 与 Release 资产均为 `5a237f72…` |

**证据边界**：本轮仍没有车机实拍；面板拖拽外观、carclash 实际包名要等车上运行后的上传日志
（`back: … carClash=…`）确认。
