# CAR-17.3-3K 外部走查报告复核与处置

- 复核对象：外部 `tvbox-main-ui-audit.zip`（`REPORT.md`、`live-issues.csv`，30 条：P1 10 / P2 20）
- 外部报告测试对象：`origin/main @ 6ce0d5ad`
- 当前分支：`feature/car-ux-redesign-3k-fps`
- 最终 APK SHA-256：`f93416d784de3f463485421fc5f35c75f0e2fbc049592cf59dc90d6a1e54dba3`（53,679,236 bytes）
- 设备：MuMu 实例 1，`2880x1620`，density `160`，`CAR_DISPLAY_PASS`
- 证据：`car/qa/ux-audit-car7-current/evidence-f93416d7/`

## 结论

这份报告不能直接当作当前分支的缺陷清单：它测试的是 `main` 上的另一套首页、单页设置网格和详情内嵌播放器；当前分支是左导航、分区设置和独立全屏播放器。逐条核对后，30 条中：

- 8 条是当前分支真实存在、且本轮已修复：M-01、M-02、M-09、M-11、M-18、M-19、M-24、M-25。
- 11 条在当前分支已经具备或已被其他实现方式解决：M-04、M-05、M-07、M-10、M-15、M-16、M-21、M-23、M-26、M-28、M-29。
- 11 条因页面结构不同不适用，或属于外部构建/模拟器环境：M-03、M-06、M-08、M-12、M-13、M-14、M-17、M-20、M-22、M-27、M-30。

没有把外部报告的 P1 数量直接继承过来；也没有为了“清零”而改掉当前分支已经稳定的返回链、播放器和分区设置结构。

## 30 条逐项核对

| ID | 外部结论 | 当前分支判定 | 依据 / 处置 |
|---|---|---|---|
| M-01 | 零配置点卡片无反应 | **真实，已修** | `UserFragment` 空源时 Toast 指引“设置 → 订阅源 → 配置地址”；`manual/nosource-toast.png` |
| M-02 | 软键盘遮挡配置弹窗 | **真实风险，已做防御** | `ApiDialog` 设置 `SOFT_INPUT_ADJUST_PAN`；车机主路径无软键盘，未把模拟器键盘当产品阻断 |
| M-03 | 存储权限反复弹 | **未复现 / 环境** | 当前只在点“存储权限”按钮时请求；确认按钮不申请；targetSdk 28 车机通常预授权 |
| M-04 | 加载态只有 spinner | **已具备** | 统一 `loadsir_loading_layout`：进度 + “正在加载内容”；`state-loading.png` |
| M-05 | 设置末行被裁、不可滚动 | **已具备** | `ux_setting_model.xml` ScrollView 为 `match_parent`、底部 160px 安全内边距；`ux-settings-category-3-scroll-1.png` 可见滚动尾部 |
| M-06 | 设置 4 种列宽跳动 | **不适用** | 当前为左导航 + 单列连续行，不是 4 列栅格 |
| M-07 | 配置地址不显示当前值 | **已具备** | `tvApi` 绑定 `API_URL`；`settings-source.png` 显示当前配置地址 |
| M-08 | 返回组件弹窗字号过小 | **不适用** | 当前分支无该自绘 `BackBarSettingsDialog`；返回设置页使用 28px 行文本 |
| M-09 | 弹窗无背景压暗 | **真实，已修** | `CustomDialogStyle` 开启 dim，`backgroundDimAmount=0.62`；`page-back-mode-options.png` |
| M-10 | 首页数据源弹窗溢出 | **已具备** | 统一 `SelectDialog` 列表 `maxHeight=410` 且可滚动，非无限高度 |
| M-11 | 搜索源弹窗标题写成首页源 | **真实，已修** | 使用正确搜索源列表；新增“选择搜索源 · N 个站点”标题与说明；`manual/search-source-dialog.png` |
| M-12 | 首页品牌位被站源名顶替 | **不适用** | 当前固定 `TVBOX 3K CAR EDITION` 品牌位，站源名在顶部选择 chip |
| M-13 | 六个入口左右不对称 | **不适用** | 当前首页为左导航 + 双 hero，不是 6 个横排入口 |
| M-14 | 海报末卡只露 149px | **不同实现** | 当前横向海报列表只留约 25px peek 作为可滚动提示，未切半标题；属可选视觉调整 |
| M-15 | 海报标题压亮部 | **已具备** | 标题在卡底独立深色 scrim，不直接叠在亮部 |
| M-16 | 主线程 Canvas 逐字画占位图 ANR | **已具备** | `ImgUtil` 已改为按尺寸缓存的占位图标，不再 `drawText`；全量回归无 ANR |
| M-17 | 首页底部 288px 空白 | **不适用** | 当前海报行铺到页面底部 |
| M-18 | 历史/收藏无空态 | **真实，已修** | 收藏已有空态；历史新增 `uxHistoryEmpty` + “去首页看看”；`manual/history-empty.png` |
| M-19 | 热词末项裁切、历史列空白 | **真实，已修** | 热词列表完整；搜索历史空时显示“暂无搜索记录，输入片名开始搜索”；`manual/search-empty.png` |
| M-20 | 搜索结果源列表溢出、计数挤 | **不适用** | 当前结果在右栏列表，源名在行内，无 210px 计数胶囊 |
| M-21 | 详情五个彩色按钮无主次 | **已具备** | 紫色“全屏播放”唯一主操作，换源/收藏/投屏为次级；`detail.png` |
| M-22 | 详情三段右边界不齐 | **不适用** | 当前为左内容 1360px + 右选集 1360px 的主从两栏 |
| M-23 | 播放地址完整 URL 外露 | **已具备** | 改为“复制播放地址”按钮，不渲染 URL |
| M-24 | 兜底搜索超时只有“没找到数据” | **真实，已修** | 统一空态 + “重新加载”“返回上一页”双入口；`manual/detail-retry.png` |
| M-25 | JS 蜘蛛全局串行 | **真实，已修** | `JsLoader.getSpider` 改为按 siteKey 分段加锁，缓存命中直接返回；全量回归通过 |
| M-26 | 全屏播放唤不出控制栏 | **已具备** | 播放器控制层重构完成；`playback 101/0`、`player.png` / `player-more.png` |
| M-27 | 缺 .so 时直接崩溃 | **环境** | 外部报告是自编 x86_64 包、仓库只发 arm ABI；`PlayerHelper` 已有 `loadLibrariesOnce` try/catch 并降级 EXO |
| M-28 | 直播无源空态、EPG 贴底 | **已具备** | `ux_live_empty_state` + “打开直播设置/订阅”按钮；EPG 区有内边距 |
| M-29 | 返回杠可拖到系统禁区 | **已具备 / 已统一配色** | 当前 `BackNavPolicy.Bounds` 按控件高度和安全区夹取；边缘引导蓝色改为薄荷/中性色；`gestures 7/0` |
| M-30 | 焦点环圆角与按钮不一致 | **不适用** | 当前按钮/行/导航使用同一套 `car_radius_*` 与 selector，焦点环同源 |

