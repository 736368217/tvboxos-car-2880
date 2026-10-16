# car.7 设计与兼容修复

基线：car.6 / `72cd6c8bf6a68434a0bf7bdd3a6522d9c4b562d5`。包名 `com.github.tvbox.osc.jun`、标签 `TVBox`、Python ARM32＋ARM64 flavor 保持。验收环境为完整横屏 2880×1620、density 160、rotation 1，无 size/density override；显示与输入 viewport 必须同时通过 `tests/verify_car_display.sh`。

## Python 真实站点

Python flavor 包含解释器，并不保证第三方 Spider 的 Requests 语义、TLS、Cookie 和缓存处理兼容。car.7 补齐生产链路：

1. `pyramid/src/python/tvbox_http.py` 以标准 Requests `BaseAdapter` 连接 Android `PythonHttp`，返回真实 `requests.Response`。Requests 负责请求准备、参数、Cookie、相对重定向和 302/307 语义，桥接保留原始字节、编码、reason、final URL、重复响应 header 和 timeout。
2. native Requests 保持优先。可重放的 HTTPS 请求遇到连接异常或既定错误状态时回退到 Java HTTP；显式代理、stream/files 和不可重放 body 继续使用 native。诊断只记录 method/host/status/耗时。
3. `app.py` 下载脚本只接受有界重定向后的 2xx、非空且语法有效内容；临时文件通过检查后原子替换。失败不覆盖有效缓存，也不静默导入旧缓存冒充本轮成功。
4. `ApiConfig.parseJson()` 传递完整配置给 Python loader；PY 排除出后台预热，避免同步加载锁阻塞前台站点。`pyLoader.setConfig()` 延迟应用 pending 配置，不在主线程启动解释器。

clun 是本轮真实 PY 测试面；当前 xiaosa 为 108 JAR/CSP＋1 JS、0 PY，分别验收。Windows 仅静态解析远程数据，脚本仅在 Android 目标应用内执行。

## 车机页面和返回

背景与视频铺满窗口，控件保留侧 79、顶 90、底 124px 的安全操作区。主页快捷入口横向布局，栏目可滚动，海报与标题独立布局，ViewPager 使用剩余高度。

返回控件为 Activity decor 内普通 View，无系统悬浮窗或无障碍权限。模式为 `floating`、`edge`、`both`；标准球视觉/热区 189/243px，大号 216/270px。参考单位按 `min(w/1280,h/720)*1.5` 换算，完整尺寸见 `BACK-CAR-SCALE.md`。冷启动测量在测量子 View 前更新缩放，避免第一次打开仍为 72px 热区。

拖动吸边、独立归一化坐标、闲置 alpha .38 和触摸唤醒保留。边缘短拉、反悔、CANCEL 和纵向手势不返回，400ms 长按可调整高度。根页面点击只提示；分类先回主页，再由 Activity 原返回链处理。

弹窗外部点击以卡片边界和完整手势判定；外部拖动后回到起点、多指或 CANCEL 不提交也不误取消。选择器和普通输入/确认弹窗分别保留其原业务回调。

## 当前视频设置和生命周期

9音效、11画面、SR与补帧的ID/参数保持。设置写偏好后应用到实际MyVideoView和当前音频session。配置作为不可变快照在GL帧边界生效。EXO的Texture↔GL交接保持旧producer，直到新Surface在主线程绑定成功后才释放；提交回调延后一条主线程消息，避免绘制遍历期间移除子View。快速设置只应用最新快照，失败回滚，退出取消待交接回调。IJK从prepare开始保留稳定GL输出，关闭时恒等直通、无FBO/SR/持续补帧，避免运行中替换消费者。均不restart/seek，不替换decoder identity。

`VideoView.onInfo()` 不允许迟到的 BUFFERING_END/RENDERING_START 将显式暂停提升为播放。退出先尝试解除 Surface，再释放引擎与 renderer；GL detach 失败时保留 producer，成功后才设置 terminal released，允许重复释放重试。

最终D2A的95项状态/生命周期通过、八图像素7/8。EXO全部正常；IJK初始播放、前台恢复、重进已正常，但硬解全屏重建producer后仍绿偏，短暂停再绑定未解决此项。软解Texture/GL对照正常。正式发布暂缓，保留私有草稿与完整失败证据；不将MuMu像素结果外推为真实ARM车机性能结论。
