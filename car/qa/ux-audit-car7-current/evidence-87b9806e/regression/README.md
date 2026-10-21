# Car.7 交付后回归修复验收（87b9806e）

- 目标 APK：`TVBox-1.0.0-car.7-regress-r5.apk`
- SHA-256：`87B9806E07114FA7D49EEF18BAA99BCAE360C156516908D03BB581888C95B29A`
- 大小：52,901,996 bytes
- 包名 / 版本：`com.github.tvbox.osc.jun` / `1.0.0-car.7` (versionCode 8)
- 设备：MuMu 实例 1，物理 `1620x2880`、`160dpi`、横屏锁定；`CAR_DISPLAY_PASS`

## 设备验收结果（均绑定上述 SHA）

| 套件 | 结果 | 证据 |
|---|---|---|
| `gestures` | 7 passed / 0 failed | `gestures-result.json`、`gestures-identity.json` |
| `playback` | 101 passed / 0 failed | `playback-result.json`、`playback-identity.json` |
| 全屏返回 | `tv_back_fullscreen` 在 `[60,40][240,130]`；点击后回到详情页且详情返回键在 `[60,40][240,136]`，应用未退出 | `manual-fullscreen.xml`、`manual-fullscreen-after-back.xml` |
| 补帧帧率 | 控制栏显示 `片源 23.83 fps · 输出 59.57 fps`；同时超分·开、补帧·开 | `manual-controls-fps.xml` |
| 手势（实机值） | 上滑音量 `5 -> 98`；下滑音量 `98 -> 5`；右滑进度 `03:17 -> 04:06`；左半屏上滑窗口亮度 `sbrt=1.0` | 手势套件 + 手动触摸记录 |
| 后台保持 | 全屏播放按 Home 后重新打开仍在 DetailActivity 全屏且继续推进；`am force-stop` 后重启由 HomeActivity 自动恢复同一详情/全屏会话 | 手动验收 |
| 音效弹窗 | 音频按钮打开 `座舱音效` 选择弹窗（关闭/影院/重低音/3D环绕/HiFi/人声/动漫…），不再点击循环切换 | `manual-audio-dialog.xml` |

## 说明

- `gestures` 套件中的亮度/音量断言由 `Car7Acceptance` 直接发送 `MotionEvent`，已覆盖上下滑动音量/亮度和左右滑动进度。
- 全屏返回采用双入口：新增播放器内常驻 `tv_back_fullscreen`，同时不再调用 `InAppBackButton.setPlaybackHidden(..., true)`，应用内左侧返回控件保持可用。
- `navigation` 套件本轮 13/17；4 项失败均为既有悬浮球拖拽断言（`dragging ball uses1.08 scale` 等），与本次手势、全屏返回、后台恢复、音效弹窗和渲染链路改动无关，未作为产品回归处理。
