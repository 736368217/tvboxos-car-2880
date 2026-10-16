# 本地Skill更新与迁移

本次先完成应用验收和逐页报告，再将已验证经验落到本地skill；这轮又补齐Git接手一致性。没有用未解决的外站失败撰写“已解决”步骤。

| Skill | 本次维护内容 | 防止的问题 |
|---|---|---|
| `glsurfaceview-video-lifecycle` | 补充IJK稳定producer、旧context pbuffer detach/新context attach、player+surface联合身份、post跨draw遍历、实际版本和源码根提示 | 状态/帧数正常却绿屏；新player误跳过绑定；误把模拟器泛化为所有驱动 |
| `android-native-acceptance-evidence` | 修正重复编号；区分通用QuickJS契约与外站超时；真实Future/Call取消；R8字段读取；HTTP200错误页；一次后台启动；偏好文件名/权限恢复；Git来源核对 | 假全绿、无限后台任务、不确定重放、恢复错文件、只存在本机的测试 |
| `git-workspace-handover` | 新增完整Git+ZIP+skill交接规程，显式暂存、远端ref、只读校验、新鲜clone、报告结构与经验迁移 | ZIP新/Git旧、*.md被忽略、旧目录命令失效、私钥传播、覆盖用户工作 |

## 快照位置

`skills/<name>/SKILL.md`是维护后选定版本，不是新的一套系统技能目录。`skills/index.json`记录每文件CRLF→LF后的SHA-256及大小。可在Git中审阅，也随交接ZIP分发。原机`~/.agents/skills`已实际更新；接手机器不自动安装。

## 接手时如何使用

1. 先读这些快照，按任务信号选择，不需要全部加载进上下文。
2. 若是同一电脑，本机Settings中的技能可能已经更新；先比较SHA/内容，不重复覆盖。
3. 若是另一台电脑，先读取本地同名SKILL.md，再用skill_manage patch合并差异；本地没有时才create，附完整When to Use / Procedure / Pitfalls / Verification。
4. 不覆盖新版本，不创建项目自己的MuMu池，不将原目录路径写成所有机器的默认路径。
5. 修改本地技能后，在源码根运行`python -B .\car\tools\export_handover_skills.py`，检查导出index，再按MAINTENANCE更新Git和包。

Skill里的示例与项目当前目录层级要核对：旧APK工作台命令有tvbox-os-src前缀，实际Git clone根没有；项目START-HERE中的根目录命令优先。Skill是流程约束和经验，不是旧进程、旧租约或旧测试结果的当前状态证明。
