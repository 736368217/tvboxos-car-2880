# r74：日志暴露的封面解码循环修复与环境规范固化

## 1. 现场证据

诊断 v2 拉取的 `tvbox-5fbd191b…` 最近日志（13,937 行）中：

- `cover: decode failed path=…` **132 次**，涉及 15 个反复出现的 `.img`；
- 紧跟 `D/skia --- Failed to create image decoder with message 'unimplemented'`；
- 同一批 URL 均为 `https://jpg.girigirilove.com/...webp`，网络 Glide 路径无 `glide load failed`，说明图本身能显示；
- 部分文件从内容键 `restored … from content key` 复制而来，之后 `remember()` 又把「文件存在且非空」当成已缓存，不再重新下载；
- 结果：每次绑定都「本地 decode failed → 网络回退 → 认为已缓存」，滚动一次重复一轮，从 18:36 持续到 19:09。

`0 FATAL EXCEPTION`；`CCodecConfig BAD_INDEX`、`CloseGuard` 是既有噪声，不是崩溃。

## 2. 根因

r68 起历史/收藏/首页观看历史统一走 `CoverBinder`，本地命中时直接
`BitmapFactory.decodeFile` 绕开 Glide/OkHttp/DNS。这是为解决「图床/DNS 挂掉时本地真图被
Glide 失败回调覆盖」而刻意设计的，但 `CoverStore` 落盘的是网络原始字节：

1. 扩展/动画 WebP、AVIF、HTTP 200 的 HTML 错误页，在某些 skia 版本上
   `BitmapFactory` 建不出解码器；Glide（API 28+ 走 `ImageDecoder`）却能显示；
2. 坏文件一旦落盘，URL 键与内容键都会命中它，网络兜底成功也写不回可解码格式；
3. `findLocalPathByContent` / `findLocal` / `remember` 只判断 `exists && length>0`，把坏文件当正常缓存。

## 3. 修复

- 新增 `CoverFormat`：纯 Java 文件头嗅探（JPEG/PNG/GIF/普通 WebP/扩展 WebP/AVIF/未知），
  `bitmapFactoryRisky()` 标出不能保证 `BitmapFactory` 可解的格式；`CoverFormatHostTest` 27 checks。
- 新增 `CoverImageIo`：下载完成后真实解码验证；风险格式或本机不可解码时用
  `ImageDecoder` 取首帧并压成通用 JPEG；仍不可解则拒收，不再落盘；少数「风险头但本机能解」
  用同名 `.ok` 标记保留；旧坏文件启动时后台转码或删除；`CoverBinder` 解码失败时单文件异步自愈。
- `CoverStore`：读取/复制/reindex/remember 全部走 `isReadableImage()` 快筛；复制同步 `.ok`；
  下载落盘前 `ensureDecodable()`；启动时 `healLegacyCovers()`；`FILE_LOCK` 防止下载与自愈并发改写。
- `App.onCreate()` 挂启动自愈；`app/build.gradle` versionCode 46 / `3.1.0-car.7-r74`。

预期日志变化：`cover heal: scanned=… normalized=… removed=…`、
`cover: normalized <file> to jpeg` 或 `cover: removed undecodable <file>`；
同一坏文件不再每次滚动都 `cover: decode failed`。

## 4. 同时固化的流程规范

- `car/handover/MUMU-VERIFICATION-SPEC.md`：每台播放/详情/音量验收设备必须锁横屏、
  配 `https://clun.top/box.json`、三项判据确认有源；禁止把单台清空配置写成全局「无源」。
- 每次发布必须带非空 `notes`：官方 `latest.json`、GitHub Release、App 更新弹窗三处一致；
  没有 notes 不许发版。
- 全局 `~/.pi/agent/car-headunit.md` 增加「TVBox 当前项目实测补充」，指向仓库规范，
  避免后续会话只靠压缩摘要记忆。

## 5. 验证

- `git diff --check`：PASS。
- `:app:assemblePythonRelease -x … --offline`：**BUILD SUCCESSFUL in 2m 1s**（r73 源码，版本号随后改为 r74）。
- `CoverFormatHostTest`：`PASS checks=27`。
- r74 APK 签名、安装、MuMu 自愈、更新弹窗 notes、真实起播 `makeup` 日志：见本轮实机记录；
  车机零跑 B01 听感 A/B 未做，不编造结论。
