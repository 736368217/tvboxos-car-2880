# r88/r89：更新检查改为 R2 主通道，GitHub 兜底

## 背景

用户决定：车机/TVBox 的 APK 分发从 GitHub Release 迁到 **Cloudflare R2**，分发域名
`https://dl.736.de5.net`。GitHub Release 降级为兜底。

## 客户端改动

| 文件 | 改动 |
|---|---|
| `app/src/main/java/com/github/tvbox/osc/update/R2Manifest.java` | 新增。解析 `<project>/latest.json`（`version / versionCode / apk / size / sha256 / notes`），并把 `apk` 对象 key 展开成绝对地址。**白名单**：必须 `https` 且主机名恰为 `dl.736.de5.net`；`http`、其它域名、`dl.736.de5.net.evil.com` 这类后缀伪装、`userinfo@` 伪装一律拒绝。 |
| `app/src/main/java/com/github/tvbox/osc/update/AppUpdate.java` | ① 新增 `R2_BASE / R2_PROJECT / R2_MANIFEST` 常量与 `queryR2()`；② `check()` 先读 R2（带 DoH 兜底的客户端），有更新直接返回；R2 可读但「已是最新」时继续问 GitHub，GitHub 全挂再用 R2 结果收口，避免把「已最新」报成「检查失败」；③ 下载候选：R2 地址不套 gh 镜像前缀（`isOfficialUrl` 现在同时认 `dl.736.de5.net`），实测 `candidates=1`；④ 新增 `shortSha()` 日志（打印 sha 前 12 位）。 |
| `car/tests/R2ManifestHostTest.java` | 新增，21 项：字段解析、sha 大写化、短 sha 丢弃、缺 version / 缺 apk / 非对象拒绝、http 拒绝、其它主机拒绝、后缀伪装拒绝、userinfo 伪装拒绝、大小写主机、显式端口、前导斜杠规整。 |
| `tools/publish_r2.ps1` | 从 `car-browser/b-tvb` 拷入，作为本仓库的发布入口（Worker PUT，非 S3 直传）。 |

## 发布（R2）

```powershell
powershell -NoProfile -File tools\publish_r2.ps1 -Project tvboxos-car-2880 `
  -Version 3.1.0-car.7-r89 -VersionCode 61 `
  -Apk <apk> -Notes "更新改为优先从 dl.736.de5.net 检查`nGitHub 仍作兜底`n修复调音量卡顿"
```

- 上传走 Worker 的 `PUT`（脚本用 `curl.exe` + `X-Upload-Token`），**没有**用 rclone / aws cli / S3 直传：
  本机到 `<account>.r2.cloudflarestorage.com` 的 TLS 会被中间层打断。
- 口令读取 `C:\Users\WIN10\Desktop\认证\r2-upload-token.txt`，不落日志、不进仓库。

**清单实际地址**：`https://dl.736.de5.net/tvboxos-car-2880/latest.json`
（`history/<version>.json` 每个版本留一份，便于回滚）

## 真实检查更新结果（设备实测）

设备：MuMu vm6，2880×1620/d160；先装 **r88**（R2 客户端），R2 上是 **r89**。

1. 检查（读 R2，含 sha 前缀）：

```
I AppUpdate: r2 manifest ok local=3.1.0-car.7-r88 remote=3.1.0-car.7-r89 hasUpdate=true
             size=52771531 sha256=D3E70499569C…
I AppUpdate: check ok channel=R2 force=false local=3.1.0-car.7-r88 remote=3.1.0-car.7-r89
             hasUpdate=true sha256=D3E70499569C…
I AppUpdate: auto update dialog shown on HomeActivity
```

2. 下载（R2 单地址，未套镜像前缀）：

```
I AppUpdate: download start size=52771531 -> .../TVBox-Original-Feature-3.1.0-car.7-r89.apk
             candidates=1 first=https://dl.736.de5.net/tvboxos-car-2880/TVBox-...-r89.apk
I AppUpdate: download try pass=1 1/1 resume_from=0 url=https://dl.736.de5.net/...
```

3. 校验与安装：

```
I AppUpdate: sha256 ok D3E70499569C60A8FDBF36E6C72F9D25EC83E8C8D5D295E2BE6B7539541C5285
I AppUpdate: verify ok package=com.github.tvbox.osc.jun versionCode=61 sig=2FEB63C511CD7…
I AppUpdate: install launched file=TVBox-Original-Feature-3.1.0-car.7-r89.apk
```

4. 安装后：`versionCode=61 versionName=3.1.0-car.7-r89`。

证据：`car/reports/r88-r2-update/`（更新弹窗截图、`r2-update.log`、`latest.json` 快照）。

## 兜底与边界

- GitHub 多通道（API → Web 302 → gh 镜像）代码保留；R2 读不到时才走它。
- R2 清单里的 `apk` 只接受本域名的 https 地址，发布清单被篡改也换不到第三方源；
  下载完仍做 SHA-256 与签名/包名/versionCode 校验（实测三项都通过）。
- 白名单校验在客户端，不在传输层：Worker 若被换掉域名，客户端会直接拒绝该清单。
- 本次只验证了 MuMu 车机模拟器与当前网络；真车上 R2 的 CDN 命中与移动网络表现未单独测。
