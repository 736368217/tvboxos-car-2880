# r71：基线核对 + 音量状态分裂 / 返回条长驻 / 保活 / 手势主轴

## 0. 先回答「是不是从老版本改的、有没有丢另一个会话的更新」

**没有丢。** 开工时 HEAD 是 `975f6980`（r70），`git log --graph --all` 是严格线性：

```
975f6980 r70 (诊断 v2)          ← 本会话上一轮
a3a96dd3 r69 (订阅封面修复)     ← 会话 a131414b 的改动，是本轮 HEAD 的直接父提交
6c721487 r68 (封面/日志/300%/球) ← 本会话
60631c7b r67 ...
```

`git merge-base --is-ancestor a3a96dd3 HEAD` 与 `6c721487 HEAD` 都返回 0；
reflog 里 `f9f9ca0d → a3a96dd3` 是那次会话自己的 amend，不是被我覆盖。
另一个工作区 `D:\github\tvboxos-car-2880` 在 `feature/car-ux-redesign-3k-fps`，与 main 无关，
只有几个未跟踪文件，未纳入本次打包。本轮所有改动都从 r70 出发，r71 是它的直接子提交。

## 1. 为什么「300% 和 100% 一个声音」——不是采样没放大

用 r70 真机上传的诊断日志（v2 只读拉取，6926 行）确认：

```
configure accepted=true gain=3.0
set_gain 2.95 -> 3.0
queue n=21501 gain=3.0 enc=PCM_16BIT level_in=2948248 level_out=8844744
```

`level_out / level_in = 3.000`，**PCM 采样层确实放大了 3 倍**。所以问题不在上限常量，
而在状态同步：

- `BaseController.applyVolumeLimit()` 在每次 PREPARED/PLAYING（包括 `onResume` 触发）
  把增益夹到引擎能力后 `setVolume(effective)`；能力探测短暂返回 false 时 `effective=1f`，
  view/processor 被压成 1f，但 Hawk 与 UI 仍是 300%。
- `onDown` 只把 `mAppVolume` 重载成 3f，**不重发**；`slideToChangeVolume` 到 300% 时
  因差值 <0.002 提前 return → controller 显示 300%，实际输出 1x。
- 另有物理限制：16bit 满量程样本在 2x/3x 都削顶，强电平片源听感可相同。

修法：
- `setPlayState()` 改为 `reapplyAppVolume()`：从 Hawk 读用户偏好，能力可用时**直接下发 3f**
  并补一次广播（防 sink 重建），不可用时只压本次运行值、绝不改写偏好；
  打 `volume_reapply preferred=… effective=… supported=…` 便于现场判定。
- 这样切窗口回来 `onResume → PLAYING` 也会把 300% 重新灌回 processor，而不是压成 1f。

## 2. 返回悬浮窗长时间显示、关不掉

现场日志 `InAppBack: vis mode=edge …` 出现 **147 次**。每次 `applyVisibility()` 都无条件
`setVisibility(VISIBLE)` + `setActive(true)`，而 `EdgePullBackView.setActive(true)` 把
`visibleAlpha` 复位为 1 并重排隐藏计时；host 又在每次页面 DOWN 调 `wake()` —— 计时被无限重置。
r68 只修了 `FloatingBackBallView`，**默认模式是边缘条**，所以现象依旧。

修法：
- `applyVisibility()` 只在 `showBall/showEdge/showSwipe` 真的变化时才碰子 View，
  日志改成 `vis_changed`；实机从 147 次降到 2 次。
- `wake()` 加 1 秒冷却，阻止车机重复派发的 DOWN 刷计时。
- `wakeAt(x,y)` + `EdgePullBackView.wakeFromEdge()`：隐藏后只有落在边缘热区的触摸才把条
  拉回来，页面中央的连续触摸不再反复点亮。
- 实机：边缘热区点击后 4 秒，左侧条带像素变化 1471/29058 → 确实淡出。

## 3. 保活：切窗口回来只能回主页

