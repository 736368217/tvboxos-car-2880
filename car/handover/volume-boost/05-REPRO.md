# 05 · 复现步骤（构建 / 签名 / 取证）

> 本文命令都是**本机实测跑过**的（Windows PowerShell 5.1）。带 `✅` 的是本次实际输出。

## 1. 仓库与版本

```
仓库（worktree）: D:\github\tvboxos-upstream-feature-pack
分支            : main
r56 提交        : <本次提交，见文末>
Application ID  : com.github.tvbox.osc.jun      label: TVBox
versionCode     : 29        versionName: 3.1.0-car.7-r56
```

## 2. 构建（离线）

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\HermesData\gradle-tiers'   # 用 C:\Users\WIN10\.gradle 会报 offline 无缓存
Set-Location 'D:\github\tvboxos-upstream-feature-pack'
& 'D:\gradle-7.5\gradle-7.5\bin\gradle.bat' :app:assemblePythonRelease `
    -x :pyramid:generatePythonReleasePythonRequirements --console=plain --offline 2>&1 |
  Tee-Object -FilePath "$env:PI_SCRATCH_DIR\build-r56.log" | Out-Null
Get-Content "$env:PI_SCRATCH_DIR\build-r56.log" -Encoding Default -Tail 3
```

✅ `BUILD SUCCESSFUL in 1m 33s / 104 actionable tasks`
产物：`app\build\outputs\apk\python\release\TVBox_release-python.apk`（45,711,494 bytes，**缺 chaquopy .imy**）

> 排错：错误行形如 `D:\…\X.java:NN:`，控制台是 GBK，必须 `Get-Content -Encoding Default` 才能读懂中文错误。

## 3. 注入 chaquopy 依赖 → 对齐 → 签名

```powershell
$bt  = "D:\codex\projects\2026-08-21\a\work\android-toolchain\sdk\build-tools\30.0.3"
$s   = $env:PI_SCRATCH_DIR
$uns = "app\build\outputs\apk\python\release\TVBox_release-python.apk"
$don = "…\delivery\TVBox-Original-Feature-3.1.0-car.7-r40.apk"   # 任意含 assets/chaquopy/*.imy 的旧 Release
$reb = "…\rebuild_apk.py"                                       # 见 §3.1

python $reb $uns $don "$s\r56-raw.apk"                          # rebuild_apk.py <src> <donor> <dst>
& "$bt\zipalign.exe" -f -p 4 "$s\r56-raw.apk" "$s\r56-aligned.apk"
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
    --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
    --v1-signing-enabled true --v2-signing-enabled true --v4-signing-enabled false `
    --out "$s\TVBox-Original-Feature-3.1.0-car.7-r56-diag.apk" "$s\r56-aligned.apk"
& "$bt\apksigner.bat" verify --print-certs "$s\TVBox-Original-Feature-3.1.0-car.7-r56-diag.apk"
```

✅ `entries=1304 added=3` / `Signer #1 certificate SHA-256 digest: 2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c`

> **证书必须是 `2feb63c5…fa9c`**，否则车机报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

### 3.1 `rebuild_apk.py`（20 行，作用：把 donor 里的 3 个 `.imy` 塞进新包）

```python
import sys, zipfile
src, donor, dst = sys.argv[1], sys.argv[2], sys.argv[3]
names = ["assets/chaquopy/requirements-arm64-v8a.imy",
         "assets/chaquopy/requirements-armeabi-v7a.imy",
         "assets/chaquopy/requirements-common.imy"]
with zipfile.ZipFile(donor) as d:
    blobs = {n: d.read(n) for n in names}
count = 0
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, allowZip64=True) as zout:
    existing = set(zin.namelist())
    for info in zin.infolist():
        ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        ni.compress_type = info.compress_type
        ni.external_attr = info.external_attr
        ni.create_system = info.create_system
        zout.writestr(ni, zin.read(info.filename))
        count += 1
    for n, b in blobs.items():
        if n in existing:
            continue
        ni = zipfile.ZipInfo(n, date_time=(2020, 1, 1, 0, 0, 0))
        ni.compress_type = zipfile.ZIP_STORED
        zout.writestr(ni, b)
print("entries=%d added=%d" % (count, len(blobs)))
```

## 4. 产物校验（DEX 级）——**不要只看文件大小**

```python
# verify_r56.py：确认诊断插桩真的进了包
import zipfile, hashlib, os
apk = os.path.join(os.environ["PI_SCRATCH_DIR"], "TVBox-Original-Feature-3.1.0-car.7-r56-diag.apk")
needles = ["CarAudioGain","configure n=","queue n=","level_in=","sink_built","renderers_factory=",
           "app_gain request=","set_gain ","configure REJECTED","pcmStored=","pcmQueued=",
           "runtime_apply","volume_limited_by_player","loudness_unavailable"]
with zipfile.ZipFile(apk) as z:
    dexes = sorted(n for n in z.namelist() if n.endswith(".dex"))
    print("dex:", dexes)
    for nd in needles:
        where = [n for n in dexes if nd.encode() in z.read(n)]
        print("  %-26s %s" % (nd, where or "MISSING"))
    u16 = "3.1.0-car.7-r56".encode("utf-16-le")
    print("versionName in manifest:", u16 in z.read("AndroidManifest.xml"))
print("SHA-256:", hashlib.sha256(open(apk,"rb").read()).hexdigest().upper())
print("size   :", os.path.getsize(apk))
```

