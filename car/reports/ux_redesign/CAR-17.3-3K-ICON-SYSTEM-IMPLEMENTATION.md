# CAR-17.3 3K 全量图标系统实施报告

## 交付物

- APK：`TVBox-1.0.0-car.7-icon-system-final2.apk`
- SHA-256：`9998629679fb9b5e24fc34c77d218ad253805430becc5df13ec04b2d5c9ae46b`
- 大小：`53,662,234 bytes`
- 包名：`com.github.tvbox.osc.jun`
- versionCode `8` / versionName `1.0.0-car.7`
- 设备证据：`car/qa/ux-audit-car7-current/evidence-99986296/`

## 实施范围

按 `TVBox-3K-Icon-System-Optimized.zip` 的 Android handoff 落地：

- 61 枚功能图标 default + 61 枚 active，统一 `ic_tvbox_*` 前缀，24×24 viewport VectorDrawable。
- 108dp Adaptive 图标三层：`ic_launcher_background.xml`、`ic_launcher_foreground.xml`、`ic_launcher_monochrome.xml`。
- `mipmap-anydpi-v26` 方形/圆形 adaptive 入口，以及 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi 五档 legacy PNG。
- 首页品牌改为 C·ORBIT 轨道标 `ic_tvbox_brand_orbit.xml`，Launcher 改由 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round` 提供。
- 图片失败占位改为 `ic_tvbox_placeholder.png`，保留 `BitmapFactory` 兼容路径。

## 页面接入

- 首页：7 个左侧导航图标、品牌标、搜索、播放/选集按钮；导航使用 default/active selector，焦点或当前页切换为全薄荷 active。
- 详情：返回、播放、智能换源、收藏、投屏、快速搜索、完整简介、复制地址、排序、播放源标题。
- 播放器：主控、快退/快进 15 秒、上一集/下一集、选集、更多、画面预设、超分、补帧、音效、换源、画面比例、倍速、字幕、音轨、视轨、弹幕、搜弹幕、全屏、锁屏、后台播放、解码器；超分/补帧/锁屏/后台播放/播放暂停按状态切换 active 图标。
- 设置/高级：左侧 7 个分类、动态设置行、画面/音效/返回页签均加语义图标。
- 直播：返回、已收藏、设置、全屏、换源、收藏、EPG/回看、空态、播放/暂停。
- 搜索/文件/空态：搜索、键盘、筛选、聚合、热词、删除、清空、文件类型、空态、错误、加载。
- 对话框：确认/取消、音效九项选择、画面预设沿用统一弹窗主题。

## 视觉系统

将原有冷青/蓝/琥珀与冷蓝面板统一到图标包 HYBRID 视觉：

- 页面/导航：`#06100E` / `#0A1813`（旧 `car_bg #070A10` 作为兼容别名保留）
- 卡片/控件：`#0E211A` / `#12261D`
- 主强调：`#6FF2C6`
- 次强调：`#A78BFA`
- 正文：`#E9FFF8`，弱文本 `#9AB8AB`
- 弹窗面板与选择态同步改为深绿/薄荷/紫；旧青色和琥珀色在 UI 截图中残留为 0（纯色计数）。

## 验证结果

| 项目 | 结果 |
|---|---|
| `:app:assemblePythonRelease` | BUILD SUCCESSFUL |
| APK 安装后 `base.apk` SHA | `9998629679fb9b5e24fc34c77d218ad253805430becc5df13ec04b2d5c9ae46b` |
| `apksigner verify` | v1/v2/v3 全部 true |
| `run_host_checks.py --core-only` | 全绿 |
| `test_handover.py` | 11/11 OK |
| MuMu `gestures` | 7 passed / 0 failed |
| MuMu `playback` | 101 passed / 0 failed |
| 全屏返回 | 通过：点击 `[60,40][240,130]` 的全屏返回后回到详情，再点详情返回回首页 |
| 音效弹窗 | 通过：9 项选择，取消与选中均正常 |
| 页面几何 | 12 页 uiautomator XML，越界节点 0 |
| 页面覆盖 | 首页、详情、播放器、音效、设置、高级、直播、搜索、聚合搜索、收藏、历史、推送、本地文件 |

`gestures` 数值：亮度 `0.35 -> 1.0`，音量 `4 -> 100`。

## 证据文件

- 页面 PNG/XML 与总览：`car/qa/ux-audit-car7-current/evidence-99986296/pages/`
- 手势：`.../tests/gestures/`
- 播放：`.../tests/playback/`
- 主机检查：`.../tests/host-checks.json`
- APK 身份：`.../identity.json`

## 说明与边界

- 图标包提供的 `action_voice` 因当前产品没有语音入口，未强行绑定；其余功能图标已按语义接入。`ic_tvbox_action_check`、`ic_tvbox_content_star`、`ic_tvbox_file_qr` 已分别用于确认、推荐角标和推送二维码说明。
- 未改包名、versionCode、versionName、签名、订阅源或第三方脚本。
- 未提交 APK、keystore、凭据、私有偏好或原始远端响应。
- 本报告与证据绑定上述 APK SHA；旧 SHA 的截图不作为本次交付证据。
