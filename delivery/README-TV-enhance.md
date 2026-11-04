# TV 版（老版本 oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明

## 当前版本：r4（带应用内更新）

| 项 | 值 |
|---|---|
| 文件 | `TVBox-TV-oldtv-enhance-r4.apk` |
| 大小 | 16,968,722 bytes |
| SHA-256 | `8744E541E1AB82F780136C18F7D27ACD0205EE464DDDA92C87D46F01D1594C5E` |
| 版本 | `1.0.3-oldtv-enhance` / versionCode 4 |
| 包名 | `com.github.tvbox.osc`（TV 版原包名） |
| 目标 | minSdk 18 / targetSdk 26，带 `LEANBACK_LAUNCHER` |
| ABI | 仅 armeabi-v7a（32 位，与原版一致） |
| 签名 | `2feb63c5…fa9c`（v1/v2/v3 通过） |
| Release | `tv-v1.0.3-vc4`（仓库 `736368217/tvboxos-car-2880`，目标分支 `feature/tv-oldtv-enhance`） |

旧包 r1/r2/r3 已废弃，请装 r4。

## 应用内更新：有，且按 TV 遥控习惯做

### 入口

**设置 → 检查更新**（设置页最后一行，和「关于」并排）。右侧显示当前版本号。

### TV 操作习惯（用户特别要求，已实机验证）

| 要求 | 实现 |
|---|---|
| 不搞点不了的按钮 | 弹窗**自己接管**方向键与确认键，不依赖 Android View 焦点。实测部分盒子上 Dialog 里 `focused=false`，只靠 `requestFocus` 会出现「按钮看得见却按不动」——已规避。 |
| 遥控器能操作 | 两个大按钮「立即更新 / 暂不更新」横排：左右键（或上下键）切换，OK / 回车键触发，返回键关闭。触摸/鼠标点击也有效。 |
| 默认焦点 | 默认选中「立即更新」，按一次 OK 就开始下载。 |
| 不抢焦点、不打断看片 | **不做静默自动弹窗**。只有用户主动点「检查更新」才检查并弹窗；无更新时只在设置行显示「已是最新版本」，不弹窗。 |
| 看得清状态 | 检查中 / 发现新版本 / 已是最新 / 失败原因都写回设置行文字，不依赖一闪而过的 Toast。 |
| 下载状态 | 进度、失败原因、安装提示都写在同一个弹窗里，不额外弹层。 |

### 实机验证（MuMu，release 混淆包，vc3 模拟旧版）

```
# 纯遥控器按键（无触摸）
DPAD_RIGHT  → btnSkip selected=true      （切到「暂不更新」）
DPAD_LEFT   → 切回「立即更新」
KEYCODE_23(OK) → TvUpdate: download start ...   ✅ 按钮真的能按
```

```
设置行：1.0.2-oldtv-enhance
TvUpdate: check ok tag=tv-v1.0.3-vc4 vc=4 local=3 hasUpdate=true
TvUpdate: download start size=16964626
SubFetch: file fail try=1/4 Connection reset          (直连 GitHub 21s)
SubFetch: file fail try=2/4 Socket closed            (gh-proxy.com 45s 硬超时)
SubFetch: file ok   try=3/4 cost=4413ms bytes=16964626  (ghfast.top)
TvUpdate: verify ok vc=4 sig=2FEB63C511CD7257
→ 系统「安装未知应用」授权页被唤起
```

首次安装需授权「安装未知应用」，授权后再点一次「立即更新」即可（下载会命中缓存/重新拉，弹窗状态行会提示）。

### 更新链路的健壮性

- 更新清单：GitHub API `/releases`（国内直连实测 200/0.6s），只认 `tv-v` 开头的 tag，不会误装车机版包
- 下载：`MirrorFetch` 多通道（直连 → gh-proxy.com → ghfast.top → ghproxy.net），单次总超时 45s，
  慢速 25s 放弃，避免死等
