# r64：音量生命周期与 5% 手势修复

## 确认的故障点

- BaseVideoController 构造会调用 initView。BaseController 在 initView 读取保存音量后，自己的字段初始化又把 mAppVolume 重置为 1f；且当时尚未绑定真实引擎，旧 loadAppVolume 会把 >100% 的保存值夹到 100%。
- applyVolumeLimit 在 PREPARED/PLAYING 中数值相同就不再下发，播放器重建后不能保证基础音量同步；能力限制还会写回 Hawk，永久丢掉用户在 EXO 下设置的增益。
- MyVideoView 的低音量与 duck 同时作用于 AudioTrack 和 PCM，导致重复衰减。基础层重设与 PCM 重设的时机不同，恢复时听感可能变化。
- 焦点丢失时调用普通 pause 会 abandonFocus，导致瞬时失焦没有 GAIN 恢复通知；旧的排队回调也可能在暂停/重建后重新施加 duck。

PlayFragment 的 pendingVolumeBoostAfterSwitch 有 >1f 外层保护，未发现普通切集把 -1 哨兵写成音量的问题，本版不改该分支。

## 修复

- 保存的用户音量在控制器构造完成后读取，绑定播放器时下发；PREPARED/PLAYING 无论设置是否改变都同步。引擎能力限制只影响本次输出，不覆盖保存值。
- VideoView 单独保存临时 duck，静音与 duck 合成到同一个 applyVolume 入口。设置音量、初始化/重置、窗口状态变化与前台恢复均复用该入口。
- EXO 的 AudioTrack 设为 1，增益/静音/duck 仅在 PCM 应用一次；非 PCM 在播放器层输出 min(gain,1)*factor。
- 焦点事件全部排到主线程并检查代次；主动暂停/释放取消旧回调、清 duck，临时失焦暂停保留通知。缓冲时收到 duck 也保留。
- 手势以起手值加累计位移计算，量化到最近 5%：0、5、10……200；当前不支持增益的引擎仍限制输出到 100%。不按 MOVE 次数累加。

## 验证

- AudioVolumeRoutingHostTest：4897 checks PASS。真实生产数学函数 + AudioGainProcessor 样本输出，覆盖重复 reset/configure、0/5/50/100/150/200%、静音、duck 和全部手势档位。
- AudioFocusHelperHostTest：77 checks PASS。编译生产 AudioFocusHelper，以确定性 Android test doubles 驱动回调队列，覆盖旧回调失效、临时失焦恢复、主动暂停不自动续播、缓冲 duck、静音。
- 原 AudioGainProcessorHostTest / VolumeBoostMathHostTest PASS。
- debug 与 release 串行 Gradle 构建成功。新探针只位于 src/debug，正式包无探针 Activity。
- MuMu 的 debug 探针使用生产 MyVideoView、VodController、EXO 和本地固定视频，两轮均 DONE checks=20 failed=0。覆盖保存150%构造绑定、非PCM限幅不覆盖保存值、返回PCM、150→155→150手势、50%单次衰减、duck/静音、全屏/小窗、自然结束接下一项、全新View/控制器、真实桌面往返。实际 PCM level_out/level_in≈1.5。
- debug 本地与安装 SHA：b79bbd001a9a5fa79384e491425d6bd76d1f52d2f668196dad6a3af8767f5d62。
- 正式 r64 安装与冷启动成功，PID 5074，崩溃缓冲为空；安装 SHA 与下述交付 SHA 一致。

## 产物与边界

- versionName：3.1.0-car.7-r64；versionCode：36。
- Application ID：com.github.tvbox.osc.jun；label：TVBox。
- APK：52,734,169 bytes。
- SHA-256：3489ba3cffe491b1ff2a6d0ac3f41a6c3d12eee86fefed47616e44c0579f1976。
- v1/v2/v3 校验通过，证书 SHA-256：2feb63c511cd725795cadc8fa890f9cbc754fe59c0bbfe0787ac33f94f44fa9c。
- 原生生命周期断言跑在 debug 变体；正式 release 另做构建、包内容、签名、安装SHA和启动检查。没有把 debug 测试冒充最终 release 的 instrumentation。
- 未在零跑车机做听感/RMS复测，未证明不同节目本身的录音响度一致。本次修复的是 App 内保存和应用同一音量的稳定性。
- MuMu 已恢复为正式 r64，测试偏好由探针 onDestroy 恢复，释放本会话租约并保留 MuMu 主程序。

日志与完整本地证据位于本轮会话 scratch：r64-audio-first.log、r64-audio-final.log、r64-verification.json。
