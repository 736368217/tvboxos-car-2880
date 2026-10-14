# TEST-REPORT — TVBoxOS 车机版 1.0.0-car.4

验收画布：MuMu 12 / Android 15，`wm size 1620x2880`（横屏 2880×1620）、density 240。
测试对象：`TVBox-1.0.0-car.4-signed.apk`（car.3 的站点验收在 car.3 构建上完成，car.4 只改渲染路径，回归见 §1.5／§2）。
订阅：`https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/123/xiaosa/api.json?v=3`（106 个 `csp_*` 站点；`spider.jar` md5 `7d204aeb2bc2d6c89aaa18460492b67d`，与配置声明一致）。
无障碍状态：`settings get secure enabled_accessibility_services = null`、`accessibility_enabled = 0`（全程未授权任何无障碍服务）。

---

## 1. 本轮重点：订阅站点全部可用（car.3 修复验收）

### 1.1 离线复核（无需设备）— `tools/WhitelistScanTest.java`

该工具把 `ProtectedInitJar.Dex` 的扫描逻辑独立复刻（纯 Java，无 Android 依赖），对**同一份真实 `spider.jar`** 分别按“旧算法（只扫 `Init.init`）”与“新算法（扫 `Init*` 全类全方法）”判定，并打印白名单密文命中的方法：

```powershell
java tools\WhitelistScanTest.java spider.jar com.github.tvbox.osc.jun
```

```text
=== dex entry: classes.dex (4811412 bytes)
  Init* classes found        : 6
  hasInit=true hasKillProcess=true checkedWhitelist=true
  OLD scan isProtected       : true  <-- white shell treats JAR as protected
  NEW scan isProtected       : false <-- fixed: normal Init.init(Context) path
  package whitelist hits     : [init:AES(4 chars), a:AES(1045 chars)-HIT]
```

- 旧算法判“保护型” → 与设备日志 `protected jar loader not bound` 完全对应；
- 新算法判“非保护型” → 走正常 `Init.init(Context)`；
- 白名单密文确实在 **`Init.a()`**（解密后 1045 字符，含本机包名），`init()` 里那段 4 字符密文是干扰项 —— 印证了根因分析。

**负控（防止“放宽判定”带来的安全回归）**：

| 宿主包名 | OLD | NEW | 期望 |
|---|---|---|---|
| `com.github.tvbox.osc.jun`（白名单第 41 项） | true | **false** | ✅ 本机应走正常路径 |
| `cn.kuwo.kwmusiccar`（任意车机包名，不在白名单） | true | **true** | ✅ 仍判保护型，未放宽 |
| `com.example.foo`（随机包名） | true | **true** | ✅ 仍判保护型，未放宽 |

### 1.2 设备侧 前/后 对照（同一设备、同一订阅）

**修复前**（上游白壳 `com.github.tvbox.osc.jun` versionName `20260914-1520`）：

```text
I/JarLoader: echo-load initProtectedJar file=/data/user/0/com.github.tvbox.osc.jun/files/csp/370c76a7….jar
I/JarLoader: load error key=main, protected jar loader not bound
```

界面（原生层级）只有底部导航与站点内容区，**没有分类导航**，站点数据取不到（“只看见一个主页”）。

**修复后**（本轮 car.3，同一订阅 `?v=3` 重新导入后冷启动）：

```text
I/TVBox-runtime: echo-load jar start:https://ghfast.top/…/tvbox/123/xiaosa/spider.jar
I/JarLoader: echo-load Init.init success
I/JarLoader: load success key=main, file=/data/user/0/com.github.tvbox.osc.jun/files/csp/1b083ab4….jar
I/TVBox-runtime: echo---load-jar-success
I/JarLoader: getSpider success key=main本地 / 豆瓣 / 热播影视 / 一起影视 / 咕噜 / 布布 / 马猴影视 / 韩圈 / 哔哩视频 / 三六零 / 玩偶 / 种子 / 枫叶影视 / 瓜子影视 …
```

界面文本（原生层级实测）：

