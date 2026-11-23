# r94 内置 GitHub 加速

2026-11-23：`3.1.0-car.7-r94` / versionCode 66 把 [cdn-url 服务目录](https://lancelotrar.github.io/cdn-url/) 的公开节点内置进 App，让订阅、Jar/JS、封面图与更新下载在 GitHub 直连不稳时仍能取到内容。纯内置，没有设置项。

## 覆盖范围

| 请求 | 是否加速 | 说明 |
|---|---|---|
| 订阅/配置 GET | 是 | `ApiConfig` 走公共 OkHttp 栈 |
| Jar / JS 源文件 | 是 | `OkGo`、`catvod.net.OkHttp`、`FileUtils` 共用同一拦截器 |
| 封面与图片 | 是 | Picasso 用同一 client |
| 更新查询与 APK 下载 | 是 | 更新层不再自行展开镜像列表，交给公共层统一做健康与回退 |
| Python `requests` GET/HEAD | 是 | 包装层复用同一节点表与停用状态，`downloadPlugin` 走原生流式 |
| R2 主通道 | 否 | 仅 `github.com` / `raw.githubusercontent.com` / `api.github.com` 等公开主机参与 |
| 带 `Authorization`/`Cookie`/token 的请求 | 否 | 一律直连，不把凭据交给第三方节点 |
| POST/PUT/PATCH | 否 | 只加速公开读取 |
| IJK 原生取流、WebView、EXO 分片 | 否 | 不经应用网络层，属已知边界 |

内置节点：`v4.gh-proxy.org`、`gh-proxy.com`、`gh-proxy.org`、`cdn.gh-proxy.org`（支持 API），`ghproxy.net`、`ghfast.top`（仅文件）。每次最多试 3 个节点，最后一个候选永远是原始 GitHub 地址。

## 失效节点怎么处理

- 成功一次即记住该节点，后续优先使用。
- 超时/连不上：按失败次数冷却 5 分钟起、最长 24 小时；`429` 固定 15 分钟。
- 证书过期或主机名不符：停用 6 小时，到期后允许一次复测；复测成功即恢复。
- 返回 HTML 停放页或跳到非 GitHub 域名：进入普通冷却，不会因为一个文件坏掉就永久弃用一个服务。
- `404/410/416`：只说明这个文件不存在，不惩罚节点。
- 状态持久化在 App 私有 `github-cdn-health`，重启与升级后仍有效；恢复方式不需要清数据。

第三方节点拿不到 GitHub 原始地址：响应里的 `url` 与相对重定向都还原成 GitHub 地址，避免订阅里的相对路径被解析到镜像域名；同时丢弃节点下发的 `Set-Cookie`。

## 已验证

- 主机 28 个入口全通过，其中新增 `GitHubCdnHostTest`（地址白名单、凭据排除、停用与恢复）与真实 `Request/Response` 的传输套件（失败回退、字节不变、取消、Range 与 cookie 处理）。
- Python：8 项离线测试，覆盖镜像全失败后仍走既有 Java 回退、流式字节不变、节点冷却。
- 设备（MuMu API 35 独立实例，验收后删除）：`app_stack_github_raw=true`、`app_stack_release_asset=true`，日志显示 `GitHubCdn: ok node=v4.gh-proxy.org origin=raw.githubusercontent.com http=200` 与 `origin=github.com http=200`；同一轮 R2 更新检查仍返回 `channel=R2`，未被加速层影响。

## 边界

- 公共镜像的可用性与速度会随时间变化，运行时会自动换节点，但任何一次测量都不保证未来容量。
- MuMu 只证明链路与回退逻辑，不代表 B01 车机网络、OEM DNS 或运营商策略。
- 证据见[版本汇总](../reports/r94-github-verification.json)与[报告页](../reports/r94-github/index.html)。
