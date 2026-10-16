# BUILD-INFO — TVBoxOS 1.0.0-car.7 候选

## 最终测试候选 D2A49B5D

- `TVBox-1.0.0-car.7-python-signed.apk`
- 包名/标签：`com.github.tvbox.osc.jun` / `TVBox`
- versionCode/versionName：`8` / `1.0.0-car.7`
- Python release，ARM32＋ARM64，保留Java/JAR、QuickJS和全部既有音画参数。
- 大小：52,668,696B。
- SHA-256：`D2A49B5DA2B0371CF3C84CCDAC6C5753EF649721639434FB21955F4054A30803`。
- v1/v2/v3、zipalign4、badging/Manifest、双ABI Python/Chaquopy/QuickJS检查通过。
- 签名证书SHA-256：`2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C`；沿用既有测试证书。
- 正式发布暂缓；本包以候选和私有草稿交付，IJK硬解全屏像素异常仍未解决。

## 构建

Windows/JDK17.0.20.8/Gradle7.5/AGP7.2.2/build-tools34.0.0/Chaquopy12.0.1/Android Python3.8.13。主机构建使用指定Python3.8，缓存D:\gradle-cache。

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-cache'
$env:TVBOX_BUILD_PYTHON='C:\path\to\python3.8.exe'
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
.\gradlew.bat --no-daemon --max-workers 1 '-Dorg.gradle.jvmargs=-Xmx1536m' :app:compileJavaReleaseJavaWithJavac :app:assemblePythonRelease --console=plain
```

最终实际exit0，123.67秒。未签名包52,561,339B，SHA-256 `61ACF26E5A73EA4C593AC9B7F5712FD6A689CD9AB3605E92B3F966ADB90F5721`。再经zipalign、签名与最终包检查，未以编译成功替代设备结果。

## 验证入口

`car/tests/test_python_http.py` 20项通过；Java8 host suite包括164 XML、11预设、音画旧参数保持、44配置/88 SDR-HDR/1936比较、738返回策略。可复现native harness见 `car/tests/native/README.md`；绑定实际APK SHA和最终R8映射。生成物留scratch。

D2A播放PID47234，95/0状态/生命周期；八图像素7/8，唯一失败IJK硬解全屏。IJK保留恒等GL输出，关闭时无SR/补帧/FBO处理，EXO保留Texture/GL切换。早期E395的PY五站、真实远程播放及返回控件58/0保留为历史基线；最终D2A的PY/UI结果见测试报告和device-acceptance.json，不自动继承旧哈希的通过项。

APK、Spider JAR、keystore、私有偏好、完整远程响应与运行时敏感字段不入Git。模拟器不证明真实OEM全屏、SA8295P性能、HDR/DoVi或ARM32运行。
