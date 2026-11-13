# r82 用户反馈修复与验收

目标版本：`3.1.0-car.7-r82`，versionCode `54`，包名 `com.github.tvbox.osc.jun`，应用名 `TVBox`。

最终签名 APK SHA-256：`e69a227502f17b82b111e9a2574a77bca06122825e4b4f78337b709c77c5247e`，52,750,546 字节。

## 修复行为

- 主动返回：全屏播放返回详情，再返回首页。`finish()` 先使恢复会话失效；暂停只更新已有会话，不再重新激活。返回键与应用内返回组件使用同一退出语义。
- 正常恢复：按 started/stopped 判断整个应用前后台；内部切页不标成离开应用。等待订阅和加载器就绪后恢复。恢复 Intent 和同片目有效会话优先于旧窗口状态；进度仅使用匹配片目的有效快照。
- 删除“音频通道对齐音乐”按钮。所有旧偏好值统一使用 MEDIA/MOVIE 属性，音频焦点由 VideoView 管理，EXO 不再二次抢焦点。
- 视频音量补偿按声道折算帧数，累计四秒有效声音后锁定，半秒淡入。静音开场不耗尽测量；同格式 seek 保持锁定，换片源重置，重新开启重新测量。它是逐片源峰值补偿，最大四倍，不是响度标准化。
- 高级设置重做为“播放与声音 / 返回操作 / 其他”三个分类，当前状态可见；去掉失效上传入口。返回手势改为深色小提示与白箭头，返回杠保留独立轻点入口。

## 数字音频证据

直接编译生产类的主机测试全部通过：会话生命周期26项；SourceLevelFramesHostTest28项；SourceLevelLockHostTest441项；其余返回、音频路由/焦点、画质、封面及更新检查通过。

同一已知 PCM 输入，用户增益100%：补偿关闭时峰值1000，开启锁定后4000，输出比率4.0；用户增益0%输出静音，500%叠加补偿后峰值19999。单双六声道均在四秒有效声音后锁定。

MuMu 已知低电平测试片段实际播放时，音频队列记录 `makeup_locked=true`、`makeup=4.0`、`measured_ratio=4.0`；设置页显示“已锁定 ×4.00”。这是解码后 PCM 的证据，不能外推为所有片源与音乐等响，也不能据此排除真车系统路由或音量表的影响。r80 文档顶部已补纠正说明。

## 设备与界面

使用独占 vm6；完整显示与触摸映射2880×1620、density160，无手工 size/density override，`CAR_DISPLAY_PASS`。安装包使用共享证书（SHA-256 `2FEB63C511CD725795CADC8FA890F9CBC754FE59C0BBFE0787AC33F94F44FA9C`），覆盖安装成功，Python ARM32/ARM64运行库齐全。

完整场景31项通过：原生触摸确认进入全屏、播放进度推进、后台返回、全屏→详情→首页、延迟不重入、设置文字边界及 edge/bar 两种触摸入口。恢复时复用存活详情；同时修复全屏恢复在 Fragment Handler 初始化前取消超时引发的空指针。冷启动结果另见下列证据目录。

冷启动已验证：后台保存真实全屏状态与75,001 ms进度，强制结束进程后恢复详情、全屏，读取进度78,415 ms，确认从保存位置继续播放；随后两次返回首页并清除会话。主动退出后的再次冷启动独立断言通过，保持首页且会话失效。

- [原生截图画廊](../reports/r82-feedback/index.html)
- [原生完整测试](../reports/r82-feedback/final.log)
- [冷启动恢复记录](../reports/r82-feedback/cold-restore.log)
- [主动退出后冷启动记录](../reports/r82-feedback/cold-no-restore.log)
- [安装包身份](../reports/r82-feedback/r82-identity.txt)
- [显示与触摸检查](../reports/r82-feedback/r82-display.txt)
- [PCM运行日志](../reports/r82-feedback/runtime.log)
- [证据摘要](../reports/r82-feedback/manifest.json)

## 验证边界

指定订阅 `https://clun.top/box.json` 在设备内加载93站源；默认Python豆瓣源返回10分类和真实详情。另测Python短剧源返回35分类和真实详情，但该样本60秒内未进入播放态，不能宣称此站源播放通过。可控播放、PCM和生命周期检查使用本地生成的120秒H.264/AAC片段，经应用现有推送播放入口进入正式播放器。

未验证真车音响听感与音乐等响；未验证真车OEM回收策略。MuMu期间多次出现管理端 `action:close`，相应未完成测试不作产品缺陷证据。原生测试中的R8名称、线程和截图连接故障已修复并单独处理。

## 构建与复现

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:GRADLE_USER_HOME='D:\HermesData\gradle-tiers'
$env:TVBOX_BUILD_PYTHON='C:\Users\WIN10\AppData\Local\Programs\Python\Python38\python.exe'
& 'D:\gradle-7.5\gradle-7.5\bin\gradle.bat' :app:assemblePythonRelease --console=plain --offline
python -B car\tests\run_playback_session_host_test.py --java-home $env:JAVA_HOME
```

独立原生测试源码：`car/tests/device/r82/Acceptance.java`。用同一 APK 的R8 mapping构建测试包，测试包使用共享证书，正式APK没有测试入口。只通过GitHub Release发布；不再使用已废弃服务器。
