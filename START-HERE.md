# TVBox car.7-r2 新工作区入口

这是实际源码仓库根目录，`app/`、`player/`、`quickjs/`、`pyramid/`直接位于这里。命令中的`car/`相对本目录，不要再拼一层`tvbox-os-src`。

## 1. 从 Git 接手

仓库：`https://github.com/736368217/tvboxos-car-2880.git`。接手锚点：`handoff-car.7-r2-20261017`，完整 commit 在交接ZIP根的`SOURCE-STATE.json`。这是交接标签，不是新的正式发布。旧`v1.0.0-car.7`仍是历史候选。

新建空工作区时可执行：

```powershell
git clone --branch main https://github.com/736368217/tvboxos-car-2880.git tvbox-car
Set-Location .\tvbox-car
git fetch origin tag handoff-car.7-r2-20261017
git switch -c work-from-car7-r2 handoff-car.7-r2-20261017
```

若项目界面已经克隆仓库，只从实际仓库根开始核对HEAD，不再套一层目录。若已有用户修改，先保留并报告差异，不运行reset、clean或强制覆盖。私有仓库需要用户本机已有Git凭据，不在文档中填Token。

## 2. 交接包怎么用

把`TVBox-car7-r2-handover.zip`放在仓库外或忽略目录中，并先核对同名`.sha256.txt`。**不要覆盖解压到Git工作树。** Git是源码权威；包内的`tvbox-os-src/`只提供同提交的离线源码副本，APK/报告/构建Python是补充产物。

在仓库根，`--bundle`替换为收到的ZIP实际路径：

```powershell
python -B .\car\tools\verify_handover.py --repo . --bundle ..\TVBox-car7-r2-handover.zip --check-remote --out "$env:PI_SCRATCH_DIR\car7-handoff-check.json"
```

校验器只读：检查提交、当前工作树、全部载荷SHA、Git blob、APK和11个生产指纹；不解压、不覆盖。若HEAD不同，不要根据报错自动回退用户工作。先读包内commit再核对用户意图。

需要APK/报告时，解压到**仓库之外的独立目录**，例如兄弟目录`tvbox-car-kit/`。包内`tools/python38-build`是已迁移启动验证的Python3.8.10；JDK17/SDK/Gradle缓存与签名私钥不随包复制。ZIP内清单不含自己；整个ZIP摘要在外侧校验文件中。

## 3. 先读这些文档

1. [项目地图](car/handover/PROJECT-MAP.md)：模块、数据流、页面、关键代码归属。
2. [完整交接](car/HANDOVER-car.7-r2.md)：不可回退的修复、复测入口与已知失败。
3. [问题与踩坑复盘](car/handover/LESSONS.md)：错误尝试、证据和防复发检查。
4. [最终验收](car/TEST-REPORT-car.7-r2.md)、[构建说明](car/BUILD-INFO-car.7-r2.md)。
5. [新AI启动清单](car/handover/NEXT-AI.md)、[维护交接包](car/handover/MAINTENANCE.md)。

用户报告在交接包`tvbox-os-car/report-car.7/`：27页PDF、22张标注截图的Word与单文件HTML。报告源数据和截图在Git中，报告二进制及APK不进Git。

## 4. 第一轮不用模拟器

```powershell
python -B .\car\tools\run_host_checks.py --core-only --out "$env:PI_SCRATCH_DIR\car7-host-core"
python -B .\car\tests\test_handover.py
```

需要JDK17、宿主Python和requests。完整宿主检查另需本机已缓存的QuickJS/OkHttp依赖及QuickJS编译桥；参数见`--help`和构建说明。没有缓存时明确记录缺少前置条件，不能当成应用回归，也不要自动全机安装。

本次交接不要求重复跑已完成的Android全部矩阵。若之后改APK，必须重新构建与签名、生成对应最终R8 mapping的测试包、独占租约、显示门禁、安装SHA核对、原生与像素验收。

## 5. 当前结论

本地播放101/0、像素8/8、逐页49/0、返回/UI58/0、QuickJS14/0、35站搜索12/0。枫叶内容拒绝页/空分类、剧踪阳性片名未命中、爱动漫搜索超时未伪装通过。真实车机OEM全屏和持续性能未验收。

交接包附skill版本快照在`car/handover/skills/`；先读再决定是否同步本机`~/.agents/skills`，不覆盖用户较新的技能版本。原APK工作台全局台账和MuMu池不复制成第二套。
