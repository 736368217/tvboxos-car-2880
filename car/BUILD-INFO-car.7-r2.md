# car.7-r2 构建与身份

## 当前产物

- 文件：工作区 `tvbox-os-car/TVBox-1.0.0-car.7-r2-python-signed.apk`
- 字节数：52,676,888
- SHA-256：`B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1`
- Application ID：`com.github.tvbox.osc.jun`
- Label：`TVBox`
- versionCode：8；versionName：`1.0.0-car.7`。r2 是产物修订号。
- Python ABI：`armeabi-v7a`、`arm64-v8a`
- ZIP 对齐、签名验证、badging、manifest：均通过。

## 源码状态

基线HEAD为`fa297472a608dcc37ee23bcdefafe7107f721fae`，旧`v1.0.0-car.7`仍是历史候选。本次源码交接锚点为`handoff-car.7-r2-20261017`，完整源码commit由交接ZIP的`SOURCE-STATE.json`记录。ZIP中的源码来自已提交的Git blob，不再是游离工作树快照。

未恢复用户原有的 `car/cartest.mp4` 与 `car/cartest-lowsat.mp4` 删除。新测试媒体由 fixture 工具在 scratch 生成，不把它们补回源码。

## 已验证的构建环境

- Windows PowerShell；JDK 17：本机 Eclipse Adoptium 17.0.20.8。
- Gradle Wrapper 7.5；AGP 7.2.2。
- Android SDK platform 33 / build-tools 34.0.0 用于独立 native harness。
- Chaquopy 12.0.1；构建解释器为 Python 3.8。宿主诊断/报告可使用 Python 3.12。
- 当前工作区完整命令成功，用时约 114 秒；随后签名和 native harness 生成完成。

从实际源码仓库根执行。`TVBOX_BUILD_PYTHON`指向单独解压交接包中的Python3.8；不要把包覆盖到Git工作树。示例假定包解压到兄弟目录`tvbox-car-kit`：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:TVBOX_BUILD_PYTHON = (Resolve-Path ..\tvbox-car-kit\tools\python38-build\python.exe).Path
$env:GRADLE_USER_HOME = 'D:\gradle-cache'
.\gradlew.bat --no-daemon --max-workers 1 '-Dorg.gradle.jvmargs=-Xmx1536m' :app:compileJavaReleaseJavaWithJavac :app:assemblePythonRelease --console=plain
```

这是新工作区的等效命令。已验证的是原工作区构建；交接快照解压到新路径后没有另外完整重建一次。SDK/JDK/Gradle cache 不随 ZIP 打包。后台执行须使用隐藏管道和真实退出码，不弹控制台，不启动重叠构建。

## 签名边界

r2 使用本机既有开发测试签名环境。私钥、keystore、密码不随源码快照/报告传播。新构建需要使用同一受控本机签名环境才可覆盖安装；不能因文件名相同就假定证书或 APK SHA 相同。

重新构建后 SHA 变化必须重新生成最终 R8 mapping 对应的测试包并跑设备门禁，不能把 B2EEDC14 的证据改名当新包证据。

## 报告

`tvbox-os-car/report-car.7/TVBox-car7-report.pdf` 为 27 页、22 张截图。DOCX 经 Microsoft Word 打开、更新目录、保存并导出 PDF；HTML 已在 1440×1000 和 390×844 下检查 22 图加载、无横向溢出，关键页截图非空。

## 宿主复核

```powershell
python -B .\car\tools\run_host_checks.py --core-only --out "$env:PI_SCRATCH_DIR\car7-host-core"
python -B .\car\tests\test_handover.py
```

完整host模式还需要本机已缓存的wrapper-java2.1.0、okhttp3.12.11、okio1.15.0和QuickJS桥classes.jar。可用`--gradle-cache <本机缓存>`和`--quickjs-classes <已构建桥>`指定，缺前置条件返回2，不启动Gradle/模拟器，不自动下载。当前机器的完整host模式已通过；新clone可先执行core-only。
