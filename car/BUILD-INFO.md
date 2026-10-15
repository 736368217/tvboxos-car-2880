# BUILD-INFO — TVBoxOS 1.0.0-car.6

## 产物

- `TVBox-1.0.0-car.6-python-signed.apk`，Python release，ARM32＋ARM64。
- 包名 `com.github.tvbox.osc.jun`，Label `TVBox`，versionCode7 / versionName1.0.0-car.6。
- 52,635,477B，SHA-256 `02C7CF892C49BA5D286A325C6FC46B69B861070582FB552A2868B75352808D08`。
- v1/v2/v3及zipalign4验证通过。沿用既有Android Debug测试证书，证书SHA-256 `2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`。
- [v1.0.0-car.6 Release](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.6)。

## 环境与构建

Windows PowerShell；JDK17.0.20.8；Gradle7.5 / AGP7.2.2；compileSdk33 / targetSdk28 / minSdk19；Android build-tools34.0.0。Chaquopy12.0.1，Android Python3.8.13，主机构建Python3.8.10。Python flavor继续保留Java/JAR和QuickJS。

在完整工程根目录执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\gradle-cache'
$env:TVBOX_BUILD_PYTHON='C:\path\to\python3.8.exe'
$env:TEMP='C:\path\to\scratch\gradle-tmp'
$env:TMP=$env:TEMP
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
New-Item -ItemType Directory -Force $env:TEMP | Out-Null
$car6Sdk=(Join-Path $env:LOCALAPPDATA 'Android\Sdk').Replace('\','/')
[IO.File]::WriteAllText((Join-Path (Get-Location) 'local.properties'), "sdk.dir=$car6Sdk`n", [Text.Encoding]::ASCII)
.\gradlew.bat --no-daemon --max-workers 1 -Dorg.gradle.jvmargs=-Xmx1536m :app:assemblePythonRelease --console=plain
```

最终生命周期修复构建107.23s，exit0。输出 `app/build/outputs/apk/python/release/TVBox_release-python.apk`。官方embeddable Python3.8需禁用`python38._pth`，使Chaquopy可以设置构建模块搜索路径；不设置不受该插件支持的`python.version`。

双ARM均检查到`libpython3.8.so`、`libchaquopy_java.so`、`libquickjs-android-wrapper.so`和Chaquopy资源。依赖沿用car.5解析：lxml4.6.3、ujson1.35、pyquery2.0.2、requests2.32.4、jsonpath0.82.2、cachetools5.5.2、pycryptodome3.9.4、beautifulsoup4 4.15及传递依赖。

## 对齐与签名

```powershell
$car6Tools=Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\34.0.0'
& "$car6Tools\zipalign.exe" -f -p 4 app\build\outputs\apk\python\release\TVBox_release-python.apk $env:TEMP\TVBox-car6-aligned.apk
& "$car6Tools\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out .\TVBox-1.0.0-car.6-python-signed.apk $env:TEMP\TVBox-car6-aligned.apk
& "$car6Tools\apksigner.bat" verify --verbose .\TVBox-1.0.0-car.6-python-signed.apk
& "$car6Tools\zipalign.exe" -c 4 .\TVBox-1.0.0-car.6-python-signed.apk
Get-FileHash .\TVBox-1.0.0-car.6-python-signed.apk -Algorithm SHA256
```

APK、私钥和下载的Spider JAR不入Git。签名使用已知的开发测试证书，不适合作为独占生产发布证书。

## Host与GPU复现

```powershell
$car6Tests=Join-Path $env:TEMP 'tvbox-car6-tests'
New-Item -ItemType Directory -Force $car6Tests | Out-Null
javac -encoding UTF-8 --release 8 -d $car6Tests `
  player\src\main\java\xyz\doikki\videoplayer\render\VideoEnhancePreset.java `
  player\src\main\java\xyz\doikki\videoplayer\render\VideoPostProcessPolicy.java `
  app\src\main\java\com\github\tvbox\osc\car\AudioEffectPreset.java `
  car\tests\VideoEnhancePresetTest.java car\tests\Car6PostProcessTest.java
java -cp $car6Tests VideoEnhancePresetTest
java -cp $car6Tests Car6PostProcessTest
python car\tests\prepare_car6_fixtures.py --output "$car6Tests\fixtures"
```

GPU探针编译类为`car/tests/VideoGpuProbe.java`及生产`VideoShaders`、`VideoEnhancePreset`、`VideoPostProcessPolicy`。用Android33的android.jar作为classpath，经d8转换dex后放入测试设备`/data/local/tmp/car6-gpu/classes.dex`，在可用Android namespace/classpath中运行：

```sh
CLASSPATH=/data/local/tmp/car6-gpu/classes.dex app_process /system/bin VideoGpuProbe /data/local/tmp/car6-gpu/results
```

MuMu Java shell需通过插件`mumu_shell`执行共享LF脚本，进入system_server的mount namespace并导入classpath。测试仅在独占租用实例运行并保持静音。原始GPU断言全部通过后进程Shutdown139的情况已记录，详见TEST-REPORT。

## 本地播放复现

生成脚本需要Pillow及ffmpeg，独立生成32s低饱和片、200s 24fps移动片、80s1080p、32s4K。将生成目录放到guest `/sdcard/TVBoxCar5/`，从应用设置导入`http://127.0.0.1:9978/file/TVBoxCar5/api.json`。测试源通过应用原有Python/QuickJS及HTTP文件服务运行，不新增产品调试入口；结束恢复用户订阅。

`lives`可选字段省略，上游`ApiConfig`对`lives:[]`会越界，本次未改该业务代码。car.5历史fixture和文档单独保留；car.6媒体生成器不依赖旧版被用户移除的mp4文件。

## 源码与差异

相对car.5源码提交`1038379`，新增音效不可变表、11档画面、独立开关、两Pass FBO、官方SGSR1适配、时域策略与Surface生命周期修复。`GIT-DIFF-car.6.patch`由Git以UTF-8 bytes生成，包含新增源码/测试；`src/`是对应关键类副本。SGSR上游及许可证见AUDIO-VIDEO-ENHANCE。
