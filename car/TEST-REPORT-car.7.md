# car.7 候选验收报告

日期：2026-10-16。当前交付为私有草稿候选，正式发布暂缓。Python运行兼容缺口已修复，真实PY内容和EXO播放已验证；枫叶源当前内容为空、IJK硬解全屏像素异常仍保留。

## 最终候选和环境

| 项目 | 实际值 |
|---|---|
| APK | TVBox-1.0.0-car.7-python-signed.apk |
| package / label | com.github.tvbox.osc.jun / TVBox |
| versionCode / versionName | 8 / 1.0.0-car.7 |
| 大小 | 52,668,696B |
| SHA-256 | D2A49B5DA2B0371CF3C84CCDAC6C5753EF649721639434FB21955F4054A30803 |
| 静态检查 | v1/v2/v3、zipalign4、入口/Manifest、双ARM的Python/Chaquopy/QuickJS通过 |
| 环境 | MuMu6.8.4.0 / Android15 / 实例1 / 完整2880×1620 / density160 / rotation1 / 无override |

每轮先验证真实显示和输入viewport均2880×1620，打印CAR_DISPLAY_PASS。host与guest静音，未启用无障碍。所有设备操作经过MuMu插件并检查独占租约；原配置恢复后核验，最后只释放实例1，保留MuMu主程序。

## Python故障原理与修复

之前已切换到Python flavor，解释器原本就在APK内。JS与PY的Spider/HTTP链不同，JS成功不能证明PY的Requests兼容。缺口在请求体/原始字节、Cookie、Response、相对重定向和TLS回退语义，以及下载缓存与同步预热调度。

car.7新增标准Requests BaseAdapter与真实Response，保留原始字节、超时、reason、final URL、重复header和Cookie策略；可重放HTTPS请求遇既定错误时Java回退，native仍优先。脚本下载有重定向上限、2xx/非空/语法检查，临时文件通过后原子替换；失败不覆盖有效缓存、不静默导入旧脚本冒充成功。ApiConfig传递完整配置，PY不参与后台抢锁预热，解释器与pending config在首次真实请求时应用。

不修改用户订阅内容，不移除Java/JAR或QuickJS，不盲目补依赖。Windows只下载/静态解析远程数据；实际脚本仅在Android目标进程内执行。新增HTTP诊断仅method/host/status/耗时，完整URL、headers、body和运行时凭据不归档。

## 最终D2A真实PY结果

订阅 `https://clun.top/box.json`，当日97站/44PY。实际安装APK哈希与D2A一致。tag `final-python-D2A49B5D-r1`，PID51004；严格断言12 PASS/1 FAIL。失败源为py_maple返回空数据，不能把它从报告中删掉。

| PY样本 | 分类数 | 分类条目 | 详情 | 搜索“潜伏” | 播放解析 |
|---|---:|---:|---:|---:|---|
| py_douban | 10 | 20 | 不适用 | 不适用 | 不适用 |
| py_maple | 8 | 0 | 未到达 | 0 | 未到达 |
| py_juzong | 18 | 42 | 1 | 12 | URL返回，parse=1，需网页解析 |
| py_apples | 7 | 21 | 1 | 20 | URL返回，parse=0 |
| py_guazi | 5 | 30 | 1 | 15 | URL返回，parse=0 |

苹果PY真实EXO播放：1280×720，STATE_PLAYING=3，位置243→2761ms；不是fixture、脚本下载或URL存在性检查。桥接form/0..255字节POST和响应/重复Cookie相对跳转/Response合约通过，真实clun脚本200/17,227B。证据 `evidence/car.7/final-python-D2A49B5D/`。未逐站验证全部44PY，也未验证每一条外部线路。

历史E395同一天的完整五站轮：PID10695、12/0，枫叶分类24/详情1/搜索7，其余四站与上表数量一致，见 `python-real-E39569F0/`。E395补充播放轮PID36591也实际播放苹果1280×720、192→2712ms，但枫叶那轮已经为空。不同轮结果一起保留，说明枫叶并非解释器缺失，当前源端响应仍有限制。不能把历史有数据自动当作最终轮通过。

## 最终D2A xiaosa JAR/QuickJS

原地址 `https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json`；guest以ghfast转发加载。快照109站＝108JAR/CSP＋1JS，0PY。API200/28,993B，JAR200/1,863,956B，ZIP含classes.dex；声明/host/guest JAR MD5均 `bca2498f2d40c694b285471062748cf0`，元数据在 `xiaosa-real-E39569F0/network-metadata.json`（同一当日JAR快照）。

最终tag `final-xiaosa-D2A49B5D-r1`，PID51918；9 PASS/4 FAIL，JAR Init.init/main及JSLoader加载成功，没有身份门终止或应用FATAL。

