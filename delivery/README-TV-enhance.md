# TV 版（老版本 oldTv）+ 超分 / 补帧 / 滤镜 —— 交付说明

## 当前版本：r3

| 项 | 值 |
|---|---|
| 文件 | `TVBox-TV-oldtv-enhance-r3.apk` |
| 大小 | 16,956,378 bytes |
| SHA-256 | `F6FF992B640298CF006743BBBB14D43AF22DE6AB4B52FA7152755B2650028888` |
| 版本 | `1.0.2-oldtv-enhance` / versionCode 3 |
| 包名 | `com.github.tvbox.osc`（TV 版原包名） |
| 目标 | minSdk 18 / targetSdk 26，带 `LEANBACK_LAUNCHER` |
| ABI | 仅 armeabi-v7a（32 位，与原版一致） |
| 签名 | `2feb63c5…fa9c`（v1/v2/v3 通过） |

旧包 `…r1.apk`（SHA `0E6B5048…`）、`…r2.apk`（SHA `93777021…`）均已废弃，请装 r3。

## r3 修的是什么：TV 端「订阅完全用不了 / 点视频空白」

### 逐个排除（都有实测证据）

| 猜测 | 结论 |
|---|---|
| 包名触发 spider.jar 白名单 | **否**。解密当前 jar 白名单：应用名表含 `TVBox`、包名表含 `com.github.tvbox.osc`，精确命中。 |
| jar 与宿主 Spider 基类契约不兼容 | **否**。反编译 `AnimeGiri`：只调 `super.init()`，网络走 jar 自带 `merge.k.b`。 |
| DNS 挂死 | **否**。实机 `Spider.safeDns().lookup()` 13~33ms 返回 6 个 IP；标准 OkHttp 请求该站 API `200`/3742 字节。 |
| jar 内部 HTTP 客户端坏了 | **否**。后台线程直调 jar 的 `k.b.l()` 返回 13197 字节、`total:3147`。 |

> 排查中发现一个容易误判的坑：jar 的 spider 方法内部**同步发 HTTP**，必须在后台线程调用；
> 在主线程调会得到 `NetworkOnMainThreadException`，被 jar 内 `catch (Exception)` 吞掉后表现为
> 「0ms 返回空列表」。真实 App 的 `SourceViewModel` 用 `spThreadPool` 后台线程，不受影响。

### 真正的三个故障点（均已修）

1. **首次安装没有任何默认订阅** → `loadConfig` 直接返回 `-1`，首页永远空白，用户以为「订阅功能坏了」。
   修：`ApiConfig.DEFAULT_API_URL` 内置 `xiaosa-unlimited`（jsDelivr CDN，国内直连实测 384ms/25315 字节），
   首次启动自动写入 `HawkConfig.API_URL`。
2. **国内直连 `raw.githubusercontent.com` 不通**（宿主机实测 15s 超时），配置与 1.9MB 的 `spider.jar`
   都拿不到；`ghproxy.net` 还会返回 **HTTP 200 但只有 818KB 的半截 jar**（坏 jar 加载失败 → 站点全空白）。
   修：`MirrorFetch` 候选 = 直连 → jsDelivr CDN（仅文本）→ fastly jsDelivr → ghproxy.net → gh-proxy.com → ghfast.top；
   下载完按配置里声明的 **md5 逐一校验**，不匹配就删掉换下一个候选。
3. **`fixContentPath` 把 `./spider.jar` 拼到 jsDelivr 地址** → `cdn.jsdelivr.net/.../spider.jar` 一律 **403**。
   修：识别 `@branch` 形式的 CDN 基址，还原成 `raw.githubusercontent.com/o/r/branch/`，再由 MirrorFetch
   展开候选；二进制资源不进 CDN 候选。

附带：`lives:[{}]`（现代订阅的直播源留空）会让 `loadLives` 抛 NPE 打断解析 —— 已加防护。

### 端到端实测（清空数据、零手输，MuMu 模拟器，release 混淆包）

