# TV 版（oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明

## 当前版本：r6

| 项 | 值 |
|---|---|
| 文件 | `TVBox-TV-oldtv-enhance-r6.apk` |
| 大小 | 16,968,722 bytes |
| SHA-256 | `FF36CF77477A84C5E2B4B11F2BF835935BE45DB7CB94552CE24AEBA64641D282` |
| 版本 | `1.0.5-oldtv-enhance` / versionCode 6 |
| 包名 | `com.github.tvbox.osc` · minSdk 18 / targetSdk 26 · LEANBACK · 仅 armeabi-v7a |
| 签名 | `2feb63c5…fa9c`（v1/v2/v3） |
| **更新通道** | **R2**：`https://dl.736.de5.net/tvbox-tv/latest.json`（GitHub Release 兜底） |

r1–r5 全部废弃。**装 r6 之后，以后在应用内「检查更新」即可，不用再拷包。**

## 这次修的是「列表都显示不出来，进不到播放」

### 根因：宿主 `Spider` 契约与当前 jar 不匹配

对比上游 `main` 的 `JarLoader.getSpider()`：

```java
spider.siteKey = key;              // ← oldTv 没有
spider.initApi(new SpiderApi());   // ← oldTv 连 SpiderApi 类都没有
spider.init(App.getInstance(), ext);
```

而 oldTv 的 `Spider` 还是 2021 年的 abstract 版本，缺：
`siteKey`、`initApi(SpiderApi)`、`empty`、`client()`、`liveContent`、`action`、`proxy()`。

当前 spider.jar 里的站点（按新版宿主编译）在 `init` / `homeContent` 里会读 `siteKey`、
用 `api.getAddress()` 拼代理地址、或用 `Spider.client()` 发请求。oldTv 缺这些成员时抛的是
`NoSuchFieldError` / `NoSuchMethodError` / `NoClassDefFoundError` —— 都是 **Error 不是 Exception**，
被站点自己的 `catch` 漏掉后直接返回空串，界面上就是**订阅加载完、列表一片空白**。

### 修复

1. `crawler/Spider.java` 升级为上游 main 契约（保留 `safeDns()` 仍返回本工程 DoH 实例，
   网络栈不换；`client()` 返回 `OkGoHelper` 默认客户端）
2. 新增 `crawler/SpiderApi.java`（与 main 一致：`getAddress` / `getPort` / `log` /
   `getScreenOrientation` / `multiReq` / `webParse`）
3. `JarLoader.getSpider()` 补注入：`sp.siteKey = key; sp.initApi(new SpiderApi());`，
   并打 `SpiderJar: loaded <cls> siteKey=<key>` 日志

### 实机验证（MuMu，清空数据、零手输）

```
SubFetch: api_url empty -> default cdn.jsdelivr.net/.../api.json      (4018ms, 25608B)
SubFetch: file ok try=2/4 cost=18635ms bytes=1919771                  (spider.jar, 新版本)
SpiderJar: loaded Douban siteKey=豆瓣                                  ✅
SpiderJar: loaded AppRJ siteKey=热播影视                               ✅
```

切到「热播 • APP」站点后，首页分类栏变成 **内地/电影/动漫/综艺/韩剧/泰剧/港剧/日剧/美剧/台剧**
（该站点 `homeContent` 真实返回），内容区出现多张封面卡片（[39,231][220,467] 等）。
数据源选择弹窗里 112 个站点全部列出。

对比修前：只加载首页数据源，切站点后分类栏空、列表空。

### 同时保留的播放修复（r5）

oldTv 原版把默认播放器写死 IJK(1)，且 `PlayerHelper` 三处 `System.loadLibrary` 把
`UnsatisfiedLinkError` 吞掉 → `new IjkMediaPlayer()` 才失败 → 点开视频黑屏。
现已改为默认 **ExoPlayer(2)**（对齐上游 main），IJK 加载失败自动回退 Exo，
设置页显示「播放器 → Exo播放器」。

## 自动更新：已切到 R2

- 客户端：`TvUpdate` 先读 `https://dl.736.de5.net/tvbox-tv/latest.json`（R2），
  失败才回落 GitHub Release
