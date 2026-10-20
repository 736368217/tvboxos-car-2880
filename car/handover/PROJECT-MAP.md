# 项目地图与阅读顺序

## 模块

| 模块 | 职责 | 优先阅读 |
|---|---|---|
| `app` | TVBox页面、订阅解析、Spider路由、EXO/IJK封装、车机返回与设置 | `api/ApiConfig.java`、`viewmodel/SourceViewModel.java`、`car/` |
| `player` | 原视频View/控制生命周期、Texture/GL输出、画面预设、SGSR1/时域处理 | `player/VideoView.java`、`render/GlEnhanceRenderView.java` |
| `quickjs` | Android QuickJS native上下文桥；不能在JVM上冒充native JS执行 | 模块桥类和`app/.../crawler/js/`调用链 |
| `pyramid` | Python嵌入层、基类与Requests兼容传输 | `src/python/app.py`、`base/spider.py`、`tvbox_http.py` |
| `car` | 本版设计、验收、fixture、宿主/原生测试、报告数据与交接工具 | `TEST-REPORT-car.7-r2.md`、`tests/native/`、`handover/` |

`settings.gradle`仅含以上四个Gradle模块。主体UI仍为Java，Python flavor增加Spider运行时；不要将任务误解为重写Python UI。基础SDK33、JDK17、Gradle wrapper7.5，Python构建解释器3.8。详细身份见`handover/baseline.json`。

## 订阅到内容

```text
设置保存订阅URL
  -> ApiConfig解析配置和SourceBean（延迟初始化/按需预热）
  -> SourceViewModel按源类型获取分类/列表/详情/搜索
  -> ApiConfig.getCSP
       JAR: JarLoader / ProtectedInitJar
       JS: JsLoader -> JsSpider -> Async / Global / Connect
       PY: pyLoader -> PythonLoader / PythonSpider -> pyramid
  -> 页面adapter、详情与播放器
```

Python网络路径默认原生Requests；特定异常/状态才经`tvbox_http.JavaHttpAdapter -> PythonHttp.java -> app OkHttp`回落。Requests仍掌管准备请求、重定向与cookie，不在活跃session上并发替换adapter。脚本原子下载避免半文件缓存。完整URL/headers只留目标内存，不写报告。

TVBox Spider可能检查包名/label/签名DN。原`com.github.tvbox.osc.jun`与`TVBox`不可随意改；上游白壳可能把普通JAR错分到protected loader。这些问题不能靠置空spider或全局拦killProcess掩盖。配置缓存和JAR缓存独立，不把配置?v=当成JAR更新。

## 播放链

```text
DetailActivity / LivePlayActivity
  -> PlayFragment / MyVideoView / VodController
  -> app PlayerHelper选择EXO或IJK
  -> player VideoView管理解码器、render和Surface绑定
  -> TextureRenderView 或 GlEnhanceRenderView
       OES输入 -> 可选颜色/SGSR1/时域链 -> EGL窗口
```

IJK在prepare前使用稳定GL consumer，增强关闭时是identity输出，不换producer。GL窗口Surface与解码器的Surface不是同一个对象。EGL重建使用pbuffer+原SurfaceTexture detach/attach，主线程绑定player，GL线程管理纹理。先读这条边界再修改“全屏/恢复”。

EXO允许普通Texture与增强GL按配置接管；替换/退休View不能同步发生在dispatchDraw遍历中。所有这些通过播放101/0和原生色条8/8共同验证。时域混合不等同光流MEMC；软件输出节奏不等于实机稳定60fps。

## 页面与返回

11个Activity：`HomeActivity`、`DetailActivity`、`SettingActivity`、`CarAdvancedActivity`、`SearchActivity`、`FastSearchActivity`、`HistoryActivity`、`CollectActivity`、`LocalFileActivity`、`PushActivity`、`LivePlayActivity`。主页`UserFragment`承载推荐/“我的”内容，分类是`GridFragment`；没有另一个独立“我的Activity”。

`BaseActivity`把`InAppBackHostLayout`附到当前DecorView。`InAppBackButton`、`FloatingBackBallView`、`EdgePullBackView`、`BackNavPolicy`处理配置与行为。先Fragment栈，再分类/主页tab，其他页面Activity.onBackPressed；全屏先退全屏，首页根只提示。详情的inAppBackStep绕开原D-pad焦点分支，但不更改实体Back。

默认返回热区在本轮环境中243px；实际设计值与`BACK-CAR-SCALE.md`分开阅读。安全边距与背景填充分离。不能从“截图2880×1620”推导显示/input真是横屏，先做display qualification。

## 搜索链

`SearchActivity/FastSearchActivity`维护UI轮次、待启动和pending站点；`SourceViewModel`提供有界线程池与HTTP调用；`SearchSession.Scope`拥有每源Future/Call和唯一对象tag。先注册再提交；拒绝放回waiting由pump推进；一次结算；关闭后拒绝迟到结果。详情自动换源使用独立detailFallback namespace和高水位token。

第三方JAR忽略中断无法安全强杀同进程线程。正确承诺是上限受控、UI收敛、迟到结果丢弃，不是“任意远端代码都立即消失”。35站fixture覆盖32个慢站与尾部正常/空/无效结果，不必去外站制造压力。

## 验收文件怎么读

- `evidence/.../identity.json`：该轮实际安装APK SHA、PID、tag；不能按文件名猜身份。
- `result.json`：逐断言pass/failed；instrument shell0仍可能failed>0。
- `pixels.json`：色条采样中位数，能检出状态正常但绿屏的缺陷。
- `pages/*.json`：原生节点位置。包括不可见的ViewPager子树，不能把屏幕外节点当截图重叠。
- `python-results.json` / `subscription-results.json`：传输与业务计数、阶段、脱敏根因，不是raw响应。
- `summary.json`：历史验收汇总；交接提交以ZIP中的SOURCE-STATE为准，不能用旧scratch绝对路径识别当前工作区。

先做无设备host检查。需要重新跑Android时，每批一租约、每次操作前check、插件优先、单instrument、实际显示/input与安装SHA门禁。测试结束恢复原偏好与权限、卸载测试包、干净启动、release。参考`LESSONS.md`和`HANDOVER-car.7-r2.md`。

## Trial source memory

See [`TRIAL-SOURCES.md`](TRIAL-SOURCES.md) for the user-requested clun and xiaosa subscription URLs, sanitized probe identities, and acceptance boundaries. Raw source bodies remain outside Git.
