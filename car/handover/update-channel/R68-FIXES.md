# r68：历史封面彻底修复 / 诊断日志 / 300% 音量 / 悬浮球自动隐藏

## 1. 历史封面：找到的两个真原因

之前 r54/r61 一直在「URL 键落盘 + 内容键兜底」上打补丁，但用户实测新历史也不显示。
这次逐页查，**两个独立断点**：

1. **有两个历史页面**：独立 `HistoryActivity`（`HistoryAdapter`）和首页「设置页 → 观看历史」
   模式下的 `UserFragment` + `HomeHotVodAdapter`。后者 `convert()` 里只调 `ImgUtil.load(pic)`，
   **完全没读 CoverStore**，所以首页历史推荐从来不会用本地封面。
2. **Glide 失败回调盖掉真图**：`ImgUtil.getListener().onLoadFailed()` 无条件
   `view.setImageDrawable(fallback)`。列表复用时，旧条目的网络请求失败回调会落到已绑定新条目的
   ImageView 上，把已经显示好的本地真图重新盖成随机色块。

### 修法

- 新增 `cache/CoverBinder.java`：所有历史/收藏/详情/首页历史卡片走**同一个绑定入口**。
  - bind 前 `Glide.with(view).clear(view)`，清掉旧请求；
  - 本地缓存命中 → **直接 `BitmapFactory.decodeFile` + `setImageBitmap`**，绕开整个
    Glide / OkHttp / DNS 链路（这是「一定显示」路径）；
  - 未命中才走网络，并同时 `CoverStore.remember` 落盘；落盘完成用 token 防复用后直接换真图；
  - 大图按 `inSampleSize` 降采样，避免几十张历史封面一次解码 OOM。
- `HistoryAdapter` / `CollectAdapter` / `HomeHotVodAdapter`（首页观看历史模式）/ `DetailActivity`
  全部改为 `CoverBinder.bind(...)`；`CoverStore.reindex()` 补 URL 键副本。
- 新增 `cache/HistoryCoverRepair.java`：**点进历史记录页时，对 pic 为空的旧记录按
  sourceKey + id 重新拉一次站源详情（失败再按片名搜索），拿到 pic 就写回数据库并落盘**，
  成功后 EventBus 刷新页面。单次最多 30 条、60 秒内只跑一轮，避免打爆站源。
- `HistoryActivity.onResume()` 重新读库，从详情页返回也立即生效。

实机（MuMu idx1 / Android 15 / 2880x1620）证据：

```
I/CoverCache: cover: local bitmap hit name=后会友期 path=.../files/cover/92b7….img
→ 历史页卡片区域实测 2054 种颜色（真图，不是几十色的色块）

人为把唯一历史记录的 pic 清空 + 删除全部封面缓存后重进历史页：
I/CoverCache: history cover repair: start missing=1
I/CoverCache: cover: empty pic and no local name=后会友期
I/CoverCache: history cover repair: fixed name=后会友期 url=https://p.lztutu.com/…webp
I/CoverCache: cached 9a9190c8….img bytes=15442 …
→ sqlite3 复查: "pic":"https://p.lztutu.com/…webp"   （旧记录已永久补齐）
```

## 2. 滚动诊断日志 + 服务器接收（可直接分析）

用户要求「日志上传、滚动储存、不无限增长、你直接可以分析」。

### 客户端 `diag/DiagLog.java`

- App 启动即常驻读 `logcat -v time`（**全部 tag**，含 Glide/OkHttp/系统安装器/音频），
  写入私有目录 `files/diaglog/part-NNNNNN.log`。
- 本地环形：**每片 256 KiB，最多 4 片 = 1 MiB**，超出删最旧；进程重启接着上次位置写。
- 启动 20 秒后，每 2 分钟把「上次成功位置之后」的新日志 POST；失败退避 30 秒，
  **不推进游标所以不丢日志**；单次 ≤480 KiB。
- `高级设置 → 诊断日志` 可手动「立即上传」；失败/成功都写入 logcat 便于自查。

### 服务器

- `tvbox-logd.service`（`127.0.0.1:18973`，Python 3.11，systemd 开机自启），nginx
  `https://dl.z736.vip/tvbox-logs/` 反代 + 限速。
