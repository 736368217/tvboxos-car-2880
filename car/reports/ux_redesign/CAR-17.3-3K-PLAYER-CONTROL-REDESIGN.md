# CAR-17.3-3K 全屏播放页控件重构

## 结论

全屏播放页已从“两排常驻按钮 + 隐藏横向高级条”改为成熟播放器结构：

1. 顶部：返回、标题、分辨率/补帧信息、可选屏显、锁屏。
2. 底部：进度条 + 一排主控。
3. 主控只保留 `上一集 / 后退 15 秒 / 播放暂停 / 前进 15 秒 / 下一集 / 选集 / 更多`。
4. 画面、音效、超分、补帧、字幕、音轨、换源、倍速、弹幕、后台播放、屏显等收进“播放设置”分组面板。
5. 播放器、解码、重播、刷新、投屏、片头/片尾等再收进“高级”，默认不出现。

## 交互结构

### 主控行

- `play_pre`：上一集；单集影片自动隐藏；第一集禁用。
- `uxRewind`：后退 15 秒。
- `uxPlayPause`：主操作，224×124，实心紫底、白色文字/图标。
- `uxForward`：前进 15 秒。
- `play_next`：下一集；单集影片自动隐藏；最后一集禁用。
- `uxEpisodes`：选集；单集影片自动隐藏。
- `uxMore`：打开/收起播放设置；打开时选中态高亮。

主控行使用统一高度：普通按钮 108px、主按钮 124px、工具按钮 104px；全部垂直居中，不再使用 `drawableTop` 造成图标/文字错位。图标统一通过 `setStartIcon` 设为 28–42px，并保留 12px 图文间距。

### 播放设置面板

面板尺寸 2440px，底部停在主控岛上方，只覆盖视频区，不遮挡播放/暂停：

- 画面与声音：画面预设、座舱音效、超分、补帧、画面比例。
- 播放与字幕：倍速、换源、字幕、音轨、视轨。
- 弹幕与工具：弹幕、搜弹幕、后台播放、屏显。
- 高级：播放器、解码、重播、刷新、投屏、横竖屏、片头、片尾、重置。
- 解析线路：仅在源提供解析列表时显示。

面板打开后仍可直接操作底部主控；点击“更多”、关闭按钮、面板外视频区或返回键都能收起。

## 视觉调整

- 新增顶部/底部电影感渐变 scrim，只在控件显示时出现。
- 新增统一的圆角主控岛、选项卡片、圆形图标按钮选择器。
- 进度条轨道从 3px 提升到 10px，缓冲进度用紫色、当前进度用薄荷绿。
- 主控岛背景使用 `#0A1813 → #12291F` 渐变 + 薄荷绿细描边。
- 主按钮使用 `#6C5CE7 / #A78BFA` 紫系，和现有图标系统配色一致。
- 选中/焦点/按下三态统一；`更多` 打开时保持选中高亮。
- 移除了原第二行常驻的 7 个增强按钮和底部 15 个横向控件堆叠。

## 代码变更

- `app/src/main/res/layout/player_vod_control_view.xml`
- `app/src/main/res/values/ux_scene.xml`
- `app/src/main/java/com/github/tvbox/osc/player/controller/VodController.java`
- `app/src/main/java/com/github/tvbox/osc/ui/fragment/PlayFragment.java`
- `car/tests/native/Car7Acceptance.java`
- 新增播放器专用 drawable：
  - `car_player_top_scrim.xml`
  - `car_player_bottom_scrim.xml`
  - `car_player_dock_bg.xml`
  - `car_player_panel_bg.xml`
  - `car_player_panel_scrim.xml`
  - `car_player_round_icon_selector.xml`
  - `car_player_transport_selector.xml`
  - `car_player_primary_selector.xml`
  - `car_player_option_selector.xml`
  - `car_player_seek.xml`

## 验证

最终 APK：

- 路径：`C:\Users\WIN10\.pi-desktop\scratch\1fc58544-545a-4f33-9c56-de975a6fb08b\TVBox-1.0.0-car.7-player-redesign.apk`
- SHA-256：`ff7a7081e6e3ea44463ccc4530e4f55b66c829638176810f7a599bb247f5f1ee`
- 大小：`53,670,931 bytes`
- 包名：`com.github.tvbox.osc.jun`
- 版本：`versionCode 8` / `versionName 1.0.0-car.7`

MuMu 实例 0：

- `CAR_DISPLAY_PASS`，物理 `1620x2880`，旋转锁 `1`，density `160`
- `playback`：`101 passed / 0 failed`
- `ux3k`：`94 passed / 5 failed`，播放器相关项全部通过；失败项为既有直播 fixture
- `pages`：`74 passed / 6 failed`，播放器相关项全部通过；失败项为直播 fixture/favorite 与 gallery-live 返回宿主
- `gestures`：亮度/音量手势通过；3 项返回模式/皮肤默认值断言失败与本次播放器无关
- 控件树几何：主控行、更多面板均无越界/重叠

证据：`car/qa/ux-audit-car7-current/evidence-ff7a7081/`

## 保留能力

返回、手势亮度/音量/进度、播放/暂停、快退/快进、上一集/下一集、选集、换源、音效弹窗、字幕、音轨、视轨、超分、补帧、画面预设、播放器/解码、后台播放、屏显、锁屏等能力全部保留；仅调整了入口层级。
