# r76：按车机实测反馈修正返回手势、音量上限与日志

## 1. 用户反馈与现场证据

用户实测三条：

1. 「返回边缘滑动，几乎要占据半个屏幕」；
2. 「历史记录页面滑动返回直接失效，之前提过」；
3. 「音量还是太小，和音乐没同步；上限改成 500%，我要自己调整；日志收集详细点」。

诊断 v2 最新日志（r74/r75 设备）确认：

- `mode=swipe host=2880x1620 scale=3.375`，旧 `SWIPE_MAX_DP=320` 换算成 **1080px**（37.5% 屏宽），叶形半高 `170×3.375≈574px`，观感接近半屏；
- `attach HistoryActivity ... show=true edge=true` 存在，但没有 `host_down/back_perform` 之类手势链路，说明此前没修到分发层，且日志粒度不足；
- `gain=3.0 makeup=1.0 combined=3.0 autoLevel=false`，用户常驻 300% 时自动对齐完全让位，轻音轨仍比音乐低十几 dB。

## 2. 修复

### 2.1 边缘不再占半屏

新增 `EdgeGeometry`，与 AutoSize `tokenScale` 解耦，全部按真实屏幕像素封顶：

| 项目 | 2880×1620 上限 | 旧实现 |
|---|---:|---:|
| 命中带 | 40–86px（实测 40px） | 135–数百 px |
| 视觉最大宽度 | 230px（8%） | 1080px（37.5%） |
| 触发距离 | ≤173px（6%） | 284px 起 |
| 叶形半高 | 178px（11% 高） | 574px |

`EdgePullBackView` / `EdgeSwipeBackView` / `InAppBackHostLayout` 统一改用 `EdgeGeometry`；`geom` 日志新增 `hitPx / visualMaxPx / leafHalfMaxPx`。

### 2.2 历史页返回失效

`InAppBackHostLayout.dispatchTouchEvent()` 旧逻辑只在 `lastShowEdge=true` 且宿主自己预判命中时，才把 DOWN 转给 `edge`；AutoSize 刷新/可见性状态时序稍滞后就会整条手势丢失。

r76 改为：

- 所有页面 DOWN 无条件交给当前激活的 `edge/swipe` 视图自行判定；
- 命中才建立 `mEdgeCapture + mEdgeCaptureView`，MOVE/UP 完整转发给同一视图；
- 空白区仍 `return super.dispatchTouchEvent()`，不拦截内容；
- 新增 `host_down / host_move / host_up / back_perform`，带 `page / mode / x / y / hit / capture`。

实机：历史页 `host_down page=HistoryActivity hit=true` → `back_perform` → `host_up`，页面从 `HistoryActivity` 回到 `HomeActivity`。

### 2.3 音量 500%、自动对齐与日志

- 全链路上限 `3f→5f`：`VolumeBoostMath`、`ControlWrapper`、`AudioGainProcessor`、`AudioEffectController`、`HawkConfig` 注释；非 PCM `MAX_BOOST_MB=1400`；手势仍是 5% 档位。
- `AutoLevel.MAX_TOTAL_GAIN=4→20`；`AudioGainProcessor` 删除「用户增益≠100% 就不自动对齐」的条件，自动对齐始终叠加；用户手动增益与自动补增益相乘，由软限幅守满量程。
- `CarAudio / CarAudioGain` 日志新增：`max_gain / auto_level / user_gain / target_peak / peak_dbfs / expected_peak / measured_ratio / auto_samples`；`volume_gesture` 新增 `max / auto_level / pcm_capable`；`DiagLog.event()` 同步写 `CarFeedback` logcat，便于把反馈和操作一一对应。
- 高级设置切换返回模式/自动对齐时写结构化 EVENT。

实机：连续上滑到 `-> 500% max=500%`，`gain=5.0 combined=5.0 measured_ratio=4.50`（软限幅使高电平片段不精确 5 倍）；260% 时 `combined=2.6156 = 2.6 × makeup 1.006`，证明自动对齐不再让位。

## 3. 验证

- `git diff --check`：PASS。
- Gradle `:app:assemblePythonRelease`：BUILD SUCCESSFUL。
- host 回归全 PASS：`EdgeGeometry 10`、`EdgeHitPolicy 18`、`GestureAxis 15`、`CoverFormat 27`、`CoverKey 49`、`OfficialManifest 24`、`UpdateVersion 95`、`VolumeBoostMath 25`、`VolumeLimit 18`、`AutoLevel 18`、`AutoLevelProcessor 5`、`AudioGainProcessor`、`AudioVolumeRouting 4958`。
- MuMu 按规范锁横屏 2880×1620、配置 `https://clun.top/box.json`，`JarLoader/PyLoader` 成功；`geom hitPx=40 visualMaxPx=230 leafHalfMaxPx=178`。
- 历史页返回、500% 手势、自动对齐日志均见上节硬证据。
- 签名证书 `2FEB63C5…FA9C`，vc48 / `3.1.0-car.7-r76`。

## 4. 边界

车机零跑 B01 的「听歌→看视频→再听歌」听感 A/B 未做；MuMu 只证明 PCM 链路和日志数值，不替代车机听感结论。若用户仍觉偏轻，下一轮按 `peak_dbfs / makeup / combined / measured_ratio` 判断是「音轨基准低」还是「系统/ROM 输出衰减」，不再靠猜。
