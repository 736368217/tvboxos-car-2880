# TVBox car.7-r2 交接：先读此文

用户要求先完成实现和逐页报告，再做交接。本交接文件在27页/22图报告完成并验证后编写。当前应用端功能门禁通过；源特定失败和真实车机验收边界仍保留，不要把它们改成全绿。

## 1. 唯一当前基准

- 当前产物：`tvbox-os-car/TVBox-1.0.0-car.7-r2-python-signed.apk`，52,676,888B。
- SHA：`B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1`。
- 包名`com.github.tvbox.osc.jun`、标签`TVBox`、code8/versionName1.0.0-car.7不变。
- 源码基线为`fa297472a608dcc37ee23bcdefafe7107f721fae`；r2与交接工具使用新锚点`handoff-car.7-r2-20261017`。交接ZIP的`SOURCE-STATE.json`记录完整源码commit，并逐文件核对Git blob。接手以该交接ref为准，不再使用未提交工作树快照。
- 旧`v1.0.0-car.7`及GitHub私有草稿仍是D2A候选。上一正式版仍为car.6。禁止让旧草稿附件冒充本r2 SHA。
- 用户删除的`car/cartest.mp4`、`car/cartest-lowsat.mp4`保留删除，不要恢复。媒体由fixture工具生成到scratch。

先看：`car/README.md`、`car/BUILD-INFO-car.7-r2.md`、`car/TEST-REPORT-car.7-r2.md`、`car/reports/car.7/report-data.json`。完整用户报告在工作区`tvbox-os-car/report-car.7/`。

新工作区先读仓库根`START-HERE.md`、`car/handover/PROJECT-MAP.md`与`LESSONS.md`。Git源码根已经包含app/player，不再多套一层tvbox-os-src。交接包单独解压，不覆盖Git；skill维护说明见`car/handover/SKILL-CHANGES.md`。

## 2. 已完成与证据

最终同SHA的设备门禁：播放101/0，像素8/8，逐页49/0，返回/UI58/0，QuickJS14/0，35站搜索压力12/0。证据集中在`car/evidence/car.7/r2-B2EEDC14/`。

报告已逐页覆盖全部11个实际Activity、主页分类子页、播放器及主要弹窗，并补充悬浮球、水滴、胶囊状态。Word已由Microsoft Word更新目录和保存，PDF27页；22张图，标题齐全、无空页。HTML在1440×1000、390×844下22图均加载、无横向溢出。不要再把“要做报告/仍有IJK绿屏”当未完成任务。

宿主检查：164 XML、11预设、44配置、88 SDR/HDR场景、1936转换比较、738返回策略、1237搜索会话断言、真实OkHttp取消隔离、QuickJS等待契约、20个Python HTTP用例均通过。新增`QuickJsRuntimeAcceptance.java`是可扩展独立素材；本轮Android14项由`native/Car7Acceptance.java`实际执行，不要把未执行的扩展测试也计为通过。

## 3. 不可回退的修复机制

### IJK / EGL producer

文件：`player/src/main/java/xyz/doikki/videoplayer/render/GlEnhanceRenderView.java`；同目录factory/config与`VideoView.java`；应用`util/PlayerHelper.java`。

IJK从prepare开始用稳定GL consumer。增强关闭时只是identity OES passthrough，不跑FBO/SR/时域处理。EXO可使用普通Texture并按当前配置切换，不能把两个引擎的策略混为一谈。

根因不是“解码器没有继续播放”。旧D2A的状态、position、source帧可正常增长，但全屏重挂GLSurfaceView后更换consumer会产生绿块。r2通过自定义EGLContextFactory，在旧context销毁前创建1×1 pbuffer，使旧context current，detach原SurfaceTexture，再销毁context；新onSurfaceCreated让同一SurfaceTexture attach新OES texture。Surface对象不换。

`mBoundPlayer`和surface identity同时匹配才跳过setSurface。切换player时不能只比较surface。主线程调用播放器setSurface；GL线程处理纹理。涉及同级view退休的onBound/onFailure回调通过Handler.post跨过dispatchDraw，避免ViewGroup遍历中同步修改子树。

不能只开`setPreserveEGLContextOnPause(true)`就宣称覆盖全屏重挂。不能把像素门禁阈值调宽来“修复”绿屏。不要回退成全局禁增强或拦截所有killProcess。

### QuickJS异步运行时

文件：`app/src/main/java/com/github/catvod/crawler/js/{JsSpider,Async,Global,Connect,Req}.java`。

