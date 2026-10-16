---
name: git-workspace-handover
description: 用户将从Git克隆到新工作区并携带交接包时，固定提交与产物身份、迁移文档和技能、校验包与真实clone一致。
tags: ["自习"]
---

# Git工作区与交接包一致性

## When to Use

- 用户要新建项目/工作区、从Git同步并交给新的AI继续。
- 本地已有可交付APK/报告，但源码可能还在dirty worktree。
- 需要让经验和skill随项目迁移，避免旧标签/旧路径/仅存在scratch的工具误导接手。

Don't use for: 自动发布正式版、强制移动旧tag、覆盖用户已有修改、导出签名私钥或全局凭据。

## Procedure

### 1. 先核对真实来源

用PI bash从实际仓库根运行只读命令，不凭目录名或旧总结猜版本：

```powershell
git status --short
git rev-parse --show-toplevel
git rev-parse HEAD
git remote -v
git ls-remote origin refs/heads/main refs/tags/handoff-car.7-r2-20261017
```

远端URL若包含凭据，不输出其内容；改报remote名称。区分Git commit、annotated tag object/peeled commit、APK SHA和ZIP SHA。用户计划从Git同步时，未提交源码不能只塞进ZIP就称可顺利接手。

### 2. 明确三层交付

- Git：源码、测试、START-HERE/AGENTS、项目地图、问题复盘、验证与维护工具、选定skill快照。
- ZIP：已验收APK、报告二进制、可迁移构建解释器、同commit离线源码副本及只读参考。
- 清单：完整source_commit、handoff_ref、候选身份、逐载荷SHA/Git blob、排除项和未通过项。清单不哈希自己，ZIP摘要放外侧。

检查gitignore，不让*.md默默忽略交接入口。已有上游库/wrapper与下载的Spider JAR区别处理；不为了交付卫生越界删除原历史文件。私钥、私有prefs、raw远端响应不进入新提交或包。

### 3. 文档必须能迁移

写明实际clone根目录，避免把旧workspace/tvbox-os-src路径套两次。输出目录和缓存用CLI参数/JAVA_HOME/GRADLE_USER_HOME等，scratch脚本提炼进仓库。提供：

1. 项目目标、模块/数据流/关键文件地图。
2. 当前候选、通过门禁、失败分类和未验证边界。
3. 错误尝试、根因证据、修复机制与防复发检查。
4. 新AI第一轮只读核对与无设备host检查，设备操作另按租约。
5. 后续如何重建/复测/更新报告和交接包。

报告先完成并验证，再写交接；不要把handover当未完成功能的替代。外站数据失败、harness异常、客户端回归和像素失败分别记录。

### 4. 同步Git后才打包

显式暂存本任务文件；不使用git add -A顺带提交用户删除。检查staged diff和新增文件类型，运行相关测试，然后提交、推送main与一个新的handoff ref。示例中的ref是本案例固定值，别在其他案例复用：

```powershell
git diff --cached --check
git diff --cached --name-status
git commit -m "Car.7 r2: verified runtime fixes and reproducible AI handover"
git tag handoff-car.7-r2-20261017
git push --atomic origin main refs/tags/handoff-car.7-r2-20261017
git ls-remote origin refs/heads/main refs/tags/handoff-car.7-r2-20261017
```

先确认ref不存在；不强制移动/覆盖。若远端main已变化，保留用户修改并判断合并，不force push。推送后检查远端真实commit，不能以本地commit成功代替push成功。

### 5. 从提交读取源码并校验

本案例工具在car/tools，适用于实际clone根：

```powershell
python -B .\car\tests\test_handover.py
python -B .\car\tools\run_host_checks.py --core-only --out "$env:PI_SCRATCH_DIR\car7-host-core"
python -B .\car\tools\package_handover.py --delivery-root <artifact-parent> --out <outside-repo>\TVBox-car7-r2-handover.zip --python38 <existing-python38>
python -B .\car\tools\verify_handover.py --repo . --bundle <outside-repo>\TVBox-car7-r2-handover.zip --check-remote --out "$env:PI_SCRATCH_DIR\handover-check.json"
```

pack只读Git blob、拒绝未提交tracked修改、核对远端ref；Windows CRLF由Git处理，不靠修改任意源文件凑hash。verify必须只读，发现commit/hash差异就说明，不能自动reset/clean/覆盖解压。包中的源码是离线副本，不是覆盖当前Git的升级补丁。

### 6. 用新clone演练

在会话scratch新建路径克隆远端固定ref，验证HEAD、包、host core和校验器负向测试。输出全部放scratch。清楚区分“新clone接手检查通过”和“新路径完整Gradle/签名/Android/真车复测通过”。未做后者不能宣称。

更新本机skill后，把指定版本及support files导出到Git的skills快照并生成SHA索引。新AI先比较本机版本再合并，不盲目覆盖。

## Pitfalls

- ZIP有新源码但remote main还是旧候选，是交接失败，不是仅少一个文档。
- annotated tag输出不是commit本身，检查^{commit}/^{}，不要误称不匹配。
- 文件在本地不等于已入Git，尤其上游*.md与ignored生成物。
- 新clone可能包含原机用户本地删除的历史媒体，明确例外，不恢复原机或替用户提交删除。
- 本地包CRC/SHA只证明载荷一致，不证明远端附件已上传或全部业务通过。
- 旧路径、旧PID、旧租约owner和private prefs不能当作新工作区当前状态。
- Word保存会改样式ID，结构检查用outline级别；报告二进制改变需重做渲染检查，不沿用旧校验JSON。
- 避免多轮代理/广泛审查拖延交付；分离任务写入必须结束后才消费最终快照。

## Verification

必须实际验证：远端handoff ref、实际clone HEAD、SOURCE-STATE commit相同；每载荷SHA/Git blob/APK身份通过；负向测试拒绝错提交、脏源码、篡改载荷、私钥和危险路径且不修改用户文件；技能快照摘要匹配；原用户改动保留；有清楚的未通过/未验证清单。最后给用户仓库/ref、交接ZIP/外侧摘要、阅读入口和已执行检查，不只写“已归档”。
