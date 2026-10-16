# 交接材料维护与验收契约

## 权威分工

| 内容 | 权威位置 |
|---|---|
| 源码、测试、手写文档、报告源、skill快照 | Git的handoff ref所指commit |
| 已安装验收的APK | baseline.json中的SHA与各identity.json |
| 原生断言/截图/像素与外站失败 | car/evidence/car.7/r2-B2EEDC14 |
| APK、报告二进制、构建Python、离线源副本 | 交接ZIP载荷 |
| 完整交接commit与全部载荷摘要 | ZIP根SOURCE-STATE.json；外部MANIFEST是相同数据副本 |
| 整个ZIP摘要 | ZIP外侧同名.sha256.txt，不在ZIP中自我引用 |
| 全局包名与MuMu租约 | 原工作台共享台账、用户目录唯一全局池；包内只读副本不具备分配权 |

不要把产品APK SHA、Git commit ID、tag object ID、ZIP SHA混为一个值。Git源码与原机工作树CRLF可能不同；包源码从Git blob导出，11个Java指纹仅归一CRLF→LF，用于确认仍是原已验收代码，而不是给修改过的代码借用旧证据。

## 维护顺序

1. 完成任务相关改动和相应检查，冻结生产代码；只做交接文档时不重跑无关设备矩阵。
2. 更新START-HERE、HANDOVER、LESSONS、NEXT-AI及SKILL-CHANGES。实际更新本机skill后导出快照，新增Markdown先检查.gitignore。
3. 显式暂存任务路径，`git diff --cached --check`，审阅暂存文件清单，确认无新增APK/私钥/原始远端日志、无用户无关删除。
4. 提交并推送main和**新的**handoff ref，ls-remote核对。已有ref不强制移动。旧正式发布、v1.0.0-car.7均不改。
5. 在Git checkout之外生成报告/包。pack只读HEAD里的blob，拒绝未提交tracked修改（仅原机两条用户删除有明确例外）；新源码不能冒充旧已验收APK。
6. 运行verify_handover，检查每个载荷SHA、Git blob、候选APK、生产指纹、远端ref；保存外部validation。
7. 用新鲜clone模拟新AI接手：核对commit、运行core-only host与handover tests、再校验ZIP。它不等于新路径Gradle重构建或真实车机测试，分别汇报。

## 维护命令

从实际源码根执行。已有本机环境时，完整host参数示例：

```powershell
python -B .\car\tools\run_host_checks.py --out "$env:PI_SCRATCH_DIR\car7-host" --gradle-cache D:\HermesData\gradle
python -B .\car\tests\test_handover.py
git diff --cached --check
```

D盘路径仅为本次机器缓存示例，新机器用GRADLE_USER_HOME或`--gradle-cache`实际值。不以缺缓存为由改生产代码或自动安装全局依赖。

提交与推送完成后，`--delivery-root`是包含tvbox-os-car的补充产物父目录，**不是强制旧源码路径**：

```powershell
python -B .\car\tools\package_handover.py --delivery-root <artifact-parent> --out <outside-repo>\TVBox-car7-r2-handover.zip --python38 <existing-python38-folder> --reference-root <original-apk-workspace>
python -B .\car\tools\verify_handover.py --repo . --bundle <outside-repo>\TVBox-car7-r2-handover.zip --check-remote --out "$env:PI_SCRATCH_DIR\handover-verify.json"
```

pack自动检查远端ref；没有真的push就不能生成声称Git同步的包。生成到临时.building，通过完整校验后再替换目标ZIP。请先把上一次交接ZIP保存到会话scratch，保留回溯。

工具只使用本地已有产物，不生成/安装APK，不操作模拟器。源码包排除私钥、私有偏好、构建缓存、下载的Spider JAR和大量历史证据；保留明确白名单的上游依赖JAR和Gradle wrapper。原上游历史keystore没有因本次文档维护而删除或重写，但不进入交接ZIP、不新提交秘密。

## 报告再生成

```powershell
python -B .\car\tools\generate_car7_report.py .\car\reports\car.7\report-data.json --out <outside-repo>\tvbox-os-car\report-car.7
```

若Word/PDF内容变化，必须重做渲染和结构/图片/版面验收；不能沿用旧document-validation声称新报告通过。本轮已提供参数化render_report.ps1和validate_report.py，不依赖旧scratch路径。不要自动在有用户文档的Word进程上操作；隐藏owned实例、ByRef、finally清理与outlineLvl检查见LESSONS。

## 技能如何维护

本机`~/.agents/skills`由skill_manage维护；先单独Read实际SKILL.md，优先patch。修改后导出指定技能及其support files到`car/handover/skills/`，并更新index.json摘要。接手机器只读比较，不盲目覆盖较新版本。经验必须包含可复现步骤和验证，不能只写“注意风险”。

## 完成标准

- 远端main/新handoff ref、实际clone、SOURCE-STATE对应同一commit。
- 交接载荷CRC/SHA/源blob通过；外侧校验文件对应最新ZIP。
- 11个生产指纹与原验收候选一致；本地与外站失败分别保留。
- Git只有任务相关提交；原机用户删除继续保持，未重置工作区。
- 新clone的基本接手命令实际执行过；没有运行的Gradle/Android/真车检查不冒充通过。
