# r96 · 云端诊断日志 + 音量不再打断播放 + 线路快速让位 + carclash 返回原样移植

版本 `3.1.0-car.7-r96` / versionCode 68。只针对 B01。

## 1. 云端诊断日志（此前一直拿不到的原因）

`DiagLog` 早在 r70 就有完整实现（logcat 抓取、4×256 KiB 环形、pending 批次持久化、失败重试），
但它把批次 `POST` 到 **`dl.z736.vip/app-diagnostics/v2/upload`**——该通道 2026-11-12 就已停用，
失败后 pending 一直重发同一个包，所以云端什么也没有。

现改为：

- 传输：`PUT https://dl.736.de5.net/logs/tvboxos-car-2880/<device>-<batch>.json.gz`，
  头 `X-Upload-Token`，成功判据简化为 HTTP 200（R2 不回业务字段）。
- 令牌：构建期从 `car/ci/upload-token.txt`（gitignore）注入 `BuildConfig.UPLOAD_TOKEN`，
  仓库与源码里没有明文；源码里旧的 `yi2y…` 硬编码令牌已删除。
- 入口：设置 → 其他 → 「诊断日志上传 · 立即上传」；另外每个 tick（30 s）自动补传，
  首次上传在启动后约 5 s。
- 回读：`https://dl.736.de5.net/logs/tvboxos-car-2880/…json.gz`，本地 `gunzip` 即得到含
  `rawLog` 的 JSON 信封。

**已验证**：MuMu 装机后 70 s 内云端出现对象
`logs/tvboxos-car-2880/tvbox-da72ba06-…-607c37120910c078.json.gz`，下载解压后 `rawLog` 78896 字节。

## 2. 音量从 <100% 调到 >100% 会卡顿并「重播」

> **r97 起已由「写死 EXO」取代**：`PREFER_VOLUME_BOOST_EXO` 与「下次起播换引擎」的过渡逻辑已不再生效 （EXO 恒支持 PCM 增益，切换路径根本不会走到）。本节保留为 r96 档案，现役行为见 [R97-FIXES.md](R97-FIXES.md) §2。

原因：`BaseController.slideToChangeVolume` 在非 PCM 引擎（IJk）上遇到 >100% 时调用
`requestVolumeBoost` → `PlayFragment` 做 IJK→EXO 引擎切换 → `mVideoView.release()` +
`goPlayUrl()`，必然卡数秒并从头重新缓冲。

现改为**不在播放中重建播放器**：

- 请求只记下 `HawkConfig.PREFER_VOLUME_BOOST_EXO = true`，本次保持当前引擎与 100%，并提示一次；
- `PlayerHelper.updateCfg` 在下次起播时看到该偏好就把 `pl` 从 1（IJk）改为 2（EXO），
  届时 >100% 由 PCM 增益实现。

## 3. 直连打开视频要等 30 秒以上才正常

`PlayFragment` 的解析超时 15 s 与起播超时 20 s 是**串联**的：一条死线路能把首帧等待吃到 35 s，
之后自动换线才正常。两处都改为 8 s，失败线路尽快让位。

**证据边界**：这条是按代码路径与实车描述的时长匹配做的修复；实车日志（现在开始能上传了）
才能确认具体卡在哪一段。

## 4. 返回组件：按 carclash `EdgeSwipeView` 原样移植

按 `carclash-hybrid` 仓库的 `EdgeBack.kt` / `EdgeSwipeView.java`（`BAND_DP=36`、`TRIGGER_DP=45`、
`PEAK_DP=180`、`HALF_HEIGHT_DP=180`、`PANEL_SCALE=0.60`）重写 `BackAffordanceView`：

- 静止时**不画任何东西**（只有透明触摸带），与 carclash 一致；旧实现那条常驻灰带已删除；
- 拖动时纯黑贝塞尔透镜面板，宽度 = `min(180dp, 距离 × 0.6)`，上下各 180dp；
- 白色勾选箭头，透明度 `80 + 150 × 进度`，进度 ≥ 0.62 时出现第二个箭头；
- 描边 3dp，箭头 9×18dp——全部照抄，没有自创样式。
- host 侧改传 `policy.maxInward()`（向内距离），不再是自家换算的 `visualWidth()`。

## 5. carclash 接管检测失效

旧实现写死 `com.github.kr328.clash.car/…EdgeBackService`；而 carclash 的 `applicationId` 来自
`custom.application.id`（缺省 `com.github.metacubex.clash`），车上装的包名对不上，所以永远判「未开启」。

现改为按**服务类名**匹配：`ENABLED_ACCESSIBILITY_SERVICES` 里任一组件、其 `/` 后的类名以
`EdgeBackService` 结尾即视为接管（float 与 short 两种写法都接受）。诊断上传里也带上原始组件名，
下次能直接看到实际包名。

**已知近似仍保留**：carclash 允许无障碍服务常驻、用私有 `car_ui/edge_back_enabled` 开关手势，
那个开关跨应用读不到。本判定是「服务已启用」，偏保守（宁可本应用让位）。

## 6. 验证

| 检查 | 结果 |
| :--- | :--- |
| `car/ci/run-host-tests.py` | `ALL_HOST_TESTS_PASS` |
| `car/tests/run_github_cdn_host_test.py` | PASS（Python 8 项 OK） |
| `:app:assemblePythonRelease :app:lintPythonRelease` | 成功 |
| `car/ci/verify-b01-apk.py` | `B01_APK_PASS`，`minSdk=32`、`arm64-v8a`、65020790 B、`ab3ba879…c98e` |
| MuMu 装机 | `versionCode=68 / versionName=3.1.0-car.7-r96` |
| 云端日志 | 装机 70 s 内出现 `logs/tvboxos-car-2880/tvbox-…json.gz`，解压 `rawLog` 78896 B |
| R2 / GitHub | latest.json 与 Release 资产摘要一致（`ab3ba879…`） |

**未完成/未验证**：车机上「拖出面板」的截图取证这次没拿到——`input motionevent` 注入没有产生
`edge_back` 日志（手势没到 host），所以面板外观只有代码级一致性与 MuMu 上的一次静止态截图；
carclash 接管的实际包名也要等下一份上传日志（`back: ... carClash=...`）才能确认。
