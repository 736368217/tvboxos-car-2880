# TV 版（dev / java32 基线）交付说明

## 基线
- 仓库分支：`feature/tv-dev32-enhance`，基于上游 `dev` 提交 `712fbd3`（2026-11-16）。
- flavor：`java32`（仅 `armeabi-v7a`，含 `LEANBACK_LAUNCHER` + banner）。
- 包名：`com.github.tvbox.osc`（原版，可与存量 TV 版直接覆盖升级）。
- 当前版本：`1.1.2` / `versionCode 9`。

历史版本：

| 版本 | vc | 变更 |
| --- | --- | --- |
| 1.1.0 | 7 | 改用 dev 32 位基线；设置页三项增强；应用内更新（R2） |
| 1.1.1 | 8 | 修更新弹窗内容为空；下载超时放宽到 300s；R2 不走 gh 镜像；已下载包复用 |
| 1.1.2 | 9 | 播放页「增强」入口（滤镜/超分/补帧/音效/音效对比）；爱动漫 JS 修复；启动自动检查更新 |

## 构建
```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\HermesData\gradle-tiers'
& 'D:\gradle-7.5\gradle-7.5\bin\gradle.bat' :app:assembleJava32Release --console=plain --offline
```
产物：`app/build/outputs/apk/java32/release/TVBox_release-java32.apk`（未签名）。

签名（debug.keystore，证书 SHA-256 `2FEB63C5…FA9C`，与车机版同源）：
```powershell
$bt='C:\Users\WIN10\AppData\Local\Android\Sdk\build-tools\30.0.3'
& "$bt\zipalign.exe" -f -p 4 TVBox_release-java32.apk aligned.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-pass pass:android `
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

## 播放增强（1.1.2 起）
入口两处，同一个弹窗（`VideoEnhanceDialog`）：
- 播放页控制条最前面的「增强」按钮
- 设置页「播放增强」行（另有独立「音效」行）

弹窗五行，遥控器操作（弹窗自己接管按键，不依赖 View 焦点）：

| 行 | 档位 | 生效时机 |
| --- | --- | --- |
| 画面滤镜 | 原画/臻彩/自然/影院/鲜艳/柔和/明亮/暖色/动漫·暗光/日间/清透 | 下次起播 |
| 超分辨率 | 关闭 / SGSR1 / FSR / Anime | 下次起播 |
| 运动补帧 | 关闭 / Oversample / 4帧 Cubic / Safe-Flow | 下次起播 |
| 音效 | 关闭 / 影院 / 重低音 / 3D环绕 / HiFi / 人声 / 动漫三档 | 立即 |
| 音效对比 | 增强音效 / 原始声音 | 立即 |

操作：上下换行、左右改值、OK 等价右移一位、返回关闭。改动即时写偏好，无需保存。

音效链路：`AudioEffectController`（Equalizer + BassBoost + Virtualizer +
LoudnessEnhancer，500ms 渐变）挂在 ExoPlayer 的 audioSessionId 上；
换集/换引擎后会话变化会在 `STATE_PLAYING` 重挂，`apply()` 对同会话同档位幂等短路。
A/B 对比只释放/重建效果链，不动已保存档位。

## 订阅与 jar
- 首次安装无订阅时自动写入内置默认源，避免首页永久空白。
- `spider.jar` 的 jsDelivr 地址还原成 raw，再按镜像级联下载（镜像优先、直连兜底）。
- 镜像只对 GitHub 域名生效，R2/自建域名不套 gh 代理。
- `assets/js/lib/net.js` 用带同步 `net` 对象的版本：JS 站点普遍写 `await net.json(...)`，
  缺 `net` 会直接 ReferenceError。同步是刻意的 —— QuickJS 只在 Java→JS
  调用栈上排 promise job，async 版会让 `await` 挂死。

## 已实测（MuMu 1.1.2 测试包，vc9）
- 清空数据首启：自动写入内置默认订阅（jsDelivr 文本 384~939ms）→ 站点列表 →
  `spider.jar` 经镜像级联下载（`gh-proxy.com`）→ 首页出豆瓣卡片。
- 播放增强弹窗：五行全部渲染；DPAD 上/下换行、右移把超分从「关闭」切到
  「标准全能 (SGSR1)」、补帧切到「高阶时域 (4帧 Cubic)」、音效切到「影院」；
  返回键关闭后设置页摘要同步。
- 启动自动检查更新：装 vc7 测试包启动，`TvAutoUpdate: prompting vc=9`，
  弹窗显示 `当前 1.1.2-old → 新版 1.1.2` + notes + 「安装包约 18 MB」。
- 无新版时静默：vc9 对线上 vc9 只打一行 `nothing to prompt`，不弹窗。
- 手动检查更新、下载、校验、唤起安装器的完整链路见 1.1.0/1.1.1 记录
  （19MB 包 196s 下完，`verify ok vc=… sig=2FEB63C511CD7257`）。

## 未在真机验证
- 实际播放画面与超分/补帧/滤镜的视觉收益：模拟器访问国内 CDN 片源返回 0 字节，
  这条只能在实机上看。渲染器接线与开关持久化已确认。
- 爱动漫端到端：模拟器内构造本地订阅时，OkHttp 对本地静态服务报
  `unexpected end of stream`，无法把设备订阅切到测试源。修复依据是确定性差异：
  旧 `net.js` 486 字节无 `net` 定义，爱动漫.js 有 5 处 `await net.json(...)`，
  现包里 `net.js` 2262 字节含 `const net = {` / `json(url` / `globalThis.net`。
  若实机仍失败，取 `adb logcat -s JsLoader JsSpider` 与
  `adb logcat -d | findstr /i "ReferenceError net is not defined evaluateModule"`。
