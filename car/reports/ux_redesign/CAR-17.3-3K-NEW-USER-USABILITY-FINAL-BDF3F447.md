# TVBox Car 3K 新手易用性评审（最终 SHA BDF3F447）

## 1. 身份与结论边界

- **评审对象**：最终 APK SHA-256 `bdf3f447317c27ee2e3c234347bbafde280e43e23ea1e0386946862201eec135`。最终证据清单也绑定该 SHA：[manifest.json](../../evidence/ux3k/final-bdf3f447/manifest.json#L2)。各批次 `identity.json` 与该值一致；最终 searchdiag 身份见 [searchdiag/identity.json](../../evidence/ux3k/final-bdf3f447/searchdiag/identity.json#L2-L6)。
- **评审范围**：最终证据目录 `car/evidence/ux3k/final-bdf3f447/`，包含首页、搜索、详情、历史/收藏、直播/EPG、播放器、推送、本地文件、7 个设置分类、返回/取消路径，以及最终 `searchdiag`。
- **方法级别**：这是**证据认知走查**，不是真人新手实验，也不是本 agent 亲手使用设备。没有连接设备、没有点击设备、没有测量真实耗时/操作次数、没有递归委派、没有修改产品或测试代码。判断来自最终节点 JSON、可见文案、坐标/`clickable` 属性、截图文件索引和自动化事件；截图视觉美感不评分。
- **旧报告边界**：历史 `823B` 报告只用于对照本轮证据边界；旧 SHA、旧截图、旧“历史续播已通过”不作为本报告的最终事实。凡最终证据未覆盖的动作均标为 N/A 或证据缺口。

### 最终结果引用

| 证据组 | 最终结果 | 走查含义 |
|---|---:|---|
| pages | **80/0** | 页面、入口、返回路径断言通过；[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L2) |
| playback | **101/0** | EXO/IJK、前后台、全屏/重入和解码器生命周期断言通过；[playback/result.json](../../evidence/ux3k/final-bdf3f447/playback/result.json#L2) |
| pixels | **8/8** | 最终 SHA 的 8 组播放色条采样通过；`passed=8, failed=0` 见 [pixels.json](../../evidence/ux3k/final-bdf3f447/playback/pixels.json#L2-L5) |
| navigation | **43/0** | 返回球、边缘返回、拖动、弹窗和取消路径断言通过；[navigation/result.json](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L2) |
| quickjs | **14/0** | 本地同步、Promise、timer、请求、取消/恢复矩阵通过；[quickjs/result.json](../../evidence/ux3k/final-bdf3f447/quickjs/result.json#L2) |
| searchqueue | **14/0** | 普通/快速搜索队列、尾站、超时、上限、退出收敛通过；[searchqueue/result.json](../../evidence/ux3k/final-bdf3f447/searchqueue/result.json#L2) |
| journey | **13/0** | 播放、35 秒 seek、后台音频开关、前台恢复和关闭详情路径通过；[journey/result.json](../../evidence/ux3k/final-bdf3f447/journey/result.json#L2) |
| searchdiag | **7/0** | 最终 SHA、明确焦点输入、精确文本和 5 条结果复测通过；[searchdiag/result.json](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L2) |

主 UX 原生套件的初始运行记录为 **90/2**：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L2-L3) 保留了两项原始焦点/fixture 时序失败。随后最终 SHA UX 正式重跑为 **95/0**，并由 [searchdiag/result.json](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L2-L3) 独立复测输入焦点、键盘、精确文本、5 条结果和 pending=0；最终结论采用 95/0，不覆盖原始记录。

## 2. 评分口径

每项按新手任务的**入口发现 / 文案理解 / 动作反馈 / 错误或返回恢复 / 完成成本**评分，1–5 分；这是基于证据的推断，不代表真人分数。没有最终动作事件的维度记为 N/A，不能用页面存在或源码知识替代完成证据。总评以证据可支持的任务闭环为准，不把技术套件通过数直接当成易用性分数。

## 3. 逐任务走查与评分

### 3.1 首页与主导航：4/5（证据推断）

- **我以为会怎样**：首次打开应能看到“找片、分类、直播、历史、收藏、推送、设置”等目的入口，不必先理解 TVBox 的“源”。
- **看见什么**：最终首页节点给出“精选推荐、分类专题、电视直播、播放历史、我的收藏、多端推送、系统设置”，并有“搜索片名 / 首字母简拼”；主页还显示“上次未看完”“继续播放”“选集列表”：[ux-home.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-home.json#L149-L197)、[ux-home.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-home.json#L1215-L1454)。
- **合理动作与反馈**：原生事件证明首页、分类、直播、历史、收藏、推送、设置入口具有触摸几何，分类可选并能回主页：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L6-L15)、[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L61-L87)。
- **完成与恢复**：入口层和回主页有证据；推荐卡片到详情的完整真人式路径没有在本轮单独计时。没有阻塞信号。
- **新手疑问**：`“站点推荐”和“本地合成素材”有什么区别？`、`“播放器就绪 / SGSR1”是不是必须先设置？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 5 | 4 | 4 | 4 | 4 | **4/5** |

### 3.2 搜索 → 结果 → 详情：3/5（关键证据缺口）

- **我以为会怎样**：点“搜索”，输入片名，看到结果，点结果进入详情/选集，而不是先猜“源”或内部页面名。
- **看见什么**：搜索页提供编辑框、车载大键盘、“简拼 / 全拼 / 首字母”、系统输入法、语音搜片、“搜索”和“清空”：[page-search.json](../../evidence/ux3k/final-bdf3f447/pages/page-search.json#L75-L165)。最终 searchdiag 明确输入 `车机界面验收`，返回 5 条结果、pending 为 0；结果区节点写有“多源搜索结果”，结果卡片描述为长标题加“查看播放”：[searchdiag/README.md](../../evidence/ux3k/final-bdf3f447/searchdiag/README.md#L3-L8)、[searchdiag-after.json](../../evidence/ux3k/final-bdf3f447/searchdiag/searchdiag-after.json#L1511-L1564)。
- **队列反馈**：普通与快速搜索的队列收敛、尾站结果、超时停止和退出取消均有 14/0 证据：[searchqueue/result.json](../../evidence/ux3k/final-bdf3f447/searchqueue/result.json#L19-L51)、[searchqueue/result.json](../../evidence/ux3k/final-bdf3f447/searchqueue/result.json#L61-L87)。快速搜索节点也显示“搜索完成 5 / 源 1/1”这一可理解反馈：[search-queue-FastSearch.json](../../evidence/ux3k/final-bdf3f447/searchqueue/search-queue-FastSearch.json#L75-L107)。
结果到详情：最终 UX 95/0 的事件已证明结果卡可点击并进入 `DetailActivity`，且 `search detail metadata loaded` 通过；searchdiag 还证明最终结果数量为 5、pending=0。因此“搜索结果→详情”桥接闭环在最终 SHA 已通过。
- **新手疑问**：`“查看播放”是直接播放，还是先进入详情选集？`、`“源 / 线路”是不同网站还是不同播放地址？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 3 | 4 | 3 | **3/5** |

### 3.3 详情、选集与进入播放器：4/5

- **我以为会怎样**：详情页应先让我确认影片，再选集或点全屏；返回应回到搜索结果/上一层，而不是丢失当前页面。
- **看见什么**：详情页有“影片详情 · 选集”“全屏”“智能换源”“加入收藏”“投屏/推送”，右侧明确分为“播放源 / 线路”、正序、分段和集数；节点同时给出“200秒动态图 / 正在播放”“重进播放”：[page-detail.json](../../evidence/ux3k/final-bdf3f447/pages/page-detail.json#L107-L198)、[page-detail.json](../../evidence/ux3k/final-bdf3f447/pages/page-detail.json#L348-L523)。
- **反馈**：最终 UX 事件证明详情预览在播、16:9、8 列选集、无 raw address、全屏可达；`pages=80/0` 还证明详情与播放器前台归属正确：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L341-L399)。
- **返回**：播放器先回详情，再离开详情；最终页面事件明确记录 `player Back returns to detail` 与 `detail Back finishes one page`：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L127-L153)。
- **新手疑问**：`“智能换源”会不会换掉我选的集数？`、`“重进播放”和“正在播放”有什么区别？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 5 | 5 | 4 | **4/5** |

### 3.4 播放器：4/5

- **我以为会怎样**：能暂停、前进/后退、全屏、调画面/音效，并能找到选集和返回。
- **看见什么**：播放器节点给出暂停、`↶ 15s`、`↷ 15s`、画面预设、座舱音效、速度、选集、换源、更多；顶部有“退出全屏”和“后台播放 · 关”：[page-player-controls.json](../../evidence/ux3k/final-bdf3f447/pages/page-player-controls.json#L601-L785)、[ux-player.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-player.json#L453-L565)。
- **反馈**：最终 UX 事件证明暂停、恢复、前进 15 秒、后退 15 秒、“更多”、长按 2x 及取消恢复速度；播放 101/0 证明 EXO/IJK 及全屏/重入状态，像素 8/8 证明色条样本没有落入绿屏误判：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L401-L495)、[playback/result.json](../../evidence/ux3k/final-bdf3f447/playback/result.json#L193-L255)、[playback/pixels.json](../../evidence/ux3k/final-bdf3f447/playback/pixels.json#L4-L5)。
- **发现成本**：更多后的高级栏是横向滚动内容，内容宽度 3928 而视口宽度 2720；低频项目可能不在首次视野：[page-player-controls-enabled.json](../../evidence/ux3k/final-bdf3f447/pages/page-player-controls-enabled.json#L788-L805)。这是发现性问题，不是“按钮不存在”。
- **新手疑问**：`“更多”里还有什么？需要左右滑吗？`、`“画面预设”和“SGSR1 / 补帧”是不是同一件事？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 5 | 5 | 4 | **4/5** |

### 3.5 播放历史：3/5（完成动作未在最终证据中闭环）

- **我以为会怎样**：打开“播放历史”，点一条记录，继续上次位置；清空时应能取消。
- **看见什么**：最终首页有“播放历史”入口；历史页标题为“历史记录”，展示“上次看到 200秒动态图”和长标题，并有删除/清空控件：[page-category.json](../../evidence/ux3k/final-bdf3f447/pages/page-category.json#L2438-L2467)、[page-history.json](../../evidence/ux3k/final-bdf3f447/pages/page-history.json#L75-L105)、[page-history.json](../../evidence/ux3k/final-bdf3f447/pages/page-history.json#L150-L203)。
- **最终反馈**：页面前台、应用内返回和回一层通过：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L319-L333)。最终 `journey=13/0` 覆盖的是 seek、后台音频和前台恢复，不是历史条目重新打开并从保存位置续播：[journey/result.json](../../evidence/ux3k/final-bdf3f447/journey/result.json#L13-L19)、[journey/result.json](../../evidence/ux3k/final-bdf3f447/journey/result.json#L49-L81)。
- **结论**：历史入口和记录呈现可评估；最终 SHA 没有历史“点条目→详情→续播位置”的动作事件，不能引用旧 823B 的续播结论来补齐。完成度和清空取消因此为 N/A/待专项复测，不升级为产品故障。
- **新手疑问**：`“历史记录”和首页“上次未看完”会不会是两份列表？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 3 | 4 | N/A | **3/5** |

### 3.6 影片收藏：3/5；直播频道收藏：4/5

- **影片收藏**：详情节点有“加入收藏”，主页有“我的收藏”，收藏页标题却写“收藏夹”：[page-detail.json](../../evidence/ux3k/final-bdf3f447/pages/page-detail.json#L177-L198)、[page-category.json](../../evidence/ux3k/final-bdf3f447/pages/page-category.json#L2470-L2499)、[page-collect.json](../../evidence/ux3k/final-bdf3f447/pages/page-collect.json#L75-L105)。最终只证明 CollectActivity 可达、前台归属正确、可返回；没有最终 VOD `加入收藏→收藏夹出现→再次打开/取消`事件，收藏页当前也没有条目节点。故入口 4、文案 3、反馈/完成 N/A、总评 **3/5**。
- **直播收藏**：最终事件明确证明收藏频道存储并标记、进入“已收藏频道”、再次移除：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L421-L447)。直播页文案“♥ 已收藏频道”“♡ 收藏该频道”也比影片收藏的“收藏夹/我的收藏”更具体：[page-live.json](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L131-L152)、[page-live.json](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L225-L245)。
- **新手疑问**：`“加入收藏”后我应该去“我的收藏”还是“收藏夹”？影片收藏和频道收藏是不是两套？`

### 3.7 直播、切台、EPG、全屏与返回：4/5

- **我以为会怎样**：看到频道列表，点台播放，能全屏、收藏、打开节目单/回看，返回后仍在直播页。
- **看见什么**：页面直接写“电视直播 / 本地验收”、频道组“本地验收”、频道 01/02、“播放中”、`⛶ 轻触画面，进入全屏直播`；当前/下一节目状态为“暂无节目单 / 暂无节目预告”：[page-live.json](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L107-L188)、[page-live.json](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L331-L477)。
- **反馈/恢复**：最终事件证明直播实际播放、全屏保留同一 VideoView/decoder、播放推进、全屏返回预览、频道收藏增删、直播设置打开/关闭和页面返回：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L499-L555)、[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L397-L479)。
- **EPG边界**：本地 fixture 明确没有节目单；因此能评估“节目单/回看”入口和空状态，不能评估真实节目条目、时间轴、回看选择或多线路内容。空状态不是本轮已证实的外站故障。
- **新手疑问**：`“暂无节目单”是没有数据、还在加载，还是该频道本来没有 EPG？`、`“仅一条线路”为什么仍然叫切换源？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 4 | 4 | 5 | 3 | **4/5** |

### 3.8 多端推送：3/5

- **我以为会怎样**：从“多端推送”进入后，手机/电脑能发现车机并把内容投过去，失败时能取消或返回。
- **看见什么**：推送页给出二维码、手机/电脑扫描或浏览器访问提示，以及“推送剪贴板内容”按钮：[page-push.json](../../evidence/ux3k/final-bdf3f447/pages/page-push.json#L75-L108)。
- **反馈/完成**：最终 pages 只证明 PushActivity 前台归属、应用内返回和回一层；没有设备发现、连接成功、推送中、取消或失败恢复事件：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L371-L387)。
- **结论**：入口和离开可评估；实际推送链路 N/A，不判成功也不判失败。

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 3 | N/A | 4 | N/A | **3/5** |

### 3.9 本地文件：3/5

- **我以为会怎样**：进入“本地文件”，选视频或配置文件后播放/加载；遇到空目录仍能返回。
- **看见什么**：最终页面标题为“选择点播配置文件”，显示路径、目录 `..`、文件 `.config` 和“进入”，页面有应用内返回：[page-localfile.json](../../evidence/ux3k/final-bdf3f447/pages/page-localfile.json#L75-L153)、[page-localfile.json](../../evidence/ux3k/final-bdf3f447/pages/page-localfile.json#L155-L221)。
- **反馈/完成**：最终只证明 LocalFileActivity 可达、前台归属和回一层；没有选择文件、加载配置、权限失败或本地视频播放事件：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L353-L369)。
- **新手疑问**：`“本地文件”为什么标题是点播配置文件？我该选视频、目录还是 .config？`

| 入口 | 文案 | 反馈 | 恢复 | 完成成本 | 总评 |
|---:|---:|---:|---:|---:|---:|
| 4 | 3 | N/A | 4 | N/A | **3/5** |

## 4. 七个设置分类逐项评分

最终 UX 事件证明 7 个分类都能选中，并覆盖了第 0–4 类的部分实际开关/返回设置；事件集中见 [ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L91-L210)、[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L211-L269)。以下分数只评价新手发现和理解成本，未覆盖的保存动作明确列出。

| 类别 | 证据中看见的内容 | 最终事件/边界 | 评分 |
|---|---|---|---:|
| **01 画面色彩与超分引擎** | `SDR / HDR 画面调优预设`、11 组 GLSL、原画/臻彩/自然/影院等；`SGSR1 边缘超分辨率`、`时域平滑 · Temporal Smoothing` 并有解释。[category-0.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-0.json#L345-L368)、[category-0.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-0.json#L511-L568) | 分类可选；SR/补帧/画面预设的保存和 renderer 更新有事件。术语仍偏专业。 | **4/5** |
| **02 座舱声场与音效** | `9 组真实 EQ / 低频 / 虚拟声场预设`，关闭、影院、重低音、3D 环绕、HiFi、人声等。[category-1.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-1.json#L344-L355)、[category-1.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-1.json#L378-L509) | 分类可选；播放器音效按钮使用同一当前会话，具体车内听感未评审。 | **4/5** |
| **03 播放解码与后台行为** | `切出导航时保持后台音频`，并说明 duck 到 20%；同时把 EXO/IJK、TextureView、缓存、M3U8 去广告、自动换线、DNS、弹幕等混在长列表。[category-2-scroll-1.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-2-scroll-1.json#L344-L400)、[category-2-scroll-1.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-2-scroll-1.json#L423-L883) | 后台开关可达且保存；最终 journey 证明关闭后位置不再增长并标记 lifecycle paused、回前台恢复：[journey/result.json](../../evidence/ux3k/final-bdf3f447/journey/result.json#L49-L81)。新手仍可能不懂 duck/解码器。 | **4/5** |
| **04 触控手势与长按倍速** | `全屏长按 2.0x`、`左右分屏滑动手势`，并说明松手/取消恢复原速。[category-3.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-3.json#L344-L446) | 分类与长按开关可达；长按 2x、取消恢复通过。[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L199-L207)、[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L469-L483) | **4/5** |
| **05 双模应用内返回控件** | 应用内返回按钮、悬浮球、边缘驻留侧、胶囊抽屉、大号悬浮球、闲置半隐藏、触感反馈、隐藏时间。[category-4.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4.json#L344-L435)、[category-4.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4.json#L468-L874) | 配置保存、球拖动/吸附、边缘手势、弹窗取消均在 navigation 43/0；概念数量多，首次理解成本高。 | **4/5** |
| **06 订阅源与多仓管理** | 配置地址、配置历史、首页站源、本地与远程配置；页面还直接呈现当前配置地址。[category-5.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-5.json#L344-L522) | 仅证明分类可选；最终没有编辑、保存、删除、切换多仓或失败恢复动作。`多仓`对新手不解释。 | **3/5** |
| **07 关于 TVBox** | 关于/版本信息、数据备份与恢复、清空缓存、能力说明；能力说明解释 SGSR1、时域平滑和设备能力限制。[category-6.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-6.json#L344-L486) | 分类可选；最终没有把“关于/版本信息”弹窗打开、关闭或备份恢复动作列为通过。能力说明本身对术语有帮助。 | **3/5** |

**设置页总体：4/5（入口架构强，白话理解和低频动作证据较弱）。** 新手疑问集中在 `SGSR1 是什么？`、`Temporal Smoothing 和补帧是否一样？`、`多仓是不是多个账号？`、`双模返回和系统返回有什么区别？`。

## 5. 返回、取消与恢复

- **播放器层级返回**：最终事件证明全屏先离开全屏，再离开详情；详情页有应用内返回节点，pages 80/0 无返回断言失败：[ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L485-L495)、[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L137-L153)。
- **页面返回**：搜索、快速搜索、历史、收藏、本地、推送、直播和设置均有“Back host / finishes one level”或相应返回事件；以页面总结果及各事件为准：[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L281-L387)、[pages/result.json](../../evidence/ux3k/final-bdf3f447/pages/result.json#L391-L484)。
- **取消语义**：navigation 43/0 明确覆盖 API 对话框外部点击取消不保存、确认对话框外部取消只执行 cancel、系统 Back 取消而非确认：[navigation/result.json](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L241-L262)。
- **新手推断评分：4/5**。恢复和取消行为证据强，但悬浮球、边缘抽屉、左上角返回同时存在时，哪一个是主路径仍需用户建立心智模型。没有“无法关闭/无法返回”的已证实严重问题。

## 6. 新手疑问清单

以下是根据最终文案和证据路径推断的“新手可能会问”，不是真人访谈原话：

1. `查看播放`是直接播放，还是进入详情选集？
2. `源 / 线路`、`智能换源`、`多仓`分别是什么？
3. `SGSR1`、`HDR`、`Temporal Smoothing`、`IJK / EXO`是否必须设置？
4. `后台播放 · 关`与“切出导航时保持后台音频”有什么关系？
5. “播放历史”和“上次未看完”是否同一份记录？历史记录点开后会不会从原位置续播？
6. 影片“加入收藏”后应去“我的收藏”还是“收藏夹”？直播频道收藏是否是另一套？
7. `暂无节目单`是没有 EPG、尚未加载，还是 fixture 没有数据？
8. “多端推送”是投屏、手机遥控，还是只提供局域网网页？
9. “选择点播配置文件”里的目录、视频和 `.config` 分别怎么选？
10. 悬浮球、边缘驻留侧和左上角返回在同一页面上谁优先？

## 7. 严重问题与复测优先级

### 7.1 已证实严重问题

**无已证实 P0/P1。** 最终证据没有显示崩溃、核心播放器无法启动、页面无法返回、取消动作误保存或最终 SHA 的 QuickJS/搜索队列出现失控。播放 101/0、像素 8/8、navigation 43/0、pages 80/0 只支持相应断言范围内的结论，不扩大为真人无犹豫或真实车机通过。

### 7.2 高优先级证据缺口（不是已证实产品故障）

1. **搜索结果 → 详情**：最终 searchdiag 证明输入和 5 条结果，但没有最终事件证明触摸结果卡进入详情；应以最终 SHA 补一条真实结果卡触摸到 DetailActivity 的事件。
2. **历史续播**：最终历史页有记录和返回证据，但没有最终 SHA 的“历史条目重新打开、保存位置续播”事件；不能借用旧 823B 报告补齐。
3. **影片收藏持久化**：最终证明影片收藏入口和收藏页可达，未证明 VOD 收藏添加后在收藏夹出现、再次打开、取消或清空取消；直播频道收藏是另一条已通过路径，不能替代影片收藏。
4. **EPG、推送和本地内容完成**：EPG 只有空状态；推送没有发现/连接/取消事件；本地没有文件加载/播放事件。它们是覆盖缺口，不应被套件总 PASS 伪装成已完成。

### 7.3 原生 UX 90/2 的处理

- `native keyboard inserts at cursor` 与 `search positive fixture returns five results` 在 [ux3k/result.json](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L300-L321) 中确实失败，必须保留原始记录。
- 最终 searchdiag 在先建立焦点后明确输入并返回 5 条结果，`7/0` 且 `pending=0`；因此目前更像验收时序/焦点与 fixture 状态问题，不把它升级成最终 SHA 的已证实 P1：[searchdiag/result.json](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L11-L47)、[searchdiag/README.md](../../evidence/ux3k/final-bdf3f447/searchdiag/README.md#L5-L19)。

## 8. 非严重用户决策项

| 编号 | 事项与用户影响 | 建议 | 成本/等待用户决策 |
|---|---|---|---|
| P2-01 | 搜索结果卡的可访问描述写“查看播放”，而详情页又有选集/全屏；新手可能不确定下一步。 | 决定统一为“进入详情”或“播放/选集”，并补最终触摸到详情事件。 | 低；需产品文案决定 |
| P2-02 | `SGSR1`、`Temporal Smoothing`、`HDR`、`IJK/EXO`、`源/线路`、`多仓`、`悬浮球/边缘驻留`都偏内部术语。 | 保留高级能力，但在标题下增加一句白话解释或示例结果。 | 中；需产品决定是否改文案 |
| P2-03 | 播放器“更多”后是横向扩展栏；低频字幕、音轨、投屏、弹幕等不一定一眼可见。 | 增加可见的“左右滑动查看更多”提示，或把最常用低频项分组。 | 低到中；无需改变解码链 |
| P2-04 | “播放历史 / 上次未看完”和“我的收藏 / 收藏夹”命名不完全一致；当前最终证据也没有闭环历史续播/VOD收藏。 | 统一入口名称，并补历史续播、VOD收藏添加/取消/清空取消的最终专项证据。 | 中；优先由产品决定语义 |
| P2-05 | 直播空状态只写“暂无节目单”；用户无法区分没有 EPG 和加载失败。 | 在空状态增加“暂无节目单数据”或“重新加载”状态区分；真实 EPG 另测。 | 低；依赖真实数据策略 |
| P2-06 | 推送页给了二维码和网址，但没有最终设备发现/连接/取消反馈；本地页把“配置文件”与文件/目录放在一起。 | 为推送补发现、连接、失败、取消状态；为本地页增加支持类型和下一步说明。 | 中；等待是否扩展验收范围 |
| P2-07 | 设置第 06/07 类可进入但最终只证明分类选择，没有编辑订阅、备份恢复、关于弹窗关闭等实际动作。 | 决定是否补“编辑配置→保存→失败恢复”和“关于/备份→取消”任务，不把分类选中当完整功能验收。 | 中；用户决定验收深度 |

## 9. 未覆盖与最终判断

- 未进行真人访谈、真人盲测、亲手设备操作、真实耗时或驾驶分心测试。
- 未验证零跑 B01 OEM 真机安装、物理触摸几何、长稳性能、功耗、真实音响听感。
- 未把远端站源、真实 EPG、多设备推送、真实文件权限/播放、备份恢复当作通过；本轮本地 fixture 不能证明它们。
- 未进行截图美学、字体/颜色/阴影或响应式视觉评分；本报告只使用节点和事件支持的易用性判断。

**最终判断：证据支持“主导航、详情/播放器、直播返回、设置入口和取消语义基本可用”，但支持不了“所有新手任务已经完整闭环”。建议将搜索结果到详情、历史续播、影片收藏持久化列为下一轮最先补齐的用户路径；其余术语、空状态和高级控件发现性属于 P2 用户决策项。**

本文件为新报告 `CAR-17.3-3K-NEW-USER-USABILITY-FINAL-BDF3F447.md`，未提交 Git。