当前 HEAD 的真实代码里**没有** `CarPlaybackSession`（调查代理这部分是幻觉，已按实际文件复核）：
`PlayFragment.onPause()` 只有 `pause()`，`HomeActivity.onResume()` 没有任何恢复逻辑，
Manifest 无 `launchMode` / `alwaysRetainTaskState`。所以系统回收任务栈后只能落到 HomeActivity。

修法（按实际代码，不引入不存在的类）：
- 新增 `cache/PlaybackSnapshot.java`：在 `onPause` 保存 `sourceKey / vodId / name / pic /
  playFlag / playIndex / position / duration`，**小窗、预览、音频播放一律保存**。
- `setData()` 消费快照：>1s 的进度在起播后 1.8s `seekTo`，并清掉快照防止冷启动误回旧片。
- Manifest 给 HomeActivity 加 `alwaysRetainTaskState=true` + `launchMode="singleTask"`，
  保证从桌面/其他窗口回来保留任务栈，不重建到主页根。
- 边界：进程被彻底杀掉后的完整「自动回到详情页」仍未做（需要 App 单例 VodInfo 仍在或
  重新解析），当前保证的是任务栈保留 + 播放进度恢复。不编造未做的验证。

## 4. 触屏：一次手势只做一件事

旧 `onScroll` 在**首个 MOVE** 用逐帧 `abs(distanceX) >= abs(distanceY)` 一次性定轴、零阈值、
相等偏进度。想上下拉音量时起始的一点横向噪声就被判成进度，之后整段锁死，最后 ACTION_UP
真的 `seekTo` —— 正是你说的「没那么正拉斜了就自动调进度」。

修法：
- 新增纯逻辑 `car/GestureAxis.java`：用**按下点 → 当前点的累计位移**定轴，
  低于 touchSlop×2 不动；主轴需 >1.2 倍占优；45° 左右含糊方向 `IGNORE`（宁可不动也不误触）。
- `BaseController`：`onDown` 记 `mDownX/Y` 与 `AXIS_NONE`，`onScroll` 调 `GestureAxis.decide`
  并锁定，`onTouch` 的 UP/CANCEL 解锁；进度只在本次锁定为 X 轴时提交。
- `GestureAxisHostTest` 15 项断言含三条「歪的垂直拉仍是音量」、三条「斜 45° 放弃」全 PASS。

## 5. 验证

- `git merge-base --is-ancestor` 双检通过；`git diff --check` 干净。
- `:app:assemblePythonRelease` BUILD SUCCESSFUL；`GestureAxisHostTest` 15、
  `AudioVolumeRoutingHostTest` 4928、`VolumeBoostMathHostTest` 25、`VolumeLimitHostTest` 18、
  `AudioGainProcessorHostTest`、`CoverKeyHostTest` 49、`OfficialManifestHostTest` 22、
  `UpdateVersionHostTest` 95 全 PASS。
- MuMu（Android 15 / 2880x1620）：r71 安装启动无 `AndroidRuntime/FATAL`；`vis_changed` 2 次；
  边缘条 4 秒后像素差证明淡出；最终包签名仍是原 debug 证书。
- 现场 r70 日志来自 `tvbox-5fbd191b-…`，经 v2 只读接口拉取，未包含任何凭据或日志正文
  写入本仓库。

## 6. 未覆盖 / 风险（如实说明）

- 没有在真机（零跑 B01）上做 300% 听感 A/B、切窗口回收任务栈、车机触屏斜拉这三项现场复测；
  MuMu 证据 + 现场 r70 日志是上述判断依据。r71 自带 v2 诊断日志可继续远程分析。
- 300% 仍受 PCM 16bit 满量程削波限制；`level_in/level_out` 与 `volume_reapply` 是判据。
- 保活目前只保证任务栈与进度，不保证「进程被杀后自动重建详情页并续播」。
- `PlaybackSnapshot` 用 `SharedPreferences.apply()`，进程被瞬间杀可能来不及落盘；
  若现场仍丢进度，下一步应改 `commit()` 或在 `onStop` 再存一次。
