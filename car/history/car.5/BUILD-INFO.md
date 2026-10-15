# BUILD-INFO — TVBoxOS 1.0.0-car.5 Python 通用版

## 交付身份

- APK：`TVBox-1.0.0-car.5-python-signed.apk`
- Flavor：`python` release，包含 `armeabi-v7a` 与 `arm64-v8a`
- VersionCode / VersionName：`6` / `1.0.0-car.5`
- 包名：`com.github.tvbox.osc.jun`
- Label：`TVBox`
- 大小：`52,627,205` 字节
- SHA-256：`6F43817EB9C4E2A97C8E30C190826244A2333E14CD46D064964F91AE45DF0C3B`
- 签名：v1/v2/v3 通过；沿用 Android Debug 测试证书，证书 SHA-256 `2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`
- zipalign：4 字节对齐通过
- Release：[v1.0.0-car.5](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.5)

car.4 构建的是 Java flavor。car.5 使用 Python flavor；Python 仅替换 Spider 运行 flavor，Android 页面、Java 播放器、Java/JAR 加载和 QuickJS 仍保留。APK 内检查到两个 ARM ABI 的 `libpython3.8.so`、`libchaquopy_java.so`、`libquickjs-android-wrapper.so`，以及 Chaquopy 资源。

## 构建环境

- Windows PowerShell
- JDK：`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Gradle Wrapper 7.5 / Android Gradle Plugin 7.2.2
- compileSdk 33 / targetSdk 28 / minSdk 19
- Android SDK：`C:\Users\WIN10\AppData\Local\Android\Sdk`
- Build Tools 34.0.0
- `GRADLE_USER_HOME=D:\gradle-cache`
- Chaquopy plugin `12.0.1`，Android runtime `Python 3.8.13`
- 主机构建解释器：官方 Python 3.8.10 embeddable（放在会话 scratch，不需要全局安装）
- `TVBOX_BUILD_PYTHON` 或 `-PbuildPython` 可指定 Python 3.8 可执行文件

Chaquopy 12.0.1 的 Android runtime 固定为 Python 3.8.13，因此 `pyramid/build.gradle` 不设置 `python.version`；只通过 `buildPython(...)` 选择构建解释器。

## 可复现构建

在仓库根目录 `tvbox-os-src` 执行。先准备 `local.properties`，并让 `TVBOX_BUILD_PYTHON` 指向 Python 3.8 可执行文件：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-cache'
$env:TVBOX_BUILD_PYTHON='C:\path\to\python3.8.exe'
$env:TEMP='C:\path\to\scratch\gradle-tmp'
$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
$car5SdkPath=(Join-Path $env:LOCALAPPDATA 'Android\Sdk').Replace('\','/')
[System.IO.File]::WriteAllText((Join-Path (Get-Location) 'local.properties'),
  "sdk.dir=$car5SdkPath`n", [System.Text.Encoding]::ASCII)

.\gradlew.bat --no-daemon --max-workers 1 `
  -Dorg.gradle.jvmargs=-Xmx1536m `
  :app:assemblePythonRelease --console=plain
```

成功输出：`app/build/outputs/apk/python/release/TVBox_release-python.apk`。本轮 Gradle 构建耗时约 155 秒，exit code 0。

若使用官方 Windows embeddable Python 3.8，先将目录中的 `python38._pth` 重命名为 `python38._pth.disabled`，使 Chaquopy 能加入它自己的构建模块搜索路径。本轮使用 3.8.10 构建解释器；Android 内实际运行版本是 3.8.13。

## 对齐、签名与校验

```powershell
$sdk='C:\Users\WIN10\AppData\Local\Android\Sdk\build-tools\34.0.0'
$unsigned='app\build\outputs\apk\python\release\TVBox_release-python.apk'
& "$sdk\zipalign.exe" -f -p 4 $unsigned .\TVBox-car5-aligned.apk
& "$sdk\apksigner.bat" sign `
  --ks "$env:USERPROFILE\.android\debug.keystore" `
  --ks-key-alias androiddebugkey `
  --ks-pass pass:android --key-pass pass:android `
  --out .\TVBox-1.0.0-car.5-python-signed.apk .\TVBox-car5-aligned.apk
& "$sdk\apksigner.bat" verify --verbose .\TVBox-1.0.0-car.5-python-signed.apk
Get-FileHash .\TVBox-1.0.0-car.5-python-signed.apk -Algorithm SHA256
```

不要把 APK、keystore 或下载的 Spider JAR 提交到 Git。APK 通过 GitHub Release 附件交付。

## car.5 代码改动

- `VideoEnhancePreset`：8 个稳定 ID、不可变参数表、旧 Boolean/0/1 设置迁移和损坏值回退。
- `CarEnhance`、`MyVideoView`、`PlayerHelper`、`GlEnhanceRenderView`：设置、渲染工厂、HDR 恒等调色、主线程 Surface 绑定和 GL 取证日志统一使用预设 ID。
- `ModelSettingFragment` 与 `dialog_video_presets.xml`：从二元开关改为带说明和当前高亮的画面预设列表。
- `pyramid/build.gradle`：移除不存在的主机 Python 硬编码路径，支持 `TVBOX_BUILD_PYTHON`/`buildPython`。
- `car/tests/`：Java 8 host test、Python 依赖/HTTP/播放 fixture、QuickJS fixture。

## 依赖解析结果

本轮 Python 双 ARM 构建实际解析并打包：`lxml 4.6.3`、`ujson 1.35`、`pyquery 2.0.2`、`requests 2.32.4`、`jsonpath 0.82.2`、`cachetools 5.5.2`、`pycryptodome 3.9.4`、`beautifulsoup4 4.15` 及其传递依赖。设备验收通过的是运行时 Python 3.8.13。

## 历史构建

- car.4：Java flavor，versionCode 5，SHA-256 `21C9FC004CCB2CF405E47E8F9536360A52012EB847F35BD96A8C3F41D872495D`
- car.3：Java flavor，versionCode 4，SHA-256 `5074478347ADC8870DA35BD0F8806728D215EAB2F942B8BEC6637282C9857557`
- car.2：Java flavor，versionCode 3，SHA-256 `4FE471C3791235F5AED86528206F10B00ECE4FB01F56862C7B706EAD5DBC8567`

## 复核本地测试材料

以下命令从工程根目录运行，测试输出写临时目录。像素比较依赖主机 Pillow、NumPy；准备 32 秒片还需 PATH 中的 ffmpeg。

```powershell
$car5TestOut=Join-Path $env:TEMP 'tvbox-preset-tests'
New-Item -ItemType Directory -Force $car5TestOut | Out-Null
javac -encoding UTF-8 --release 8 -d $car5TestOut `
  player\src\main\java\xyz\doikki\videoplayer\render\VideoEnhancePreset.java `
  car\tests\VideoEnhancePresetTest.java
java -cp $car5TestOut VideoEnhancePresetTest
python car\tests\compare_presets.py car\evidence\car.5
python car\tests\prepare_fixtures.py --output "$car5TestOut\fixtures" --long-clip
```

把生成目录的文件放到测试设备 `/sdcard/TVBoxCar5/`，在应用中授予文件读取权限，再从配置地址导入 `http://127.0.0.1:9978/file/TVBoxCar5/api.json`。该配置仅用于本地验收，测试结束恢复用户订阅。`lives` 为可选字段，fixture 特意省略；上游 `ApiConfig` 对 `lives:[]` 的解析会越界，本轮未修改该业务代码。
