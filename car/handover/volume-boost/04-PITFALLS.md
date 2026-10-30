# 04 · 坑清单（含为什么前几轮白折腾）

> 分三类：**方法论坑**（这才是根因层面的坑）、**构建/工具坑**、**设备/环境坑**。
> 每条都写"症状 → 原因 → 规避"。

---

## 一、方法论坑（★ 最重要）

### P1 · 用静音模拟器"验证"音效

- **症状**：连续 4 轮宣布"音量增益已修复并在实机验证"，用户每次反馈"还是不行"。
- **原因**：所有 MuMu 实例都用 `mumu-pool.py acquire --mute` 租用（宿主机 + guest 流双静音），
  **从来没有一次听觉验证**。所谓"实机验证"其实只有 logcat。
- **规避**：音量类改动必须至少满足以下之一才允许说"已验证"：
  ① 处理器内部日志证明采样被放大（`level_out ≈ gain × level_in`）；
  ② `AudioPlaybackCapture`/录音的 RMS 前后差 ≈6 dB；
  ③ 人耳（非静音设备）。
  只打应用侧"我调用了 setGain"的日志，**不算验证**。

### P2 · `path=pcm` 这种日志天生没有证明力

- **症状**：`CarAudio runtime_apply … path=pcm gain=2.0` 被当成"PCM 增益已生效"。
- **原因**：该日志位于 `setGain()` 调用之前一行，只反映 `usesPcmGain()` 的判断结果。
- **规避**：日志要打在**被观察对象内部**（处理器里），且要能体现"输入/输出差异"，而不是"我发起了调用"。

### P3 · 静默降级把故障藏起来

- **症状**：`LoudnessEnhancer` 创建失败、`session<=0`、编码被拒，全都无声无息。
- **原因**：`AudioEffectController.buildChain` 的 `catch (Throwable ignored)`、
  `AudioGainProcessor.onConfigure` 抛异常后被 ExoPlayer 吞掉。
- **规避**：所有"降级/跳过"路径必须打 W 级日志并带上原因（r56 已补 `configure REJECTED`）。

### P4 · 没有确认"实际用的是哪个引擎"

- **症状**：可能一直在 IJK 路径上测 EXO 的修复。
- **原因**：`pl` 会被站点配置和**自动重试**改写，并持久化进 `mVodInfo.playerCfg`（历史记录）。
- **规避**：日志里带 `engine=` 类名（r56 已补）；测试前先删掉该影片的历史记录，避免继承旧的 `pl`。

### P5 · 只在"我们自己的代码"里找问题

- **症状**：反复改 `BaseController` / `CarEnhance` / `AudioGainProcessor`，
  但没有一次验证"设备音频通路是否把增益吃掉了"。
- **规避**：先分类（没走到 / 走到了没用），再动手改。

---

## 二、构建与工具坑

### P6 · PI `edit` 多 op 的行号语义（本次实测踩 3 次）

- **症状**：
  - 相同两行源码出现两次 → `error: variable processors is already defined`；
  - 大括号丢失 → 报错落在文件末尾（`ExoPlayer.java:566: 非法的语法`），实际是中间少了一个 `}`；
  - 后半段代码孤悬 → `AudioGainProcessor.java:58: 非法的表达式开始`。
- **原因**：一次 `edit` 调用里放多个 op 时，**后续 op 的行号仍按"未修改前"的原始行号解释**，
  所以若前一个 op 增加了行数，手动按位移算出来的行号会**多算**，导致替换窗口偏移、留下残段。
- **规避**：**一次 edit 只放一个 op**；确实要多处改就串行多次 edit（每次都重新 Read 拿新 tag）。
  另一条等效纪律：op 的行号一律用"刚 Read 到的当前行号"，不要手工推算偏移。

### P7 · `BaseAudioProcessor.flush()/reset()` 是 `final`

- **症状**：`AudioGainProcessor 中的 flush() 无法覆盖 BaseAudioProcessor 中的 flush()；被覆盖的方法为 final`。
- **规避**：不要在 `BaseAudioProcessor` 子类里覆写 `flush()`/`reset()`；
  需要会话标记就写在 `onConfigure()`/`queueInput()` 里。

### P8 · PowerShell 5.1 的几个具体坑（本机实测）

