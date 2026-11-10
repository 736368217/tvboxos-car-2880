# r75：修复官方通道更新说明解析

## 1. 现场证据

r74 发布后，MuMu 上 r73 强制「检查更新」：

- 日志：`check ok channel=官方(阿里云) force=true local=3.1.0-car.7-r73 remote=3.1.0-car.7-r74 hasUpdate=true sha256=true`；
- 官方 `tvbox-car/latest.json` 含 161 字符 UTF-8 `notes`；
- 但弹窗 UI 层级显示 `（该版本没有填写更新说明）`。

排除 CDN 编码问题：按 Unicode 码点核对 `latest.json.notes` 为 `U+66F4 U+65B0 U+8BF4 U+660E` 等正确中文，UI 码点也确认是「该版本没有填写更新说明」。

## 2. 根因

`OfficialManifest.parse()` 为避免 files 数组里的同名字段干扰，把 `files` 之前的子串作为 `head`，然后 `stringField(head, "notes")`。

但 `dl-publish.sh` 生成的字段顺序是：

```json
{"software":"tvbox-car","version":"…","files":[...],"releasedAt":"…","notes":"…"}
```

`notes` 在 `files` 之后，被 `head` 截断，官方通道的 `Info.notes` 永远是空串。
GitHub API 兜底通道用 `JSONObject.optString("body")`，不受影响，所以此前从 GitHub 路径能看到 notes，造成「发布链路已修好」的假象。

## 3. 修复

- `OfficialManifest.parse()` 的顶层标量字段 `software` / `notes` 改在完整 `body` 上查找；`files` 内部字段仍只从 `firstObject` 读取，避免嵌套对象同名污染。
- `OfficialManifestHostTest` 增加真实发布形状回归：`notes` 位于 `files` / `releasedAt` 之后、含 `\n` 转义，断言说明与文件元数据同时正确；测试 `PASS (24 checks)`。
- 版本号推进到 `versionCode 47 / 3.1.0-car.7-r75`。

## 4. 发布通道修正

r74 最初发布到 `TVBox` 目录，但 App 官方优先通道硬编码读取 `tvbox-car/latest.json`。本轮已把同一 r74 包补发到 `tvbox-car`；r75 同时发布到 `tvbox-car`（App 优先）、`TVBox`（交付目录）和 GitHub Release，三处 digest 一致。

## 5. 验证判据

- r73/r74 → r75 强制检查更新，弹窗 `tvNotes` 必须显示 4 行 `r75 更新说明`，而不是「该版本没有填写更新说明」；
- 日志 `channel=官方(阿里云)`、`notes` 非空、`sha256=true`；
- 下载后 `sha256 ok`、`verify ok versionCode=47 sig=2FEB63C5…`，系统安装器覆盖安装不报签名冲突。

车机零跑 B01 听感 A/B 未做；r73 自动电平对齐已在 MuMu 真实起播日志中验证链路，但不能替代车机听感结论。
