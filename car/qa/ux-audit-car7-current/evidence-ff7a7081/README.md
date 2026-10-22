# 全屏播放页控件重构验收证据

## 身份

- APK：`C:\Users\WIN10\.pi-desktop\scratch\1fc58544-545a-4f33-9c56-de975a6fb08b\TVBox-1.0.0-car.7-player-redesign.apk`
- SHA-256：`ff7a7081e6e3ea44463ccc4530e4f55b66c829638176810f7a599bb247f5f1ee`
- 大小：`53,670,931 bytes`
- 包名：`com.github.tvbox.osc.jun`
- 版本：`versionCode 8` / `versionName 1.0.0-car.7`
- 安装后 guest `base.apk` SHA 与上表一致。
- 签名：v1 / v2 / v3 均通过。

## 车机显示门禁

- MuMu 实例 0，物理 `1620x2880`，旋转锁 `1`，density `160`。
- `verify_car_display.sh`：`CAR_DISPLAY_PASS`。
- 显示：`mCurrentDisplayRect=Rect(0, 0 - 2880, 1620)`。

## 截图

- `player/ux-player.png`：全屏播放页主控。
- `player/ux-player-more.png`：更多/播放设置面板。
- 对应控件树：`player/ux-player.json`、`player/ux-player-more.json`。

主控实测几何（2880x1620）：

- 主控岛：`[60,1291][2820,1568]`
- 播放/暂停：`[1328,1414][1552,1538]`，`224x124`
- 后退 15 秒：`[1100,1422][1314,1530]`
- 前进 15 秒：`[1566,1422][1780,1530]`
- 上一集/下一集：`150x108`，与后退/前进同排居中
- 选集：`[2462,1424][2622,1528]`
- 更多：`[2634,1424][2784,1528]`
- 更多面板：`[220,592][2660,1264]`，不遮挡底部主控岛
- 关闭：`90x90`；高级：`190x90`；锁屏：`90x90`

## 自动化

- `playback`：`101 passed / 0 failed`
- `ux3k`：`94 passed / 5 failed`
  - 播放器相关检查全部通过：主控高度、播放/暂停、前进/后退、更多面板、长按倍速、全屏返回等。
  - 5 项失败全部为既有直播 fixture 路径：`live fixture playing` 及其后续全屏/返回断言，与本次播放器控件重构无关。
- `pages`：`74 passed / 6 failed`
  - 播放器相关检查全部通过，包括 `play_car_audio` 弹窗选择后的同会话音效、`play_car_sr`、`play_car_interp`、`play_car_video`、四个快捷控件保持 decoder。
  - 6 项失败为直播 fixture/favorite 和 gallery-live 返回宿主缺失，与播放器控件重构无关。
- `gestures`：`4 passed / 3 failed`
  - 亮度、音量手势均通过。
  - 3 项失败为持久化的返回模式/皮肤默认值断言，与播放器控件无交集。

## 结论

最终 APK 与证据 SHA 绑定一致；全屏播放主控、更多设置面板、音效弹窗、超分/补帧、字幕/换源入口和返回链均通过本次车机验收。已知失败项均落在直播 fixture/返回模式默认值，不在本次播放页控件改动路径。