JS单线程只执行方法并返回Async.Result；等待放到调用线程。绝对不能恢复`executor.submit(() -> Async.run(...).get()).get()`，否则timer/network回调仍排在被占住的同一executor后。

每次调用有统一30秒deadline（含排队/执行/Promise等待），支持中断。Global按Async.Result归属登记Pending timer/Call；超时/取消先取消Java资源，native JSFunction/JSObject只在JS executor释放。上下文destroy前串行清理。异步对象跨线程传递前snapshot为普通Java值/JSON。

network tag为每个Spider独立Object，不用字符串全局tag误取消其他实例。Req.timeout传到实际OkHttp请求。不能只让等待者返回却留下无限timer/network引用。

### 搜索队列

文件：`SearchActivity.java`、`FastSearchActivity.java`、`viewmodel/SourceViewModel.java`、新增`util/SearchSession.java`。

FutureTask先登记再execute，避免即时完成或取消发生在登记之前。每会话/每源有唯一scope和requestTag；关闭、源取消后不能重新打开旧scope。HTTP Call和Future都绑定自己的scope，onCleared/退出/换词取消该实例的工作，不做全局OkGo或JsLoader.stopAll。

普通与快速搜索都有确定的pump：周期2秒、推进3秒、单站15秒截止。共享有界工作线程池为API35最多24、API30+18、其他12；SynchronousQueue拒绝时放回等待队列由pump重试。type3结果也只允许一次终结；空值/无效值要结算，迟到结果不得污染新token。详情自动换源使用独立的detailFallback namespace和单调round/batch token。

第三方JAR若忽略线程中断，只能限制并发和丢弃迟到结果，不能承诺在同进程内强制终止任意代码。不要重新用无限cachedThreadPool绕过上限。

### 返回与当前播放入口

`DetailActivity.java`的`inAppBackStep`使窗口内返回绕过遥控器“只移动焦点”分支；实体Back保持原行为。全屏先回详情，再一击离开详情。

返回控件位于DecorView，不是system overlay，不申请无障碍或全局按键权限。主页根只提示。布局安全区域与背景铺满分开。球拖动/磁吸、空闲唤醒、水滴/胶囊、CANCEL等已有58项原生证据，不需要重做另一套控件。

`VodController`四入口经真实触摸验收：先唤醒隐藏控制条，再点画面/超分/补帧/音效。验证偏好、当前renderer、decoder对象和audio session，而非只看标签变字。

## 4. 尚未完全解决的源特定问题

1. `https://clun.top/box.json`本轮97站/44PY站。枫叶脚本加载成功但分类/搜索为0。内容响应是HTTP200、138B HTML，明确命中403 Forbidden特征，Python与Java传输摘要相同。不要全局对小200响应强制fallback，更不能改出假内容通过。
2. 剧踪分类42/详情1/“潜伏”搜索12，但已知分类首项完整片名阳性搜索0。苹果、瓜子同类阳性检查通过。可能涉及源脚本/索引/内容行为，尚未定位得足以修改第三方脚本。
3. xiaosa热播JAR分类12/详情1/阳性搜索1；枫叶JAR首分类0但搜索7。保留订阅与原spider地址，不置空、不用改名碰身份白名单。
4. 爱动漫QuickJS分类48/详情1，搜索到30秒deadline。复核异常链`ExecutionException→InvocationTargetException→IllegalStateException→TimeoutException`。这是已知剩余脚本调用失败；不能宣称它搜索已修好，也不能仅由timeout再次断言通用executor互等。
5. 真车OEM全屏策略、SA8295P持续性能/功耗、硬件MEMC/DAC音质、所有外站不在本次模拟器证据内。

已验证苹果真实EXO网络播放1280×720、state3、位置162→2680ms。不要把它推广为所有站点播放可用。

## 5. 新工作区构建与复测

