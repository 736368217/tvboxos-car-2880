# 新AI接手：第一轮任务单

用户将从Git同步代码，并提供交接ZIP。目标是正确继承已验收版本和经验，不从旧候选重新定位已经修好的问题。

## 接手前的事实核对

1. `git rev-parse --show-toplevel`确认实际根；这里应直接包含app/player/quickjs/pyramid。
2. 读根`START-HERE.md`与`AGENTS.md`，再读PROJECT-MAP、HANDOVER、LESSONS。
3. 核对交接ZIP外侧SHA，使用`car/tools/verify_handover.py --repo . --bundle <ZIP>`对完整commit、Git blob、APK和清单进行只读验收。有网络时加`--check-remote`。
4. 记录当前HEAD、handoff_ref、APK SHA及已知失败。若不一致，先解释差异，不覆盖解压、不reset或移动旧标签。
5. 第一轮跑core-only host检查与test_handover。需要JDK17和宿主requests；完整host还需已有Gradle缓存/桥classes。缺依赖单列，不把无前置条件当应用回归。

以上不需要模拟器、安装新软件、读取签名文件或联网运行远端脚本。

## 已完成，不要重新造

- IJK绿块已通过同producer的EGL detach/attach修复；最终像素8/8、播放101/0。
- 双模返回、两行标题、安全区域与全部页面49/0+58/0；四个播放快捷入口已真实点击验证当前renderer/decoder/session。
- QuickJS不再在自己的executor中等自己的回调；本地Android14/0。
- 每源/每轮搜索scope、先注册FutureTask、有界worker和pump，35站压力12/0。
- 27页、22图报告已生成并校验，文件在包内；无需先再做一次相同报告。

## 尚需继续的明确事项

| 事项 | 已知证据 | 下一步边界 |
|---|---|---|
| 枫叶PY空内容 | HTTP200/138B/403特征HTML，Python与Java相同 | 固定脚本SHA与阶段，验证远端响应；不要全局改200回退或伪造列表 |
| 剧踪已知片名不命中 | 分类42、普通词12、首条完整片名0 | 查源脚本/索引与搜索规范，保留未命中证据 |
| xiaosa枫叶首分类空 | JAR搜索7，首分类0 | 与PY同名源区别分析，先看脚本/身份/缓存，不直接改订阅 |
| 爱动漫搜索超时 | QuickJS分类48、详情1、搜索30秒deadline | 找远端脚本搜索路径；本地矩阵通过不代表该路径已兼容 |
| 真车验收 | 模拟器2880×1620/d160通过 | OEM全屏、8295持续功耗/温度/帧率、真实触摸与音质尚未证明 |

不要自动执行所有方向。先确认用户本轮要解决的具体事项，再做对应最小变更和同SHA相关复测。第三方脚本修改、应用身份变化、发布正式版是新的明确动作。

## 当前资源状态

上一批MuMu实例0已release并关机，main app保留，其他实例未操作。不要复用旧owner。新测试批通过绝对全局池申请新租约，每次操作前check，插件工具优先、主机/guest静音、单instrument，最后恢复prefs并release。

上一批原Hawk2.xml恢复cmp一致、测试包卸载、干净启动PID20439稳定/FATAL0。不要把历史PID或旧shared目录当当前运行实例。

## 技能继承

`skills/`是本次确认过的版本快照，见SKILL-CHANGES。先读取，比较本机同名skill再合并，不盲目替换。主要三份：glsurfaceview-video-lifecycle、android-native-acceptance-evidence、git-workspace-handover。

## 建议给用户的首条接手汇报

简要说明已读文档、实际Git commit与包是否一致、本地已通过项、未解决的源/真车边界，以及当前是否持有设备租约。不要说“所有功能/全部外站已正常”，也不要因为旧car.7报告写7/8而忽略r2的8/8。
