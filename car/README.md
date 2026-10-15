# TVBoxOS 车机版 1.0.0-car.6

基于 [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) 源码扩展，保留 Python ARM32＋ARM64 通用 flavor、Java/JAR、QuickJS、车机布局和应用内返回按钮。

car.6 在 car.5 上新增 **3 档动漫音效、3 档动漫画面、SGSR1 超分与可独立关闭的时域平滑补帧**。既有音效 0–5 和画面 0–7 的编号、参数不变；新增音效为 6–8，新增画面为 8–10。

## 下载与安装

- [v1.0.0-car.6 Release](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.6)
- APK：`TVBox-1.0.0-car.6-python-signed.apk`
- 包名 `com.github.tvbox.osc.jun` / 应用名 `TVBox` / versionCode `7`。
- 大小及 SHA-256 以 [`MANIFEST-car.6.json`](MANIFEST-car.6.json) 和 [`SHA256SUMS-car.6.txt`](SHA256SUMS-car.6.txt) 为准。
- 沿用既有测试证书，同签名的 car.2–car.5 可覆盖升级；APK、keystore 和下载的 Spider JAR 不放入源码仓库。

## 使用

设置 → **音效**：关闭、影院、重低音、3D环绕、HiFi、人声、动漫·标准、动漫·剧场、动漫·对白，共 9 档。

设置 → **画面预设**：原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色、动漫·暗光、动漫·日间、动漫·清透，共 11 档。列表可滚动，当前项高亮。

设置 → **SGSR1 超分**、**60Hz 时域平滑补帧**：两个独立开关，默认开启，均持久化，**下次播放生效**。画面为原画时仍可单独使用超分或补帧；原画且两项关闭时使用原普通渲染器。画面预设只调整视频，应用 UI 不参与处理。

| SR | 补帧 | 视频处理 |
|---|---|---|
| 关 | 关 | 保留所选调色；原画时回普通渲染器 |
| 开 | 关 | SGSR1，按源帧更新 |
| 关 | 开 | 时域混合输出，不执行 SGSR |
| 开 | 开 | 调色 → 时域混合 → SGSR1 |

源宽度达到 2560，或输出窗口无需放大时，自动旁路 SGSR。补帧为轻量时域混合，没有运动矢量估计；快速运动的观感和功耗需在车机上选择开关评估。

## 验收与边界

Java 8 精确参数/迁移/时序测试、生产 GLSL 的 EGL 像素探针，以及 MuMu 中 EXO/IJK 实际播放、四种开关组合、1080p/4K、设置持久化、Python/QuickJS/JAR 回归的证据见 [`TEST-REPORT.md`](TEST-REPORT.md)。

模拟器中的 24fps 测试片在常见窗口输出提交约 47–58 次/秒，不能据此声称显示器稳定呈现 60fps，更不能代替 SA8295P 单帧耗时、真实车机全屏或 HDR10 精度验收。RGBA8 处理不构成端到端 10-bit HDR 保证；ARM32 仅完成打包核验。

## 交付材料

| 文件 | 用途 |
|---|---|
| [INSTALL.md](INSTALL.md) | 安装、设置与日常操作 |
| [BUILD-INFO.md](BUILD-INFO.md) | 构建、签名及复现测试命令 |
| [AUDIO-VIDEO-ENHANCE.md](AUDIO-VIDEO-ENHANCE.md) | 完整音画参数、SGSR 与时域策略 |
| [DESIGN-car.6.md](DESIGN-car.6.md) | 规格衔接与实施设计 |
| [evidence/car.6/](evidence/car.6/) | 原生节点、截图、GPU 像素、音效会话和日志摘要 |
| [tests/](tests/) | Java host/GPU 探针、独立本地验收源与媒体生成脚本 |
| [GIT-DIFF-car.6.patch](GIT-DIFF-car.6.patch) | 相对 car.5 提交 `1038379` 的 UTF-8 生产源码及测试增量 |
| [src/](src/) | 关键类/布局副本；完整工程以仓库根目录为准 |
| [licenses/snapdragon-gsr.txt](licenses/snapdragon-gsr.txt) | SGSR 上游 BSD-3-Clause 许可证 |
| [history/car.5/](history/car.5/) | car.5 原交付说明，历史证据仍在 `evidence/car.5/` |

包名与 TVBox 标签保持订阅 JAR 白名单身份。原订阅可用性受外部网络和各站解析影响，主页与部分分类成功不代表所有站点都能播放。
