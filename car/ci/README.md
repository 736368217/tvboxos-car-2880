# B01 签名、CI 与主机回归

这是零跑 B01 私人项目。设备、构建与更新入口统一见 [项目 README](../../README.md)。CI 只构建 B01 的 `pythonRelease`，不继承上游多设备矩阵。

## 工具与校验

- `run-host-tests.py`：编译仓库直属 Java 主机测试，运行两个 Python 主机启动器；依赖取自已经构建应用的 Gradle 缓存，全部输出写 `--out`。
- `verify-b01-apk.py`：验证 APK 的包名、minSdk 32、ARM64-only、Python 依赖归档、ZIP CRC/重复路径、资源表和中文资源路径。
- `upstream-check.py`：只读检查上游变化和合并冲突，不代表需要接收所有上游功能或设备支持。

```powershell
python -B car/ci/run-host-tests.py --out "$env:PI_SCRATCH_DIR/b01-host"
python -B car/ci/verify-b01-apk.py app/build/outputs/apk/python/release/TVBox_release-python.apk --aapt2 "$env:ANDROID_HOME/build-tools/30.0.3/aapt2.exe"
```

JDK/SDK/Gradle 缓存位置从 `JAVA_HOME`、`ANDROID_HOME`、`GRADLE_USER_HOME` 读取，也可显式传参。主机测试通过不证明设备运行；MuMu 和真车边界见 [设备规范](../handover/MUMU-VERIFICATION-SPEC.md)。

## 签名身份

`debug.keystore` 是本项目用于侧载覆盖安装的既有开发测试证书，正式版命名不改变它的性质。不要更换机主已安装应用的证书，也不要使用 `.github/workflows/TVBoxOSC.jks` 替代。

| 项 | 期望值 |
|---|---|
| keystore SHA-256 | `329FFAAE95367D9C9685EC39F657A495D811FE694175009F8845930B59365802` |
| 证书 SHA-256 | `2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C` |
| alias | `androiddebugkey` |

先 `zipalign`，再用原证书签名并运行 `apksigner verify --verbose --print-certs`，然后执行 B01 APK 校验。密钥已存在于仓库历史；私人用途不等于历史密钥可当作正式商店密钥，不用它签其他项目。

## 工作流

- `.github/workflows/build-release.yml`：B01 单包构建、主机测试、签名与 APK 契约校验；手动调用或作为 reusable workflow。默认只上传 artifact，明确启用 `create_release` 并填写 notes 才创建备用 GitHub Release。
- `.github/workflows/test.yml`：本仓库 main 代码变化时复用上述检查，不再克隆上游和批量打包。
- `.github/workflows/upstream-watch.yml`：上游变化提醒和只读预演；接收哪些改动由 B01 需求决定。

R2 是主分发通道，发布入口为 `tools/publish_r2.ps1`，需要用户明确指令。CI 运行和 artifact 上传不代表 R2 或车机已更新。
