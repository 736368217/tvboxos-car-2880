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

2026-11-20：已核验发布基线为 r91 / vc63；当前音量 A/B 正按 r92 / vc64 做最终验收与主备发布。状态、证据和真车边界见 README，不把待发布包当作线上包。