- **每设备 8 片 × 256 KiB ≈ 2 MiB 上限；全局 64 MiB、最多 64 台设备**，超出按最后上传
  时间淘汰整台设备；单次 body ≤512 KiB，device id 严格白名单正则。
- 写 token 用于上传，另发独立**只读 token** 给分析端（只读 token 不能写）。
- 接口：
  - `POST /tvbox-logs/upload?device=<id>&token=<写>&app=<ver>`
  - `GET /tvbox-logs/devices?token=<读>` 设备清单 + 体积 + 上限
  - `GET /tvbox-logs/latest?device=<id>&token=<读>&lines=20000` 最近日志（按时间正序）
  - `GET /tvbox-logs/health`

实测：设备 `98b103f96e2d4c40` 已自动上传，`latest` 拉到 506 行，含
`DiagLog: upload ok bytes=16085 code=200`、`history cover repair: fixed …`、
`InAppBack: vis mode=floating …` 等完整上下文；压测 12 轮上传后设备恒定 8 片 / <2 MiB；
错误 token 401、坏 device 400、超长 body 413。

> 说明：客户端内嵌的是上传 token，只做限速/挡扫描，不是保密凭据；日志含 URL，
> 所以只传用户自己的服务器。分析时用只读 token 拉取，不暴露写 token。

## 3. 音量上限 300%

- 全链路唯一常量 `VolumeBoostMath.MAX_GAIN = 3f`（0..3），`clampGain()` 统一夹取。
- 同步放开 `millibel()`（300% ≈ +950mB，未撞 `AudioEffectController` 的 1200mB 上限）、
  `pcmGain()`、`gestureVolume()`（仍 5% 一档）、`BaseController`、`ControlWrapper`、
  `MyVideoView`、`ExoPlayer.setAudioGain()`、`AudioGainProcessor.clampGain()`、
  `PlayFragment`。
- **200% 听感不明显的原因**：PCM 16bit 在 2 倍时 >16383 的采样已饱和削顶，响度不再线性上升；
  非 PCM 引擎会被压回 100%（`canBoostVolume()`）；duck/factor 是独立临时因子
  （`pcmGain = gain × factor`）。300% 让小信号段继续放大，但大信号仍会削波——这是 PCM
  采样的物理限制，不是代码漏乘。
- 测试：`VolumeBoostMathHostTest` 25 / `VolumeLimitHostTest` 18 /
  `AudioVolumeRoutingHostTest` 4928 / `AudioGainProcessorHostTest`（含 3 倍采样断言）/
  `CoverKeyHostTest` 49 / `OfficialManifestHostTest` 22 / `UpdateVersionHostTest` 95 全 PASS。

## 4. 悬浮球「一直显示」

根因：`FloatingBackBallView.enterIdle()` 只做 `animateIdle(1f)` —— 缩边 + 把 alpha 从 .9
降到约 `.38 × barAlpha/100`，**从未 `INVISIBLE` / alpha=0**；并且旧版本备份可能留下
`hide_seconds=0`（常驻），而 r42/r55 把设置入口删了，用户没法改回来。

修法：

- `enterIdle()` 真正 `setVisibility(INVISIBLE)` + `setAlpha(0)` + `setClickable(false)`；
  触摸/回弹期间的旧任务会重新排一次，不会「就此永远留在前台」。
- `wake()` 从隐藏恢复可见、可点、alpha 归位，并重排计时。
- `高级设置` 补回「几秒后自动隐藏」行：4 → 6 → 8 → 常驻 → 4 秒循环，
  直接读写 `InAppBackButton.get/setHideSeconds()`。
- 实机：切到「悬浮球」模式后，4 秒前后左侧区域像素差 739 点（球消失），
  右侧 0 点；点空白后同区域差 739 点（球唤醒）。

## 5. 验证边界

- 全部实机证据来自 MuMu（Android 15 / 2880x1620），**零跑 B01 上的听感、DNS、悬浮球
  未做现场复测**。r68 已带诊断日志：车机上出现问题时，等一次自动上传或点
  「高级设置 → 诊断日志 → 点此立即上传」，我用设备 ID 拉 `latest` 即可分析。
- 300% 仍受 16bit/浮点满量程削波限制；`level_in/level_out`、`CarAudio` 日志是判据。