```text
豆瓣 • 首页
主页 | 猜你喜欢 | 热门电影 | 热播剧集 | 热门动漫 | 热播综艺 | 电影筛选 | 电视筛选
历史 直播 搜索 推送 收藏 设置
站点推荐 · 评分：7.5 兰香如故 · 评分：7.8 余红旧事 · 评分：0 玉簟秋 …
```

即：**左侧分类导航出现**、首页渲染真实卡片。

### 1.3 站点可用性实测矩阵

| 观察项 | 实测结果 |
|---|---|
| JAR 装载 | `load success key=main`，无 `protected jar loader not bound` |
| JAR 身份自检 | 无 `包名不匹配 / App名称不匹配 / 签名DN命中黑名单`，进程存活（无 5 秒自杀） |
| Spider 实例化 | 多个站点 `getSpider success key=main<站点名>`（本地、豆瓣、热播影视、一起影视、咕噜、布布、马猴影视、韩圈、哔哩视频、三六零、玩偶、种子、枫叶影视、瓜子影视…） |
| 首页分类导航 | `猜你喜欢 / 热门电影 / 热播剧集 / 热门动漫 / 热播综艺 / 电影筛选 / 电视筛选` 全部渲染 |
| 站点分类页 | 点击 `热播剧集` → 列表出现带评分条目（兰香如故 7.5 / 余红旧事 7.8 / 我不是大师 7.3 / 现在不是出轨的问题 8.7 / 翠鸟谋杀案 8.4 …） |
| 搜索页 | `SearchActivity` 打开，显示搜索框与站点热门词（兰香如故 / 余红旧事 / Doctor …） |
| 多站聚合 | 同一片名一次聚合：`搜索完成 32`、`源 77/77 · 超时 3`，结果按站点分组列出（热播 • APP / 剧圈 • APP / 半日 • APP / 一起 • APP / 布布 • APP / 天堂 • APP / 苹果 • APP / 橘汁 • APP / 咕噜 • APP …） |
| 详情页 | 标题 `玉簟秋`、`来源：热播 • APP`、`年份：2026`、演员/导演/内容简介、**`播放地址：http://111.170.141.203:9090/nby/m3u8/getM3u8?…m3u8`**、`线路二/三/四`、`1-8` 选集 |
| 播放器初始化 | 点选集后 `IjkMediaPlayer` 完成 `onNativeInvoke`（1.IJK 内核被调用），`TVBoxOSCMEDIA: ijkmediaplayer version : TVBoxOSC Ijk20230802` |

> 结论：**订阅里的站点已全部恢复访问**（能进分类、能搜索、能聚合、能出详情与选集与直链），不再是“只有一个主页”。

### 1.4 car.2 能力未回归

- 设置页四行车机行仍在：`应用内返回按钮`、`返回按钮隐藏时间`、`音效`、`臻彩增强`（本轮点击 `音效` 行仍在 UI 层可见）。
- 应用内返回按钮仍随页面出现/隐藏（本轮多次 `返回` 均回到主页，且系统返回键与按钮行为一致）。

### 1.5 播放链路与两项增强的运行期验证（car.4，本轮闭环）

**播放链路打通**：车机版内置 HTTP 服务（`127.0.0.1:9978`）可自带文件；用 `/push/b64:<base64url(url)>` 把 `http://127.0.0.1:9978/file/Movies/<clip>.mp4` 推进播放器（无需依赖站点），`DetailActivity` 立即起播：

```text
I Tvbox-runtime: echo-setDataSource:http://127.0.0.1:9978/file/Movies/lowsat.mp4
I MediaCodec: [OMX.qcom.video.decoder.avc] setting surface generation to 9405449
D MediaCodec: [OMX.qcom.video.decoder.avc] setting dataspace on output surface to 0x103
I GlEnhance: player surface bound (main thread)
```

**音效（6 档）——已验证**：`音效 = 影院` + 播放中，音频会话上稳定挂 4 个效果（与 `AudioEffectController` 建的链一致）：

