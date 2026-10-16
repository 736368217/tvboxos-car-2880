---
name: android-native-acceptance-evidence
description: 验证改动后的Android APK真实功能时，用实际安装SHA、原生instrument结果和像素证据区分运行兼容、数据为空、工具异常与画面缺陷。
tags: ["自习"]
---

# Android原生验收：实际包、逐阶段结果与像素

## When to Use

- APK已构建/签名，但要证明真实Python/JAR/JS站点、播放器或车机控件功能可用。
- instrumentation shell返回0，却没有完整结果，或播放器位置/帧数增长但截图异常。
- release被R8混淆，需要用最终mapping构建独立测试APK。

Don't use for: 普通Java源码架构分析、把未解决的像素异常宣称为修复成功、在Windows执行下载的远程脚本、操作没有当前会话租约的实例。

## Procedure

1. 在PI `bash`记录本地APK身份，随后guest必须重新计算实际安装base.apk的SHA，不能使用文件名猜包名。

```powershell
Get-FileHash -Algorithm SHA256 .\tvbox-os-car\TVBox-1.0.0-car.7-python-signed.apk
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\aapt2.exe" dump badging .\tvbox-os-car\TVBox-1.0.0-car.7-python-signed.apk
```

2. 每次设备操作先检查全局租约，整批只acquire/release一次。已持有租约时不再acquire。

```powershell
python "$env:USERPROFILE\.mumu-pool\mumu-pool.py" check --owner <conversation-id> --index <N>
python "$env:USERPROFILE\.mumu-pool\mumu-pool.py" heartbeat --owner <conversation-id> --index <N>
```

3. 所有设备操作走 `hawchou.mumu-player`。复杂guest命令写共享UTF8/LF脚本，使用已验证的env.sh导入system_server三个classpath并进入mount namespace。车机测试先运行verify_car_display.sh，要求真实display/input2880x1620、density160、无override、CAR_DISPLAY_PASS。音量0。

