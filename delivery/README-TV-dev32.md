# TV 版（dev / java32 基线）交付说明

## 基线
- 仓库分支：`feature/tv-dev32-enhance`，基于上游 `dev` 提交 `712fbd3`（2026-11-16）。
- flavor：`java32`（仅 `armeabi-v7a`，含 `LEANBACK_LAUNCHER` + banner）。
- 包名：`com.github.tvbox.osc`（原版，可与存量 TV 版直接覆盖升级）。
- 当前版本：`1.1.1` / `versionCode 8`。

## 构建
```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\HermesData\gradle-tiers'
& 'D:\gradle-7.5\gradle-7.5\bin\gradle.bat' :app:assembleJava32Release --console=plain --offline
```
产物：`app/build/outputs/apk/java32/release/TVBox_release-java32.apk`（未签名）。

签名（debug.keystore，证书 SHA-256 `2FEB63C5…FA9C`，与车机版同源）：
```powershell
zipalign -f -p 4 TVBox_release-java32.apk aligned.apk
apksigner sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android `
  --key-pass pass:android --ks-key-alias androiddebugkey `
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
  --out tvbox-tv-<ver>-java32.apk aligned.apk
```

模拟器调试：MuMu 的 abilist 是 `x86_64,arm64-v8a,x86`，装不了纯 32 位包，
用 `:app:assembleJavaRelease`（armv7+arm64）做同源测试包，其余逻辑完全一致。

## 更新通道
- R2：`https://dl.736.de5.net/tvbox-tv/latest.json`，APK 对象 `tvbox-tv/tvbox-tv-<ver>-java32.apk`。
- 发布：`tools\publish_r2.ps1 -Project tvbox-tv -Version <ver> -VersionCode <n> -Apk <apk> -Notes "…"`（在 `tvboxos-car-2880` 仓库）。
- GitHub Release 仅兜底，tag 前缀 `tv-v`。
- 校验：包名 + versionCode + 签名摘要 + SHA-256。

## 已实测
- 清空数据首启：自动写入内置默认订阅（jsDelivr 文本 384~939ms）→ 112 站点 →
  `spider.jar` 经镜像级联下载（`gh-proxy.com`，约 30~60s）→ 首页出豆瓣卡片。
- 设置页：画面滤镜 / 超分辨率 / 运动补帧 / 检查更新四行，遥控器 DPAD 可选、
  OK 选中即回写并关弹窗。
- 检查更新：R2 `latest.json` 约 0.9s，行内显示「已是最新版本」或「发现新版本 x.y.z」。
- 有更新时：弹窗显示 `当前 x → 新版 y` + 说明 + 包大小；DPAD 左右切换、
  OK 触发下载、BACK 关闭；19MB 包实测 196s 下完，`verify ok vc=… sig=2FEB63C511CD7257`。

## 未在真机验证
- 实际播放画面与超分/补帧/滤镜的视觉收益：模拟器访问国内 CDN 片源返回 0 字节，
  这条只能在实机上看。渲染器接线与开关持久化已确认（`VideoEnhanceHelper.enabled()`
  非默认才切 `GlEnhanceRenderViewFactory`）。
