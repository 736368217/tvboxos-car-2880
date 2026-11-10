# r78：强化应用保活与播放会话恢复

## 1. 用户反馈

「随便切个其他应用切回来，马上放一半的片就没了，自动回到主页；稍微保活一下还是要的，不要那么容易死。」

## 2. 现场证据

r77 诊断日志（约 1 MB）中：

- 20:08:56 `HomeActivity` 重新 attach，随后 `DetailActivity` `visibilityChanged oldVisibility=true newVisibility=false`；
- 说明回前台时主页被先拉起、详情页被压到后台/销毁，符合 `HomeActivity launchMode=singleTask` 被 Launcher 重新激活时清上方 Activity 的行为；
- 旧 `PlaybackSnapshot` 只在 `PlayFragment.onPause()` 保存进度，没有记录「正在播放哪个页面」，也没有任何重建详情页逻辑；进程被杀或 Activity 栈被清后只能回主页。

## 3. 修复

### 3.1 不再让 Launcher 清播放页

- `AndroidManifest.xml`：`HomeActivity launchMode` 从 `singleTask` 改为 `singleTop`；保留 `alwaysRetainTaskState=true`。
- 普通切到其他应用再回来时，任务栈顶部仍是 `DetailActivity`，不再被主页覆盖。

### 3.2 持久化轻量播放会话

新增 `PlaybackSession` / `PlaybackSessionPolicy`：

- 保存 `active / foreground / restoring` 状态、`sourceKey/id/片名/封面/播放线路/集数/进度/时长/全屏标记/更新时间`；
- `PlayFragment.setData()` 开始会话；`onPause()` 保存进度并标记后台；`onResume()` 标记回到播放页；
- `App` 注册 ActivityLifecycleCallbacks，记录 `app_background / app_foreground`；前台事件**不清除待恢复标记**，只有真正回到播放页才清除；
- `claimRestore()` 原子领取一次恢复机会，避免 Home 多次 `onResume` 重复打开详情页；
- 用户主动返回详情页、主页双击退出时 `clear()`；系统 `onDestroy()` / `onDestroyView()` 不 clear，保证系统回收后仍能恢复；
- 会话 12 小时过期，避免几天后冷启动误回旧片子。

### 3.3 自动回播放页

- `HomeActivity` 在订阅/站源 `loadConfig.success()` 完成后以及 `onResume()` 检查待恢复会话；
- 若需要恢复，用持久化的 `id/sourceKey/title/picture` 重建 `DetailActivity`；
- 详情页加载完成后按原有 `showPreview` 逻辑自动 `jumpToPlay()`；`PlayFragment.setData()` 从 `PlaybackSnapshot` 读取进度并 `seekTo`；
- 结构化日志：`app_background / app_foreground / playback_background / playback_foreground / playback_restore`，进度、全屏状态、片名一并记录。

### 3.4 不做的事

- 不引入前台 Service、常驻通知、无障碍、SYSTEM_ALERT_WINDOW、画中画；
- 不改系统音量，不后台偷跑播放；
- 不改变用户主动退出语义：按返回/双击退出会清会话。

## 4. 验证

- `git diff --check`：PASS。
- Gradle `:app:assemblePythonRelease`：BUILD SUCCESSFUL。
- `PlaybackSessionPolicyHostTest`：PASS 6 checks（活跃+后台+新鲜恢复、非活跃不恢复、已前台不恢复、过期不恢复、缺时间不恢复、未来时间不恢复）。
- MuMu 按规范锁横屏 2880×1620、配置 `https://clun.top/box.json`；日志确认 `PyLoader/JarLoader` 成功。
- 普通切到桌面再回前台：`app_background`、`playback_background position=44131 full=false name=后会友期`，回来 `app_foreground`、`playback_foreground`，`topResumedActivity=DetailActivity`，播放继续，`CarAudioGain` 队列继续。
- 模拟 `am kill` 杀进程后冷启动：`playback_restore id=161204 position=103930 full=false` → 自动启动 `DetailActivity` → `playback_foreground`，`topResumedActivity=DetailActivity`。
- 杀进程恢复曾出现「没找到数据」：原因是订阅配置尚未加载完就拉详情。最终版已把冷启动恢复延后到 `loadConfig.success()` 之后；该时机修复通过源码与编译验证，**最终 APK 的该一次复跑因 MuMu 实例断开未完成**，如实记录。
- 签名证书 `2FEB63C5…FA9C`；vc50 / `3.1.0-car.7-r78`。

## 5. 边界

- 这是会话/任务栈级保活，不是保证进程永不被 LMK 杀；被杀后靠持久化会话恢复页面与进度。
- 若订阅源本身失效或站源不可达，详情页仍会按原有逻辑显示空态；不会伪造播放状态。
- 车机实机未做长时间后台压测；MuMu 只验证切桌面、Launcher 回前台、模拟杀进程三条路径。
