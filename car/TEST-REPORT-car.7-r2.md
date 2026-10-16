# car.7-r2 验收结果

最终 APK SHA-256：`B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1`。

所有当前设备证据位于 [r2-B2EEDC14](evidence/car.7/r2-B2EEDC14/)。旧 D2A/F666 结果不是本候选的最终依据。

## 应用功能

| 验收 | 通过 | 失败 | 证据目录 |
|---|---:|---:|---|
| EXO/IJK 播放与生命周期 | 101 | 0 | `playback` |
| 原生色条像素门禁 | 8 | 0 | `pixels.json` |
| 逐页与当前播放器四入口 | 49 | 0 | `pages` |
| 基础 UI、双模返回、弹窗 | 58 | 0 | `ui` |
| Android QuickJS 异步契约 | 14 | 0 | `quickjs` |
| 35站搜索压力补跑 | 12 | 0 | `searchqueue` |

搜索初轮因 R8 内联 getter 导致工具反射失败；工具改为映射后的真实字段读取，同一产品 APK 补跑通过。未改生产代码来制造可反射调试入口。

宿主回归全部退出0：164 XML、11预设、44配置、88 SDR/HDR场景、1936状态比较、738返回策略、1237搜索会话断言、真实OkHttp取消隔离、QuickJS等待契约及20项Python HTTP。

## 真实外站，不隐藏失败

| 验收 | 通过 | 失败 | 说明 |
|---|---:|---:|---|
| Python真实站点 | 12 | 1 | 枫叶内容为空 |
| ApiConfig→Python产品链 | 12 | 1 | 剧踪已知分类片名阳性搜索未命中 |
| xiaosa JAR/QuickJS | 10 | 2 | 枫叶首分类空；爱动漫搜索超时 |
| 爱动漫单站复核 | 4 | 1 | 根因链为TimeoutException |

- clun配置97站/44个PY站。本轮豆瓣分类20、剧踪42、苹果21、瓜子30；苹果真实EXO播放1280×720，state=3，位置162→2680ms。
- 枫叶PY：重复HTTP200/138B/HTML，正文特征命中403 Forbidden；Java与Python传输摘要一致，没有内容卡片。不是缺Python解释器或gzip未解码。
- 剧踪“潜伏”搜索12项，分类首个已知条目的完整片名搜索0项。苹果与瓜子的阳性ID/片名命中正常。
- xiaosa热播JAR：分类12、详情1、阳性搜索1。枫叶JAR搜索7但首分类0。未修改订阅、Spider URL或身份标签。
- 爱动漫QuickJS：分类48、详情1；搜索Promise到30秒截止。不能由本地异步14/0推导该外站搜索已完全兼容。

## 环境与恢复

MuMu6.8.7.0 / Android15，实际display/input矩形2880×1620、density160、横屏锁定、无override，静音。每轮重算已安装base.apk SHA。

完成轮次的过滤日志：FATAL EXCEPTION=0，PRODUCER_PRESERVE_FAILED/PRODUCER_RECREATED=0。播放阶段同时验证位置、producer对象身份和像素，不只验证进程存活。

原Hawk2.xml在重启前与guest私有原备份cmp一致；测试包卸载；干净启动PID20439→20439、FATAL0、已安装SHA不变。实例0租约已释放并关机，MuMu主程序保留。其他实例未操作。

## 报告与边界

用户报告在工作区 `tvbox-os-car/report-car.7/`，Word/PDF/单文件HTML均包含22张原生截图与修改标注。PDF27页、22个截图页面；标题与目录结构有效、无空页，1440和390宽度下无横向溢出。

全局包名检查因其他项目`com.carhd.fileopener`未登记而退出1；TVBox r2路径及摘要已登记，未修改无关项目。

模拟器不证明真实OEM全屏、8295长期性能、硬件MEMC、DAC音质或全部外站可用。本轮不移动旧标签、不自动正式发布。`deliveryReady=true`仅表示可交付候选，不能读成全部外站验收通过。