4. 用最终PythonRelease的mapping构建独立测试APK，生成物放scratch。

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
python -B .\tvbox-os-src\car\tests\native\build_native.py --out "$env:PI_SCRATCH_DIR\car7-native-final"
```

测试与生产包使用同一已知开发测试证书。只通过插件安装；不能因R8方法消失就修改生产包加入调试入口。内联方法可能没有实际反射入口，可读取映射后的实际状态字段，并检查业务行为。

5. instrumentation `am instrument -e` 的值是字符串。Java按字符串解析：

```java
boolean diagnostic = "true".equals(arguments.getString("pixelDiagnostic"));
```

不要用Bundle.getBoolean读取-e true，否则会静默走普通模式。检查codec.json、特定模式截图或阶段事件真的出现，不只检查参数调用意图。

6. 长探针后台单次执行，所有输出写共享文件，短轮询；不因插件timedOut/code:null重复启动。保持同一实例只跑一个instrument。示例工具调用的cmd：

```text
out=/mnt/shared/MuMuShared/<batch>; ( /system/bin/sh "$out/env.sh" "$out/run_guest.sh" playback <unique-tag> <expected-lowercase-sha> "$out" > "$out/invoke.txt" 2>&1; echo $? > "$out/invoke.rc" ) & echo launch_pid=$!
```

后台只在guest内用于instrument，主机后台构建可能被命令宿主回收。若worker消失且无结果，不能声称构建成功；用隐藏subprocess管道执行并捕获真实exit。不要重复启动重叠构建。

7. 探针每轮保存identity.json（实际APK SHA/PID/tag/version）、阶段结果、result.json和有界日志。读取result.json中的逐项pass/failed；instrument_rc=0可同时出现INSTRUMENTATION_CODE:0和断言失败，不是全绿。run_guest.sh应检查failed=0并返回非零门禁。

8. 真实PY站点至少覆盖import/init/home/category/detail/search，播放另验证playerContent、实际EXO视频尺寸、两次递增位置。远程脚本只在Android执行。类别/详情/搜索空列表必须单列失败，不以load_success或categories>0掩盖。保存计数、耗时、scheme/parse flags；完整播放URL/headers只在目标进程内存，不写证据。下载200或fixture通过不能代替外站行为。

9. 视频位置/源帧增长不能发现绿色坏帧，另跑实际PNG像素门禁：

```powershell
python -B .\tvbox-os-src\car\tests\verify_car7_pixels.py <final-evidence-dir> --sha256 <final-apk-sha> --lifecycle --output <pixel-result.json>
```

保存初始、全屏、前台恢复、重进八图。E395基线实际EXO2/2正常、IJK2/2绿图失败，可证明门禁检出真实异常。区分soft/hardware codec时记录实际mediacodec配置，先比较原始Texture和GL；不能从一次状态PASS推导颜色正确。像素失败保持失败，不改阈值凑通过。

10. 最终包变化后重新跑对应SHA门禁。历史证据保留原SHA，不全局替换哈希冒充最终验收。主机测试和静态检查分别记录exit。

11. 搜索类异步验收必须同时检查任务生命周期和结果：每站任务先登记FutureTask再提交，超时不能只移除UI侧pending状态；必须取消实际Future并让中断传递到底层请求。普通搜索的等待队列必须有确定的pump机制，不能在启动任务全部超时后留下永久waiting。验证时分别记录启动、超时、取消、完成和队列推进事件，不能以15s UI计数器变化代替真实任务已停止。若使用 `SynchronousQueue` 或有界执行器，必须确认任务先登记后提交，提交失败会进入可推进队列，并在完成/取消后调用 pump；UI 计数器变化、Future 被移出 pending 集合或 `instrument_rc=0` 都不能证明底层任务已停止。

12. QuickJS异步调用不得让JS执行器同步等待提交到同一执行器的network/timer回调。JS线程只返回Async.Result，由调用方等待统一deadline。先用本地同步/Promise/timer/HTTP/reject/超时/中断/恢复矩阵证明契约，再判断外站。一次外站30秒超时不能单独证明通用互等；分类/详情可用也不能证明搜索可用。区分调用方已结束、底层资源已取消、executor后续调用可恢复三项证据。第三方JAR忽略中断时，只能限制并发和丢弃迟到结果，不能宣称已强制终止任意同进程代码。

13. 页面验收除结果JSON外，至少覆盖首页、详情、搜索、播放器控制和高级功能的实际Activity/View状态；对可交互开关验证点击前后持久化值确实翻转，对播放器验证实际视频View存在。截图证据用于视觉检查，native-node/result.json用于结构和行为检查，两者不得互相替代。

```powershell
python -B .\tvbox-os-src\car\tests\test_python_http.py
```

14. 交付使用显式路径清单。禁止新增APK、下载的Spider JAR、私钥、私有偏好或原始远端响应进入Git；已有运行时依赖/Gradle wrapper不是Spider JAR，不越界删除上游历史文件。交接源码必须绑定已推送commit和新handoff ref，不能让包内工作树快照冒充Git已同步。新工作区根目录和命令路径以实际clone为准；文档若被*.md忽略要显式放行。

15. 结束先停目标并定位原备份实际文件名，再恢复整个guest私有prefs、属主与SELinux标记，cmp不输出内容。不能假定hawk.xml，本轮实际Hawk2.xml；备份缺失时应在任何覆盖前停止。卸载测试包，重新启动检查稳定PID/FATAL0/安装SHA，再release自己租约并保留MuMu主程序。

```powershell
python "$env:USERPROFILE\.mumu-pool\mumu-pool.py" release --owner <conversation-id> --index <N>
```

16. HTTP200与脚本import成功不等于内容有效。保存status、mime、bytes、SHA、明确403/404/captcha特征及解析条目数，不输出原文/headers。nginx只是server标记，不能单独判访问拒绝；必要时Python/Java同请求对照。已知分类ID/片名的阳性搜索与固定关键词结果分别记录，未命中仍保留失败。

17. R8内联getter没有反射入口时先查最终mapping，读取实际字段验证状态（如adapter.mData、fragment.mVideoView），不猜混淆方法、不为测试改生产包。控制条隐藏先真实触摸唤醒，再检查原生可见性并点击；验证当前renderer/decoder/session，不只看标签或持久化值。

## Pitfalls

- tool ok:true、shell0、INSTRUMENTATION_CODE:0、APK安装成功、PID存活都不能代替业务断言。
- 搜索UI超时不等于实际Future/HTTP取消，必须检查active/peak、尾站结果与退出收敛。
- 本地QuickJS矩阵通过不能推广成所有远端脚本兼容；外站超时需保留具体阶段和异常链。
- UiAutomator可能写出fresh XML后exit139；确认新文件、目标包与PID后单独记录工具异常，不伪装应用崩溃。
- 帧计数增长却绿屏是像素失败，不放宽门禁凑全绿。未用实际最终SHA重测时保留旧证据身份。
- 插件启动timeout但有launched PID时先轮询invoke.rc/result.json，禁止不确定重放；一个实例一个instrument。
- UTF8 JSON不要用本机默认GBK读取，复杂PowerShell引号用脚本和结构化参数，不混写POSIX语法。
- 修改skill前单独PI Read当前SKILL.md再skill_manage patch；并行Read可能未被read-before-write门禁记录。
- 无真实截图不能声称人工视觉检查；截图、像素量化与原生节点分别说明。

## Verification

完成需要实际安装SHA、PID/tag、完整断言与失败列表、视频尺寸/递增位置、八图像素、崩溃结论、恢复和租约释放。以当前交付commit及对应handoff/ref为准，历史tag不要求与最新main相同。deliveryReady与releaseReady分开；有源特定失败时不能写全部外站通过。远端附件若上传，逐项核对uploaded、字节大小及SHA摘要；本地包CRC/摘要不冒充远端上传成功。
