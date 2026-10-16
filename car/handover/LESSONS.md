# 本次任务的问题、坑与防复发规则

本文区分已经证实的缺陷、工具误判和仍未定位的外部失败。不要把“下一轮可尝试”写成已解决经验。验收基线为B2EEDC14候选，完整SHA见`baseline.json`。

## 01. 状态在播放，实际像素却是绿块

现象：IJK硬解position/source帧继续增长、生命周期断言通过，但全屏截图失真。失败尝试：无null间隙地换Surface、只保留EGL pause context或只看frame计数，仍不能证明画面对。

根因证据：全屏重挂GLSurfaceView引起EGL/consumer重建。修复：IJK从prepare起稳定GL producer；旧context销毁前用1×1 pbuffer current后detach原SurfaceTexture，新context attach同一对象。跳过setSurface必须同时比较player和surface。

防复发：每次渲染改动检查同Surface/SurfaceTexture、同decoder、位置前进和八图RGB像素门禁，不调阈值凑通过。关闭增强走identity passthrough，不全局禁IJK。对应skill：`glsurfaceview-video-lifecycle`。

## 02. dispatchDraw里同步退役View

现象：`ViewGroup.dispatchDraw`出现NPE。根因：render绑定回调中同步替换/移除同级View，发生在框架draw遍历中。

修复/检查：VideoView通过主Handler.post分发onBound/onFailure，跨过当前draw遍历再退休；不要为了“回调已在主线程”删掉post。播放器setSurface必须在主线程，GL纹理生命周期在GL线程。

## 03. 横向截图不等于横向显示/input几何

早期d240看起来可接受，却无法复现真车d160的巨大/裁剪界面；池设置还可能留下wm覆盖或纵向底板。仅PNG宽高不能证明触摸坐标正确。

防复发：设备操作前先检测MuMu版本；锁横屏、d160、无override，运行verify_car_display.sh，要求显示/input真实Rect(0,0–2880,1620)。背景铺满和可操作安全区域分开。模拟器像素不冒充真实物理毫米标定。

## 04. MuMu sh运行Android命令时缺classpath/namespace

现象：input/am/uiautomator可能报libnativeloader或BOOTCLASSPATH错误，并非应用崩溃。

防复发：通过插件sh执行共享UTF8/LF脚本，用system_server mount namespace与必需classpath；不要在宿主拼复杂引号，更不要用手写adb绕开租约。包装脚本即使工具code0，也需独立结果门禁。相关skill：`mumu-android-shell-namespace`。

## 05. QuickJS Promise把自己的回调堵在后面

根因：JS单执行器内部同步等Async.Result，同时network/timer回调也提交到该执行器。仅延长超时不能修复互等。

修复：JS执行器只返回Async.Result，调用者等待统一deadline；超时/中断取消归属该调用的Call/timer，native引用只在owner executor释放。防复发：本地同步、Promise、链式timer、异步HTTP、reject/throw、请求超时、30秒总截止、取消后恢复全部验收。外站超时不能反过来单独证明通用executor仍有死锁。

## 06. 搜索UI超时了，后台任务还在

问题：先execute后登记Future有竞态；只删pending会留下任务；普通搜索缺pump时尾站永不启动；重复轮次共享tag会误取消其他页面。

修复：FutureTask先注册再提交；session/source独立scope/tag；单次结算；有界共享工作池；拒绝和超时后由pump推进。35站/32慢站本地素材验证尾站、空结果、无效结果与退出取消。

防复发：看actual active/peak和尾站数据，不仅看15秒状态文字。忽略中断的第三方JAR不能被安全强杀，只能限制并发、隔离会话和丢弃迟到结果。

## 07. R8 getter消失，被误当产品故障

具体失败：release中的`getData()`、`getVideoView()`可能内联消失；反射NoSuchMethodException使测试提前结束。

修复：先查最终R8 mapping，读取保留下来的实际字段，如adapter.mData、fragment.mVideoView。不得为反射加产品debug入口。初轮35站失败保留在`searchqueue-initial-tool-failure/`，同APK补跑12/0。

防复发：测试包必须对应最终mapping；方法不存在时分类为harness失败，不能称应用搜索崩溃。`am instrument -e`是字符串参数，不用Bundle.getBoolean读取true。

## 08. 按钮“应该显示”不代表真实触摸能点到

控制条自动隐藏，调用错误的show或过期坐标会得到不完整页面。修复：真实触摸唤出bottom_container后再定位四入口，检查可见性、113px高度、偏好/renderer/decoder/session。

详情Back还有遥控器焦点分支，第一次只移动焦点不离页。r2用inAppBackStep只绕过应用内返回的焦点分支；实体Back行为保留。防复发：全屏→详情→上一页逐级验证，不只检查按钮存在。

## 09. HTTP 200但返回的是403页面

具体证据：枫叶内容请求HTTP200/138B HTML，403 Forbidden特征；Java与Python响应摘要一致，分类和搜索无条目。

失败推断：早期把nginx字符串也算access_denied，分类不够准确；后续细分403/404/压缩/HTML标记后才确认。

防复发：状态、正文类型/长度/摘要、明确错误页标记和解析计数一起看；不保存正文、cookie或私有headers。不要全局给“小200”请求强制重试，不伪造列表。静态8个分类不等于真实内容成功。

