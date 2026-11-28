# r101 与 CarClash 边缘返回联调

日期 2026-11-28。MuMu Android 15，独立 vm2，联调视口 2880×1620、density 160（模拟器覆盖设置）。TVBox 与 CarClash 均按各自正式包名安装，两份实际安装摘要与本地签名包一致。

| 应用 | 版本 | 源提交 | APK SHA-256 |
| --- | --- | --- | --- |
| TVBox | `3.1.0-car.7-r101` / 73 | `c1826ea4959699463628785876595c888f5a65ea` | `4997522893e761f21f9394c85d2eac9a6cea2f6dc96ead03da7d350736ef495f` |
| CarClash | `2.11.66` / 211066，配套测试包 | `5e3de23dd197fb5e74c735d9f0a01aec6e68affd` | `0d679287433182b6ee9530923f0074e91ada12075e5a23aeaa76e1e1ae28eae1` |

CarClash 的 `:app:assembleMetaRelease` 通过。锁定的 Go 子模块使用提交 `3f27ef39f8c96bbfbc8b0b9bb10f5c9ef60149d8`。CarClash APK 为 ARM64，49,069,851 字节。两款 APK 的签名证书 SHA-256 均为 `2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`。

| 操作 | 无障碍服务 | CarClash 手势 | TVBox 高级设置 | 左侧滑动 |
| --- | --- | --- | --- | --- |
| 安装 CarClash 前 | 无 | — | `carclash 未检测到 · 亮度关` | 设置返回首页 |
| 启用服务，保留默认值 | 开，已绑定 | 关 | `carclash 应用内关闭 · 亮度关` | 设置返回首页 |
| CarClash 工具页切开 | 保持开启 | 开 | `carclash 接管中 · 亮度关` | 设置返回首页 |
| CarClash 工具页切回关 | 保持开启 | 关 | `carclash 应用内关闭 · 亮度关` | 设置返回首页 |

CarClash 日志记录 `overlays applied (enabled)` 与 `overlays removed (disabled)`。同证书的独立测试包 `com.tvbox.edgecaller` 用准确的方法名和空参数查询，收到 Provider 的 `SecurityException`，原生断言输出 `PASS provider rejected same-cert different-package caller`。两款应用及测试包的崩溃记录数为 0。

测试包已卸载，vm2 的无障碍和显示配置已恢复。vm0 的机主应用保持原安装状态。配套源码已推送至 CarClash 私有仓库的 `feat/tvbox-edgeback-status` 分支。