以下命令从实际源码仓库根执行。JDK17/SDK和Gradle缓存留在本机，不随ZIP复制；交接包单独解压后，构建Python3.8副本在其`tools/python38-build/`。用包的实际解压路径替换示例。解释器已迁移启动验证；新路径完整Gradle重构建仍是不同于host检查的一项验收。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:TVBOX_BUILD_PYTHON = (Resolve-Path ..\tvbox-car-kit\tools\python38-build\python.exe).Path
$env:GRADLE_USER_HOME = 'D:\gradle-cache'
.\gradlew.bat --no-daemon --max-workers 1 '-Dorg.gradle.jvmargs=-Xmx1536m' :app:compileJavaReleaseJavaWithJavac :app:assemblePythonRelease --console=plain
```

签名私钥不随包携带；在原电脑用现有受控签名环境。新APK变SHA后须重建native harness，不能借用旧证据：

```powershell
python -B .\car\tests\native\build_native.py --out "$env:PI_SCRATCH_DIR\car7-native"
python -B .\car\tests\test_python_http.py
python -B .\car\tests\build_search_fixture.py --out "$env:PI_SCRATCH_DIR\car7-search-fixture"
python -B .\car\tests\verify_car7_pixels.py .\car\evidence\car.7\r2-B2EEDC14\playback --sha256 B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1 --lifecycle --output "$env:PI_SCRATCH_DIR\pixel-check.json"
```

最后一条只复核已归档PNG，不等于重新安装验收。当前native模式：`ui`、`pages`、`playback`、`quickjs`、`searchqueue`、`python`、`subscription`。run_guest参数为`mode tag expectedSha sharedOut [api] [homeSite] [sites]`。

UI素材为`car/tests/fixtures/car7_ui_probe.js`、`car7-ui.json`、`car7-live.m3u`，设备本地服务`http://127.0.0.1:9978/file/TVBoxCar5/`；运行包装为`car/tests/native/env.sh`。长视频可用prepare_car6_fixtures.py生成到scratch，再把上述car7素材放入同一fixture输出目录。不会恢复用户删除的旧car/cartest文件。QuickJS素材为quickjs_runtime.js，search JAR由build_search_fixture.py在scratch生成，不进产品或Git。Windows克隆通过.gitattributes保留原生sh为LF；共享目录中脚本也必须保持LF。

## 6. 模拟器纪律与已知坑

唯一全局池：`$env:USERPROFILE\.mumu-pool\mumu-pool.py`，不能复制到新工作区，不能使用相对scripts版本。每批只acquire一次、release一次，每次操作前check，所有设备操作通过hawchou.mumu-player插件。不要复用本会话旧owner声称持有租约。本批实例0已释放关机，主程序保留；其他实例不动。

```powershell
$pool = "$env:USERPROFILE\.mumu-pool\mumu-pool.py"
python $pool acquire --owner <new-conversation-id>
python $pool check --owner <new-conversation-id> --index N
python $pool heartbeat --owner <new-conversation-id> --index N
python $pool release --owner <new-conversation-id> --index N
```

车机门禁为真实display/input Rect(0,0–2880,1620)、d160、固定横屏、无override；不要只看横向PNG。先mumu_detect确认实际版本，本批6.8.7.0，不套用旧6.8.1缺陷。默认主机/guest静音。

复杂命令放UTF8/LF共享脚本，用env.sh进入system_server命名空间，仅导入必需三个classpath。主机耗时进程使用CREATE_NO_WINDOW、隐藏管道及try/finally回收。不要重叠两个instrument。插件8秒超时返回launched PID不代表guest任务没启动，先查invoke.rc/result.json，不重放。

`INSTRUMENTATION_CODE:0`、MuMu工具ok、shell0不证明断言通过。必须查result.failed和invoke.rc。部分MuMu包装层会吞guest退出码；标记、结果文件和具体断言必须齐全。

release getter可能被R8内联：先查最终mapping，读取真实字段（如adapter.mData、fragment.mVideoView），不要重复猜方法或为测试往产品加入口。第一次35站工具失败就是这个原因，同APK补跑12/0。

偏好原备份只放guest私有目录；先列文件名，不假定hawk.xml，本机实际Hawk2.xml。恢复整个目录、属主和访问标记，cmp不打印内容，测试包卸载，干净启动再释放租约。本轮已完成：PID20439稳定、FATAL0、SHA不变。禁止把偏好、密钥文件或原始远端正文写到外部报告。

全局包名台账仍是原APK工作台的唯一权威。com.github.tvbox.osc.jun属于同一tvboxos-car升级，不能分配给其他APP。新工作区不要自建不共享台账。全局checker当前被其他项目com.carhd.fileopener未登记阻断；TVBox r2路径已登记，不应越界修改那个项目。

## 7. 下一轮的有限目标

用户未提出新功能前，不重做已经通过的UI/播放体系。优先在真实车机安装本r2并复核OEM全屏及持续性能；若继续处理某个源，先固定该脚本SHA、请求阶段、阳性样本和错误分类，区分通用运行时与源逻辑。仅在有具体证据时修改对应边界，并在同一最终SHA上复测。

保留旧失败证据。修改订阅/身份/签名或发布正式版本属于新的明确动作，不自动移动旧标签。私钥和原始远端日志不入Git。源码commit、交接ref、ZIP源文件与APK分别校验，维护流程见`car/handover/MAINTENANCE.md`。
