# r80：音量根因定案 + 两个独立开关（片源电平补偿 / 音频通道对齐）

> **r82 纠正（历史记录）**：本文「全部视频低 33 dB、系统问题已排除、接近音乐等响」等推断不成立。`level_in / samples` 是该缓冲区的平均绝对幅度，不能当峰值、LUFS 或音乐对照测量；也不能据此排除车机路由。r82 删除通道对齐按钮、统一音频焦点，修正补偿测量与真实 PCM 验收。当前结论和边界以 [r82 验收报告](R82-FEEDBACK-FIXES.md) 为准。

## 1. 用户反馈（原文）

> 「播放视频时声音太小。我设定相同的车机音量，听音乐时音量正常，但是使用 app 看视频音量会过小，
> 需要我在软件内单独设置增益到 500% 才正常……我认为这明显是系统有问题。
> 可以看看系统是怎么处理音乐软件的音量的。」

追问后补两条：

> 「我不需要这种自动测算的啊，我希望我视频调整好音量就不用管了，
> 之后听歌和看视频音量差不多就行了，我不用手动调。
> 你这个功能先别删，可以做一个开关按钮。」
> 「你说的车机侧声音必定少 33dB 这个是无法调整的是吗？
> 那是否能让看视频的时候的声音直接走系统的音乐播放渠道？这个能否实现，
> 如果能做也做个独立开关，我都尝试一下效果。」

## 2. 根因（**事实**，非本轮新增结论）

r70 现场日志已经给出硬证据：

```
CarAudioGain: queue n=21501 gain=3.0 enc=PCM_16BIT samples=4096 \
               level_in=2948248 level_out=8844744
```

- `level_out / level_in = 3.000` → 300% 增益**确实生效、没有削顶**；
- `level_in / 4096 ≈ 720`，仅占 16bit 满量程 32767 的 **2.2% ≈ −33 dBFS**；
- 音乐母带接近 **0 dBFS**。

**结论：这不是"放大倍数不够"，而是"基准电平不一致"。** 系统音量由音乐与视频共用，
用户为了听清视频拉高系统音量，回头听歌就过响，只能来回调。

### 2.1 车机侧已逐项排除（**事实**）

| 检查项 | 结果 | 证据 |
|---|---|---|
| `STREAM_MUSIC` 音量 | 15/15 满载 | `21-dumpsys-audio.txt:52-58` |
| TVBox 的 usage | `USAGE_MEDIA`，正常拿到 `AUDIOFOCUS_GAIN` | `ota-live4.log:560-573` |
| usage→音量组映射 | `USAGE_UNKNOWN`/`USAGE_MEDIA` **同属 MUSIC 上下文** | `CarAudioContext.java:85` |
| duck / fade / mute | 全部为空 | `21-dumpsys-audio.txt:509-513` |
| 输出总线 | `BUS00_MEDIA`，与网易云**同一条** | `ota-live4.log:573` |

### 2.2 为什么车机侧无法"调好这 33 dB"

- 车机对音乐和视频**一视同仁**：同一总线、同一档索引、同一套 gain 表；
- 33 dB 的差在**进入车机之前**已存在于片源；
- 若在车机侧给媒体整体 +33 dB，**音乐会直接削波**（已达 0 dBFS）；
- 车机唯一可调的是媒体组音量档（0–66），那是全局的，会同时影响音乐。

→ 只能由播放器 App **按片源**补偿。

### 2.3 "走系统音乐播放渠道"不可行（**事实**）

`ota-live4.log:560-573`：

```
W/CarZonesAudioFocus: onAudioFocusRequest, clientUid = 1010137, usage = 1
W/CarAudioFocus: Evaluating focus holder: ...com.netease.cloudmusic.player...
W/CarFocusInteraction: evaluateRequest result INTERACTION_EXCLUSIVE
W/CarAudioFocus: sendFocusLoss for ...cloudmusic... with loss type LOSS
W/CAR.AUDIO: usagesHoldingaddressList=[BUS00_MEDIA]
```

**网易云与 TVBox 本来就是同一条 `BUS00_MEDIA`、同一个 `USAGE_MEDIA`**；
TVBox 请求焦点时直接把网易云踢掉（`INTERACTION_EXCLUSIVE`）。

