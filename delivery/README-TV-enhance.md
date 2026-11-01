# TV 版（老版本 oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明 r1

## 交付物

| 项 | 值 |
|---|---|
| 文件 | `TVBox-TV-oldtv-enhance-r1.apk` |
| 大小 | 16,952,282 bytes |
| SHA-256 | `0E6B5048066E09060416F1068EFBDD113DD4E2BFECBA4326383EC58202639190` |
| 包名 | `com.github.tvbox.osc`（TV 版原包名，与车机版 `.jun` 不同，可共存） |
| 版本 | versionName `1.0.0` / versionCode `1`（沿用原版，未改） |
| 目标 | minSdk 18 / targetSdk 26 / compileSdk 33 |
| TV 标识 | `LEANBACK_LAUNCHER` + `android.software.leanback` + banner ✓ |
| ABI | **仅 armeabi-v7a（32 位）** —— 原版设定，未改动 |
| 签名 | `2feb63c5…fa9c`（与本机 `~/.android/debug.keystore` 同源，可直接覆盖同签名旧包） |

## 基线来源

上游白盒仓库 `q215613905/TVBoxOS` 的 **`oldTv` 分支**（HEAD `a545c27`）——即带 `LEANBACK` 的
老版本 TV 版；工作副本在独立目录 `D:\github\tvbox-tv-oldtv-enhance`，未触碰
`D:\github\tvboxos-upstream-feature-pack`（车机版）及其任何文件。

## 本次只增加了这三项

| 功能 | 说明 | 取值 |
|---|---|---|
| 画面滤镜 | 色彩/对比/亮度/色温预设（GPU 调色） | 原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色、动漫·暗光/日间/清透（11 档） |
| 超分 | SGSR / FSR / Anime 线条放大（GPU 着色器） | 关闭、标准全能(SGSR1)、影视专精(FSR)、动漫线条(Anime) |
| 补帧 | 运动插值 | 关闭、基础平滑(Oversample)、高阶时域(4帧 Cubic)、保守光流(Safe-Flow) |

入口：**设置 → 画面滤镜 / 超分 / 补帧**（三项并排一行，点开为选择列表）。
三项全为默认（原画 / 关闭 / 关闭）时，播放器仍走原版的 Texture/Surface 渲染方式，
行为与未改动的 TV 版完全一致；任一非默认才启用 GL 增强渲染器。

生效时机：**下一次起播生效**（渲染器在起播时重建）。设置页弹窗标题已注明。

## 改动清单（全部）

新增（player 层，从车机版原样移植，零外部依赖）：
```
player/.../render/GlEnhanceRenderView.java        GL 增强渲染器（OES → FBO → 超分/补帧输出）
player/.../render/GlEnhanceRenderViewFactory.java 渲染器工厂（携带配置）
player/.../render/VideoEnhanceConfig.java         配置快照
player/.../render/VideoEnhancePreset.java         11 个滤镜预设
player/.../render/VideoEnhanceTiers.java          超分/补帧档位常量
player/.../render/VideoPostProcessPolicy.java     补帧时序策略
player/.../render/VideoShaders.java               基础着色器
player/.../render/VideoTierShaders.java           分档着色器
player/.../render/VideoDocShaders.java            滤镜文档着色器
player/.../render/MpvHookPipeline.java            MPV 钩子管线
player/.../render/MpvHookShaderCompiler.java      钩子着色器编译
```
新增（app 层）：
```
app/.../util/VideoEnhanceHelper.java              三项设置唯一读写入口 + 渲染器工厂
```
修改（共 4 处，均为接线）：
```
app/.../util/HawkConfig.java                       +3 个配置 key
app/.../util/PlayerHelper.java                     +2 处：增强开启时改用 GL 增强渲染器
app/.../ui/fragment/ModelSettingFragment.java      +3 个选择弹窗 + 3 个取值 TextView
app/src/main/res/layout/fragment_model.xml         +1 行（三项并排）
```
构建基础设施（不改业务行为）：
```
build.gradle                                       补阿里云 public 仓库（原依赖来自已关停的 jcenter）
```
未改动：VideoView、所有播放器实现、UI 首页/详情/播放页、任何原有设置项与行为。

## 安装与自查

1. 安装：`adb install -r TVBox-TV-oldtv-enhance-r1.apk`（或拷贝到 TV 上用文件管理器安装）。
2. 若提示 `INSTALL_FAILED_NO_MATCHING_ABIS`，说明你的设备是**纯 64 位**（不支持 armeabi-v7a），
   该 TV 版原版为 32 位包，需换设备或另行适配。
3. 打开「设置」，确认「画面滤镜 / 超分 / 补帧」三项存在，点开能选档。
4. 选一个滤镜或超分档位，**重新起播**任意影片。
5. 看日志确认增强真正生效：
   ```
   adb logcat -s GlEnhance
   ```
   预期出现 `GlEnhance: setVideoSize …`、`GlEnhance: path=…` 等渲染路径日志；
   未开启增强时不应出现。

## 未验证项（如实说明）

- **未能在模拟器实机验证**：本机 MuMu 的 ABI 为 `x86_64, arm64-v8a, x86`，不含
  `armeabi-v7a`，因此该 32 位 TV 版包无法安装（`INSTALL_FAILED_NO_MATCHING_ABIS`）。
  这是原版 oldTv 的 ABI 设定（`abiFilters 'armeabi-v7a'`，player 模块 jniLibs 无 64 位 so）。
- 已完成的可验证项：Java 全量编译通过；`R.id.llFilter/llSr/llMemc/tvFilterValue/tvSrValue/tvMemcValue`
  六个资源 ID 均进入 `resources.arsc`；DEX 内含 `GlEnhance`/`Safe-Flow`/`MpvHook`/界面中文串；
  apksigner v1/v2 校验通过；clean 重建两次 SHA-256 完全一致（可复现）。

## 构建命令（可复现）

```powershell
$env:JAVA_HOME="$env:PI_SCRATCH_DIR\jdk11"          # AGP 4.2.2 需 JDK 11（本机默认 17 不可用）
$env:GRADLE_USER_HOME="D:\HermesData\gradle-tiers"  # 复用依赖缓存
cd D:\github\tvbox-tv-oldtv-enhance
.\gradlew.bat clean :app:assembleRelease --console=plain
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
# 对齐 + 签名：zipalign -f -p 4 → apksigner sign（debug.keystore）
```
