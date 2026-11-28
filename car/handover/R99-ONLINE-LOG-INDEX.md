# r99 · 公开完整日志接口

版本 `3.1.0-car.7-r99` / versionCode 71。r99 保留 `latest.json`，并新增按日期列出全部批次、单批完整 JSON 和原始 GZIP 下载的公开接口。

## 公共接口

| 用途 | 地址 |
| :--- | :--- |
| 最新批次 | `https://dl.736.de5.net/logs/tvboxos-car-2880/latest.json` |
| 某日全部批次 | `https://dl.736.de5.net/logs/tvboxos-car-2880/dates/YYYY-MM-DD.json` |
| 单批完整日志 | 日期索引 `records[].json` |
| 单批压缩下载 | 日期索引 `records[].gzip` |

例如：

```text
https://dl.736.de5.net/logs/tvboxos-car-2880/dates/2026-11-28.json
```

日期索引返回 `schema=tvbox-online-date-v1`、日期、批次数量和 `records` 数组。数组每项包含批次、设备、App 版本、上传时间、日志字节数，以及：

- `json`：公开、未压缩、完整 JSON，包含 `rawLog`；网页端 AI 直接 GET；
- `gzip`：公开原始 `.json.gz`，下载后用 gzip 解压；
- `batch`：批次 ID，用于引用诊断证据。

`latest.json` 返回 `schema=tvbox-online-log-v1`，包含最新批次的完整 `rawLog`，并带 `sourceJson`、`sourceGzip`、`dateIndex` 三个回指地址。

## 访问与安全边界

以上查看和下载接口都是公开 GET，不需要上传令牌、`_ls` 或目录权限。按用户选择采用公开完整日志方案，因此 `rawLog` 中可能出现完整 URL、订阅地址或其他诊断细节；网页端 AI 读取后不得在回复中复制 token、Authorization、Cookie 或完整敏感 URL。

App 仍使用 `X-Upload-Token` 写入 R2。上传令牌只存在构建机的 `car/ci/upload-token.txt`，不进入仓库、网页请求或日志内容。原始 GZIP 批次不会删除，日期索引会持续追加并按 batch 去重。

## 历史数据

r99 首次运行后，已将 R2 中已有的 207 个历史 GZIP 批次回填为公开 JSON 记录，并生成日期索引：

- `2026-11-25`：7 条；
- `2026-11-27`：169 条；
- `2026-11-28`：31 条。

后续 App 每次自动上传成功后，会自动写入当天日期索引、单批 JSON 和 `latest.json`。

## 网页端 AI 读取规则

1. 先 GET 日期索引或 `latest.json`，确认 HTTP 200 和 schema；
2. 需要查全天时遍历 `records[]` 的 `json` 地址；
3. 需要下载原始证据时使用同一项的 `gzip` 地址；
4. 按 `publishedAt` 和 `batch` 排序；
5. 关键词优先搜索 `ExoPlayerImpl`、`MediaCodec`、`CarAudioGain`、`GitHubCdn`、`echo-detail`、`carClash`、`FATAL EXCEPTION`；
6. 报告引用时保留日期、batch、App 版本和来源 JSON 地址，不输出凭据或完整敏感 URL。
