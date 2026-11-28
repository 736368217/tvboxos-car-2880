# r101 播放页与高级设置

`3.1.0-car.7-r101`，versionCode `73`。签名 APK SHA-256 为 `4997522893e761f21f9394c85d2eac9a6cea2f6dc96ead03da7d350736ef495f`，大小 64,820,086 字节。

## 播放与设置

- PR #3 的音频 v5 已在 `main`，合并提交 `0f8069e6`。播放中的音量弹窗提供原始声音、车载均衡和高速压缩，沿用 EXO 的 PCM 链路。历史的动态关闭档按原始声音处理。
- 滤镜仅提供原画、动漫暗光和动漫日间。旧滤镜偏好超出此范围时回到原画。
- 上一集排在下一集之前。删除音效、播放器选择、弹幕和弹幕搜索。点播跳过弹幕初始化与加载，主设置的弹幕选项也已移除。
- 高级设置复用原有设置弹窗，显示当前值并保留返回、亮度、订阅缓存和诊断入口。

## CarClash 返回状态

TVBox 由启用列表定位 `EdgeBackService`，向同一个包的 `${applicationId}.edgeback` 调用 `edgeBackState`。仅在读到 `enabled=true` 时停用自己的边缘返回。旧版 CarClash 2.11.64 缺少接口，MuMu 设置页实际显示「carclash 无法读取」。

CarClash 配套源码位于私有仓库分支 `feat/tvbox-edgeback-status`，提交 `5e3de23dd197fb5e74c735d9f0a01aec6e68affd`，基于 `1e49816`。接口在主进程读 `EdgeBack.isEnabled`，核对 TVBox 包名和调用 UID 的签名后返回布尔值。配套包 `2.11.66` 在 MuMu 完成关闭、开启和回切验证。系统服务保持启用时，TVBox 依次显示「应用内关闭」「接管中」「应用内关闭」。同证书异包名的查询实测被拒绝。见[联调记录](../reports/r101-carclash-edgeback.md)。

## 验收证据

构建、签名、B01 静态门禁和主机回归通过。Lint 任务退出 0，仍有既有 AppCompat 检查器错误与 `DiffUtilEquals` 报告。

MuMu Android 15 通过 2880×1620、density 160 显示与触摸门禁，实际安装包 SHA 一致。本地双集 88 项、冷启动 9 项通过。覆盖高级设置显示与关闭、三档音量、PCM 运行状态、同播放器持续起播、滤镜选择、字幕入口、双向换集和后台暂停。冷启动从保存的 5209 ms 恢复至 5240 ms。无目标应用 FATAL，已恢复测试前 APK、移除临时用户并释放实例。

[证据清单](../reports/r101-release/manifest.json) · [点播断言](../reports/r101-release/ui1-report.txt) · [冷启动断言](../reports/r101-release/cold-report.txt)

R2 `latest.json` 与历史清单一致。GitHub Release 标签指向 `c1826ea4`。两个通道整包下载的大小、SHA-256 和签名均通过核对，见[发布回读记录](../reports/r101-release-verification.json)。

本轮使用 MuMu 和本地样片。零跑 B01 真车与外部站源测试仍待实测。
