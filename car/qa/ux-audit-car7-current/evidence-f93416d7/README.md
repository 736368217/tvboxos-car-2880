# TVBox Car 3K 外部走查复核证据（最终）

## 身份

- APK SHA-256：`f93416d784de3f463485421fc5f35c75f0e2fbc049592cf59dc90d6a1e54dba3`
- 大小：`53,679,236 bytes`
- 包名：`com.github.tvbox.osc.jun`
- 版本：`versionCode 8` / `versionName 1.0.0-car.7`
- 设备：MuMu 实例 1，物理 `1620x2880`，显示 `2880x1620`，density `160`，`CAR_DISPLAY_PASS`

## 本轮最终补齐

- M-24：详情兜底失败空态新增“重新加载”和“返回上一页”两个明确入口；`manual/detail-retry.png`。
- M-11：搜索源弹窗新增“选择搜索源 · N 个站点”标题与说明；`manual/search-source-dialog.png`。
- M-02：配置弹窗聚焦输入框后整体仍在屏内；MuMu 无软键盘，`manual/api-dialog-focused.png` 仅证明焦点与布局，不声称完成真机 IME 验收。
- M-01 / M-09 / M-18 / M-19 / M-25 保持上一轮修复。

## 回归

- `pages`：`80 passed / 0 failed`
- `ux3k`：`98 passed / 0 failed`
- `playback`：`101 passed / 0 failed`
- `gestures`：`7 passed / 0 failed`
- `states`：`5 passed / 0 failed`

结果 JSON 与 identity 位于 `tests/`，均绑定上述 APK SHA。

## 截图

`screenshots/` 为页面与播放器截图；`manual/` 为针对性复核：

- `history-empty.png`、`search-empty.png`、`nosource-toast.png`
- `detail-retry.png`、`search-source-dialog.png`
- `api-dialog.png`、`api-dialog-focused.png`

背景彩条/灰度块是本地验收视频，不是产品 UI；`state-*` 为实际 Android 状态组件截图，不是网络事件录像。