音乐"听起来正常"的真实原因：**网易云在 App 内部做了响度归一化**，
不是通道不同。→ 没有"换渠道"这条路。

## 3. 修复：两个独立开关

两个开关都在 **设置 → 高级设置**，默认开启，**运行中即时生效**，关闭后行为与 r77 完全一致。

| 开关 | 偏好键 | 默认 | 作用 |
|---|---|---|---|
| 视频音量自动对齐 | `source_level_match` | 开启 | 每个片源锁定一次电平补偿 |
| 音频通道对齐音乐 | `audio_usage_media` | 开启 | 显式 `USAGE_MEDIA`/`CONTENT_TYPE_MOVIE` |

### 3.1 开关一：静态片源电平补偿

新增 `player/.../exo/SourceLevelLock.java`：

| 常量 | 值 | 含义 |
|---|---|---|
| `TARGET_PEAK` | `0.5f` | 约 −6 dBFS，对齐音乐母带 |
| `MAX_MAKEUP` | `4f` | +12 dB 上限 |
| `MEASURE_SECONDS` | `4f` | 片源开头测量窗口 |
| `SILENCE_PEAK` | `0.002f` | 低于此不抬底噪 |

行为：

1. 片源开始后累计 4 秒峰值；
2. `makeup = clamp(TARGET_PEAK / peak, 1.0, MAX_MAKEUP)`；
3. **锁定，整片不再变化**；
4. 锁定后 0.5 秒淡入，避免突变；
5. `begin()` 在 `onConfigure` 时重置（换源/换格式）；
6. **开关关闭时 `apply()` 直接返回 1.0**，与 r77 行为逐位一致。

接入 `AudioGainProcessor.queueInput()`：

```java
float makeup = muted ? 1f : sourceLevel.apply(peak, samples);
if (makeup > 1.0001f) {
    combined = combined * makeup;   // 补基准，不占用用户档位
}
```

#### 与 r73（已删除）的本质区别

| 维度 | r73 动态自动电平 | r80 静态锁定 |
|---|---|---|
| 变化频率 | 每约 1 秒重算 | **整片恒定** |
| 用户感知 | 忽大忽小（r76 实测） | 感知不到 |
| 与手动增益 | r76 起始终叠加 → 加倍失真 | 补偿表达"基准"，用户档位仍是原语义 |
| 可关闭 | 只能整段删除 | 一行开关，即时生效 |
| 换源重置 | — | `begin()` 显式重置 |

### 3.2 开关二：音频属性对齐

`player/.../exo/ExoMediaPlayer.java` 新增 `applyAudioAttributes()`，
在 `initPlayer()` 建好 `mInternalPlayer` 后调用：

```java
com.google.android.exoplayer2.audio.AudioAttributes exoAttrs =
        new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
mInternalPlayer.setAudioAttributes(exoAttrs, true);
```

- 之前全仓库**从未设置过** `setAudioAttributes`，走 ExoPlayer 默认 `USAGE_UNKNOWN`；
- 本车机上 `USAGE_UNKNOWN(0)` 与 `USAGE_MEDIA(1)` 同属 MUSIC 上下文
  （`CarAudioContext.java:85`），**故大概率不改变音量**；
- 保留此开关的目的是**排除"OEM 音量表按 usage 分档"**这一剩余变量，供用户实测 A/B；
- 无效可关闭，不影响其他功能。

### 3.3 不做的事

- 不改系统音量、不改车机侧任何配置；
- 不引入无障碍 / 系统权限 / 前台 Service；
- 不恢复 r73 的逐秒动态增益；
- 不动返回组件、封面缓存、更新通道等非音量功能。

## 4. 验证

### 4.1 host 测试（全 PASS）

```
SourceLevelLockHostTest PASS checks=231
  lock math: PASS
  processor quiet source: PASS (boost 720 -> 2880)
  switch off/on: PASS (off=720 on=2880)
FixedGainHostTest       PASS checks=26
```

### 4.2 增益阶梯实测（−33 dBFS 片源，振幅 720）