✅ 14 个串全部命中；`versionName in manifest: True`

## 5. host 测试（不需要设备）

```powershell
$libs = # exoplayer-core/common/container/decoder/extractor/datasource 的 classes.jar + guava-30.1-jre.jar
$aj   = "…\sdk\platforms\android-33\android.jar"
& javac -encoding UTF-8 -nowarn -cp "$libs;$aj" -d "$s\hostout" `
    "car\tests\AudioGainProcessorHostTest.java" `
    "player\src\main\java\xyz\doikki\videoplayer\exo\AudioGainProcessor.java"
& java -cp "$s\hostout;$libs;$aj" AudioGainProcessorHostTest
```

✅ `AudioGainProcessorHostTest PASS`

- `.aar → classes.jar` 的提取：`zipfile` 读 `exoplayer-*/2.19.1/*/exoplayer-*.aar` 里的 `classes.jar`；
- **`android.jar` 必须同时在编译与运行 classpath 上**（`AudioProcessor$AudioFormat` 引用了
  `android.content.pm.PackageManager$NameNotFoundException`）；
- 该测试**不需要 `android.jar` 的 Log 实现**：`AudioGainProcessor.log()` 已把
  `android.util.Log` 的 `Stub!` 异常吞掉，音频路径不会因为日志失败而崩。

其它 host 测试（`car/tests/*.java`）：`VolumeBoostMathHostTest`、`VolumeLimitHostTest`、
`BackNavPolicyHostTest`、`CoverKeyHostTest`、`UpdateVersionHostTest`、`Car6PostProcessTest`、
`Car7RuntimeConfigTest`、`VideoEnhancePresetTest`。

## 6. 安装到车机（或模拟器）

```powershell
adb connect 127.0.0.1:16384          # MuMu 自带 adb: D:\Program Files\Netease\MuMu\nx_device\15.0\shell\adb.exe -P 5055
adb install -r -d "…\TVBox-Original-Feature-3.1.0-car.7-r56-diag.apk"
adb shell dumpsys package com.github.tvbox.osc.jun | findstr versionName     # 期望 3.1.0-car.7-r56
```

## 7. 车机取证（决定性问题）

```powershell
adb logcat -c
adb logcat -v time -s CarAudio CarAudioGain TVBox-runtime > "$env:PI_SCRATCH_DIR\car-audio.txt"
# 然后在车机上：进播放页 → 播一集 → 把应用内音量滑到 200% → 停 10 秒
```

看四类行：

| 行 | 含义 |
|---|---|
| `CarAudio renderers_factory=…` | 工厂被构建（EXO 引擎） |
| `CarAudio sink_built floatOut=… offloadArg=… processors=1` | **`buildAudioSink` 覆写真的跑了** |
| `↳ CarAudioGain configure n=1 enc=PCM_16BIT … accepted=true` | **处理器在链上**，且格式可处理 |
| `↳ CarAudioGain queue n=1 gain=2.0 samples=… level_in=… level_out=…` | **采样被放大**（看 `level_out/level_in`） |
| `CarAudio runtime_apply … engine=… path=pcm factor=1.0 applied=2.0 pcmStored=… pcmQueued=…` | 应用侧状态快照 |

## 8. 客观测量（把听感变成数字）

```powershell
ffmpeg -i a100.wav -af astats=metadata=1:reset=0 -f null - 2>&1 | findstr /i "RMS_level"
ffmpeg -i a200.wav -af astats=metadata=1:reset=0 -f null - 2>&1 | findstr /i "RMS_level"
# 期望 200% 比 100% 高 ≈6 dB（对应 gain=2.0）
```

录音方案见 `03-HYPOTHESES.md` §客观测量（首选 Android 10+ 的 `AudioPlaybackCapture`）。

## 9. 本次交付的验收状态（诚实清单）

| 验收项 | 状态 | 证据 |
|---|---|---|
| r56 能被编译通过 | ✅ 完成 | `BUILD SUCCESSFUL in 1m 33s` |
| 插桩真的进了 APK | ✅ 完成 | DEX 里 14 个诊断串全部命中 |
| 插桩不影响音频数学 | ✅ 完成 | `AudioGainProcessorHostTest PASS`（真实处理器在 JVM 上跑） |
| 签名与车机存量同源 | ✅ 完成 | `SHA-256 2feb63c5…fa9c` |
| **在车机上验证"200% 真的更响"** | ❌ **未做** | 需要车机 + 听觉/录屏取证；本机 MuMu 全程 `--mute` |
| **确认车机播放时走的是 EXO/PCM 路径** | ❌ **未做** | 需要车机日志（本机无法代表车机） |

- 产物：`TVBox-Original-Feature-3.1.0-car.7-r56-diag.apk`
  SHA-256 `02FAD567D31D2DD8B17BEC7FEDF234961807960AB74BB4FC12288686C1E854D4`，52,730,073 bytes
  证书 SHA-256 `2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C`
