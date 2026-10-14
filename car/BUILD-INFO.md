# BUILD-INFO — TVBoxOS 车机版 1.0.0-car.3

## 交付身份（car.3，当前版本）

- APK：`TVBox-1.0.0-car.3-signed.apk`
- 包名（Application ID）：**`com.github.tvbox.osc.jun`（白名单第 41 项，未改名）**
- 应用名：`TVBox`（白名单第 13 项，未改名）
- VersionCode / VersionName：`4` / `1.0.0-car.3`
- 大小：`33,302,029` 字节
- SHA-256：`5074478347ADC8870DA35BD0F8806728D215EAB2F942B8BEC6637282C9857557`
- MD5：`06ad9d3555959538ac94c6f618dade89`
- 签名：v1/v2/v3 通过；测试证书 `C=US, O=Android, CN=Android Debug`，证书 SHA-256 `2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`
- zipalign 4 字节对齐：通过
- 启动 Activity：`com.github.tvbox.osc.jun/com.github.tvbox.osc.ui.activity.HomeActivity`
- flavor：`java` release（R8）；native ABI：`arm64-v8a` + `armeabi-v7a`
- 构建时间：2026-10-14

> 与 car.2 的签名相同（同一 debug 证书），可直接覆盖安装 car.2；若设备上装的是**上游官方白壳**（`20260914-1520`，另一套签名），需先卸载再安装。

## car.3 改动（本次交付核心）：修复 `ProtectedInitJar` 误判导致订阅站点全废

### 问题

订阅 `…/tvbox/123/xiaosa/api.json?v=3` 含 **106 个 `csp_*` 站点**，JAR 下载与 md5 均正常，但页面只显示“主页”，其它全失败。设备日志：

```text
I/JarLoader: echo-load initProtectedJar file=/data/user/0/com.github.tvbox.osc.jun/files/csp/370c76a7….jar
I/JarLoader: load error key=main, protected jar loader not bound
```

### 根因

1. `ProtectedInitJar.check()` 用 DEX 扫描判定“保护型 JAR”，条件之一是**在该 JAR 的 `com.github.catvod.spider.Init` 类的 `init()` 方法字符串常量里找不到宿主包名**；而这份 JAR 的白名单密文放在 `Init.a()/Init.b()`，`init()` 里只有一个 4 字符的无关密文 → 扫描漏判。
2. 被判为“保护型”后走 `ProtectedInitJar.init()`，其中 `bindDexLoader()` 硬要求 JAR 内存在 `com.github.catvod.spider.DexNative`——本 JAR 没有（`DexNative`/`getLoader`/`replaceCloudDiskNames` 出现 0 次）→ `return false`。
3. 上游 `JarLoader.invokeInit()` 在保护型路径失败时**不回退**，直接返回 false → JAR 不装载 → 106 个站点全部 `SpiderNull` → “只有主页”。

### 修法（两层，互补）

| 层 | 文件 | 改动 |
|---|---|---|
| ① 修正误判 | `app/src/main/java/com/github/catvod/crawler/ProtectedInitJar.java` | `scanInitWhitelist()` 只扫 `init` → 改为 `scanWhitelistMethods()` 扫 `Init*` 全类全方法；`isProtected()` 对每个 `Init*` 类都做白名单扫描 |
| ② 兜底回退 | `app/src/main/java/com/github/catvod/crawler/JarLoader.java` | `invokeInit()` 在 `protectedInitJar.init(clz)` 失败时，回退调用 `Init.init(Context)`（新增 `invokeInitMethod()`，带 `echo-load` 取证日志） |

安全性：层 ① 只在**该 JAR 自己的白名单里出现本机包名**时才判定“非保护型”，对不在白名单的包名（如车机包名 `cn.kuwo.kwmusiccar`）仍判为保护型——已用 `tools/WhitelistScanTest.java` 做负控验证（见 `TEST-REPORT.md` §1.1）。
包名/应用名都未改（白名单第 41 / 13 项），因此层 ①② 之后 JAR 自身的三项身份自检（包名 / 应用名 / 签名 DN 黑名单）全部通过，不会触发「加载失败」+5 秒自杀。

### 版本号

`app/build.gradle`：`versionCode 4`、`versionName '1.0.0-car.3'`。

