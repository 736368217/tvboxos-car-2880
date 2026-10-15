# TVBoxOS 车机版（源码构建）— 返回按钮 + 音效 / 臻彩 + 车机规范 + JAR 装载与臻彩渲染修复

上游工程：<https://github.com/q215613905/TVBoxOS>（白盒源码）。
本目录是车机适配产物：**每个页面都有可点击的应用内返回按钮（不依赖无障碍）**、**画面（臻彩 GL 调色）与音效（6 档）增强**、按 `CAR-UI-SPEC-TVBOX-2880x1620` 完成整改，并修复了两个上游/自身缺陷：
① 上游白壳的 `ProtectedInitJar` 误判（订阅里 106 个 `csp_*` 站点此前全部失效、只剩主页）；
② 臻彩 GL 渲染视图在 GL 线程绑定 Surface 导致 **开臻彩时画面全黑**。

## 交付物（推荐使用 car.4）

| 文件 | 说明 |
|---|---|
| `TVBox-1.0.0-car.4-signed.apk` | **最新（推荐）**，可直接安装的签名 APK（33,302,029 B，SHA-256 `21C9FC00…2495D`） |
| `GIT-DIFF-car.4.patch` | car.4 改动（臻彩 GL 渲染修复 + 版本号） |
| `GIT-DIFF-car.3.patch` | car.3 改动（JAR 装载修复 + 版本号） |
| `GIT-DIFF-car.2.patch` | car.2 改动（增强 + 规范整改，相对上游 `main`，含新增文件） |
| `src/` | 新增/修改类的源码副本（`car/`、`player/render/`、`xyz/…/render/`、`com/github/catvod/crawler/`） |
| `tools/WhitelistScanTest.java` | 离线复现“保护型 JAR 误判 / 已修复”的独立 DEX 扫描器（见 `TEST-REPORT.md` §1.1） |
| `AUDIT-CAR-UI-SPEC.md` | 按规范的逐条审查与整改记录（含实测数值、残留项） |
| `BUILD-INFO.md` | 版本/签名/改动清单/可复现构建步骤 |
| `TEST-REPORT.md` | 验收证据、测试范围与已知边界 |
| `AUDIO-VIDEO-ENHANCE.md` | 音频/视频增强的完整方案、参数表、源码入口、调优方式与运行期验收命令 |
| `INSTALL.md` | 安装与使用说明 |
| `cartest-lowsat.mp4` | 低饱和静态测试片（640×360 / 8s / 440Hz，sat≈0.41），用于验证臻彩调色的**像素级**生效 |
| `cartest.mp4` | 原始合成测试片（640×360 / 8s / 440Hz，sat≈0.76） |

历史版本保留在同目录仅供对比：`TVBox-1.0.0-car.3-signed.apk`（33,302,029 B，SHA-256 `50744783…57557`）、`TVBox-1.0.0-car.2-signed.apk`（33,297,933 B，SHA-256 `4FE471C3…C8567`）。

## 四块能力

1. **应用内返回按钮**（car.1 起，car.2 按新规范调位）
   窗口内 View，挂在当前 Activity decor；无需无障碍/权限/ADB。空闲 4 秒自动隐藏、任何触摸或按键唤醒；点击上一级（分类页→主页分类、播放页先退全屏），首页根级只提示不退出。设置页可开关、可设 4/6/8 秒或「常显」。
2. **音效（6 档）**
   `关闭 / 影院 / 重低音 / 3D环绕 / HiFi / 人声`，由 Equalizer + BassBoost + Virtualizer + LoudnessEnhancer 组成，绑定 EXO/IJK 的音频会话，500ms 渐入。设置页 `音效` 行循环切换。
3. **臻彩（画面增强）**
   GL 片元着色器调色（饱和度/对比度/亮度），开：(1.35 / 1.12 / +0.03)、关：(1 / 1 / 0)；HDR/HLG/杜比视界自动关闭并提示。设置页 `臻彩增强` 行开关，**下次播放生效**。
   - car.4 修复：`GlEnhanceRenderView` 原先在 **GL 线程**调用 `player.setSurface()`，EXO 抛 `IllegalStateException: Player is accessed on the wrong thread`（异常被吞）→ 解码器拿不到渲染 Surface → **开臻彩时画面全黑**。现改为切回主线程绑定 Surface，并补 `SurfaceTexture.setDefaultBufferSize()` 与 `GlEnhance` 取证日志。
4. **订阅站点全部可用**
   上游白壳把本订阅的 `spider.jar` 误判为“保护型 JAR”，而该 JAR 没有 `DexNative`，导致初始化失败且**不回退** → 所有 `csp_*` 站点拿到 `SpiderNull`，现象就是“只有主页”。
   car.3 两层修复：① 修正误判本身（白名单密文在 `Init.a()/Init.b()`，扫描范围由 `Init.init` 扩到 `Init*` 全类全方法）；② `JarLoader.invokeInit()` 在保护型路径失败后**回退**到 `Init.init(Context)`。
   实测：`load success key=main` + 全站 `getSpider success`，主页分类、站点分类、搜索、多站聚合（`源 77/77`）、详情与选集全部正常。

## 车机规范（摘要）

- 顶部 40mm / 底部 55mm 避让禁区、左右 35mm 安全边距；播放器覆盖层顶部 ≥40mm、底部 ≥60mm；视频渲染层不缩放不变形。
- 全部 XML 使用 `mm`（1mm = 2.25px @2880×1620）；最小热区 40mm×40mm；相邻热区间距 ≥12mm；暗色沉浸主题（`#101318` / `#1E222B` / `#EDEDED` / `#2979FF`）。
- 包名保持 `com.github.tvbox.osc.jun`（白名单第 41 项，未改名）；应用名保持 `TVBox`（白名单第 13 项）——两者都是 JAR 身份自检通过的前提，**不要改**。