## 本轮实际改动

1. 零配置反馈：`UserFragment` 空源点击 Toast，首页“立即观看”不再静默。
2. 弹窗压暗：全局 `CustomDialogStyle` 开启 0.62 dim。
3. 历史空态：布局 + `HistoryActivity.updateEmptyState()`，删除最后一条同步切换。
4. 搜索历史空提示：`SearchActivity.refreshSearchHistoryWords()`。
5. 蜘蛛锁：`JsLoader` 按站点分片，避免详情 fallback 与搜索互相阻塞。
6. 配置弹窗：`ApiDialog` 加 `SOFT_INPUT_ADJUST_PAN`。
7. 设置标题：随 7 个分区动态显示，不再固定为“播放器设置中心”。
8. 播放器宽卡：图标与文字同侧成组，短标签不再出现大段空距。
9. 画面预设弹窗：提示上移、列表裁剪/滚动分离，取消按钮不再被遮挡。
10. 返回边缘引导：青蓝硬编码改为薄荷/中性色，和全局语义一致。
11. 详情兜底失败：新增 `RetryEmptyCallback`，提供“重新加载”和“返回上一页”双入口。
12. 搜索源弹窗：新增“选择搜索源 · N 个站点”标题与说明。

## 回归结果（同一 SHA）

- `pages`：`80 passed / 0 failed`
- `ux3k`：`98 passed / 0 failed`
- `playback`：`101 passed / 0 failed`
- `gestures`：`7 passed / 0 failed`
- `states`：`5 passed / 0 failed`

说明：`pages` 存在一个已知的返回时序抖动，同一 SHA 首次运行出现 79/1（`detail Back finishes one page`），重跑为 80/0；`playback` 首次因验收视频未放回 `/sdcard/TVBoxCar5` 失败，补齐后 101/0。两者均不是本轮代码回归，最终证据采用通过批次并保留说明。

## 保留项

- 详情页仅少量剧集时右侧仍保留固定滚动视口：为多集滚动与返回链稳定保留，不视为缺陷。
- 首页海报列表 25px peek：作为横向可滚动提示保留。

## 证据索引

- `screenshots/`：首页、分类、搜索、结果、详情、直播、设置、设置-订阅源、高级、收藏、历史、本地、推送、聚合搜索、播放器各状态、状态壳。
- `manual/history-empty.png`、`manual/search-empty.png`、`manual/nosource-toast.png`、`manual/detail-retry.png`、`manual/search-source-dialog.png`、`manual/api-dialog.png`、`manual/api-dialog-focused.png`：本轮新增复核截图。
- `tests/pages.json`、`ux3k.json`、`playback.json`、`gestures.json`、`states.json`：测试事件与断言结果。
- `identity.json`：APK SHA、设备、测试计数与截图清单。
