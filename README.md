# TVBox · 零跑 B01 私人车机版

这是车主自用项目，基于 TVBoxOS 选择性维护。产品范围只包括车主的零跑 B01 中控；不承接上游的全设备、旧电视、手机、32 位或 Google Play 支持。

## 设备与构建约定

| 项目 | 唯一目标 |
|---|---|
| 设备 | 零跑 B01，SA8295P |
| 系统 | Android API 32（车机厂商系统） |
| 屏幕 | 17.3 英寸，2880×1620 横屏，density 160 |
| 原生 ABI | `arm64-v8a` |
| 包名 | `com.github.tvbox.osc.jun`，保持覆盖安装身份 |
| 发布构建 | `:app:assemblePythonRelease`，只保留 `python` 产品风味 |
| Python | Chaquopy 12.0.1 / Python 3.8，保留 Python 站源 |
| APK 输出 | `app/build/outputs/apk/python/release/TVBox_release-python.apk` |

`pythonDebug` 仅用于调试同一台 B01，不是第二个设备版本。`java`、`java32`、`java64`、`python32`、`python64` 的产品构建已停用。第三方库中保留的通用代码不代表本项目支持那些设备。

应用 `minSdk=32`。暂时保留 `targetSdk=28` 的既有权限和存储行为；提高 targetSdk 需要另行验证车机权限、文件访问、后台和安装链路。Play 分发规则及 API 19–31 兼容不作为本私人项目的验收目标。

## 构建与回归

要求 JDK 17、Gradle 7.5、Android SDK API 33、build-tools 30.0.3、构建端 Python 3.8。设置 `ANDROID_HOME` 和 `JAVA_HOME`，并在未跟踪的 `local.properties` 写入本机 `sdk.dir`；`TVBOX_BUILD_PYTHON` 指向 Python 3.8 可执行文件。

```powershell
.\gradlew.bat :app:assemblePythonRelease :app:lintPythonRelease --no-daemon --console=plain
python -B car/ci/run-host-tests.py --out "$env:PI_SCRATCH_DIR/b01-host"
python -B car/ci/verify-b01-apk.py app/build/outputs/apk/python/release/TVBox_release-python.apk --aapt2 "$env:ANDROID_HOME/build-tools/30.0.3/aapt2.exe"
```

Linux 使用 `./gradlew`。依赖已缓存时可加 `--offline`。主机脚本使用 `GRADLE_USER_HOME` 中的实际 ExoPlayer 2.19.1 缓存；先构建应用，缺少缓存时明确报错。`car/ci/verify-b01-apk.py` 检查 API 32、ARM64-only、Python 依赖、资源表和 UTF-8 文件名，防止上游合并重新引入通用设备包。

主机测试和 B01 Release 构建是日常门禁。不要再运行 12 变体矩阵，也不要为上游未纳入的设备兼容问题扩大本项目范围。Lint 的现存非阻断配置仍需审阅实际结果，命令退出 0 不表示零告警。

设备验收要求见 [MuMu 与真车规范](car/handover/MUMU-VERIFICATION-SPEC.md)。MuMu API 35 只是功能代理环境；真车 API 32 的安装、OEM 窗口/后台策略和主观听感，只有实际车机证据才能确认。
播放栏可直接切换「增强音量 / 原始声音」；原声绕过 App 音频增强，切回恢复保存设置。用法、处理边界与版本验证入口见 [音量 A/B 说明](car/handover/R92-AUDIO-AB.md)。

## 应用内更新

- **主通道是 R2**：`https://dl.736.de5.net/tvboxos-car-2880/latest.json`。查询成功即返回，包括“已是最新”；只有失败才启用 GitHub 备用链。
- **GitHub CDN 备用链**参考 [CDN URL 方案](https://lancelotrar.github.io/cdn-url/)，使用经本项目探测的 `gh-proxy.org`、`cdn.gh-proxy.org`、`v4.gh-proxy.org`、`gh-proxy.com`。前三者使用 `https://<服务>/github.com/...`，后者保留 `https://<服务>/https://github.com/...`。
- 该网站是静态 URL 方案目录，应用不在每次更新时抓取网页。目录未承诺 API 支持；本项目对上述节点另外探测 Release JSON，再保留直连 API/Web 兜底。不向已知不支持的页面或 API 反复请求。
- CDN 仅用于 GitHub，不给 R2 地址套代理。保留下载测速、断点续传、取消、包名、版本、签名与可用 SHA-256 校验。CDN 仍可能缓存或临时不可用。
- 发布包文件名保持 `TVBox-Original-Feature-<版本>.apk`，不因收敛设备范围破坏旧版更新契约。

本项目按用户约定，每轮更新验收通过后默认提交并推送 main，将同一签名 APK 发布到 R2 主通道与 GitHub 备用 Release，再回读摘要，无需逐次确认；用户明确暂停或验收失败时才停止。发布入口为 `tools/publish_r2.ps1`，须填写 notes；CI artifact 不等于已发布。`dl.z736.vip` 已退役，不再探测或恢复。

## 文档与上游边界

- 当前项目定位、构建和更新行为以本 README 为入口；Agent 边界见 [AGENTS.md](AGENTS.md)。
- [签名与 CI](car/ci/README.md)：共享测试证书、B01 单包校验和工作流。
- [上游跟进](car/handover/UPSTREAM-SYNC.md)：只选择对 B01 有用的改动，不全量继承设备矩阵。
- [原始交付档案](DELIVERY-ORIGINAL-APP.md)及旧版本交接、报告只证明其对应版本，不作为当前构建/发布手册；现役行为按本 README 指向的说明和证据核对。
- `car/reports/main-72e38f61-full-test.html` 是 r90 / `72e38f61` 的历史测试报告；其中 12 变体和低 API 风险是收敛前的结果，不是当前验收要求。

2026-11-21：当前已发布 **3.1.0-car.7-r92 / versionCode 64**，发布源码提交 `c94c23de`。同一签名 APK 已发布到 R2 主通道和 GitHub 备用 Release，完整下载 SHA、资产摘要及原证书均匹配；本地主机 26 项、MuMu 原生 75 项和 GitHub B01 CI 通过。详见[版本与发布回读](car/reports/r92-release-verification.json)和[最终包报告](car/reports/r92-release/index.html)。旧 [r91 发布记录](car/reports/r91-release-verification.json)保留原身份。R2 本次对 Range 返回 200 完整内容，客户端会从头下载，未证明分段续传；真车 OEM 行为与听感仍待实测。
