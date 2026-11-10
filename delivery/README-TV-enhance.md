# TV 版（老版本 oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明

## 当前版本：r5（修播放）

| 项 | 值 |
|---|---|
| 文件 | `TVBox-TV-oldtv-enhance-r5.apk` |
| 大小 | 16,968,722 bytes |
| SHA-256 | `B133390DA1438CEA15C1AAAD53B8C9835273204C7C7E24DB7C27E14C38C3E676` |
| 版本 | `1.0.4-oldtv-enhance` / versionCode 5 |
| 包名 | `com.github.tvbox.osc`（TV 版原包名） |
| 目标 | minSdk 18 / targetSdk 26，带 `LEANBACK_LAUNCHER` |
| ABI | 仅 armeabi-v7a（32 位，与原版一致） |
| 签名 | `2feb63c5…fa9c`（v1/v2/v3 通过） |
| Release | `tv-v1.0.4-vc5`（目标 `feature/tv-oldtv-enhance`） |

**请装 r5**，r1–r4 全部废弃。

## 这次回答「是不是最开始就找错包了」

### 调研结论：没找错包，但用错了默认播放器

上游 `q215613905/TVBoxOS` 只有 4 个分支，逐个核对：

| 分支 | applicationId | LEANBACK | ABI | 说明 |
|---|---|---|---|---|
| `main` / `dev` / `file_transfer` | `com.github.tvbox.osc.jun` | ❌ 无 | 多 flavor（java/java32/java64/python…） | 不是 TV 版 |
| **`oldTv`** | `com.github.tvbox.osc` | ✅ 有 | 仅 armeabi-v7a | **唯一带 LEANBACK 的老 TV 版，32 位** |

`main` 的 `java32` flavor 虽然也是纯 32 位，但它没有 LEANBACK/banner，属于手机/通用版。
所以基线选 `oldTv` 是对的；问题出在 **oldTv 是 2021 年的老快照，播放器配置与能正常播放的新版不同**。

### 播放失败的真正根因（逐项对比上游 main）

| | 能正常播放的 `main` | 我一直用的 `oldTv` |
|---|---|---|
| 默认播放器 | **ExoPlayer (2)** | **IJK (1)** — `App.initParams()` 强制写死 |
| ExoPlayer | 2.19.1 + jellyfin ffmpeg 扩展 | 2.14.2（纯 Java） |
| `loadLibrary` 失败 | 不吞异常、直接抛 | **吞掉 `UnsatisfiedLinkError`**，只 `printStackTrace()` |
| native 依赖 | 不依赖私有 ijk so | 依赖自带 6 个 32 位 ijk so（`libijkffmpeg/libijksdl/libplayer`…） |

`oldTv` 首次启动即把 `PLAY_TYPE` 写成 1(IJK)；`PlayerHelper` 里两处 `updateCfg` 与 `init()`
都把 `System.loadLibrary` 的 `UnsatisfiedLinkError` 吞在 `catch(Throwable)` 里，
于是 `loadLibrariesOnce` 误以为加载成功，随后 `new IjkMediaPlayer()` 才失败 ——
TV 上表现就是**点开视频一片黑 / 空白**，日志里只有一行堆栈，很容易被忽略。
同设备的其他 TVBox 用的是 Exo，不碰这组老 so，所以正常。

模拟器复现证据（改之前）：启动时日志刷屏
`java.lang.UnsatisfiedLinkError: dlopen failed: library "libijkffmpeg.so" not found` 等 3 条。

### 修复（对齐上游 main 的做法）

1. `App.initParams()`：默认 `PLAY_TYPE` 从 1 改为 **2(Exo)**；并把已装用户 Hawk 里残留的
   0/1 值一并迁移为 2（r1–r4 用户升级后不会继续卡在坏 IJK）。
2. `PlayerHelper` 两处 `updateCfg` 的默认值 `Hawk.get(..., 0)` 改为 `2`。
3. 新增 `tryIjkOrExo(codec)`：`loadLibrary` **不再吞异常**；任一 so 加载失败即回退
   `ExoMediaPlayerFactory`，并打 `TvPlayer` 日志。用户手动选 IJK 时也不会再进坏状态。
