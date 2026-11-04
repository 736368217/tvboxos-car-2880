# 报告：历史封面丢失的综合判定与修复（r69）

## 结论先说

**用户判断正确：主因在订阅源，不在 App。** 之前会话 `pi-desktop://session/4d871654-bf81-464c-bfc5-976704a071ac` 把「潇洒 xiaosa」订阅里的 **爱动漫** 源改成了官方 JSON API，但封面地址归一化写错了域名，导致「首页有图、详情/历史写入的是 404 地址」。因为所有客户端共用同一订阅，**软件没变的另一台设备最近的历史也一起没封面**，与用户描述完全吻合。

另外发现那个会话还做过很多高风险操作（反复删除/替换 `spider.jar`、用 `mkstubs.py` 把 `./js`、`./py`、`./json` 引用文件覆写成 4 字节 `stub`、裁剪整个仓库），但本次线上 `123` 分支的这些文件已恢复完整，且封面问题不依赖它们即可复现/修复，所以**没有再去动那堆 jar/裁剪逻辑**，避免引入新问题。

## 一、怎么确认是订阅而不是软件

1. 找到会话 scratch：`D:\pi-data\data\scratch\4d871654-bf81-464c-bfc5-976704a071ac`。
   它维护的是 `736368217/tvbox` 的 `123` 分支，配置 `xiaosa/api.json`（112 个站点，第一位是爱动漫）。
2. 线上订阅地址：`https://gh-proxy.com/https://raw.githubusercontent.com/736368217/tvbox/123/xiaosa/api.json`，
   三个镜像（raw / gh-proxy / ghproxy.net）返回同一份 27,803 字节配置。
3. 爱动漫源：`api.json` → `./js/爱动漫.js?v=2`，代码用
   `HOST = https://m3u8.girigirilove.com` 调列表/详情接口。
4. **实测两个接口给的封面格式不一致**：

   | 接口 | `vod_pic` 返回值 | 旧 JS 补成 | 实测 |
   |---|---|---|---|
   | `get_list` | `https://jpg.girigirilove.com/upload/vod/...webp`（完整） | 原样 | 200 `image/webp` |
   | `get_detail?vod_id=6115` | `upload/vod/20261103-1/c09e…webp`（相对） | `https://m3u8.girigirilove.com/upload/vod/...` | **404** |
   | 同一路径换到 `jpg` 域名 | — | `https://jpg.girigirilove.com/upload/vod/...` | **200 / 53,660 字节** |

   旧 `pic()` 是 `return /^https?:/i.test(url) ? url : HOST + '/' + url`，所以列表没事、详情必坏。
   TVBox 写历史用的是详情页 `VodInfo.pic`，于是**每看一集就多一条永远 404 的历史**。

5. 会话日志里的证据也一致：`lc3.txt`～`lc7.txt` 里 `homeVideo` 的 `vod_pic` 全是
   `jpg.girigirilove.com` 完整地址；而详情链路产生的历史不显示封面。

## 二、那个会话还留下什么坑（已核查，不动）

- `su.sh`/`su2.sh`/`su3.sh`/`sync_final.sh`：`spider.jar` 在「固定旧版 → 删除 → 用 DEX 补丁去认证」之间反复横跳；
  `mkstubs.py` 曾把 `xiaosa/js/爱动漫.js`、`py/红果短剧.py` 等写成 `stub`。
- 线上最终状态（commit `a548f1bb`）已恢复：
  `爱动漫.js` 5,373 字节、两个 py、五个 json、README、patched `spider.jar` 都在；
  `api.json` 声明的 md5 `d320359a…` 与 jar 实际 md5 一致。
- 与上游 `qist/tvbox` 对比：只多一个「爱动漫」站点 + patched jar md5，其余 111 个站点和
  `rules/doh/flags/parses/lives` 全部一致 —— 所以**不重装/不回滚整个订阅**，只修封面函数。

## 三、修了什么

### 1. 订阅侧（主修，已在用户仓库生效）

`736368217/tvbox` 分支 `123`，commit `f9971b48`：
`xiaosa/js/爱动漫.js` 新增 `IMG_HOST = https://jpg.girigirilove.com`，重写 `pic()`：

