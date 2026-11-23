# r95 · 音频 v3 温和上行压缩 + 直连优先 + 订阅缓存优先 + carclash 返回

版本 `3.1.0-car.7-r95` / versionCode 67。只针对 B01（SA8295P / API 32 / arm64-v8a / 2880×1620 / density 160）。

## 1. 音频 v3（`CarAudioDynamicsEngine`）

依据实车录音诊断（`tvboxos_r92_audio_clipping_fix_plan_1_.md`）：B01 硬件 DSP 在 `-1.5 dBFS` 下 THD 仅 0.4%，
真凶是上一版 `∞:1` AGC 把 12 dB 自然动态压成 2.3 dB，并在 `-12/-6 dBFS` 段倒扣 `-6.4 ~ -9.7 dB`。

| 项 | r93（旧） | r95（v3） |
| :--- | :--- | :--- |
| 输出天花板 | `-6.0 dBFS` (0.501187) | `-1.5 dBFS` (0.841395) |
| 增益方向 | 双向 clamp（可倒扣） | 仅上行 `boostDb = clamp((阈值 - env)·斜率, 0, 上限)` |
| 正常段落 | 被压平 | `minGainDb = 0`，0 dB 透明直通 |
| 包络 | 快攻 | 攻击 350 ms / 释放 2000 ms |
| 限幅 | 逐采样 80 ms | 保留逐采样 80 ms |

- `ORIGINAL/OFF(-18, 0, 0)`、`CAR_BALANCED(-18, 0.55, 8.5)`、`CAR_HIGHWAY(-16, 0.65, 11.5)`，`GATE_DB=-52`。
- 门限以下不叠加上行提升；`userGain=0` 仍然整块静音，不被 DRC 解除。
- 约束保留：原声逐字节旁路、系统音量/静音/焦点不变。

## 2. 网络：直连优先，镜像只作救援

r94 是「镜像优先 + 所有 GitHub URL 改写」，实测代价是：直连可用时反而先绕第三者（慢/黑屏），
媒体清单被改写会破坏 Range/字节偏移。r95 改为：

- `GitHubCdn.candidates()`：**origin 第一**，镜像排后且只在直连失败时使用；
- `canonical()` 拒绝 `.m3u8/.mpd/.ts/.m4s/.mp4/.mkv/.key`：播放清单与分片永不改写；
- 镜像尝试预算 `CONNECT_MS=2500 / READ_MS=5000`；直连只收紧 CONNECT（`6000`），读超时保持调用方原值，不影响下载；
- 直连失败保留原错误并继续尝试镜像（`if (!mirror) { failure = error; continue; }`）。

边界不变：仅公开 GET/HEAD；R2、非 GitHub 主机、带 `Authorization`/`Cookie`/token 一律直连。

## 3. 订阅缓存优先 + 手动强制更新

- `HomeActivity` 默认 `useCacheConfig = true`：先用上次缓存解析并显示首页，不再每次进入都等网络。
- `ApiConfig.refreshConfigCache(apiUrl)`：缓存命中后**后台**拉一次远端，内容变化才 `FileUtils.saveCache`；
  不改 Hawk、不重解析站源、不打断当前会话；API 集合（多线路）留给下次启动按原逻辑换线；离线只记日志。
- 设置 → 其他 → 「订阅缓存 · 立即更新」：`loadConfig(false, …)` 强制拉取，成功后 `RefreshEvent.TYPE_HOME_SOURCE_CHANGE` 让首页重建。

实测（MuMu，`clun.top/box.json`）：冷启动首页内容约 7.0 s 可见，`echo-config cache unchanged` 在 22:21:12 出现（启动后约 3 s），
首页不阻塞等配置。

## 4. 版本提示只在远端确实更新时出现

`ModelSettingFragment.refreshCheckUpdateSummary()` 从「只读 `UPDATE_HAS_UPDATE` 缓存标志」改为
`UPDATE_HAS_UPDATE && UpdateVersion.hasUpdate(local, tag)`，远端比本机旧或同一版本一律显示本机版本号。

