# B01 更新 CDN 原生验收

独立测试包读取本轮 `pythonRelease` 的 R8 mapping，不在产品中加入调试入口。设备范围仍以 [项目 README](../../../../README.md) 和 [统一设备规范](../../../handover/MUMU-VERIFICATION-SPEC.md) 为准。

```powershell
python -B car/tests/device/cdn/build.py --sdk $env:ANDROID_HOME --java-home $env:JAVA_HOME --mapping app/build/outputs/mapping/pythonRelease/mapping.txt --out "$env:PI_SCRATCH_DIR/b01-cdn-native"
```

如果实例已有机主 TVBox，使用独立 `applicationIdSuffix '.cdnqa'` 的同源码 B01 诊断构建，并把本轮诊断 mapping 和 `--target-package com.github.tvbox.osc.jun.cdnqa` 传给测试构建器；不要覆盖原包。诊断构建仍仅有 B01 的 ARM64 `pythonRelease`，不新增产品变体。

只在持有租约的实例安装本轮签名产品包与输出的 `cdn-test.apk`，先跑显示门禁并比对设备 APK SHA，再执行：

```sh
am instrument -w -r com.tvbox.car.cdntest/com.tvbox.car.cdntest.CdnProbe
```

检查目标版本、设置页 R2 的“已是最新”结果，以及 App 自身网络栈（`com.github.catvod.net.OkHttp`，与订阅/配置同一 client）能否读到 GitHub Raw 文件与 Release 资产；`UpdateVersion`/`AppUpdate.query` 的通道与资产契约由主机测试覆盖，不在本包里反射 R8 混淆后的构造器。

结果写到目标应用 external-files 的 `b01-cdn-qa/result.json`，截图在同目录。必须检查逐条 `events[].pass` 和 `INSTRUMENTATION_RESULT: result=B01_CDN_PASS`；shell 退出 0 不等于断言通过。

这里直接调用备用查询路径，证明的是客户端 CDN 查询/Range 能力；不伪装成切断真实 R2 后的完整故障演练。弱网取消续传、真车 OEM 安装和不同车机网络另行验证。结束恢复原实例状态，卸载本轮新增的测试包，释放自己的租约。
