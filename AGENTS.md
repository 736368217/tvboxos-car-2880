# 项目边界

本仓库是车主自用的零跑 B01 车机应用，只维护 SA8295P / API 32 / ARM64 / 2880×1620 横屏 / density 160。
完整的构建、更新和支持边界以 [README.md](README.md) 为准。

## 工作约定

- 不全量继承上游全设备支持，不新增旧电视、手机、32 位或 Google Play 兼容任务。
- 只构建 `:app:assemblePythonRelease`；`pythonDebug` 仅调试同一设备。不要跑 12 变体矩阵。
- 保留 Python 站源、包名、发布 APK 命名及签名的覆盖安装契约。
- 工具链：JDK 17、Gradle 7.5、SDK API 33、Python 3.8。设置方式见 README，机器路径不写死在脚本中。
- 回归：`python -B car/ci/run-host-tests.py --out <临时输出目录>`；构建后运行 `car/ci/verify-b01-apk.py`。
- 应用代码在 `app`，播放器在 `player`，Python 运行时在 `pyramid`，JS 桥在 `quickjs`。
- 更新以 R2 为主，查询失败才进入 GitHub CDN 备用链。CDN 只包 GitHub URL，不关闭包名/版本/签名/摘要校验。
- `car/tests`、`car/ci` 是可复跑验收入口；设备操作遵循 `car/handover/MUMU-VERIFICATION-SPEC.md`。
- MuMu 验收不能冒充真车测试。真实 B01 的 OEM 行为、车机网络和听感需实际设备证据。
- 上游只做选择性合并，保留 B01 的返回、生命周期、PCM 增益、历史与更新实现。
- 历史报告保留对应版本和 SHA，不覆盖为本轮“已通过”；版本档案不构成现行支持承诺。
- 不覆盖或清理已有未提交/未跟踪工作。临时文件写会话 scratch，项目输出放对应目录。
- 用户已约定：每轮更新验收通过后默认提交、推送 main，并发布到 R2 主通道与 GitHub 备用 Release，再回读验证；不再逐次等待发布指令。只有用户明确暂停或门禁失败才停止，并说明原因。

## 当前状态

2026-11-23：当前已发布 **3.1.0-car.7-r95 / versionCode 67**（源码 `aea620d7`，CI 45194003592，APK SHA `d97db2e0…`）：音频 v3 只上行提升、天花板回 `-1.5 dBFS`、正常段 0 dB 直通；网络直连优先、镜像仅救援、播放清单/分片不改写；订阅缓存优先秒开 + 后台比对 + 设置内立即更新；版本提示仅在远端真更新时出现；返回组件只留 carclash 边缘样式，并与 carclash 的 EdgeBackService 互斥。设备侧已用 `/push/` 接口实测本机 HTTP mp4 与远端 HTTPS HLS 播放（增益 0.9998→2.6607、outPeak≤0.8414），真车听感与用户自己的 123 订阅仍待实测。r93 / vc65、r94 / vc66 为版本档案，证据见 README 与 `car/handover/R95-AUDIO-V3-NETWORK.md`。
