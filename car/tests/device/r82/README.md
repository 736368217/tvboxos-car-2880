# r82 原生验收包

此测试包独立于产品 APK，使用相同证书安装到专属测试实例。它会改该实例内 TVBox 的测试偏好和播放会话，只在持有租约的模拟器上运行。

## 构建

先构建并签名产品 APK，保留该构建的 R8 mapping。

```powershell
python -B car\tests\device\r82\build.py --sdk 'D:\codex\projects\2026-08-21\a\work\android-toolchain\sdk' --java-home 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot' --mapping 'app\build\outputs\mapping\pythonRelease\mapping.txt' --out "$env:PI_SCRATCH_DIR\r82-native"
```

脚本编译 Java、D8、aapt2、zipalign，并用仓库 `car/ci/debug.keystore` 签名，输出 `acceptance.apk`。临时中间产物只写 `--out`。

## 运行

实例显示与触摸映射必须先通过 `car/tests/device/verify_car_display.sh`。测试截图断言2880×1620，密度160；使用 `mumu-android-shell-namespace` 中的 system_server namespace/classpath wrapper。

将测试 APK 和已知 H.264/AAC 文件复制到共享目录，通过 MuMu 插件执行 `pm install -r <guest-apk-path>`。已知片段路径为 `/sdcard/Download/r82-quiet-test.mp4`，建议至少120秒、48kHz、音轨峰值低于0.125，便于验证四倍补偿。

```sh
am instrument -w -r -e source fixture -e label suite -e scenario suite com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am instrument -w -r -e source fixture -e label arm -e scenario arm com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e label restore -e scenario restore com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
am force-stop com.github.tvbox.osc.jun
am instrument -w -r -e label no-restore -e scenario no_restore com.tvbox.car.r82test/com.tvbox.car.r82test.Acceptance
```

每个阶段必须检查 `INSTRUMENTATION_RESULT: result=PASS` 后才能进入下一步。用 `nohup /system/bin/sh env.sh run.sh </dev/null > invoke.log 2>&1 &` 留住后台调用；run.sh 内保留 `-w`，否则没有 UiAutomation 截图连接。

测试先将用户指定订阅 `https://clun.top/box.json` 写入偏好，等待93站源的实际加载，再读取站源分类。`-e source fixture` 使用现有推送播放入口播放已知本地片段；`-e source <真实站源key>` 则验证该源的真实详情和播放。不能把两类结果混成“所有网络片源正常”。

输出在 `/sdcard/Android/data/com.github.tvbox.osc.jun/files/r82-<label>/`。最终截图、报告应复制进仓库证据目录，绑定实际安装APK SHA。单独卸载测试包并释放本轮租约。

## 反射边界

R8 mapping 的内联帧不是可调用接口。尤其 `enterFullPreview()` 内联进 `jumpToPlay()` 后，两者可映射到同一个运行方法；直接反射会重装播放数据，不能证明进入全屏。本测试通过原生触摸点击预览画面，随后检查实际全屏字段。

EXO 查询走主线程；等待有15秒超时。运行补偿状态读取真实设置页 TextView，PCM queue日志独立证明采样增益。设置截图同时检查原生文字布局高度、行宽与省略状态。
