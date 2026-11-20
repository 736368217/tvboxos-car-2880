# r82 原生验收包

此测试包独立于产品 APK，使用相同证书安装到专属测试实例。它会改该实例内 TVBox 的测试偏好和播放会话，只在持有租约的模拟器上运行。

## 构建

先构建并签名产品 APK，保留该构建的 R8 mapping。

```powershell
python -B car\tests\device\r82\build.py --sdk "$env:ANDROID_HOME" --java-home "$env:JAVA_HOME" --mapping 'app\build\outputs\mapping\pythonRelease\mapping.txt' --out "$env:PI_SCRATCH_DIR\r82-native"
```

脚本编译 Java、D8、aapt2、zipalign，并用仓库 `car/ci/debug.keystore` 签名，输出 `acceptance.apk`。临时中间产物只写 `--out`。

## 运行（联网或旧单片段场景）

实例显示与触摸映射必须先通过 `car/tests/device/verify_car_display.sh`。测试截图断言2880×1620，密度160；使用 `mumu-android-shell-namespace` 中的 system_server namespace/classpath wrapper。

将测试 APK 和已知 H.264/AAC 文件复制到共享目录，通过 MuMu 插件执行 `pm install -r <guest-apk-path>`。旧本地片段入口为 `/sdcard/Download/r82-quiet-test.mp4`，建议至少 120 秒、48 kHz；音量 A/B 与换集使用下方的双集 fixture。

```sh
am instrument -w -r -e source fixture -e label suite -e scenario suite com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am instrument -w -r -e source fixture -e label arm -e scenario arm com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e label restore -e scenario restore com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e label no-restore -e scenario no_restore com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
```

每个阶段必须检查 `INSTRUMENTATION_RESULT: result=PASS` 后才能进入下一步。用 `nohup /system/bin/sh env.sh run.sh </dev/null > invoke.log 2>&1 &` 留住后台调用；run.sh 内保留 `-w`，否则没有 UiAutomation 截图连接。

本节的联网场景使用 `https://clun.top/box.json`，等待至少 3 个站源，再读取所选站源分类；不把动态站点总数写死。`-e source fixture` 用推送入口播放单一本地片段，`-e source <站源key>` 验该站真实详情和播放；下面的双集场景改用回环 HTTP 源，只要求至少 1 个站源。

输出在 `/sdcard/Android/data/com.github.tvbox.osc.jun/files/r82-<label>/`。最终截图、报告应复制进仓库证据目录，绑定实际安装APK SHA。单独卸载测试包并释放本轮租约。

## 反射边界

R8 mapping 的内联帧不是可调用接口。尤其 `enterFullPreview()` 内联进 `jumpToPlay()` 后，两者可映射到同一个运行方法；直接反射会重装播放数据，不能证明进入全屏。本测试通过原生触摸点击预览画面，随后检查实际全屏字段。

播放状态和位置查询经主线程，单次主线程工作有 15 秒超时，阶段等待另有各自截止时间。设置页由原生 TextView 核对动态音量，不再要求旧“已锁定 ×4”文本；PCM queue 日志与主机采样对照分别提供运行态和数值证据。

## 播放栏音量 A/B（本地双集）

`python -B car/tests/device/r82/make_audio_fixture.py --out "$env:PI_SCRATCH_DIR/audio-fixture"` 生成两段 120 秒 H.264/AAC、48 kHz 双声道视频，按 12 秒循环交替轻声和响段。需 ffmpeg 在 PATH 中，或通过 `--ffmpeg` 指定实际路径；不依赖远端片源。

将 `episode-1.mp4`、`episode-2.mp4` 放到专属实例的 `/sdcard/Android/data/com.github.tvbox.osc.jun/files/audio-fixture/`。若 root 复制文件，必须将真实 `/data/media/0/Android/data/...` 下这两份文件的属主设为该应用 UID、权限设为 0644；root 的 0700 文件会让测试进程报 FileNotFoundException。禁止在机主安装上改偏好，本地验收应使用专用空实例。

```sh
am instrument -w -r -e scenario audio_fixture -e label audio-ab com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e scenario audio_restore -e label audio-cold com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e scenario no_restore -e label audio-exit com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
```

`audio_fixture` 仅在回环地址 18765 提供 JSON 双集站源和 Range 媒体，结束仪器时关闭服务；不配置无用的空 `lives` 数组。新播放阶段须用干净的专用测试应用数据，避免旧选集历史影响起点；`audio_restore` 重启该服务并保留待验证会话与 A/B 偏好，不能在两阶段之间清数据。各阶段分别检查 PASS，不能外推远端站源或真车听感。
