---
name: glsurfaceview-video-lifecycle
description: Android 视频增强渲染器使用 GLSurfaceView、SurfaceTexture/OES、EXO/IJK 时，排查前后台恢复冻结、输出 Surface 误绑定与 producer 释放竞态。
tags: ["自习"]
---

# GLSurfaceView 视频 producer 生命周期验收

适用于在原有Android播放器中接入OES → FBO → 屏幕后处理。区分解码器输出Surface与GLSurfaceView的EGL窗口Surface，并验证真实源帧和像素。car.6曾在MuMu6.8.4.0验证；car.7-r2在MuMu6.8.7.0/Android15验证同producer全屏/恢复和八图像素。工具命令以实际源码根为准：根中已有app/player时不要再套tvbox-os-src。不可由这些模拟器证据推广为全部驱动context-loss或真实车机性能已通过。

## When to Use

- 图像增强在首次播放正常，Activity退后台/回前台后画面冻结或失效。
- renderer继承GLSurfaceView，而上层VideoView用`instanceof SurfaceView`恢复输出。
- 全屏移动视图、context重建、关闭播放时有BufferQueue错误或native崩溃。
- 增加持续输出后，需要证明暂停/后台不空转、退出无producer残留。

不用于普通Java源码架构分析，也不把日志提交频率当显示器实际呈现帧率。

## Procedure

1. 用PI grep定位恢复/释放链，不只读自定义renderer：

```powershell
Select-String -Path .\player\src\main\java\xyz\doikki\videoplayer\player\VideoView.java -Pattern 'resume\(|SurfaceView|setDisplay|release\('
Select-String -Path .\player\src\main\java\xyz\doikki\videoplayer\render\GlEnhanceRenderView.java -Pattern 'onSurfaceCreated|createProducerSurface|setSurface|releaseProducer|onDrawFrame'
```

2. 明确两个不同目标：解码器写入`new Surface(surfaceTexture)`；EGL写入GLSurfaceView窗口。GLSurfaceView本身是SurfaceView的子类，因此普通`setDisplay(getHolder())`恢复逻辑会把解码器重定向到EGL窗口，断开OES输入。为增强renderer增加专门分支，复用它的`attachToPlayer(player)`/主线程producer绑定，再恢复播放。

3. 主线程调用播放器`setSurface`。GL线程只创建/消费纹理。异步绑定前后校验renderer是否释放、player identity、current surface identity及surface有效性。EXO的`setSurface`不能从GLThread调用。

4. context重建要按引擎区分。EXO可以采用新consumer接管后再退休旧对象；但IJK/MediaCodec即使`setSurface(newSurface)`无null间隙、状态/位置/源帧继续增长，也可能在全屏重挂GLSurfaceView后输出绿色块。car.7-r2已验证的IJK方案是从prepare开始绑定稳定GL consumer，并在增强关闭时仅做identity OES passthrough，保留同一个SurfaceTexture和Surface，而不是创建替代producer。

   - 单靠`setPreserveEGLContextOnPause(true)`不足以覆盖View重挂/GLThread退出。自定义EGLContextFactory，在destroyContext前用相同EGLConfig建立1×1 pbuffer，将旧context设为current，调用既有SurfaceTexture的`detachFromGLContext()`，再撤销current、销毁pbuffer和旧context。
   - 新`onSurfaceCreated`分配OES texture，并让原SurfaceTexture执行`attachToGLContext(newTexture)`；解码器看到的Surface对象保持不变。
   - 跳过重复`setSurface`必须同时比较player identity和Surface identity。仅比较surface会使新播放器误判已绑定。
   - UI回调可能触发旧视图退休时，用主线程Handler.post跨过当前dispatchDraw遍历；不能在draw遍历内同步修改同级View集合。
   - 如果driver无法保留producer，应明确降级/失败，不用计数正常掩盖坏帧。MuMu6.8.7.0/Android15的最终B2EEDC14候选，生命周期101/0、EXO/IJK八图像素8/8；旧D2A候选像素7/8是历史失败，不能改写。

   最终像素门禁的已执行命令（参数换成当前候选）：

```powershell
python -B .\tvbox-os-src\car\tests\verify_car7_pixels.py <final-evidence-dir> --sha256 <final-apk-sha> --lifecycle --output <pixel-result.json>
```

   全屏进入和退出都要断言同一个Surface/SurfaceTexture、同一解码器、播放位置前进，并查`PRODUCER_PRESERVE_FAILED|PRODUCER_RECREATED|FATAL EXCEPTION`；只看无异常或playing=true不算完成。