- 完整性：声明的**文件大小 + SHA-256**逐一校验，半截文件（ghproxy.net 常见 200 但截断）自动换源
- 安装前：包名、versionCode、签名证书三项校验，避免装错包
- 版本比较：优先 versionCode，其次 tag 里 rNN，最后点分段（host 测试 29 项 PASS）

## 之前的订阅修复（r2/r3，保留）

- 首次安装内置默认订阅 `xiaosa-unlimited`（jsDelivr CDN，国内实测 384ms）→ 开箱即用，不用手输地址
- 配置/jar 下载多通道 + spider.jar md5 校验，避免半截 jar
- `fixContentPath` 把 jsDelivr `@branch` 基址还原成 raw，避免 `./spider.jar` 落到 403 的 CDN 地址
- `lives:[{}]` 的 NPE 防护

端到端实测：清空数据零手输 → 112 站点 → 完整 jar 1926329B → 首页卡片「小城良方 8.0 分」
→ 吉里 `categoryContent` 3695B 真实条目。

## 保留的三项功能

设置 → **画面滤镜 / 超分 / 补帧**：

| 功能 | 档位 |
|---|---|
| 画面滤镜 | 原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色、动漫·暗光/日间/清透（11 档） |
| 超分 | 关闭、标准全能(SGSR1)、影视专精(FSR)、动漫线条(Anime) |
| 补帧 | 关闭、基础平滑(Oversample)、高阶时域(4帧 Cubic)、保守光流(Safe-Flow) |

三项全默认时走原版 Texture/Surface 渲染；任一非默认才启用 GL 增强渲染器，**下次起播生效**。

## 自查命令

```
adb logcat -s TvUpdate SubFetch   # 更新检查 / 下载 / 换源 / 校验
adb logcat -s GlEnhance           # 画面增强是否生效
```

## 未能在本机验证

32 位 IJK 在 x86_64 模拟器上缺 so，播放链路仍需 TV 真机确认。若站点列表有了但点开详情空白：

```
adb logcat -d | findstr /i "System.err girigiri"
```

## 改动清单（vs upstream `oldTv` @ a545c27）

新增：
```
player/.../render/ 11 个增强文件
app/.../util/VideoEnhanceHelper.java
app/.../util/MirrorFetch.java           多通道下载 + CDN 规范化 + md5/size/SHA256 校验
app/.../update/TvUpdateInfo.java        Release 解析 + 版本比较（host 测试 29 项）
app/.../update/TvUpdate.java            检查 / 下载 / 校验 / 唤起安装
app/.../ui/dialog/TvUpdateDialog.java   TV 遥控专用弹窗（显式接管按键）
app/res/layout/dialog_tv_update.xml
app/src/debug/.../SpiderProbeActivity.java  仅 debug 诊断探针（正式包 DEX 不含）
car/tests/TvUpdateInfoHostTest.java
delivery/tv-update.json                    Release 清单
```
修改：
```
app/.../util/HawkConfig.java  +3 配置 key
app/.../util/PlayerHelper.java 增强渲染器接线
app/.../ui/fragment/ModelSettingFragment.java  三项弹窗 + 检查更新入口
app/res/layout/fragment_model.xml  两项新增行
app/.../api/ApiConfig.java    默认订阅 + 多通道配置/jar + 完整性校验 + lives 防护
app/build.gradle              1.0.3 / vc4
build.gradle                  阿里云 public 仓库（原依赖来自已关停 jcenter）
```
未改动：VideoView、播放器实现、首页/详情/播放页、原有设置项、ABI 配置。

## 构建

```powershell
$env:JAVA_HOME="<JDK 11>"
$env:GRADLE_USER_HOME="D:\HermesData\gradle-tiers"
cd D:\github\tvbox-tv-oldtv-enhance
.\gradlew.bat clean :app:assembleRelease --console=plain
# zipalign -f -p 4 → apksigner sign（debug.keystore, v1/v2/v3）
# 发版：tag 必须 tv-v 开头，上传 APK + tv-update.json（body 用 tv-update.json 内容）
```