## 10. 阳性搜索样本与外部依赖边界

固定关键词为空不代表搜索解析失败；类别中已知ID/片名是另一条阳性证据。但已知片名未命中仍要保留，不能换一个成功关键词就抹去失败。

本轮剧踪“潜伏”12项、首个已知标题0项；苹果/瓜子阳性命中。爱动漫分类48/详情1、搜索到30秒截止。这些尚未完全解决，不把本地QuickJS14/0当全部外站兼容证明。

## 11. JAR身份门、壳兼容与缓存混在一起

改包名/label/签名DN可能触发JAR五秒后killProcess；白壳protected-loader误分类也会全站加载失败。配置与JAR各自缓存，?v=只改配置并不改变派生JAR URL。

防复发：身份、loader、响应大小/内容和缓存分别取证；保留com.github.tvbox.osc.jun/TVBox。不要置空spider或全局禁止自杀来掩盖根因；详见原工作台14号identity gate参考。

## 12. 后台启动超时被重复执行

插件8秒timeout但已返回launched PID，guest仍可能工作。重放会重叠instrument、互相清日志。防复发：单次后台启动、唯一tag、轮询invoke.rc/result.json；每批一个instrument。host helper可能被宿主回收，用隐藏管道捕获真实exit并finally清理owned进程，不通过弹控制台观察进度。

代理也要有边界：本轮两个分离修复任务持续较久，停止继续写入后才统一构建。以后明确文件所有权、停止条件与输出，不在代理写源码时构建，不反复做广泛审查。

## 13. 偏好备份文件名与权限

错误：恢复脚本假定hawk.xml，实际是Hawk2.xml；门禁提前停止，未覆盖原偏好。修复：只读列出文件名/层级，不输出内容；完整目录恢复、chown、restorecon、cmp后再启动。

防复发：原备份留guest私有目录，测试后副本分开，不拿测试后的设置覆盖原备份。恢复→卸载test APK→稳定PID/FATAL0/安装SHA核对→release，仅操作自己租约，主MuMu保留。本批已完成恢复与release，不复用旧owner。

## 14. PowerShell引号、GBK与工具退出码

错误：嵌套Python exec/引号在PowerShell被拆开；默认GBK读UTF8 JSON失败；输出包含替代字符时再次编码异常。修复：结构化JSON读取、显式UTF8；复杂命令写脚本；诊断输出可ensure_ascii。

防复发：不混写POSIX语法；参数用数组；每个阶段记录实际exit与业务结果。不把tool ok、shell0或INSTRUMENTATION_CODE:0当断言全通过。

行号编辑也发生过偏移：替换条件时误覆盖了下一行raise，重读局部后立即修正。防复发：Edit前基于最新Read/Grep锚点，多个操作使用原始行坐标并优先降序，改条件时一起检查所属代码块；成功写入后也要语法/测试校验，不因工具返回modified就认为程序正确。

## 15. Word渲染“文件存在”不等于报告正确

本轮实际坑：COM Hwnd为空；ByRef调用要求[ref]；前一次新建隐藏Word未取得ownership导致残留；Word保存后Heading1样式ID变成1。

修复：启动前后进程差集唯一确认owned实例，未归属不能结束用户已有Word；ReadOnly/ByRef参数按接口传递；finally关闭文档与owned进程。标题结构按outlineLvl而非样式ID字面值；目录用field并更新保存。

验收：XML/嵌图/标题、PDF27页22图且无空页、HTML1440/390宽22图加载无溢出，实际预览主页/播放器/边缘状态。不能把结构检查当所有页面都人工看过。

## 16. 交接包有源码，但Git还是旧版本

本轮发现：r2修复未提交，旧包仅是工作树快照；新AI直接clone会回到D2A。上游*.md规则还可能把新增文档和skill漏掉。

防复发：显式放行文档、显式暂存任务路径、保留用户删除；提交后push main+新交接ref，ls-remote核对；打包只读Git blob，清单绑定完整commit。不要移动旧v1.0.0-car.7，不把标签object ID和其peeled commit混读。

## 17. 旧目录层级和scratch脚本不可迁移

旧命令假定workspace/tvbox-os-src，Git克隆根实际上已有app/player；测试依赖还硬编码旧绝对classpath。修复：根START-HERE统一目录，host/pack/verify工具入Git，显式输出到scratch和依赖参数，包内提供构建Python而不传播本机私钥。

验证：新鲜clone上执行只读校验与核心host检查；ZIP单独解压不覆盖Git。源码清单使用Git blob处理LF/CRLF差异；SHA不匹配时停下说明差异，不reset用户文件。

## 18. Skill只存在当前机器，下一AI仍会重踩

防复发：本机skill更新后把选定版本作为Git快照导出，附版本摘要/安装说明，先比较再合并，不覆盖更高版本。通过skill_manage修改前单独Read当前SKILL.md；本轮并行Read没有被read-before-write门禁识别，重读后才补丁。

技能应用表见`SKILL-CHANGES.md`。报告与交接源同时受版本控制，生成后二进制与源码commit通过SOURCE-STATE关联。没有执行的新环境重建或真车测试必须保留为未验证。
