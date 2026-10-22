# TVBox Car 3K 外部走查复核证据

## 身份

- APK SHA-256：`5f8b8c747b76f0b1c056e9fce3d029ca39850973eb0f513c6f2404ab7feca8c2`
- 大小：`53,679,180 bytes`
- 包名：`com.github.tvbox.osc.jun`
- 版本：`versionCode 8` / `versionName 1.0.0-car.7`
- 设备：MuMu 实例 1，物理 `1620x2880`，显示 `2880x1620`，density `160`，`CAR_DISPLAY_PASS`

## 来源

外部报告：`tvbox-main-ui-audit.zip`（`REPORT.md` / `live-issues.csv`，30 条，P1 10 / P2 20）。
外部报告测试对象为 `origin/main @ 6ce0d5ad`，首页/设置/播放器结构与当前 `feature/car-ux-redesign-3k-fps` 不同。本目录只记录当前分支的复核结果，不把外部报告结论直接当作当前缺陷。

## 本轮真实修复

- `M-01` 零配置点击无反馈：首页无源时点击热播卡或“立即观看”给出明确 Toast，指向“设置 → 订阅源 → 配置地址”。
- `M-09` 弹窗无背景压暗：`CustomDialogStyle` 开启 `backgroundDimEnabled`，`backgroundDimAmount=0.62`。
- `M-18` 历史记录空态：新增图标、说明与“去首页看看”主按钮；删除最后一条记录时同步切换。
- `M-19` 搜索历史空白：最近搜索为空时显示“暂无搜索记录，输入片名开始搜索”，并隐藏清空图标。
- `M-25` JS 蜘蛛全局串行：`JsLoader.getSpider` 改为按 siteKey 分段加锁，缓存命中不再排队。
- `M-02` 配置弹窗键盘遮挡：`ApiDialog` 设置 `SOFT_INPUT_ADJUST_PAN`，键盘出现时窗口整体上移。
- 设置页标题：从固定的“播放器设置中心”改为随分区变化的“设置中心 / …”。
- 播放器宽卡选项：图标与文字改为同侧成组，消除短标签时的大段空距。
- 播放器画面预设弹窗：提示文案移到列表上方，列表裁剪与滚动区域分离，取消按钮不再被遮挡。
- 返回边缘引导：硬编码青蓝色改为薄荷/中性色，和全局语义一致。

## 截图

`screenshots/` 为 2880x1620 Android 原生截图：

- 首页、分类、搜索、搜索结果、详情、直播、频道列表、设置、订阅源设置、高级功能、收藏、历史、本地文件、推送、聚合搜索
- 播放器、暂停、More、Advanced、Hidden、画面预设弹窗
- 空 / 加载 / 错误 / 离线状态壳

`manual/` 为针对外部报告新增的复核截图：

- `history-empty.png`：历史空态
- `search-empty.png`：搜索历史空提示
- `nosource-toast.png`：零配置点击提示

`state-*` 由实际 Android 状态组件强制展示，不是网络事件录像。背景彩条/灰度块是本地验收视频，不是产品 UI。

## 回归

- `pages`：`80 passed / 0 failed`
- `ux3k`：`98 passed / 0 failed`
- `playback`：`101 passed / 0 failed`
- `gestures`：`7 passed / 0 failed`
- `states`：`5 passed / 0 failed`

结果 JSON 位于 `tests/`，均绑定上述 APK SHA。

## 保留项

- 外部报告 `M-24` 的详情兜底失败：当前已显示统一空态与“检查网络连接，或切换源站后重试”指引，但未新增独立“重试”按钮；作为 P2 产品决策保留。
- 外部报告 `M-11`：当前搜索源弹窗使用正确的搜索源列表，但弹窗没有标题；作为 P2 文案优化保留。
- 详情页只有少量剧集时，右侧选集列表仍保留固定滚动视口；这是为了多集列表可滚动和返回链稳定，不视为缺陷。
