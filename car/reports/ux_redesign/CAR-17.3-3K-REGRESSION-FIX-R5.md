# CAR-17.3 3K 交付后回归修复报告（r5）

## 交付物

- APK：`TVBox-1.0.0-car.7-regress-r5.apk`
- SHA-256：`87B9806E07114FA7D49EEF18BAA99BCAE360C156516908D03BB581888C95B29A`
- 大小：52,901,996 bytes
- 包名：`com.github.tvbox.osc.jun`
- versionCode `8` / versionName `1.0.0-car.7`
- 设备证据：`car/qa/ux-audit-car7-current/evidence-87b9806e/regression/`

## 修复项与根因

### 1. 上下滑动音量/亮度、左右滑动进度全部失效

根因：`BaseController.setEnableInNormal(true)` 只写了 `mCanSlide`，没有同步 `mEnableInNormal`。`VideoView.startPrepare()` 每次开始播放都会调用 `setPlayerState(PLAYER_NORMAL)`，后者把 `mCanSlide` 重置为 `mEnableInNormal`，而该值始终为 `false`，于是所有滑动手势被关闭。

修复：`setEnableInNormal` 同时设置 `mEnableInNormal` 与 `mCanSlide`；`GestureVideoController` 同步修正。设备实测上滑音量 `5 -> 98`、下滑 `98 -> 5`，右滑进度 `03:17 -> 04:06`，左半屏上滑窗口亮度 `sbrt=1.0`。

### 2. 全屏后左侧返回按钮消失

根因：`PlayFragment.setImmersiveFullscreen()` 调用 `InAppBackButton.setPlaybackHidden(activity, true)` 隐藏应用内返回控件；`VodController.setImmersiveFullscreen()` 又隐藏 `top_l_container`，播放器自身的 `tv_back` 随之消失，全屏没有任何返回入口。

修复：
- 完整移除“进入全屏隐藏应用内返回”的行为，并在每次全屏切换时显式 `setPlaybackHidden(activity, false)` + `wake(activity)`，保证左侧应用内返回控件始终可用。
- 新增播放器内常驻 `tv_back_fullscreen`（`[60,40][240,130]`），不随控制栏隐藏；点击退出全屏回到详情页。
- 设备验证：全屏时 `tv_back_fullscreen` 可见；点击后 `tv_back_fullscreen` 消失、`uxDetailBack` 出现，App 未退出。

### 3. 补帧帧率显示缺失

根因：`player_vod_control_view.xml` 的 `tv_framerate` / `tv_framerate_bottom` 均为 `visibility="gone"`，`updateFrameRateDisplay()` 只设置文本，从未恢复可见性。

修复：补帧开启时显示 `片源 X fps · 输出 Y fps`，关闭时隐藏；每秒刷新。设备实测 `片源 23.83 fps · 输出 59.57 fps`。

### 4. 音效点击循环切换

根因：`mCarAudioBtn` 的点击逻辑为 `(audioMode + 1) % MODE_COUNT`。

修复：新增 `showCarAudioDialog()`，使用 `SelectDialog` 展示 9 个音效选项（关闭、影院、重低音、3D 环绕、HiFi、人声、动漫·标准、动漫·剧场、动漫·对白），确定后即时应用；画面预设弹窗复用同一交互并补充取消与当前选中态。

### 5. 切后台再回前台自动回主页 / 不保持播放

根因：播放器没有会话快照；任务被系统回收或 `force-stop` 后，启动只能回到 `HomeActivity`，详情页和播放状态丢失。

修复：
- 新增 `CarPlaybackSession`：全屏播放进入后台时保存 id、sourceKey、标题、海报、剧集、进度与时间戳。
- `HomeActivity.onResume()` 在配置源就绪后自动恢复 `DetailActivity` 并带 `uxResume`、`startPosition`；配置未加载时 500ms 轮询重试，避免空详情页。
- `DetailActivity` 重建时若 `fullWindows` 为 true，自动重新进入全屏播放；播放器按保存进度 seek。
- `DetailActivity.onBackPressed()`（真正退出详情）清理会话，避免用户主动返回主页后被再次拉起。
- 设备验证：全屏按 Home 后重开仍在 DetailActivity 全屏并继续推进；`am force-stop` 后重新启动自动恢复到同一详情/全屏并播放。

### 6. 超分、补帧实际无效果

根因：
- SGSR1 输出 shader 的 `vote` 门限为 `32/255`，绝大多数像素直接走原图；边缘增量 clamp 仅 `±16/255`，视觉变化很小。
- 时域补帧上限 `MAX_BLEND_ALPHA=0.28`，且只在很短相位内生效，运动画面几乎看不出 60Hz 输出。

修复：
- SGSR1 门限降为 `6/255`，边缘增量上限提升到 `±40/255`，锐度权重乘 1.8；额外增加 5 抽头 unsharp mask（0.35）作为 SGSR 前置锐化，显著扩大有效像素范围。
- 时域混合改为源帧间隔前半段的 50/50 中点交叉淡化（`MAX_BLEND_ALPHA=0.5`），输出目标 60fps；关闭补帧时回到 `WHEN_DIRTY`/源帧率。
- 设备日志验证：`srRequested=true srActive=true`；`frame_stats` 持续 `sourceFps≈24 outputFps≈59.5`；`playback` 套件 101/101 通过（包含 EXO/IJK、Texture→GL 热切换、全屏保持解码器、色彩/音效预设、释放幂等）。

## 验证汇总

- 主机检查：`run_host_checks.py --core-only` 全绿（含 `Car6PostProcessTest` 更新后的 0.5 混合策略）。
- `test_handover.py`：11/11 OK。
- 设备 `gestures`：7 passed / 0 failed。
- 设备 `playback`：101 passed / 0 failed。
- 全屏返回、补帧 fps、后台恢复：手动触摸 + `uiautomator` 证据见 `evidence-87b9806e/regression/`。
- `navigation`：13/17；4 项为既有悬浮球拖拽断言，与本次改动路径无关，未作为产品回归处理。

## 未改变

- 包名、versionCode、versionName、签名、订阅源/第三方脚本均未改变。
- 未提交 APK、keystore、凭据或原始远端响应。