- 相对 `upload/vod/...` → `jpg` 域名（修详情接口）；
- 已经是 `jpg.girigirilove.com` 的完整 URL → 原样保留（不破坏列表接口）；
- 绝对地址若仍是 `m3u8.girigirilove.com` / `girigirilove.com` → 换到 `jpg`；
- 其它 CDN 的完整 URL → 原样不动（不误伤别的图床）。

验证：Node 跑真实 6 组样例，映射全部符合预期；raw / gh-proxy / ghproxy.net 三个入口都返回
修复后的 6,234 字节 JS（含 `IMG_HOST`）。其它 111 个站点、`spider.jar`、api.json 都未改。

### 2. App 侧（兜底，防止旧缓存/旧数据继续坏）

`r69`（commit `f9f9ca0d`）：

- `CoverBinder.repairUrl()`：把数据库里**已经写坏的** `m3u8.girigirilove.com/upload/vod/...`
  自动改到 `jpg.girigirilove.com`，历史/收藏/首页观看历史/详情页统一生效；
- `DetailActivity.insertVod()`：写库前先修 `vodInfo.pic`，即使客户端还缓存旧 JS，也不会再产生坏记录；
- 与 r68 的 `CoverBinder`（本地封面直接 decodeFile、绕开 Glide 失败回调）叠加，
  旧记录即使没有网络也能靠内容键/落盘缓存显示。

## 四、实机验证（MuMu idx1 / Android 15 / 2880x1620）

故意把历史数据库里唯一记录的 `pic` 改成那个 404 地址，并**清空所有封面缓存**：

```
I/CoverCache: cover: network fallback name=后会友期
              url=https://jpg.girigirilove.com/upload/vod/20261103-1/c09e….webp
I/CoverCache: cached 342c8f94….img bytes=53660 url=https://jpg.girigirilove.com/…
I/CoverCache: cover: resolved from disk name=后会友期
```

截图历史卡片区域 **2,539 种颜色**（真图，不是几十色的随机色块）。
即：坏地址被自动纠正 → 真图下载落盘 → 页面立刻换真图。

## 五、交付物

- 订阅修复：`https://github.com/736368217/tvbox/blob/123/xiaosa/js/爱动漫.js`（commit `f9971b48`）
- App：`3.1.0-car.7-r69`（vc41），SHA-256
  `D3B234FF4C17CC99EEAE99DD90E4B9B35F668A3CA58D80ED3F137BF469CF0DC1`，原 debug 证书
- 官方通道 `https://dl.z736.vip/tvbox-car/latest.json` 已指向 r69；
  GitHub Release `v3.1.0-car.7-r69`，远端 digest 与本地一致
- 代码提交 `f9f9ca0deabb3bfd4577e596c1b90c524e8728bc` 已推送 `main`
- MuMu 租约已释放；idx0 上另有一个 `tv-full-ui` 租约，未触碰

## 六、用户需要做什么

1. **另一台没升级的客户端**：在 TVBox 里刷新一次订阅/清一次配置缓存（让 `爱动漫.js?v=2`
   重新拉），之后新历史会直接写入正确封面。已存在的坏历史记录，如果那个客户端没有
   r69 的 `repairUrl()` 兜底，可以清掉这几条历史重新看一次，或安装 r69。
2. **本机/车机**：更新到 r69 即可，旧坏记录会自动修，不需要手工清数据库。
3. 如果刷新订阅后仍个别源无封面，点「高级设置 → 诊断日志 → 点此立即上传」；
   r68 已上线日志滚动通道，我可以直接拉现场 logcat 继续定位（判据：
   `cover: network fallback url=…` 后面的 HTTP 结果 / `glide load failed root=…`）。

## 七、如实说明的边界

- 本站封面域名是**按当前线上接口实测**写死的 `jpg.girigirilove.com`；若站方以后再换 CDN，
  订阅 JS 里的 `IMG_HOST` 需要再改一次。App 兜底只修已知的 `m3u8/girigirilove` 坏域，
  不会猜任意域名。
- 零跑 B01 实机未跑；证据来自公网接口实测 + MuMu。