| 样本 | 分类数 | 分类条目 | 详情 | 搜索 |
|---|---:|---:|---:|---|
| 热播影视（JAR） | 10 | 12 | 1 | 0 |
| 枫叶影视（JAR） | 8 | 0 | 未到达 | 7 |
| 爱动漫（QuickJS） | 6 | 48 | 1 | 30秒超时 |

4个失败记录为热播搜索空、枫叶分类空、爱动漫超时及超时终止剩余探针。证据 `final-xiaosa-D2A49B5D/`。历史E395真实UI已通过导入、豆瓣分类/推荐、聚合搜索37结果（抓取时72/79完成）和热播47集详情；不宣称所有源永久可用。首次错误导入是测试输入残留拼接URL，fresh XML识别后纠正，不算应用缺陷。

## 返回与车机UI

最终D2A tag `final-ui-D2A49B5D-r1`，PID51576，58 PASS/0 FAIL；证据 `final-ui-D2A49B5D/`。覆盖冷启动热区243px、主页设置入口安全范围、横向分类/推荐最后项、独立标题区、分类回主页、根点击不退出、高级页返回、三模式、球拖动吸边/位置持久化、闲置alpha.38与触摸唤醒、边缘短拉/反悔/CANCEL/纵向拒绝/400ms长按移位，以及多指和弹窗外拖不误提交/取消。

返回View位于Activity decor，无系统overlay/无障碍权限。标准视觉/热区189/243px，大号216/270px。历史E395的屏幕水滴139×331、胶囊328×256已测量且在内置浏览器实际查看形状/文字，证据 `back-redesign-d160-E39569F0-edge-trace/`；最终D2A重复了全部58项和截图，但没有把浏览器后续不可见描述为人工查看完成。真实车机的人机工程仍待实机确认。

## 当前视频、生命周期与像素

最终D2A tag `final-playback-D2A49B5D-r1`，PID47234，95 PASS/0 FAIL。EXO/IJK各实际播放本地200秒24fps合成片，验证9音效/11画面作用当前decoder/session、暂停迟到701/702/3不提升为PLAYING、快速连续配置、全屏/退全屏、HOME后台/前台两段源帧推进、释放live=0、重复release、重进、切换中退出清理。

EXO的Texture/GL交接保留旧consumer至新setSurface成功，提交/回滚延后一条主线程消息，避免Texture回调在绘制遍历中移除子View。IJK保留从prepare建立的GL消费者；全关为恒等OES直通，无FBO/SR/持续补帧，仍保持原画语义。不restart/seek、不替换decoder identity。全屏producer重建时IJK有短暂原生暂停和原播放意图恢复；用户显式暂停优先。

像素门禁独立检查8张实际PNG：7 PASS/1 FAIL。EXO初始/全屏/前台/重进4图正常；IJK初始/前台/重进正常，硬解全屏图仍绿偏并有块状异常。不能用95项状态断言代替画质通过。证据 `final-playback-D2A49B5D/{result,playback-observations,pixels}.json`。

历史对照：E395动态硬解Texture正常/GL绿，软解Texture/GL均正常；初始GL硬解正常。C996交接曾发生ViewGroup.dispatchDraw空指针，FABB改为异步提交后不再崩溃；02C固定IJK输出后剩余全屏一图，D2A短暂停重绑仍未消除此图。失败与通过均保留，未重试到绿或修改像素门槛。当前MuMu结果不能直接推断真实ARM车机解码器表现。

没有强制注入detach失败、暂停期间EGL context-loss；播放控制栏四入口已接生产方法，当前实例行为通过，不把它写成逐按钮UI全矩阵验收。

## 主机、驱动和清理

最终Java release编译＋Python release构建exit0；Python HTTP/下载20测试、164 XML、Java8、11预设/参数保持、44运行配置/88SDR-HDR/1936比较、738返回策略、diff whitespace通过。apksigner有上游META-INF的v1提示，v2/v3整包验证通过。全局台账checker仍因另一任务未登记 `com.carhd.fileopener` 非零，未改他人条目，TVBoxOS保持occupied且路径/身份正确。

Computer Use插件与原生driver分开核对，0.28.2已安装并通过probe/doctor/list_apps。后续观察被覆盖、浏览器面板间歇不可见不是“驱动未安装”。本任务Paint已关闭。

原偏好/订阅从guest私有备份恢复且cmp一致，测试包com.github.tvbox.osc.car7.test卸载成功；恢复后PID52210稳定、HomeActivity前台、FATAL0、fresh XML。实例1release返回released=true、shutdown成功，MuMu主程序仍运行；未操作实例5。未发现本批Python/Java辅助进程残留。

正式发布阻塞：IJK硬解全屏像素异常。源端枫叶空响应、部分JAR/JS搜索超时明确保留。真实OEM全屏、SA8295P性能、长期温升功耗、稳定60fps、ARM32运行、HDR/DoVi未实测。候选可按默认EXO测试Python修复，正式car.7 Release不发布为完成状态。