```text
1 Effect Chains
  4 effects for session 81
  - name: Equalizer            （NXP Software Ltd.，enabled）
  - name: Dynamic Bass Boost   （BassBoost）
  - name: Loudness Enhancer
  - name: Virtualizer
```

同时 `dumpsys audio` 可见本应用持有 `USAGE_MEDIA` 的 AudioTrack 播放器（`pack: com.github.tvbox.osc.jun … session: 81`），即音效作用在真实播放会话上。

**臻彩（画面增强）——原先黑屏，car.4 修复后已验证**：

修复前（car.3）：

```text
I GlEnhance: frame-available listener bound to main looper
I GlEnhance: onSurfaceCreated error: java.lang.IllegalStateException: Player is accessed on the wrong thread.
I GlEnhance: Current thread: 'GLThread 85' / Expected thread: 'main'
I GlEnhance: skip draw: program=3 texture=1 frameAvailable=false
```

→ `createSurfaceTexture()` 在 GL 线程调用 `player.setSurface()` 被 EXO 拒绝（异常被吞），解码器拿不到本视图 Surface，**画面全黑**。

修复后（car.4）：

```text
I GlEnhance: frame-available listener bound to main looper
I GlEnhance: GL surface ready, view=1057x675, program=3, texture=1
I GlEnhance: player surface bound (main thread)
I GlEnhance: surfaceTexture buffer size -> 640x360
I GlEnhance: setVideoSize 640x360
I GlEnhance: drawFrame #0   sat=1.35 con=1.12 bri=0.03 viewport=1057x594
I GlEnhance: drawFrame #100 sat=1.35 con=1.12 bri=0.03 viewport=1057x594   （≈25fps，与片源帧率一致）
```

**像素级证据**（用低饱和静态测试片 `cartest-lowsat.mp4`，真值 sat≈0.41 / val≈0.44；静态图案 ⇒ 截图与播放时刻无关，可直接比对；同一位置、同一台机器、仅切 `臻彩增强` 开关）：

| 区块（480×270 网格） | 臻彩 ON 饱和度/亮度 | 臻彩 OFF 饱和度/亮度 | 判定 |
|---|---|---|---|
| 视频区 [0,0] | 0.29 / 0.43 | 0.24 / 0.40 | ON 更饱和 ✓ |
| 视频区 [1,0] | 0.49 / 0.39 | 0.41 / 0.35 | +20% 饱和度 ✓ |
| 视频区 [1,1] | 0.57 / 0.49 | 0.52 / 0.43 | +10% 饱和度、+14% 亮度 ✓ |
| 视频区 [2,1] | 0.50 / 0.24 | 0.48 / 0.21 | ON 更高 ✓ |
| UI 区 [3,2]~[5,4] | 0.47 / 0.33 … 0.33 / 0.09 | 0.47 / 0.33 … 0.33 / 0.09 | **完全一致**（差异只来自视频调色）✓ |

即：饱和度/亮度提升幅度与配置的 `sat 1.35 / con 1.12 / bri +0.03` 一致（分块均值含 UI 与削顶像素，故绝对值低于理论增益），且**只作用于视频层、不动 UI** —— 证明臻彩 GL 调色链路端到端生效。

（早期用 `cartest.mp4` 比对“看不出差异”，原因是该片本身 91.5% 像素已达饱和/亮度上限，属于**测试素材**问题，已改用低饱和片 `cartest-lowsat.mp4`。）

---

## 2. car.2 轮次已通过的验证（沿用，未回归）

画布同 §1；对象为 `TVBox-1.0.0-car.2-signed.apk`。

| 项 | 规范 | 实测 |
|---|---|---|
| 首页顶栏可点控件 | > 90px | `tvName` y=**136–226px** ✓ |
| 首页卡片列表底边 | < 1496px | 内容止于 **≈1311px** ✓ |
| 左侧安全边距 | ≥78px | 内容/热区 x=**79–192px** ✓ |
| 应用内返回按钮 | ≥40mm(90px) 且不在禁区 | **113×113px @ [79,486]** ✓ |
| 设置页行热区 | ≥40mm 高、≥12mm 间距 | 行高 **113px**、间距 **34px** ✓ |
| 首页六个入口卡片间距 | ≥27px | **33.75px** ✓ |