```
SubFetch: api_url empty -> default https://cdn.jsdelivr.net/gh/736368217/tvbox@xiaosa-unlimited/api.json
SubFetch: text ok try=1/4 cost=384ms len=25315 url=cdn.jsdelivr.net/...
SubFetch: file fail try=1/4 cost=30447ms SocketTimeoutException  url=raw.githubusercontent.com/.../spider.jar
SubFetch: file ok   try=2/4 cost=42524ms bytes=1926329 url=ghproxy.net/...   ← md5 校验通过
```

- 首页真实渲染：`豆瓣 • 首页`，分类栏 主页/猜你喜欢/热门电影/热门动漫/…，卡片「小城良方 8.0 分」等
- 112 个站点加载成功；`loadJar success`
- 吉里（`csp_AnimeGiri`）：`homeContent` 返回 6 个真实分类；`categoryContent` 返回 **3695 字节**
  真实条目（新 魔法骑士雷阿斯 / 转生成为魔剑了 第二季 / 侦探已死 第二季…）
- jar 内部 HTTP：`13197` 字节、`total:3147`

**未能在本机验证**：32 位 IJK 在 x86_64 模拟器上缺 so，播放链路仍需在 TV 真机确认。
若真机上站点列表有了但点开详情空白，抓这两条给我：

```
adb logcat -s SubFetch
adb logcat -d | findstr /i "System.err girigiri"
```

## 保留的三项功能（r1 起，未改动）

设置 → **画面滤镜 / 超分 / 补帧**：

| 功能 | 档位 |
|---|---|
| 画面滤镜 | 原画、臻彩、自然、影院、鲜艳、柔和、明亮、暖色、动漫·暗光/日间/清透（11 档） |
| 超分 | 关闭、标准全能(SGSR1)、影视专精(FSR)、动漫线条(Anime) |
| 补帧 | 关闭、基础平滑(Oversample)、高阶时域(4帧 Cubic)、保守光流(Safe-Flow) |

三项全默认时走原版 Texture/Surface 渲染，行为与未改动版本一致；任一非默认才启用 GL 增强渲染器，
**下次起播生效**。自查：`adb logcat -s GlEnhance`。

## 改动清单（vs upstream `oldTv` @ a545c27）

新增：
```
player/.../render/ 11 个增强文件（GL 渲染器 + 分档着色器 + 滤镜预设 + 工厂/配置/档位）
app/.../util/VideoEnhanceHelper.java   三项设置入口
app/.../util/MirrorFetch.java          多通道下载 + CDN 规范化 + md5 校验
app/src/debug/.../SpiderProbeActivity.java  仅 debug 的诊断探针（不进 release，实测 DEX 不含）
```
修改：
```
app/.../util/HawkConfig.java            +3 个配置 key
app/.../util/PlayerHelper.java         增强开启时改用 GL 渲染器（2 处）
app/.../ui/fragment/ModelSettingFragment.java  +3 个选择弹窗
app/res/layout/fragment_model.xml       +1 行（三项并排）
app/.../api/ApiConfig.java             默认订阅 + 多通道配置/jar + md5 校验 + lives 防护 + 相对路径基址
app/build.gradle                       版本号 1.0.2 / versionCode 3
build.gradle                           补阿里云 public 仓库（原依赖来自已关停的 jcenter）
```
未改动：VideoView、播放器实现、首页/详情/播放页、原有设置项、ABI 配置。

## 构建（可复现）

```powershell
$env:JAVA_HOME="<JDK 11>"                        # AGP 4.2.2 不兼容 JDK 17
$env:GRADLE_USER_HOME="D:\HermesData\gradle-tiers"
cd D:\github\tvbox-tv-oldtv-enhance
.\gradlew.bat clean :app:assembleRelease --console=plain
# 产物 app/build/outputs/apk/release/app-release-unsigned.apk
# zipalign -f -p 4 → apksigner sign（debug.keystore, v1/v2/v3）
```