## 5. 返回组件：只保留 carclash 样式，且与其互斥

- `BackAffordanceView.drawEdge()` 按 carclash `EdgeBack.kt` 的几何重画：静止窄带 + 全高圆角半透明面板、
  chevron 指示、宽度随拖动 `×0.6`、`trigger` 后高亮描边（对应 `BAND_DP=36 / TRIGGER_DP=45 / PEAK_DP=180 / PANEL_SCALE=0.60`）。
- 设置「返回方式」只剩 `边缘手势 / 关闭` 两个选项；`InAppBackButton.getMode()` 固定返回 `BACK_MODE_EDGE`，旧的「返回杠」偏好按边缘手势处理。
- 互斥：`InAppBackButton.carClashEdgeBackActive(context)` 读 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`，
  命中 `com.github.kr328.clash.car/…EdgeBackService` 时本应用自动释放自己的返回组件。
  **近似性**：carclash 的开关存在其私有 `car_ui` prefs（`edge_back_enabled`），跨应用读不到，
  因此以无障碍服务启用状态作为代理判定；carclash 关掉服务后，本应用下一次 `attach/refresh` 即恢复绘制。

## 6. 验证

| 检查 | 结果 |
| :--- | :--- |
| `car/ci/run-host-tests.py` | `ALL_HOST_TESTS_PASS`（28 份日志，含 `AudioDynamicsHostTest`、`AudioGainProcessorHostTest`） |
| `car/tests/run_github_cdn_host_test.py` | `GitHubCdnTransportTest PASS checks=19` + Python 8 项 OK |
| `:app:assemblePythonRelease :app:lintPythonRelease` | 成功；Lint 基线不变（1 Fatal / 3 Error / 1234 Warning，`NewApi=0`） |
| `car/ci/verify-b01-apk.py` | `B01_APK_PASS`，`minSdk=32`、`arm64-v8a`、65028982 B、`d97db2e0…45b87b5` |
| 签名 | `2feb63c5…44fa9c`（覆盖安装契约不变） |
| MuMu 安装 | `versionCode=67 / versionName=3.1.0-car.7-r95` |
| 返回组件像素 | 边缘滑入后中段第 0–13 列实测 `(57,63,73)` = `0x668E9AA7` 叠在深底上的窄带 |
| 设备播放（本机 HTTP mp4） | 用应用自带 `RemoteServer` 的 `/push/` 推 `http://10.0.2.2:8080/clip.mp4`（r93 诊断片）：`MediaCodec OMX.qcom.video.decoder.avc` 配置输出 surface，音频按实时速率推进 |
| 设备播放（远端 HTTPS HLS） | 推 `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`：音频持续出帧，轻声段 `gain=2.660725`（正好 BALANCED 的 +8.5 dB 上限），响段 `gain≈0.9998` 直通 |
| 设备音频行为 | 21 帧采样：增益区间 `0.9998–2.6607`、`outPeak` 最大 `0.8414`（= `-1.5 dBFS`），未出现负增益 |
| 订阅缓存启动 | 冷启动首页内容约 6.6 s 可见，`echo-config cache unchanged` 在启动后约 2 s 出现 |

**边界**：MuMu 的 `screencap` 抓不到视频叠加层（连续两帧像素零差异），所以“不是黑屏”只能靠 `MediaCodec`
配置了输出 surface + 音轨实时推进来证明，不能当作像素级验收。真车听感、以及用户自己的 123 订阅能否直连播放仍需真机复测；
若直连仍失败，抓 `GitHubCdn` / `PyLoader` / `getSpider` 日志再定位。

## 7. 证据

`car/reports/r95-release/`：`device/result.json`、`device/playback-result.json`、`device/playback-logcat.txt`、
`device/logcat.txt`、`host/*.log`、`build/apk-gate.json`、`build/build-tail.log`；
发布回读记录在 `car/reports/r95-release-verification.json`。
