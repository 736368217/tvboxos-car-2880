# 应用内自动更新：官方云通道（阿里云 dl.z736.vip）

从 r65 起，更新检查不再只依赖 GitHub：**官方发布根优先，GitHub 兜底**。r67 修掉了
官方通道在「网络刚起来 / DNS 慢」时被整段跳过的问题。

## 1. 发布侧

- 上传：SMB `smb.z736.vip:4450`（用户 `736`，共享 `releases`）→ 服务器
  `/opt/1panel/apps/openresty/openresty/www/releases`
- 公开下载：`https://dl.z736.vip/<软件>/<版本>/<文件>`
- 清单：`https://dl.z736.vip/tvbox-car/latest.json`（`version` + `files[].url/size/sha256`）
- 版本目录：`https://dl.z736.vip/tvbox-car/versions.json`（保留最新 + 1，超出自动清理）
- 本机发布脚本：`python $PI_SCRATCH_DIR\publish_release.py tvbox-car <版本> <apk>`
  （paramiko SFTP 传到 `/tmp` 后调服务器 `dl-publish.sh`，自动写 `.sha256`/`latest.json`）

## 2. 客户端行为（`app/src/main/java/com/github/tvbox/osc/update/AppUpdate.java`）

1. `queryOfficial()`：先系统 DNS（`PlainHttp.systemDns()`），失败再 App 网络栈
   （`PlainHttp.withDnsTimeout(OkHttp.client(...))`）。
2. 官方说「有新版」→ 直接用它的 `apkUrl` + `sha256`，**不套 gh 镜像前缀**
   （`isOfficialUrl()`；镜像只代理 github.com，套上去必 404）。
3. 官方不可达 → GitHub（API → Web 302 → 各镜像）兜底。
4. 官方可达但说「已最新」→ 采信官方，不再被 GitHub 上更旧的 tag 覆盖。
5. **r67 新增**：GitHub 全轮跑完仍没拿到官方结果时，**再试一次官方**。
6. 下载后 `sha256(File)` 与清单比对，再走 `verify ok package/versionCode/sig`，
   最后交给系统安装器（`REQUEST_INSTALL_PACKAGES`）。

## 3. r67 修的坑：DNS 2.5 秒判死

`PlainHttp.DNS_TIMEOUT_MS` 原为 2500ms。实机（MuMu / 冷启动）测得：

```
13:24:28 W AppUpdate: official system-dns failed: UnknownHostException: dl.z736.vip（解析超时 2500ms）
13:24:31 W AppUpdate: official app-stack failed:  UnknownHostException: dl.z736.vip（解析超时 2500ms）
13:24:33 W AppUpdate: check failed channel=直连(系统DNS) : api.github.com（解析超时 2500ms）
13:24:46 I AppUpdate: web probe ghfast.topweb code=302 …（同一批域名，10 秒后秒回）
```

即：解析「通但慢」（3–6 秒）被当成「不可达」，官方通道整段跳过，只剩 GitHub 上更旧的
tag（r64），于是误报「已是最新」。小车机刚上电、刚切网时最容易命中。

修法：超时放到 **6000ms**，并在 GitHub 兜底之后**再给官方一次机会**（此时 DNS 已热）。

## 4. 已验证证据（r67）

设备：MuMu idx0 / Android 15 / 2880x1620，装 r66（已发布版本）后点「设置 → 检查更新」：

```
I/AppUpdate: check ok channel=官方(阿里云) force=true local=3.1.0-car.7-r66 remote=3.1.0-car.7-r67 hasUpdate=true sha256=true
I/AppUpdate: download start size=52738265 …candidates=1 first=https://dl.z736.vip/tvbox-car/3.1.0-car.7-r67/…
I/AppUpdate: sha256 ok FB44FAA2EEAD72B270A7B9426B59005FDF97046A033BA12A4394A6AE71C9972E
I/AppUpdate: verify ok package=com.github.tvbox.osc.jun versionCode=39 sig=2FEB63C511CD7257…
I/AppUpdate: install launched file=TVBox-Original-Feature-3.1.0-car.7-r67.apk
→ dumpsys: versionCode=39 versionName=3.1.0-car.7-r67 lastUpdateTime=2026-11-04 13:37:05
```

装成 r67 后再点一次检查：

```
I/AppUpdate: official up to date local=3.1.0-car.7-r67 remote=3.1.0-car.7-r67
I/AppUpdate: check ok channel=官方(阿里云) … hasUpdate=false
```

公网产物自证：`latest.json` → `sha256 fb44faa2…9972e`，实际下载整包 SHA-256
`FB44FAA2…9972E`（52,738,265 字节）一致。

Host 回归：`OfficialManifestHostTest` 22 checks PASS、`UpdateVersionHostTest` 95 checks PASS。

## 5. 已知边界

- 「安装未知应用」首次仍要用户手点一次系统开关（`appops set … REQUEST_INSTALL_PACKAGES allow`
  仅用于自动化验收），App 会打开设置页并提示回来点「立即安装」。
- 官方清单没有 `notes` 时弹窗显示「（该版本没有填写更新说明）」；发布脚本可带 notes。
- 车机（零跑 B01）实机更新链未跑：上面的证据全部来自 MuMu。