| 坑 | 症状 | 规避 |
|---|---|---|
| `Select-String -Path "a\**\*.java"` | **静默返回空**（`**` 不被展开） | 用 `Get-ChildItem -Recurse -Include *.java -Path a | Select-String` |
| `-Pattern "… <= 20 …"` | ParserError / 命令被截断 | 把正则放进单引号，或避免 `<` `>` 字面量 |
| `2>&1` | 只要 stderr 非空就被判成 exit 1（假失败） | 用 `2>$null`，或落盘后 `Get-Content` |
| 读 Gradle 输出 | 中文错误信息乱码 | 用 `Get-Content -Encoding Default`（控制台 GBK）；自己写的文件用 UTF-8 无 BOM |
| 变量名 | `$s`/`$S` 是同一个变量 | 不要用大小写区分变量 |

### P9 · Gradle 构建链（本机事实）

```
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\HermesData\gradle-tiers'      # 用 C:\Users\WIN10\.gradle 会报 offline 无缓存
gradle.bat :app:assemblePythonRelease -x :pyramid:generatePythonReleasePythonRequirements --console=plain --offline
```

- `-x :pyramid:generatePythonReleasePythonRequirements` 用来跳过无法离线执行的 chaquopy 步骤；
  产物因此**缺 `assets/chaquopy/requirements-*.imy`**，必须用 `rebuild_apk.py` 从旧 Release APK（r40）注入。
- 输出重定向偶发 `FileOutputStream.write` 异常导致假 BUILD FAILED（重跑即成功）。
- 构建失败时错误行在日志里是 `D:\…\X.java:NN:`，但控制台编码是 GBK，读日志要指定 `-Encoding Default`。

### P10 · 签名

- 必须用 `~/.android/debug.keystore`，证书 SHA-256 必须是 `2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C`，
  否则车机上出现 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` / "软件包与现有软件包存在冲突"。
- 命令：`apksigner sign --ks … --v1-signing-enabled true --v2-signing-enabled true`，
  校验：`apksigner verify --print-certs`。

---

## 三、设备与环境坑

| 坑 | 说明 | 规避 |
|---|---|---|
| MuMu 租约 | 池里实例有 `lease.owner` / `do_not_touch`，抢别人在用的会被记 busy | 每批次 `acquire --owner <新owner>`，结束时 `release` |
| 前台留 app 被判 busy | 租期结束时若前台是本 app，池会拒绝释放 | `input keyevent 3` 归位后再 release |
| MuMu `tun0` | MuMu 自带 VPN 起来后 guest Java DNS 全挂（`UnknownHostException`），native `ping` 正常 | 重启实例；`/etc/hosts` 所在分区 ext4 `ro`，改不了 |
| `adb reverse` | MuMu 上不通（18080/8088 都验过） | 别用它做 guest→host 的 HTTP |
| adb daemon 抖动 | 频繁 `device not found` | 每条命令前先 `adb connect 127.0.0.1:<port>`；用 MuMu 自带 adb（`-P 5055`） |
| `uiautomator` | Android 15 上不可用 | 改用 OCR（`car/tests/device/tools/ocr*.ps1`） |
| 设置页 `SelectDialog` | tap / DPAD / TAB / ENTER 都点不中；`frida-server` 连上即断 | 不要指望脚本化切换播放器；改成用日志/配置改 `pl` |
| 代理 App 抢前台 | `io.nekohasekai.sfa` 会抢前台 | 测试期 `pm disable-user --user 0 io.nekohasekai.sfa`，结束 `pm enable` |
| `gh` 默认仓库 | 默认指向 upstream `q215613905/TVBoxOS` | 一律显式 `--repo 736368217/tvboxos-car-2880` |
| 宿主机过载 | `HOST_OVERLOADED`，构建/设备操作排队 | 避免并发构建；长命令放前台带大 timeout |

---

## 四、复盘：为什么这几轮技术上"看起来都对"却没解决

1. 方案本身没错（PCM 采样增益是正确方向），**错的是验证闭环**：
   验证手段只覆盖到"应用调用了"，没有覆盖到"采样变了""声音响了"。
2. 三轮都在**没有区分失败类别**的前提下改代码：
   究竟是"没走到增益"还是"走到了没用"，从来没有被区分过（P3/P4）。
3. 一个**静默降级**（r44 的 `loudness_unavailable`）+ 一个**无证明力的日志**（`path=pcm`），
   合起来制造了"已修复"的假象（P1/P2）。

> 下一位接手者的第一件事不是改代码，而是**用 r56 拿一份车机日志把 H1/H2 分掉**。
