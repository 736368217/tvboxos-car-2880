# TV 版（老版本 oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明

## 交付物

| 版本 | 文件 | 大小 | SHA-256 |
|---|---|---|---|
| **r2（当前）** | `TVBox-TV-oldtv-enhance-r2.apk` | 16,956,378 bytes | `937770215C3C7B1858D4CEBCBD8F8A46D793BDD1B224C2C8B1D963B061FDA47F` |
| r1（已废弃） | `TVBox-TV-oldtv-enhance-r1.apk` | 16,952,282 bytes | `0E6B5048…2639190` |

| 项 | 值 |
|---|---|
| 包名 | `com.github.tvbox.osc`（TV 版原包名，与车机版 `.jun` 不同，可共存） |
| 版本 | `1.0.1-oldtv-enhance` / versionCode 2（r1 是 1） |
| 目标 | minSdk 18 / targetSdk 26 / compileSdk 33，带 `LEANBACK_LAUNCHER` |
| ABI | **仅 armeabi-v7a（32 位）** —— 与原版一致 |
| 签名 | `2feb63c5…fa9c`（本机 debug.keystore，v1/v2/v3 通过） |

## r2 修的是什么：订阅「获取失败」

### 先排除：不是包名/应用名白名单

微信/QQ 群里流传的「包名不匹配」在你这台机器上不成立。实测解密当前 spider.jar
（`xiaosa-unlimited/spider.jar`，md5 `d320359a34ceca5b74467faee3aaffb5`）里的两份白名单：

- 应用名白名单含 **`TVBox`**
- 包名白名单含 **`com.github.tvbox.osc`**

两者都精确命中，`Init.a/b/c` 三道校验全部通过。所以不存在「被 jar 杀掉」的情况。

### 真正原因：配置与 spider.jar 下载

老 TV 版对配置和 `spider.jar` 只发**一次**请求、用 OkGo 默认超时，没有镜像、没有慢速放弃。
实测同一网络下直连 `raw.githubusercontent.com`：

- 拉配置：32 秒超时
- 拉 1.9MB 的 `spider.jar`：87 秒超时

一旦失败，配置里 112 个站点全部落到 `SpiderNull`，界面表现就是「获取失败 / 一片空白」。

r2 改成多通道下载（`util/MirrorFetch`）：

```
直连（12s 连接 / 30s 读） → ghproxy.net → gh-proxy.com → ghfast.top
jar 另加慢速放弃：前 25 秒拿不到 256KB 就换下一个镜像
写 .part 再改名，避免半截文件被当有效 jar
```

实测日志（`adb logcat -s SubFetch`）：

```
text fail try=1/4 cost=32163ms SocketTimeoutException  url=raw.githubusercontent.com/.../api.json
text ok   try=2/4 cost=10096ms len=25315              url=ghproxy.net/https://raw...
loadConfig success, sites=112
file fail try=1/4 cost=87488ms SocketTimeoutException  url=.../spider.jar
file ok   try=2/4 bytes=1926329                        url=ghproxy.net/...
loadJar success
spider class=com.github.catvod.spider.AnimeGiri
homeContent len=223 → {"class":[{"type_id":"2","type_name":"日番"},…]}
```

jar 命中缓存（md5 一致）时第二次起 0.1 秒完成。

### 附带修掉

- `lives: [{}]`（现代订阅的「直播源留空」写法）会让 `loadLives` 抛 NPE 打断整段解析 —— 已加防护。
- 配置下载失败时优先回退到本地缓存，而不是直接报错。

## 保留的三项功能（r1 起）

设置 → **画面滤镜 / 超分 / 补帧**：

| 功能 | 档位 |
|---|---|
| 画面滤镜 | 原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色、动漫·暗光/日间/清透（11 档） |
| 超分 | 关闭、标准全能(SGSR1)、影视专精(FSR)、动漫线条(Anime) |
| 补帧 | 关闭、基础平滑(Oversample)、高阶时域(4帧 Cubic)、保守光流(Safe-Flow) |

三项全为默认时走原版 Texture/Surface 渲染，行为与未改动版本一致；任一非默认才启用 GL 增强渲染器。
**下次起播生效**。

## 改动清单（vs upstream `oldTv` @ a545c27）

新增：
```
player/.../render/{GlEnhanceRenderView,GlEnhanceRenderViewFactory,VideoEnhanceConfig,
  VideoEnhancePreset,VideoEnhanceTiers,VideoPostProcessPolicy,VideoShaders,
  VideoTierShaders,VideoDocShaders,MpvHookPipeline,MpvHookShaderCompiler}.java
app/.../util/VideoEnhanceHelper.java     三项设置入口 + 渲染器工厂
app/.../util/MirrorFetch.java            配置/jar 多通道下载（r2）
app/src/debug/.../SpiderProbeActivity.java  仅 debug 的测试探针（不进交付包）
```
修改：
```
app/.../util/HawkConfig.java             +3 个配置 key
app/.../util/PlayerHelper.java           增强开启时改用 GL 渲染器（2 处）
app/.../ui/fragment/ModelSettingFragment.java  +3 个选择弹窗
app/src/main/res/layout/fragment_model.xml    +1 行（三项并排）
app/.../api/ApiConfig.java               loadConfig/loadJar 改多通道 + loadLives 防护（r2）
app/build.gradle                         版本号 1.0.1-oldtv-enhance / versionCode 2
build.gradle                             补阿里云 public 仓库（原依赖来自已关停的 jcenter）
```
未改动：VideoView、播放器实现、首页/详情/播放页、任何原有设置项、ABI 配置。

## 安装与自查

```
adb install -r TVBox-TV-oldtv-enhance-r2.apk
```

1. 设置 → 配置地址 → 填入 `https://raw.githubusercontent.com/736368217/tvbox/xiaosa-unlimited/api.json`
2. 首页数据源里选「吉里 • 动漫」，进站点看列表
3. 观察下载过程：`adb logcat -s SubFetch`（能看到直连失败换镜像）
4. 观察增强是否生效：`adb logcat -s GlEnhance`

## 诚实说明：验证到哪一步

**已实证**（MuMu 模拟器，debug 与 release 混淆包都测过）：

- 白名单：包名 `com.github.tvbox.osc` + 应用名 `TVBox` 均在表内（解密自当前 spider.jar）
- 配置加载：112 站点（含 `吉里动漫`）
- spider.jar 下载 + DexClassLoader 加载成功
- 吉里 spider 载入：`com.github.catvod.spider.AnimeGiri`
- 吉里 `homeContent` 返回真实分类：日番 / 美番 / 真人番剧 / 剧场版 / BD副音轨 / 演唱会及其他

**未能实证**：

- **吉里分类列表内容**：模拟器里 `categoryContent` 返回 `{"total":0}`。同一 URL 从宿主机
  （直连与代理都试过）返回 `200`、22156 字节、`total:3147`，说明是**模拟器网络到不了该站 API**，
  不是 App 逻辑。真机上需要你确认一次。
- 播放：模拟器是 x86_64，原版 IJK 只有 32 位 so，无法在本机验证播放链路。

如果 r2 装到你机器上仍是「站点空白」，请在出问题的时刻抓这两条给我：

```
adb logcat -s SubFetch SpiderProbe
adb logcat -d | findstr /i "System.err girigiri"
```

前者能看出 jar 是走了直连还是镜像、有没有下载成功；后者能看出 spider 内部异常。