- 清单字段：`version / versionCode / apk / size / sha256 / notes`
- 下载：R2 直连（实测 16.9MB / 25 秒），按声明 size + SHA-256 校验，
  再校验包名 + versionCode + 签名
- 发布命令：

```powershell
cd D:\github\tvboxos-car-2880
powershell -NoProfile -File tools\publish_r2.ps1 -Project tvbox-tv `
  -Version 1.0.5-oldtv-enhance -VersionCode 6 `
  -Apk D:\github\tvbox-tv-oldtv-enhance\delivery\TVBox-TV-oldtv-enhance-r6.apk `
  -Notes "变更说明"
```

实机验证（vc5 旧包 → 检查更新）：

```
SubFetch: text ok url=dl.736.de5.net/tvbox-tv/latest.json              (5536ms, 392B)
TvUpdate: r2 check ok version=1.0.5-oldtv-enhance vc=6 local=5 hasUpdate=true
TvUpdate: download start url=.../r6.apk size=16968722
SubFetch: file ok try=1/4 cost=25277ms bytes=16968722                   ← 一次成功，无镜像跳转
TvUpdate: verify ok vc=6 sig=2FEB63C511CD7257
```

## TV 遥控操作（更新弹窗）

**设置 → 检查更新**（最后一行，右侧显示当前版本）。

弹窗显式接管按键，不依赖 Android View 焦点（部分盒子 Dialog 里 `focused` 恒为 false）：

| 按键 | 行为 |
|---|---|
| 左右 / 上下 | 在「立即更新 / 暂不更新」间切换 |
| OK / 回车 | 触发当前项；默认选中「立即更新」 |
| 返回 | 关闭（下载中不关闭） |

不做静默自动弹窗，不抢遥控焦点；状态与失败原因写在弹窗内和设置行文字上。

## 保留的功能

- **订阅**：内置默认订阅（jsDelivr）+ 多通道下载 + jar md5 校验 + `fixContentPath` 还原 raw 基址
- **三项增强**：滤镜 11 档 / 超分 3 档 / 补帧 3 档，默认全关，下次起播生效

## 自查

```
adb logcat -s SpiderJar          # 站点类是否加载成功（loaded <cls> siteKey=<key>）
adb logcat -s TvUpdate SubFetch  # 更新检查 / 下载
adb logcat -s TvPlayer GlEnhance # 播放器回退 / 画面增强
```

## 诚实边界

- 模拟器 x86_64 无法安装 32 位正式包，我用放开 ABI 的测试变体验证了
  「站点加载 → 分类栏 → 卡片列表」全链路；**播放画面输出仍需你在 TV 上确认**。
- 老 TV 版在模拟器上首页卡片按 OK 会跳到搜索页（既有行为，与本轮改动无关，
  真机遥控通常不受影响）。

若 r6 装上仍有问题，抓这两条给我：

```
adb logcat -v time -s SpiderJar
adb logcat -d | findstr /i "NoSuchMethod NoSuchField NoClassDefFound System.err"
```

## 改动清单（vs upstream `oldTv` @ a545c27）

新增：`player/render` 11 个增强文件、`VideoEnhanceHelper`、`MirrorFetch`、
`TvUpdateInfo` / `TvUpdate` / `TvUpdateDialog` / `dialog_tv_update.xml`、
**`crawler/SpiderApi.java`**、`SpiderProbeActivity`(仅 debug)、`TvUpdateInfoHostTest`。

修改：**`crawler/Spider.java`（升级契约）**、**`crawler/JarLoader.java`（siteKey + initApi 注入）**、
`App.java`（默认 Exo + 迁移）、`PlayerHelper.java`（Exo 默认 / IJK 失败回退）、
`HawkConfig` / `ModelSettingFragment` / `fragment_model.xml`、`ApiConfig.java`、
`app/build.gradle`（1.0.5/vc6）、`build.gradle`（阿里云仓库）。

## 构建

```powershell
$env:JAVA_HOME="<JDK 11>"; $env:GRADLE_USER_HOME="D:\HermesData\gradle-tiers"
cd D:\github\tvbox-tv-oldtv-enhance
.\gradlew.bat clean :app:assembleRelease --console=plain
# zipalign -f -p 4 → apksigner sign（debug.keystore, v1/v2/v3）
```
