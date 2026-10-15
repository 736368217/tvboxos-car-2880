# TVBoxOS 车机版 1.0.0-car.5 — Python 通用包与 8 档画面预设

基于 [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) 源码构建。源码与交付文档位于私有仓库 [736368217/tvboxos-car-2880](https://github.com/736368217/tvboxos-car-2880)。

car.4 交付的是 **Java flavor**；car.5 改为 **Python flavor，ARM32＋ARM64 通用 APK**，加入 Python 3.8.13 运行时，继续保留 Java/JAR 和 QuickJS。Android 界面及播放器仍使用原工程的 Java 代码；这里的“Python 版”指支持 Python Spider 的构建版本。

## 下载与文件

- [v1.0.0-car.5 Release](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.5)：`TVBox-1.0.0-car.5-python-signed.apk`，52,627,205 字节。
- SHA-256：`6F43817EB9C4E2A97C8E30C190826244A2333E14CD46D064964F91AE45DF0C3B`。
- 包名 `com.github.tvbox.osc.jun`、应用名 `TVBox`，versionCode `6`，versionName `1.0.0-car.5`；沿用 car.2–car.4 的测试签名，可覆盖升级同签名版本。
- APK 通过 Release 附件交付，仓库不存 APK、下载的 Spider JAR 或签名私钥。

| 材料 | 用途 |
|---|---|
| `BUILD-INFO.md` | 构建环境、Python flavor、依赖版本、签名与校验步骤 |
| `INSTALL.md` | 安装、Python 源使用、画面预设选择与升级说明 |
| `TEST-REPORT.md` | 本轮实际验证结果及边界 |
| `AUDIO-VIDEO-ENHANCE.md` | 6 档音效与 8 档画面的参数和代码入口 |
| `tests/` | 预设/迁移 host 测试与独立 Python、QuickJS 验收源 |
| `evidence/car.5/` | 原生节点、截图、渲染日志摘要、音效会话和像素比较数据 |
| `GIT-DIFF-car.5.patch` | 相对 car.4 源码提交 `febc8fa` 的 UTF-8 代码增量，包含新增源码 |
| `src/` | 新增类和关键修改类、布局的源码副本；完整工程以仓库根目录为准 |

## 画面预设

设置 → **画面预设** → 选择档位，**下次播放生效**。列表可上下滑动，当前项高亮；重复点击当前项保持弹窗，可点“取消”退出。

| ID | 名称 | 用途 |
|---|---|---|
| 0 | 原画 | 保留原始画面 |
| 1 | 臻彩 | 增强色彩与层次，保留旧版开启时的参数 |
| 2 | 自然 | 轻调色，接近原片 |
| 3 | 影院 | 温润色调，强化明暗 |
| 4 | 鲜艳 | 浓郁色彩，突出画面 |
| 5 | 柔和 | 降低反差，柔化色彩 |
| 6 | 明亮 | 提亮偏暗片源 |
| 7 | 暖色 | 暖色温，减少蓝色 |

旧 `video_enhance=false/0` 读取为原画，`true/1` 读取为臻彩；异常值回退原画。HDR/杜比视界保留自动关闭 SDR 调色的策略；本轮 HDR 自动处理未实测。

## 沿用的车机能力

- 每个 Activity 窗口内的返回按钮，无需无障碍、系统悬浮窗权限或 ADB；闲置 4 秒隐藏、触摸唤醒，可设 4/6/8 秒或常显，首页根级只提示。
- 6 档音效：关闭、影院、重低音、3D环绕、HiFi、人声。四个 Android AudioEffect 绑定内部播放器音频会话。
- 17.3″ / 2880×1620 的安全边距、触控热区、mm 单位与暗色主题；沿用 car.2 布局整改。
- car.3 的 JAR 保护型误判修复和初始化回退，以及 car.4 的主线程绑定 Surface 修复。本轮再次确认原订阅 `Init.init success`、分类和多站搜索。

## 本轮验证范围

MuMu 6.8.4.0 / Android 15 / 横屏 2880×1620：Python 3.8.13 实际执行 8 项依赖和本地 HTTP 断言；Python 与 QuickJS 均返回分类、卡片、详情并播放合成片。8 档设置均走真实播放路径，7 个增强档均有第 100 帧参数日志；截图比较证明视频变化且抽样 UI 区域逐像素一致。设置在应用重启后保留，影院音效四效果器挂于 session 89。

原订阅本轮“潜伏6”搜索 **21 结果、78/78 完成、3 超时**。部分外部站点返回验证页或解析错误；不保证每个外部源持续可用。真实车机、ARM32 运行、HDR/杜比视界和 IJK 全路径仍待验证。完整证据与范围见 `TEST-REPORT.md`。