## car.2 内容（沿用）

### 1. 画面与音效增强（对齐参考车机版“音效 / 臻彩”）

| 文件 | 作用 |
|---|---|
| `car/AudioEffectController.java`（新） | 6 档音效链：Equalizer + BassBoost + Virtualizer + LoudnessEnhancer；500ms 渐入（16×32ms）；切换重建、释放安全 |
| `car/CarEnhance.java`（新） | 偏好读写 + 会话下发 + 臻彩可用性判定（HDR 强制关闭） |
| `player/.../render/GlEnhanceRenderView.java`（新） | GLSurfaceView + 外部 OES 纹理着色器调色：开 (sat 1.35, con 1.12, bri 0.03)，关 (1,1,0) |
| `player/.../render/GlEnhanceRenderViewFactory.java`（新） | 渲染视图工厂 |
| `player/.../player/MyVideoView.java` | `getAudioSessionId()` / `isHdrVideo()` / `setVideoEnhanceMode()`；`prepared()` 内 HDR 判定与音效下发；`release()` 释放音效 |
| `player/.../player/ExoPlayer.java`（app 侧） | EXO 音频会话 id + HDR 判定（`ColorInfo.isTransferHdr`、`dolby-vision`、`dvhe/dvh1/dovi`） |
| `player/.../player/IjkMediaPlayer.java`（app 侧） | IJK 音频会话 id（native）+ 杜比视界编码名启发式（本 fork 无 color_trc 元数据，已注明） |
| `util/PlayerHelper.java` | 播放前按开关选择 `GlEnhanceRenderViewFactory`（HDR 时自动回退普通渲染视图） |
| `ui/fragment/ModelSettingFragment.java` + `fragment_model.xml` | 设置页新增 `音效`（6 档循环）与 `臻彩增强`（开/关）两行 |
| `util/HawkConfig.java` | 偏好键 `hifi_audio`（默认 0）、`video_enhance`（0/1） |

### 2. 按 `CAR-UI-SPEC-TVBOX-2880x1620` 的整改

详见 [`AUDIT-CAR-UI-SPEC.md`](AUDIT-CAR-UI-SPEC.md)。摘要：上下避让禁区（40mm/55mm）与左右 35mm 安全边距落地到 11 个页面布局；播放器覆盖层 40mm/60mm 让位；非零 dp/sp 清零；热区与间距按 40mm/12mm 提升；Manifest（resizeableActivity、configChanges、design_width/height、uses-feature）补齐；暗色主题与规范色板落地；可点控件补 focusable。

### 3. 应用内返回按钮（car.1 交付，car.2 调位）

- 位置/尺寸用 mm：左侧 35mm 安全边距、50mm 圆钮（2880×1620 下 `[79,486]-[192,599]`）。
- `focusable=true` + 2mm 高亮焦点描边（方控/旋钮可选中）。

## 构建步骤（可复现）

```powershell
git clone https://github.com/q215613905/TVBoxOS.git tvbox-os-src
cd tvbox-os-src
git apply ..\tvbox-os-car\GIT-DIFF-car.2.patch    # 增强 + 规范整改（相对上游 main）
git apply ..\tvbox-os-car\GIT-DIFF-car.3.patch    # JAR 装载修复（相对 car.2）
"sdk.dir=$env:LOCALAPPDATA\Android\Sdk" | Set-Content local.properties -Encoding ASCII
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-cache'
.\gradlew.bat :app:assembleJavaRelease            # -> app/build/outputs/apk/java/release/TVBox_release-java.apk
# 对齐 + 签名（测试证书）
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\zipalign.exe" -f -p 4 TVBox_release-java.apk aligned.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out TVBox-1.0.0-car.3-signed.apk aligned.apk
```

离线复核 JAR 误判修复（无需设备，JDK 11+ 直接跑单文件）：

```powershell
java tools\WhitelistScanTest.java spider.jar com.github.tvbox.osc.jun   # 期望: OLD=true, NEW=false
java tools\WhitelistScanTest.java spider.jar cn.kuwo.kwmusiccar         # 负控: OLD=true, NEW=true
```

环境备注：Gradle wrapper 分发包改用腾讯镜像（本机 `services.gradle.org` 不可达）；JDK 17；compileSdk 33。