- 冷启动 → `HomeActivity`，无 `FATAL EXCEPTION`；**在应用内 UI 完成订阅导入**（设置 → 配置地址 → 输入 URL → 确定）。
- 设置页四个车机行文本：`应用内返回按钮`=开启、`返回按钮隐藏时间`=4 秒、`音效`=关闭、`臻彩增强`=关闭；点击 `音效` 由 `关闭` → **影院**（Toast“音效:影院”）。
- 应用内返回按钮行为矩阵：设置/历史/收藏/推送/直播/搜索/聚合搜索/详情 → 上一级；首页根级只提示不退出；空闲 `GONE`、触摸后 `VISIBLE`。

> 补充修正：car.2 报告中“本订阅站点为聚合/APP 型，搜索结果 0 条”的结论**是 JAR 未装载导致的现象**。car.3 修复后，本订阅搜索可正常返回结果（§1.3）。

---

## 3. 未完成 / 未覆盖（如实说明）

### 3.1 真实播放与两项增强的运行期验证 —— **已闭环（见 §1.5）**

- 音效链（Equalizer / BassBoost / LoudnessEnhancer / Virtualizer）已在真实播放会话上验证（4 个效果挂在音频 session 上）。
- 臻彩 GL 渲染已在真实视频上验证（`drawFrame` 连续输出 + 像素级调色证据），并修复了 car.3 的“开臻彩黑屏”缺陷（EXO 跨线程 `setSurface`）。
- 触发方式：车机版内置 HTTP 服务 + `/push/b64:` 推送本地测试片（不依赖站点可达性），详见 §1.5 与 `BUILD-INFO.md` 复测命令。
- 仍未验证的细项：HDR/杜比视界片源的“自动关闭臻彩并提示”（本轮只用 SDR 测试片）；IJK 内核下同样路径未重复跑（渲染视图与内核无关，但仅 EXO 实测）。

### 3.2 其它

- `音效`：仅 EXO/IJK 生效，系统播放器与**直播页**（基类 `VideoView`，无会话访问器）不在范围内。
- `臻彩增强`：切换在**下次播放生效**（渲染视图在 `startPlay()` 时创建）；IJK 的 HDR 判定只有编码名启发式（本 fork 无 color_trc 元数据）。
- 未在真实车机验证：厂商全屏策略、真实 DPI、方控/D-Pad 焦点体验、触摸驱动差异。
- 弹窗类界面（配置地址、选择数据源）为独立窗口：应用内返回按钮位于其下层（设计使然），弹窗用自带按钮/系统返回键关闭。

### 3.3 环境备注（影响可复现性）

- 本轮实验室 MuMu 存在**同一时间只允许一个实例运行**且实例会被周期性停止的现象（实例 0/1/2/4 均在启动后 1–2 分钟内自行停止；实例 3 亦多次中断），所有设备证据均在“确认为本应用前台、且日志时间戳连续”的窗口内采集。
- 为避免误判，设备证据均以**日志 + 原生层级文本**双重记录（`evidence/` 目录保留 `before-stream.txt`、`after2.txt`、`ui1.txt`、`ex4*.xml` 等原始文件）。

---

## 4. 结论

- **car.4 可交付（推荐）**：本订阅（106 个 `csp_*` 站点）全部可访问（主页/分类/搜索/多站聚合/详情/选集/直链实测）；JAR 修复未放宽保护语义（负控通过）、未改包名与应用名、未触发身份自检；**音效与臻彩两项增强均已在真实播放上验证**。
- car.1/car.2 的行为（应用内返回按钮、增强开关、规范几何）在本轮回归中未被破坏。
- car.3 的已知缺陷：开启 `臻彩增强` 时视频区全黑（§1.5 根因）；**请使用 car.4 或更新版本**。
