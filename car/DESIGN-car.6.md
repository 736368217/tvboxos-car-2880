# car.6 音画扩展、SGSR1 与 60Hz 时域平滑实施计划

基线：car.5 / 1038379，继续使用 Python 通用 ARM32+ARM64 flavor，包名与标签不变。用户规格为 TVBoxOS car.4 音画预设扩展与 SGSR1+60Hz补帧融合开发规格书。

## 实施决策

1. 原 6 档音效参数逐项保留，新增 6 动漫·标准、7 动漫·剧场、8 动漫·对白。把数据与 Android 效果器接线适度分离，便于 host 校验精确曲线。
2. car.5 已有 0–7 画面档位，保留这些稳定编号及参数；规格的三个动漫档位追加为 8 动漫·暗光、9 动漫·日间、10 动漫·清透。共 11 档。0 原画与 1 臻彩不变。
3. 新增 video_sr、video_interp 独立偏好，默认开启。设置显示 SGSR1 超分、60Hz 时域平滑补帧，切换保存并在下一次播放创建 renderer 时应用。
4. 两 Pass：OES -> 当前 RGBA8 FBO（调色）；当前/上一帧 2D FBO -> 屏幕（局部自适应时域混合 + SGSR1）。只在新源帧到达时写 FBO；屏幕尺寸沿用 MeasureHelper/实际 viewport，保持视频比例，不写死 2880x1476。
5. 采用官方 SGSR1 的 12-tap 权重与亮度修正，显式 2D 邻域采样代替 textureGather，以保留 GLES2 基线兼容性；32/255 边缘阈值、16/255 delta 限幅、预设关联锐度。保留官方许可证与出处。
6. 时域平滑最大上一帧权重 0.28，新帧周期前 35% 递减；按局部绿色差异使用 1-smoothstep(0.015,0.10,diff)，修正规格中逆序 smoothstep 的未定义行为。首帧、seek、时间戳逆序/间隔异常不混合。
7. 补帧启用且正在播放、窗口可见时持续输出，目标显示器刷新上限 60Hz；关闭、暂停、后台、完成、释放停止持续绘制。记录源帧、输出帧、混合次数和实际节奏，不能仅凭循环计数宣称屏幕达到60fps。
8. 双 FBO 按真实源尺寸分配；1080p 16,588,800 B (15.82 MiB)，4K 更大如实记录。宽>=2560 或源已覆盖目标 viewport 时跳过 SGSR。FBO 不完整/过大或 shader 失败降级为单 Pass 调色并明确记录原因，避免无提示黑屏。
9. HDR 时调色恒等，保留超分/时域开关。RGBA8 是解码后显示处理，不能据此声称端到端10-bit HDR或2ms性能已经验证。
10. Surface 对播放器的绑定继续在主线程；GL 分配/删除只在 GL 线程；重建/尺寸变化/释放清空历史，旧 Surface 绑定有 identity guard。

## 验收门

- host：9 档音效原参数未变、11 档画面旧参数未变、新动漫参数精确；偏好迁移与异常回退；时域权重边界、seek/首帧/暂停、尺寸/SR策略。
- 构建：Java编译检查与 Python Release 构建，签名和zipalign、包名/标签/ABI核对。
- GPU：使用同一生产 shader 的离屏实测/readback，验证调色、SGSR边缘效果/平涂保护、时域门限/混合与所有开关组合；真实播放器日志确认双FBO、连续源帧和输出、参数/资源释放。
- 设备：独占MuMu租约、整批静音；9音效循环及新增音效会话；11画面列表及新增参数；超分/补帧独立开关与重启持久化；EXO、IJK播放；暂停/恢复、seek、全屏/退全屏、反复退出重进；Python/QuickJS/JAR回归。
- 交付：car.6 APK、源码/参数/测试说明、实际证据、真实车机未覆盖的性能与HDR范围。既有私有仓库和 Release 工作流沿用。

SGSR不会被描述为AI生成细节；Oversample按规格是时域混合平滑，不包含运动矢量估计。2ms/真实60Hz呈现需目标SA8295P设备实测。

## 复核后落实的生命周期修复

独立复核识别了GLSurfaceView被普通SurfaceView恢复分支误绑定、旧producer过早释放、释放与GL draw并发三点，已在最终包修复。VideoView对增强renderer恢复时重绑OES producer；解除解码输出后再释放引擎，producer释放与完整GL draw串行；context重建旧producer保留至新Surface绑定返回。最终包EXO/IJK前后台恢复后源帧继续增长，退出live=0，证据见TEST-REPORT及lifecycle-runtime-excerpts。