4. `PlayerHelper.init()`：默认 Exo 时**不再无条件加载** ijk so；只有用户显式选 IJK 才尝试。
5. 确认 `ExoMediaPlayer` 是纯 Java（`SimpleExoPlayer` / exoplayer 2.14.2），
   不含任何 `native` / `System.load` / `FFmpegApi` 调用；`TextureRenderView` 与 Exo 兼容。
   三项增强默认全关（滤镜/超分/补帧都是 0），默认播放路径完全不经过 GL 渲染器。

### 实机验证（MuMu，清空数据、零手输）

- **启动日志中不再有** `UnsatisfiedLinkError` / `libijkffmpeg` / `libplayer.so` 任何 native 错误 ✅
- 设置页显示 **「播放器 → Exo播放器」**（原来是 IJK播放器）✅
- 搜索「小城良方」返回真实剧集：热播·APP 全29集 / 布布·APP 26集全 / 剧圈·APP 国产剧… ✅
  （说明 spider.jar、订阅、详情数据链都正常，之前只卡在播放器）
- 首页卡片「小城良方 8.0 分」正常渲染 ✅

**诚实边界**：模拟器是 x86_64，无法安装 32 位正式包做「真机听感/画面」验证；
我用放开 ABI 的测试变体验证了「播放器初始化与数据链」，但**最终画面输出仍需你在 TV 上确认一次**。
若 r5 装上后仍黑屏，请抓：

```
adb logcat -v time -s TvPlayer ExoPlayerImpl AndroidRuntime
adb logcat -d | findstr /i "MediaCodec ExoPlaybackException"
```

## 之前修好的功能（全部保留）

- **订阅**：内置默认订阅（jsDelivr）→ 开箱即用；配置/jar 多通道 + md5 校验；
  `fixContentPath` 还原 raw 基址；`lives:[{}]` 防护。清空数据零手输 → 112 站点 → 完整 jar → 首页卡片。
- **应用内更新**：设置 → 检查更新；弹窗显式接管遥控器方向键/确认键，不依赖 View 焦点；
  多通道下载 + size/SHA256 校验 + 签名校验；Release `tv-v1.0.4-vc5` 已发布。
- **三项增强**：滤镜 11 档 / 超分 3 档 / 补帧 3 档，默认全关，下次起播生效。

## 自查

```
adb logcat -s TvPlayer          # IJK 回退提示
adb logcat -s TvUpdate SubFetch # 更新
adb logcat -s GlEnhance         # 画面增强
```

## 改动清单（vs upstream `oldTv` @ a545c27）

新增：`player/render` 11 个增强文件、`VideoEnhanceHelper`、`MirrorFetch`、
`TvUpdateInfo` / `TvUpdate` / `TvUpdateDialog`、`dialog_tv_update.xml`、`SpiderProbeActivity`(仅 debug)、
`TvUpdateInfoHostTest`、`tv-update.json`。

修改：`App.java`（默认 Exo + 迁移）、`PlayerHelper.java`（默认 Exo + IJK 失败回退 + init 不再强加载）、
`HawkConfig` / `ModelSettingFragment` / `fragment_model.xml`（增强三项 + 检查更新入口）、
`ApiConfig.java`（订阅修复）、`app/build.gradle`（1.0.4/vc5）、`build.gradle`（阿里云仓库）。

未改动：VideoView、播放器实现内部逻辑、首页/详情/播放页、原有设置项、ABI 配置。

## 构建

```powershell
$env:JAVA_HOME="<JDK 11>"; $env:GRADLE_USER_HOME="D:\HermesData\gradle-tiers"
cd D:\github\tvbox-tv-oldtv-enhance
.\gradlew.bat clean :app:assembleRelease --console=plain
# zipalign -f -p 4 → apksigner sign（debug.keystore, v1/v2/v3）
# 发版 tag 以 tv-v 开头，上传 APK + tv-update.json（Release body 用该 json 内容）
```