5. 播放器release顺序：先在引擎仍有效时`setSurface(null)`解除输出，再释放引擎。IJK的release可能异步，不能只看Java方法返回就立即销毁仍在使用的consumer。producer释放必须与`updateTexImage/getTransformMatrix`及本帧draw同步，GL对象删除由GL线程完成，或由已销毁context回收。

6. 若使用锁串行producer使用与释放，帧间节流/park不能持有该锁，否则主线程生命周期回调可能被每帧等待阻塞。释放标记应阻止后续draw和晚到的frame callback。

7. 用独占MuMu租约，全部设备操作优先插件。池工具是**全局绝对路径**，租约状态全机共享
（不要拷贝、不要用相对路径；owner 替换为本会话ID）：

```powershell
$pool = "$env:USERPROFILE\.mumu-pool\mumu-pool.py"
python $pool check --owner <conversation-id> --index N       # 【门禁】exit 0 才允许动手
python $pool acquire --owner <conversation-id>               # 整批只调一次
python $pool heartbeat --owner <conversation-id> --index N
python $pool release --owner <conversation-id> --index N
```

`check` 不为 0（exit 3 = 别人持有或你没有租约）就停手：不 shutdown / restart / app close / 卸载，
不驱动它的 UI，也不要改用 adb 直连绕过。整批仅一次acquire/release，不操作其他租约，保持主机及guest静音。
通过`mumu_manager control app install/launch/close`驱动，不手写adb操作模拟器。

8. 共享LF脚本经插件执行已验证形式：

```text
mumu_shell(vmIndex="N", via="sh", cmd="/system/bin/sh /mnt/shared/MuMuShared/<batch>/env.sh /mnt/shared/MuMuShared/<batch>/step.sh 2>&1")
```

`env.sh`进入system_server mount namespace并仅导入所需Android classpath。复杂引号放共享脚本，不直接拼MuMuManager命令参数。

9. 使用足够长、无历史进度的本地24fps移动片。已验证生成方法（先生成40s，再循环成200s）：

```powershell
ffmpeg -hide_banner -loglevel error -y -f lavfi -i testsrc2=size=640x360:rate=24 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 40 -c:v libx264 -preset ultrafast -crf 30 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart motion-short.mp4
ffmpeg -hide_banner -loglevel error -y -stream_loop 4 -i motion-short.mp4 -c copy -movflags +faststart motion.mp4
```

媒体输出放scratch。通过应用原有配置/本地文件服务测试，不新增产品调试Activity。

10. 逐项运行EXO和IJK：首次播放→全屏→暂停键→恢复→HOME退后台→插件app launch回前台→采集至少两次源帧统计→退出→重新进入。切换内核必须看到实际IJK `MEDIA_INFO_VIDEO_RENDERING_START`等原生日志，不把点击意图当成功。

## Pitfalls

- 暂停键通常走`start()`，不等于Activity的`resume()`路径；只验证暂停键会漏掉SurfaceView误绑定。
- 前后台回来只看到PID存活或output计数增加，仍可能是反复画旧帧；必须看到source帧增长及画面变化。
- 预览/全屏控制栏按钮位置会随暂停/播放状态改变；用新鲜XML定位，控制栏隐藏后先唤醒再点击。
- 短片在逐步工具交互期间已经播完会让切换测试无效；使用延长片与新的源标识避免保存进度。
- MuMu UiAutomator可能写出XML后退出139；删除旧文件后确认新XML和应用PID。无XML时停止依赖该节点的动作，不能沿用旧证据。
- 独立GPU探针断言全通过后Shutdown139，需要同时记下通过标记和非零退出，不写成整个进程exit0。
- 不为理论上的风险反复重做广泛审查；一次独立复核后的具体缺陷修复并针对性复测即可。

## Verification

在最终包中收取以下证据：

- EXO/IJK各一次真实前后台恢复出现producer绑定标记，随后至少两个统计窗口sourceFps>0、输出继续、画面持续变化。
- 暂停/后台`WHEN_DIRTY`，恢复后持续输出；不把最大60Hz节奏宣称为稳定60fps呈现。
- 退出先解除主线程Surface绑定，再producer live=0；重进分配新producer并正常出帧，PID保持。
- 没有应用FATAL EXCEPTION、EXO wrong-thread、draw/setSurface错误及无解释的POSTPROCESS_FALLBACK。
- 软件帧统计、driver资源声明与真实硬件性能分开。未强制注入context loss时，不宣称覆盖所有驱动恢复路径。
- 最终APK哈希与证据对应。若仅生命周期变更，保留全量矩阵基线和最终针对性复测的各自哈希，明确没有重复的项目。