| 用户档位 | 输出峰值 | 相对原始 | appliedGain |
|---|---|---|---|
| 100% | 2880 | **4.0×** | 1.00 |
| 200% | 5760 | 8.0× | 2.00 |
| 300% | 8640 | 12.0× | 3.00 |
| 500% | 14400 | 20.0× | 5.00 |

- **100% 档位即得 4 倍（+12 dB）**，用户不再需要拉到 500%；
- 峰值 2880 仅占满量程 **8.8%**，无削波；
- 锁定后连续 5 个时间窗输出**逐样本一致**。

### 4.3 本机构建（r80 首次完成真实打包）

**构建环境（本机验证通过）**：

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | Temurin 17.0.21 | AGP 7.2.2 要求 JDK 11/17；**JDK 21 不可用** |
| Gradle | 7.5 | wrapper 自带 |
| Android SDK | platform 33 + build-tools 33.0.2 + platform-tools | `sdkmanager` 安装 |
| Python | **3.8.10** | Chaquopy 硬性要求；3.12 不可用 |

**构建命令**：

```bash
# 正确的变体：python64Release（含 Python 引擎，arm64-only）
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<android-sdk>
export TVBOX_BUILD_PYTHON=<python38>/python.exe
./gradlew :app:assemblePython64Release --no-daemon

# 签名（CI 官方 keystore，alias=tvboxosc）
zipalign -f -p 4 in.apk aligned.apk
apksigner sign --ks .github/workflows/TVBoxOSC.jks \
  --ks-key-alias tvboxosc --ks-pass pass:TVBoxOSC --key-pass pass:TVBoxOSC \
  --out out.apk aligned.apk
```

**产物校验**：

```
package: com.github.tvbox.osc.jun  versionCode=52  versionName=3.1.0-car.7-r80
native-code: arm64-v8a
size: 32 MB
entries: 1167；含 libpython3.8.so + 27 个 chaquopy 运行时文件
cert SHA-256: 4b9421de6d46778f6280b180179413fa6973845f0a0132c00f6800c06464e051
sha256(apk): b3f532bfa55ec70a7080bda6b126f8d46b97f862bc6d78c0875f9f10273fca59
```

**踩过的坑（重要）**：

1. **`local.properties` 转义**：必须写 `sdk.dir=C\:/dev-tools/android-sdk`
   （冒号转义 + 正斜杠）。写成 `C:\dev-tools\...` 会让 AGP 抛
   `java.io.IOException: 文件名、目录名或卷标语法不正确`
   （发生在 `SdkLocator.validateSdkPath`），且报错完全看不出是路径问题。
2. **中文路径**：`D:\车机系统\...` 下构建会额外触发 AGP 路径检查失败，
   需要 `android.overridePathCheck=true`（已加入 `gradle.properties`）；
   但即便加了，某些任务仍会因中文路径出错 —— **建议复制到纯 ASCII 路径构建**。
3. **不要只构建 `javaRelease`**：该变体不含 Python，源解析功能会缺失；
   历史版本（r73–r79）用的都是 `assemblePythonRelease`。
4. **JDK 21 会失败**：AGP 7.2.2 不支持，必须 JDK 17 或 11。

### 4.4 版本号

`versionCode 52` / `versionName 3.1.0-car.7-r80`。

## 5. 已知边界（**未完成，需车主实测**）

1. **零跑 B01 真机听感 A/B 未做。** 本仓库至今没有任何 `[听感]` 级证据；
   §4 全部是 host 级与字节级验证。
2. `MAX_MAKEUP = 4.0` 只由一条 r70 采样支撑。若真机仍偏小，可提到 6.0–8.0
   （软限幅仍能兜住，但响段会被更多压缩）。
3. `MEASURE_SECONDS = 4` 对"开场长时间静音/黑屏"的片源可能偏短；
   可能需要改为"首个非静音样本起算 4 秒"。
4. 开关二（音频通道对齐）在本车机**预期无 audible 差异**，理由见 §3.2；
   其价值是排除变量，不是修音量。
5. 快捷验证建议（不改代码）：
   - 把车机**媒体音量**推到 60–66 档（现场日志显示当时为 30/66）；
   - 再对比开关一开/关，同片源、同系统音量档。
