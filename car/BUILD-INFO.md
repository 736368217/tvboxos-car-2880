# BUILD-INFO — TVBoxOS 车机版 1.0.0-car.2

## 交付身份

- APK：`TVBox-1.0.0-car.2-signed.apk`
- 包名（Application ID）：**`com.github.tvbox.osc.jun`（白名单内，未改名）**
- VersionCode / VersionName：`3` / `1.0.0-car.2`
- 大小：`33,297,933` 字节
- SHA-256：`4FE471C3791235F5AED86528206F10B00ECE4FB01F56862C7B706EAD5DBC8567`
- 签名：v1/v2/v3 通过；测试证书 `C=US, O=Android, CN=Android Debug`，证书 SHA-256 `2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`
- zipalign 4 字节对齐：通过
- 启动 Activity：`com.github.tvbox.osc.jun/com.github.tvbox.osc.ui.activity.HomeActivity`
- flavor：`java` release（R8）；native ABI：`arm64-v8a` + `armeabi-v7a`（上游仅此两种）

## 相对 car.1 的新增（本次交付内容）

### 1. 画面与音效增强（对齐参考车机版“音效 / 臻彩”）

| 文件 | 作用 |
|---|---|
| `car/AudioEffectController.java`（新） | 6 档音效链：Equalizer + BassBoost + Virtualizer + LoudnessEnhancer；500ms 渐入（16×32ms）；切换重建、释放安全 |
| `car/CarEnhance.java`（新） | 偏好读写 + 会话下发 + 臻彩可用性判定（HDR 强制关闭） |
| `player/.../render/GlEnhanceRenderView.java`（新，vendored player 模块） | GLSurfaceView + 外部 OES 纹理着色器调色：`color=(raw-0.5)*con+0.5+bri` + 饱和度混合；开：(sat 1.35, con 1.12, bri 0.03)，关：(1,1,0) |
| `player/.../render/GlEnhanceRenderViewFactory.java`（新） | 渲染视图工厂 |
| `player/.../player/MyVideoView.java` | `getAudioSessionId()` / `isHdrVideo()` / `setVideoEnhanceMode()`；`prepared()` 内做 HDR 判定与音效下发；`release()` 释放音效 |
| `player/.../player/ExoPlayer.java`（app 侧） | EXO 音频会话 id + HDR 判定（`ColorInfo.isTransferHdr`、`dolby-vision`、`dvhe/dvh1/dovi`） |
| `player/.../player/IjkMediaPlayer.java`（app 侧） | IJK 音频会话 id（native）+ 杜比视界编码名启发式（本 fork 的 IJK 无 color_trc 元数据，已注明） |
| `util/PlayerHelper.java` | 播放前按开关选择 `GlEnhanceRenderViewFactory`（HDR 时自动回退普通渲染视图） |
| `ui/fragment/ModelSettingFragment.java` + `fragment_model.xml` | 设置页新增 `音效`（6 档循环）与 `臻彩增强`（开/关）两行 |
| `util/HawkConfig.java` | 新增偏好键 `hifi_audio`（默认 0）、`video_enhance`（0/1） |

行为要点：
- 音效仅对 **EXO / IJK** 生效（系统播放器无会话 id，直接跳过）；`getStrengthSupported()` 不支持时静默跳过；创建失败全部 try/catch 吞掉。
- 臻彩在 **HDR/HLG/杜比视界** 下强制关闭并提示，避免对 HDR 画面二次调色；切换在**下次播放生效**（渲染视图在 `startPlay()` 时创建）。
- 直播页使用基类 `VideoView`，本轮未接入（无会话 id 访问器），已在文末“未覆盖”注明。

### 2. 按 `CAR-UI-SPEC-TVBOX-2880x1620` 的整改

详见 [`AUDIT-CAR-UI-SPEC.md`](AUDIT-CAR-UI-SPEC.md)。摘要：上下避让禁区（40mm/55mm）与左右 35mm 安全边距落地到 11 个页面布局；播放器覆盖层 40mm/60mm 让位；非零 dp/sp 清零；热区与间距按 40mm/12mm 提升；Manifest（resizeableActivity、configChanges、design_width/height、uses-feature）补齐；暗色主题与规范色板落地；可点控件补 focusable。

### 3. 应用内返回按钮（car.1 已交付，car.2 按新规范调整）

- 位置/尺寸改为 mm：左侧 35mm 安全边距、50mm 圆钮（2880×1620 下 `[79,486]-[192,599]`），属安全区内。
- 新增 `focusable=true` 与 2mm 高亮焦点描边（方控/旋钮可选中）。

## 构建步骤（可复现）

```powershell
git clone https://github.com/q215613905/TVBoxOS.git tvbox-os-src
cd tvbox-os-src
git apply ..\tvbox-os-car\GIT-DIFF-car.2.patch
"sdk.dir=$env:LOCALAPPDATA\Android\Sdk" | Set-Content local.properties -Encoding ASCII
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-cache'
.\gradlew.bat :app:assembleJavaRelease          # -> app/build/outputs/apk/java/release/TVBox_release-java.apk
# 对齐 + 签名（测试证书）
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\zipalign.exe" -f -p 4 TVBox_release-java.apk aligned.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out TVBox-1.0.0-car.2-signed.apk aligned.apk
```

环境备注：Gradle wrapper 分发包改用腾讯镜像（本机 `services.gradle.org` 不可达）；JDK 17；compileSdk 33。
