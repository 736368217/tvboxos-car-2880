# 给网页端 AI 的 TVBox 在线日志查看规则

## 固定入口

所有入口都是公开 GET，不需要上传令牌。

| 目的 | URL | 返回 |
| :--- | :--- | :--- |
| 最新一批 | `https://dl.736.de5.net/logs/tvboxos-car-2880/latest.json` | 最新完整 `rawLog` |
| 某天全部批次 | `https://dl.736.de5.net/logs/tvboxos-car-2880/dates/YYYY-MM-DD.json` | 当天批次清单 |
| 查看任意批次 | 日期清单里的 `json` 字段 | 完整、未压缩 JSON + `rawLog` |
| 下载任意批次 | 日期清单里的 `gzip` 字段 | 原始 `.json.gz` 压缩包 |

例如查看 **2026-11-27**：

`https://dl.736.de5.net/logs/tvboxos-car-2880/dates/2026-11-27.json`

App 每次成功上传一批日志后会同时更新：

1. `records/<device>-<batch>.json`：完整可读批次；
2. `dates/YYYY-MM-DD.json`：当天索引；
3. `latest.json`：最新批次指针。

## 公开接口使用方式

给定日期 `YYYY-MM-DD`，先 GET：

`https://dl.736.de5.net/logs/tvboxos-car-2880/dates/YYYY-MM-DD.json`

返回结构：

```json
{
  "schema": "tvbox-online-date-v1",
  "date": "2026-11-28",
  "count": 31,
  "records": [
    {
      "batch": "b-...",
      "appVersion": "3.1.0-car.7-r99",
      "publishedAt": 0,
      "json": "https://dl.736.de5.net/logs/tvboxos-car-2880/records/<device>-<batch>.json",
      "gzip": "https://dl.736.de5.net/logs/tvboxos-car-2880/<device>-<batch>.json.gz"
    }
  ]
}
```

网页端 AI 或其他查看者选择 `records[].json` 直接 GET 完整日志；下载器选择 `records[].gzip` 保存原始压缩包。
如果页面缓存了旧结果，在 URL 后加 `?ts=<当前毫秒时间戳>`。

2. 先检查 HTTP 状态必须是 `200`。
3. 解析 JSON，核对：
   - `schema` 应为 `tvbox-online-log-v1`；
   - `product` 应为 `tvbox`；
   - `appVersion`、`batch`、`publishedAt` 存在；
   - `rawLog` 是日志正文。
4. 只读 `rawLog` 做诊断。先搜索下面这些关键词，再按时间顺序读上下文：

   - 播放：`ExoPlayerImpl`、`MediaCodec`、`CarAudioGain`、`echo-goPlayUrl`、`LoadError`
   - 音量：`CarAudioGain`、`gain=`、`inPeak=`、`outPeak=`、`volume`、`gesture_axis`
   - 返回：`back:`、`carClash`、`EdgeBackService`、`edge_back`、`back_perform`
   - 网络：`GitHubCdn`、`PlainHttp`、`echo-config`、`echo-getSpider`、`echo-detail`
   - 生命周期：`app_foreground`、`app_background`、`playback_`
   - 崩溃：`FATAL EXCEPTION`、`AndroidRuntime`、`SIGSEGV`
5. 报告时带上 `appVersion`、`batch`、`publishedAt`，并明确日志覆盖的时间范围。不要把完整播放 URL、请求头、令牌或订阅 token 复制到报告里。

## 返回的数据形状

```json
{
  "schema": "tvbox-online-log-v1",
  "product": "tvbox",
  "device": "tvbox-<persistent-id>",
  "batch": "b-...",
  "appVersion": "3.1.0-car.7-r98",
  "clientVersion": "diag-r2-r96",
  "reason": "periodic",
  "publishedAt": 0,
  "rawLogBytes": 12345,
  "rawLog": "..."
}
```

`sourceGzip` 是同一批次的历史归档地址，需要保留原始 gzip 证据时才使用：

`https://dl.736.de5.net/logs/tvboxos-car-2880/<device>-<batch>.json.gz`

网页端 AI 通常只需要 `latest.json`。历史批次目录没有公开目录列表；不要让网页端 AI 猜测文件名，也不要把上传令牌放进浏览器请求。

## 诊断判断规则

- `appVersion` 不是当前要诊断的版本时，先提示日志属于旧包。
- `rawLog` 没有新的 `publishedAt` 时，说明设备尚未上传新批次；不能据此判定功能没有执行。
- 没有 `ExoPlayerImpl`/`MediaCodec`/`CarAudioGain` 时，只能说本批次没有捕获到播放阶段，不能说播放失败。
- `CarAudioGain` 的 `gain > 1` 是上行增益，`gain < 1` 是 r98 v4 的下行控制；同时出现属于预期双向动态。
- `FATAL EXCEPTION`、`AndroidRuntime` 属于 App 崩溃线索；`uiautomator` 自身的 `SIGSEGV` 要单独归为工具故障，不能写成 App 崩溃。
- `echo-detail ... candidates=0` 表示测试订阅详情搜索没有找到候选，不能推导播放器或小窗代码失败。
- `carClash=true component=...EdgeBackService` 表示 carclash 接管检测命中；组件包名以日志中的实际值为准。

## 人工复核命令

网页端 AI 不需要执行这些命令；需要人工复核时使用：

```powershell
$log = Invoke-RestMethod -Uri 'https://dl.736.de5.net/logs/tvboxos-car-2880/latest.json' -Headers @{ 'Cache-Control' = 'no-cache' }
$log.appVersion
$log.batch
$log.publishedAt
$log.rawLog
```

只读批次历史需要上传令牌，令牌只在本机使用，不能放入网页、截图、日志或提交：

```powershell
$token = (Get-Content "$env:USERPROFILE\Desktop\认证\r2-upload-token.txt" -Raw -Encoding UTF8).Trim()
$listing = curl.exe -sS --max-time 40 -H "X-Upload-Token: $token" 'https://dl.736.de5.net/_ls' | ConvertFrom-Json
$listing.objects | Where-Object { $_.key -like 'logs/tvboxos-car-2880/*' } | Sort-Object key
```

固定入口是网页端 AI 的正式契约；`_ls` 只用于人工找历史批次。
