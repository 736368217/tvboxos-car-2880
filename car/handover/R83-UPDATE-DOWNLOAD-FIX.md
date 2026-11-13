# r83：应用内更新 0% 与复制链接打不开

## 现象

车机上点「立即更新」后进度长期停在 0%，弹窗「复制链接」拿到的地址粘到浏览器也打不开。

## 已测证据

宿主侧对同一 Release 资产 `TVBox-Original-Feature-3.1.0-car.7-r82.apk`（52,750,546 字节）做 Range 探测：

| 地址 | 结果 |
|---|---|
| 裸 `github.com/.../releases/download/...` | 21 秒后 fetch failed |
| `https://ghfast.top/<资产>` | 206，1.0 秒，返回 `PK` 头 |
| `https://gh-proxy.com/<资产>` | 206，1.5 秒，返回 `PK` 头 |
| `https://ghproxy.net/<资产>` | 206，1.7 秒，返回 `PK` 头 |

检查通道方面：`api.github.com` 直连 1.3 秒可返回最新 tag；`ghfast`/`ghproxy` 的 API 前缀返回 403，但它们的 Web 302 通道可用。

设备侧（MuMu，Wi-Fi 已 VALIDATED、ping 223.5.5.5 通）实测：system DNS 对 `github.com` 与三个镜像全部返回 `UnknownHostException`，即设备网络在解析阶段就失败。

## 根因

1. **下载候选把直连排在首位**。`downloadCandidates()` 先放查询通道前缀，未命中时首个候选就是裸 GitHub 资产地址。
2. **单个候选的读超时是 75 秒**，而车机网络下直连是「连上但不回字节」。因此进度条长时间停在 0%，用户看不到任何错误，直到超时才换通道。
3. **复制链接直接复制 `info.apkUrl`**（通常就是裸 GitHub 地址），没有做可达性判断，于是粘到浏览器也打不开。
4. **设备 DNS 可能整体不可用**：检查与下载都只依赖系统 DNS，解析失败即全链路失败。
5. **首版 DoH 兜底自己也需要 DNS**：`doh.pub` 是域名端点，系统 DNS 全挂时它同样解析不出来；
   而 Web 302 通道用的 `systemDnsNoRedirect()` 完全没有兜底。

## 修复

- 下载前并发探测候选（Range `bytes=0-1023`，校验 `PK` 头），按「可用且更快」排序；探测失败的候选保留在末尾兜底，不会因一次探测失败放弃更新。
- 检查通道改为镜像优先（`preferMirrors`），与下载走同一条能出网的路。
- 单候选首字节读超时降到 15 秒；镜像连通后用 75 秒流式读超时传大文件，兼顾弱网。
- 「复制链接」改为先探测，再复制当前网络确实可下载的地址；探测失败才退回 Release 页。
- 新增 DNS 兜底（`PlainHttp.systemDnsWithDohFallback()`）：系统 DNS 失败时用公共 DoH JSON 解析，检查、探测、下载三条路径都使用它。
- DoH 端点全部改成 IP 直连（`223.5.5.5` / `1.12.12.12` / `223.6.6.6`），解析过程不再依赖系统 DNS；
  系统 DNS 与 DoH 并发发起，正常网络下系统 DNS 先返回就用它（行为与旧版一致），并有 5 分钟结果缓存。
- 新增 `PlainHttp.systemDnsNoRedirectWithDohFallback()`，Web 302 通道同样走 DoH 兜底。

## 验证

### 设备侧 DoH 兜底（r83 实装包，vm6）

在 system DNS 对更新域名全部 `UnknownHostException` 的车机上，直接构造 r83 包内被 R8 改名的
`PlainHttp$DohFallbackDns`（`androidx.base.c20$b`）并调用其 `lookup()`：

| 主机 | 结果 | 耗时 |
|---|---|---|
| `github.com` | `20.205.243.166` | 3189 ms |
| `ghfast.top` | `172.245.43.106` | 27 ms |
| `gh-proxy.com` | 8×IPv4 + 3×IPv6 | 23 ms |
| `ghproxy.net` | `5.135.5.132` | 13 ms |
| `api.github.com` | `20.205.243.168` | 16 ms |

即：解析阶段不再被系统 DNS 阻断。github.com 首次 3.2 秒是系统 DNS 优先窗口（800ms）+ DoH 竞争的结果，
其余主机在缓存/并发下都是几十毫秒。

### 主机测试

- `UpdateVersionHostTest` 新增镜像优先断言；完整 host suite 通过（`ALL_HOST_TESTS_PASS`）。
- `BUILD SUCCESSFUL`，产物 `vc55 / 3.1.0-car.7-r83`，包名 `com.github.tvbox.osc.jun`，
  证书 `2F:EB:63:C5…FA:9C`，`lib/arm64-v8a` + `lib/armeabi-v7a` 双 ABI。

### 完整更新链路

见 `car/reports/r83-update/`（安装身份、DoH 探测、检查到新版本、下载进度与安装结果）。

## 边界

DoH 兜底与镜像可达性取决于当地网络对 `223.5.5.5` / `1.12.12.12` / `223.6.6.6` 与镜像域名的放行情况；
本报告只证明上述实测网络下的结果，不外推为所有运营商网络。真车听感与 OEM 更新安装策略仍未验证。
