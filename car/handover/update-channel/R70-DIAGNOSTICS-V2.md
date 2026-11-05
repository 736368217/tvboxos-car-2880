# r70：诊断客户端迁移到 canonical v2（app-diagnostics）

平台侧把接收端重构为多软件通用 `app-diagnostics` v2 后，TVBox 客户端从旧
`POST /tvbox-logs/upload` 迁到 `POST /app-diagnostics/v2/upload`（`product=tvbox`）。

## 一、给平台侧的确认口径

| 项 | 确认结果 |
| :--- | :--- |
| 对外版本 | `3.1.0-car.7-r70`（vc42），官方通道 `dl.z736.vip/tvbox-car/latest.json` + GitHub Release；上一版是 r69（v1 客户端） |
| 诊断客户端标识 | `product=tvbox`，`clientVersion=diag-v2-r70`，`device=tvbox-<持久随机 UUID>` |
| 源码 | `736368217/tvboxos-car-2880` → `app/src/main/java/com/github/tvbox/osc/diag/DiagLog.java` |
| 协议 | **只走 canonical v2**；旧 `/tvbox-logs/*` 仅为服务端兼容路由，留给尚未升级的 r68/r69 设备 |
| 联调 | MuMu（Android 15 / 2880x1620）已验证上传、只读拉取、重启续传、失败复用、严格判据拒收、duplicate 成功 |

## 二、迁移要求逐条落实

1. **设备 ID 持久随机**：一次性 `UUID` 存入 `SharedPreferences(diag_identity)`，
   重启、版本升级、整机关机再开机都不变；不再用 `ANDROID_ID`。
2. **batch 与请求体先持久化、失败复用**：发请求前把 gzip 后的完整字节 fsync 到
   `pending.bin`，把 `batch / cursor / nextCursor / sha256 / createdAt` 写到
   `pending.json`；失败不删、不换 batch，下次重试发同一份字节。重读时校验 checksum，
   损坏才丢弃重组。
3. **只使用 upload-only token**：APK 内只放 tvbox 作用域的上传 token，
   用 `Authorization: Bearer` 头传（不落在 URL query）。read-only token 只存在服务器
   和分析端；对所有解压后的 APK 条目做过二进制扫描，确认未进入 APK。
4. **严格成功判据**：仅当 HTTP 200 且 `accepted=true`、`id` 非空、
   `product=="tvbox"`、`device` 匹配、`cursor` 与发出值一致、`bytes>0`、`batch` 一致时，
   才把本地游标推进到 `nextCursor` 并清 pending。
5. **duplicate**：`duplicate=true` 且 id 与已记录的一致视为成功，同样推进游标。

另外本地仍是硬上限滚动：4 片 × 256 KiB = 1 MiB，游标持久化，进程重启接着上次位置。

### 顺带修掉的两个旧缺陷

- r68 用**单线程**执行器同时跑常驻 `logcat` 泵和上传，泵把线程永久占满，
  「高级设置 → 诊断日志 → 立即上传」永远排不上。r70 拆成 `PUMP`（采集）+
  `SCHED`（30 秒 tick + 手动上传）两个线程。
- r68 的批次游标在跨分片时算错（`skip + buffer.size()`），r70 改成从起点按实际
  保留字节数回算 `advanceCursor`，并在换行处截断。

## 三、实测证据（MuMu idx0）

不贴原始日志正文、不含任何凭据，只列关键判定：

- 新设备以 `tvbox-<UUID>` 出现在 v2 `/v2/devices`，`appVersion=3.1.0-car.7-r70`；
  连续 3 批 `reason=periodic/periodic/manual`，游标 `1:0 → 1:19148 → 1:19271 → 1:24933` 连续，
  无重复批次记录。手动上传标记确实到达服务端（r68 的缺陷已消除）。
- 重启 App 后再查：设备 ID 不变、v2 设备数不增、游标继续推进；整机断电重启后同样。
- 断开网络 4 分钟：`cursor.txt` 停在 `1:46630`，`pending.bin`（266 字节）与
  `pending.json` 中同一个 `batch=b-1.46630-1.46757-…` 一直未变 → 失败复用成立。
- 预注册同 batch 但故意给错 cursor：客户端收到 `accepted=true duplicate=true` 仍
  **拒绝推进**（日志字段 `cursor=false`、`batch kept=…`），连续 3 次保持 pending；
  说明严格判据逐项生效，不是只看 200。
- 把服务端该批记录改回同 id + 正确 cursor 后，客户端下一次重试得到
  `duplicate=true`，日志记 `upload ok v2 … duplicate=true … cursor=1:46757`，
  pending 清空 → duplicate 成功分支成立。
- 兼容性：平台重构后核对过 `v1_token_accepted_by_v2=True`、`v2_upload_equals_v1=True`，
  **线上 r68/r69 旧客户端没有被这次重构打断**，仍可经兼容路由上传。

回归：`VolumeBoostMathHostTest` 25 / `VolumeLimitHostTest` 18 /
`AudioVolumeRoutingHostTest` 4928 / `AudioGainProcessorHostTest` /
`CoverKeyHostTest` 49 / `OfficialManifestHostTest` 22 / `UpdateVersionHostTest` 95 全 PASS；
`:app:assemblePythonRelease` BUILD SUCCESSFUL；`git diff --check` 干净。

## 四、边界

- 真机（零跑 B01）未现场复测；上述均为 MuMu 证据。r70 已带 v2 日志，真机上等一次
  自动上传或点「高级设置 → 诊断日志」即可远程分析。
- 服务端配额：`tvbox` 每设备 8 片 / 2 MiB、每小时 120 批；客户端每 2 分钟一批、
  失败 30 秒退避，远低于配额。

## 五、一个与本次无关的观察

当前 GitHub 仓库 `Latest` 指向另一个产品的 `tv-v1.0.3-vc4`，不是 car.7 线。
App 内更新自 r67 起官方通道优先（且版本校验会挡住低 versionCode 的异包），
所以不影响 TVBox 更新链；若希望 GitHub Web 兜底也严格指向 car.7，需要平台/发布侧另行整理 tag。
