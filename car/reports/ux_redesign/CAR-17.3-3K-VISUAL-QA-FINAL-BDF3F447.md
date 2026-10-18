# CAR-17.3 3K 最终 SHA 视觉与 UX 质量评审

## 结论

评审对象为最终 APK SHA-256 `bdf3f447317c27ee2e3c234347bbafde280e43e23ea1e0386946862201eec135`。最终证据身份与 [manifest.json#L2](../../evidence/ux3k/final-bdf3f447/manifest.json#L2) 及各组 `identity.json` 一致；本报告只采用 `car/evidence/ux3k/final-bdf3f447/`，不把旧 823B 失败带入最终结论。

功能与原生节点证据整体充分：最终 UX 页面批次 `95/0`，全页面 `80/0`、`playback 101/0`、`navigation 43/0`、`quickjs 14/0`、`searchqueue 14/0`、`journey 13/0`、`searchdiag 7/0`，播放色条自动像素门禁 `8/8`。原始 UX 批次 `90/2` 的两项由同 SHA 的最终 UX 重跑与 `searchdiag 7/0` 定点复测闭环：先建立输入框焦点后键盘输入通过，阳性搜索返回 5 条且 pending=0；这两项属于采集时的焦点/fixture 时序，不构成已证实的 P0/P1 产品故障。

本评审无法从现有 BrowserPreview 返回值看到截图像素。因而人工视觉维度“层级与美感（A）”“文字与对比（C）”全部为 `N/A`，不能据图片文件存在或像素自动色条门禁推导美观通过。`Ln/Te/Fe` 仅评价原生几何、触控事件和反馈/恢复证据覆盖，不是人工看图分数。

## 证据与方法

- 角色规范：[car/qa/subagents/car-ux-visual-qa.md#L11-L29](../../qa/subagents/car-ux-visual-qa.md#L11-L29)。
- 截图画廊：[final-bdf3f447/index.html](../../evidence/ux3k/final-bdf3f447/index.html)。画廊正文明确为原生 MuMu 截图而非 HTML 重画；本次只请求 BrowserPreview 打开该页，工具未提供截图、DOM 操作或像素回显。
- 设备/窗口证据：原生根节点为 `2880x1620`；JSON 节点坐标用于布局与可达性核对。没有操作设备、ADB、模拟器池或产品代码。
- 自动门禁不等于亲自操作：通过事件用于限定行为结论；`pixels 8/8` 只证明指定 EXO/IJK 色条采样匹配，不能证明全部画质、美感、字体、阴影或对比度。
- 本地素材明确按 demo/fixture 处理；不把本地播放、直播、搜索结果推广为所有远端源、真实 EPG、真实车机 OEM 或硬件音质能力。

评分含义：`Ln` 原生布局/容器几何，`Te` 触控与命中证据，`Fe` 状态变化、返回、取消和恢复证据；1 为阻断，3 为部分覆盖，4 为主要路径有证据，5 需要人工像素与完整真人操作，本轮不授予 5。

## 结果矩阵

| 证据组 | 结果 | 身份/结果证据 |
|---|---:|---|
| UX 页面与设置 | **95/0**（最终重跑）；初始 90/2 原始记录保留 | [ux3k final result#L2-L3](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L2-L3)、[searchdiag result#L2-L3](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L2-L3) |
| 全页面/Activity | 80/0 | [pages result#L2-L3](../../evidence/ux3k/final-bdf3f447/pages/result.json#L2-L3) |
| EXO/IJK 播放生命周期 | 101/0 | [playback result#L2-L3](../../evidence/ux3k/final-bdf3f447/playback/result.json#L2-L3) |
| 播放像素门禁 | 8/8 | [pixels#L2-L6](../../evidence/ux3k/final-bdf3f447/playback/pixels.json#L2-L6) |
| 返回球/边缘手势/弹窗取消 | 43/0 | [navigation result#L2-L3](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L2-L3) |
| QuickJS 运行时 | 14/0 | [quickjs result#L2-L3](../../evidence/ux3k/final-bdf3f447/quickjs/result.json#L2-L3) |
| 普通/快速搜索队列 | 14/0 | [searchqueue result#L2-L3](../../evidence/ux3k/final-bdf3f447/searchqueue/result.json#L2-L3) |
| 搜索输入与阳性结果定点复测 | 7/0，5 条，pending=0 | [searchdiag identity#L2-L6](../../evidence/ux3k/final-bdf3f447/searchdiag/identity.json#L2-L6)、[searchdiag result#L11-L45](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L11-L45)、[searchdiag.json#L2-L4](../../evidence/ux3k/final-bdf3f447/searchdiag/searchdiag.json#L2-L4) |
| 跨页面旅程/后台音频最终复测 | 13/0 | [journey result#L2-L3](../../evidence/ux3k/final-bdf3f447/journey/result.json#L2-L3)、[journey events#L25-L81](../../evidence/ux3k/final-bdf3f447/journey/result.json#L25-L81) |

## 实际 Activity 与页面

A/C 均为 `N/A`；下表的 `Ln/Te/Fe` 只表示证据覆盖。

| 页面 | A/C | Ln | Te | Fe | 功能/显示证据 | 最终截图与节点 |
|---|---|---:|---:|---:|---|---|
| `HomeActivity` | N/A/N/A | 4 | 4 | 4 | `2880x1620` 根、Hero 与推荐区存在；UX 导航 7 入口原生触摸几何通过，主页/分类往返通过。[ux3k result#L5-L87](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L5-L87) | [ux-home.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-home.png)、[ux-home.json#L3-L124](../../evidence/ux3k/final-bdf3f447/ux3k/ux-home.json#L3-L124) |
| 主页分类子页（仍属 Home，不是独立 Activity） | N/A/N/A | 4 | 4 | 4 | 分类 Rail 选择、前台归属和回主页有通过；最终 `pages 80/0` 不保留旧 SHA 分类失败。[pages result#L5-L21](../../evidence/ux3k/final-bdf3f447/pages/result.json#L5-L21) | [ux-category.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-category.png)、[ux-category.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-category.json)、[page-category.png](../../evidence/ux3k/final-bdf3f447/pages/page-category.png) |
| `DetailActivity` | N/A/N/A | 4 | 4 | 4 | 16:9 预览节点 `1232x693`；全屏、换源、收藏、推送入口与剧集矩阵存在；详情预览、播放器、两级返回通过。[page-detail.json#L107-L199](../../evidence/ux3k/final-bdf3f447/pages/page-detail.json#L107-L199)、[ux3k result#L337-L497](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L337-L497) | [page-detail.png](../../evidence/ux3k/final-bdf3f447/pages/page-detail.png)、[ux-detail.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-detail.png)、[page-detail-after-back.png](../../evidence/ux3k/final-bdf3f447/pages/page-detail-after-back.png) |
| `SettingActivity` | N/A/N/A | 4 | 4 | 4 | 7 个分类 Rail 均可选，右侧视口 `2100x1400`，顶部与返回节点完整；设置分类选择、开关保存和返回通过。[ux-settings-picture.json#L75-L300](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-picture.json#L75-L300)、[ux3k result#L91-L267](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L91-L267) | [page-settings-top.png](../../evidence/ux3k/final-bdf3f447/pages/page-settings-top.png)、[ux-settings-picture.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-picture.png) |
| `CarAdvancedActivity` | N/A/N/A | 4 | 4 | 4 | 画面/音效/返回三 tab 为 `580x130`；SR、时域、返回模式/皮肤/闲置等保存与逐级退出通过。[pages result#L175-L277](../../evidence/ux3k/final-bdf3f447/pages/result.json#L175-L277)、[navigation result#L79-L195](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L79-L195) | [page-advanced-top.png](../../evidence/ux3k/final-bdf3f447/pages/page-advanced-top.png)、[page-advanced-audio.png](../../evidence/ux3k/final-bdf3f447/pages/page-advanced-audio.png)、[page-advanced-back.png](../../evidence/ux3k/final-bdf3f447/pages/page-advanced-back.png) |
| `SearchActivity` | N/A/N/A | 4 | 4 | 4 | 双栏搜索、`2100x140` 输入框、`1210x660` 原生键盘、搜索/清空/光标控件存在；初始 UX 运行的输入/阳性搜索两项失败见下方，最终定点焦点复测 7/0 且 5 结果。[searchdiag result#L11-L45](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L11-L45) | [page-search.png](../../evidence/ux3k/final-bdf3f447/pages/page-search.png)、[searchdiag-before.png](../../evidence/ux3k/final-bdf3f447/searchdiag/searchdiag-before.png)、[searchdiag-after.png](../../evidence/ux3k/final-bdf3f447/searchdiag/searchdiag-after.png)、[page-search.json#L75-L175](../../evidence/ux3k/final-bdf3f447/pages/page-search.json#L75-L175) |
| `FastSearchActivity` | N/A/N/A | 4 | 4 | 4 | 结果页显示搜索完成、分词、结果卡片；普通/快速队列收敛、尾站结果、超时停止与上限通过。[searchqueue result#L11-L87](../../evidence/ux3k/final-bdf3f447/searchqueue/result.json#L11-L87) | [page-fastsearch.png](../../evidence/ux3k/final-bdf3f447/pages/page-fastsearch.png)、[search-queue-FastSearch.png](../../evidence/ux3k/final-bdf3f447/searchqueue/search-queue-FastSearch.png)、[search-queue-FastSearch.json](../../evidence/ux3k/final-bdf3f447/searchqueue/search-queue-FastSearch.json) |
| `HistoryActivity` | N/A/N/A | 4 | 3 | 3 | 历史列表 `2880x1372`，删除/清空控件各 `113x113`，本地历史卡片存在；最终 pages 只证明页面归属与返回，完整清除/恢复流程不在本组结果中。[page-history.json#L75-L225](../../evidence/ux3k/final-bdf3f447/pages/page-history.json#L75-L225) | [page-history.png](../../evidence/ux3k/final-bdf3f447/pages/page-history.png) |
| `CollectActivity` | N/A/N/A | 3 | 3 | 3 | 收藏夹标题、删除/清空控件和列表容器存在；页面归属及返回通过，卡片打开/批量清除/恢复未由本轮独立事件证明。[pages result#L337-L351](../../evidence/ux3k/final-bdf3f447/pages/result.json#L337-L351) | [page-collect.png](../../evidence/ux3k/final-bdf3f447/pages/page-collect.png)、[page-collect.json](../../evidence/ux3k/final-bdf3f447/pages/page-collect.json) |
| `LocalFileActivity` | N/A/N/A | 3 | 3 | 2 | 配置文件页有路径、目录/文件行和 `2700x1187` 列表；只证明页面与返回，真实文件打开、权限拒绝/恢复和媒体播放未覆盖。[page-localfile.json#L75-L221](../../evidence/ux3k/final-bdf3f447/pages/page-localfile.json#L75-L221) | [page-localfile.png](../../evidence/ux3k/final-bdf3f447/pages/page-localfile.png) |
| `PushActivity` | N/A/N/A | 3 | 3 | 2 | QR 图 `675x675`、推送按钮 `424x119`、应用内返回存在；未验证真实设备发现、连接、推送成功/失败恢复。[page-push.json#L75-L130](../../evidence/ux3k/final-bdf3f447/pages/page-push.json#L75-L130) | [page-push.png](../../evidence/ux3k/final-bdf3f447/pages/page-push.png) |
| `LivePlayActivity` | N/A/N/A | 4 | 4 | 4 | 直播预览 `1632x918`、频道行 `980x155`、全屏/收藏/直播设置入口存在；播放推进、全屏同实例、收藏增删、设置关闭和返回通过。[page-live.json#L97-L344](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L97-L344)、[pages result#L391-L480](../../evidence/ux3k/final-bdf3f447/pages/result.json#L391-L480) | [page-live.png](../../evidence/ux3k/final-bdf3f447/pages/page-live.png)、[page-live-channels.png](../../evidence/ux3k/final-bdf3f447/pages/page-live-channels.png)、[page-live-favorites.png](../../evidence/ux3k/final-bdf3f447/pages/page-live-favorites.png)、[page-live-settings.png](../../evidence/ux3k/final-bdf3f447/pages/page-live-settings.png)、[ux-live-fullscreen.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-live-fullscreen.png)、[ux-live-after-back.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-live-after-back.png) |

## 七个设置分类

以下均为同一个 `SettingActivity` 的右侧分类，不增加 Activity 数量。A/C 全为 `N/A`。

| 分类 | Ln | Te | Fe | 原生内容/末端证据 | 截图 |
|---|---:|---:|---:|---|---|
| 0 画面色彩与超分引擎 | 4 | 4 | 4 | 11 个画面预设、SGSR1、Temporal Smoothing；内容高 `1700`，有滚动末端。预设/renderer 与开关保存通过。[category-0 result#L109-L179](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L109-L179) | [category-0.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-0.png)、[scroll-0.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-0-scroll-0.png) |
| 1 座舱声场与音效 | 3 | 3 | 3 | 9 组 EQ/低频/声场预设、当前音频会话绑定文案；分类选择和播放页音效入口有证据，真实听感/硬件音频未验。[category-1.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-1.json) | [category-1.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-1.png) |
| 2 播放解码与后台行为 | 4 | 4 | 4 | 内容高 `2280`，两组滚动快照；后台音频最终 journey `13/0`，包含开启、关闭暂停和前台恢复事件。[journey result#L25-L81](../../evidence/ux3k/final-bdf3f447/journey/result.json#L25-L81) | [category-2.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-2.png)、[scroll-0.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-2-scroll-0.png)、[scroll-1.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-2-scroll-1.png) |
| 3 触控手势与长按倍速 | 4 | 4 | 4 | 长按 `2.0x`、左右分屏滑动、搜索/首页/历史选项，内容高 `1980`；长按切换与播放器 `2x/CANCEL` 恢复通过。[category-3 result#L199-L219](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L199-L219) | [category-3.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-3.png)、[scroll-0.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-3-scroll-0.png) |
| 4 双模应用内返回控件 | 3 | 3 | 4 | 内容高 `2480`，模式/侧/皮肤/尺寸/闲置/触感/首页根开关均有节点；navigation `43/0`。最终节点中返回球在顶部 `(79,328,243,243)` 或分类滚动快照 `(79,695,243,243)`，与左侧分类 Rail 几何相交；未证明事件因此失败。[category-4 result#L223-L267](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L223-L267) | [category-4.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4.png)、[scroll-0.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4-scroll-0.png)、[scroll-1.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4-scroll-1.png) |
| 5 订阅源与多仓管理 | 3 | 3 | 3 | 配置地址/历史/首页站源节点存在；API 弹窗内点、外拖、CANCEL、系统 Back 取消不保存通过，但真实订阅导入/保存未验。[navigation result#L217-L261](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L217-L261) | [category-5.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-5.png) |
| 6 关于 TVBox | 3 | 3 | 3 | 关于、备份恢复、清缓存入口及能力说明节点存在；本轮没有执行备份/恢复数据一致性或清缓存后恢复验证。[category-6.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-6.json) | [category-6.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-6.png) |

## 播放器

- 详情播放器与全屏播放器主控节点：预览、进度条、暂停 `260x190`、前后跳 `210x190`、画面/音效 `360x190`、更多 `140x150`，以及可横向滚动的高级控制组见 [page-player-controls.json#L601-L807](../../evidence/ux3k/final-bdf3f447/pages/page-player-controls.json#L601-L807) 和 [ux-player-more.json#L697-L943](../../evidence/ux3k/final-bdf3f447/ux3k/ux-player-more.json#L697-L943)。
- EXO/IJK 的状态、全屏进出、前后台、重入、producer/decoder 生命周期均为 `101/0`；采样 observations 中每个阶段 `state=3` 且 source/position 增长。[playback observations#L3-L72](../../evidence/ux3k/final-bdf3f447/playback/playback-observations.json#L3-L72)、[IJK observations#L75-L145](../../evidence/ux3k/final-bdf3f447/playback/playback-observations.json#L75-L145)
- 色条门禁 8/8 通过，不能替代人工观看。[pixels#L7-L80](../../evidence/ux3k/final-bdf3f447/playback/pixels.json#L7-L80)
- 截图：[EXO-playing](../../evidence/ux3k/final-bdf3f447/playback/EXO-playing.png)、[EXO-fullscreen](../../evidence/ux3k/final-bdf3f447/playback/EXO-fullscreen.png)、[EXO-reentry](../../evidence/ux3k/final-bdf3f447/playback/EXO-reentry.png)、[IJK-playing](../../evidence/ux3k/final-bdf3f447/playback/IJK-playing.png)、[IJK-fullscreen](../../evidence/ux3k/final-bdf3f447/playback/IJK-fullscreen.png)、[IJK-reentry](../../evidence/ux3k/final-bdf3f447/playback/IJK-reentry.png)、[controls](../../evidence/ux3k/final-bdf3f447/pages/page-player-controls.png)、[controls-enabled](../../evidence/ux3k/final-bdf3f447/pages/page-player-controls-enabled.png)、[more](../../evidence/ux3k/final-bdf3f447/ux3k/ux-player-more.png)、[presets](../../evidence/ux3k/final-bdf3f447/pages/page-player-presets.png)。

## 直播

直播页面、频道列表、收藏面板、直播设置和全屏/返回状态均有最终截图；原生节点证明预览 `1632x918`、频道项 `980x155`、底部操作 `120px` 高。最终结果覆盖播放推进、全屏同一 VideoView/decoder、收藏增删、设置开闭和逐级返回。[page-live.json#L271-L344](../../evidence/ux3k/final-bdf3f447/pages/page-live.json#L271-L344)、[pages result#L391-L480](../../evidence/ux3k/final-bdf3f447/pages/result.json#L391-L480)、[ux3k result#L499-L555](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L499-L555)。`暂无节目单`和`仅一条线路`属于本地直播 fixture 状态，不作为远端 EPG/源失败。

截图：[直播主体](../../evidence/ux3k/final-bdf3f447/pages/page-live.png)、[频道](../../evidence/ux3k/final-bdf3f447/pages/page-live-channels.png)、[收藏](../../evidence/ux3k/final-bdf3f447/pages/page-live-favorites.png)、[设置](../../evidence/ux3k/final-bdf3f447/pages/page-live-settings.png)、[全屏](../../evidence/ux3k/final-bdf3f447/ux3k/ux-live-fullscreen.png)、[全屏返回](../../evidence/ux3k/final-bdf3f447/ux3k/ux-live-after-back.png)。

## 弹窗与次级面板

| 场景 | 证据结论 | 最终截图/节点 |
|---|---|---|
| API/配置地址弹窗 | 面板内点、外拖、外点取消、CANCEL、系统 Back 均保持取消语义且不保存输入；未证明成功导入。[navigation result#L217-L261](../../evidence/ux3k/final-bdf3f447/navigation/result.json#L217-L261) | [api-dialog.png](../../evidence/ux3k/final-bdf3f447/navigation/api-dialog.png) |
| 返回模式/皮肤与闲置选项 | 模式、尺寸、皮肤、闲置保存及取消路径通过；视觉检查 N/A。 | [back-mode-options.png](../../evidence/ux3k/final-bdf3f447/pages/page-back-mode-options.png)、[hide-options.png](../../evidence/ux3k/final-bdf3f447/pages/page-hide-options.png)、[advanced-dual.png](../../evidence/ux3k/final-bdf3f447/navigation/advanced-dual.png) |
| 播放器画面预设/更多 | 预设、SR、补帧、音效、更多和长按速度恢复通过；“更多”有应用节点，图片人工观感 N/A。[ux3k result#L379-L497](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L379-L497) | [player-presets.png](../../evidence/ux3k/final-bdf3f447/pages/page-player-presets.png)、[ux-player-more.png](../../evidence/ux3k/final-bdf3f447/ux3k/ux-player-more.png)、[ux-player-more.json](../../evidence/ux3k/final-bdf3f447/ux3k/ux-player-more.json) |
| 直播收藏/设置面板 | 收藏增删、设置打开/关闭、一层返回通过；真实多线路/EPG未验。 | [live-favorites.png](../../evidence/ux3k/final-bdf3f447/pages/page-live-favorites.png)、[live-settings.png](../../evidence/ux3k/final-bdf3f447/pages/page-live-settings.png)、[page-live-settings.json#L526-L843](../../evidence/ux3k/final-bdf3f447/pages/page-live-settings.json#L526-L843) |

## 严重问题与复测闭环

### 已证实严重问题

没有已证实的 P0/P1。最终 pages、navigation、playback、journey 均为 0 fail；没有证据显示崩溃、卡死、无法关闭/返回、播放器主功能失效或最终后台音频开关失效。

### UX 初始 90/2 的解释

`ux3k/result.json` 初始记录两项 false：`native keyboard inserts at cursor` 位于 [L306-L309](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L306-L309)，`search positive fixture returns five results` 且 `results=0` 位于 [L318-L321](../../evidence/ux3k/final-bdf3f447/ux3k/result.json#L318-L321)。同一最终 SHA 的 `searchdiag` 身份、输入精确文本、5 条结果和 pending=0 全部通过，见 [searchdiag result#L11-L45](../../evidence/ux3k/final-bdf3f447/searchdiag/result.json#L11-L45) 及 [searchdiag README#L3-L19](../../evidence/ux3k/final-bdf3f447/searchdiag/README.md#L3-L19)。因此这两项应作为初始验收动作未先聚焦/fixture 时序的证据差异记录，不作为最终产品 blocker；也不应把它们抹成 `ux3k 90/0`。

### 几何风险，留给用户决定

最终设置截图节点显示返回球在 `(79,328,243,243)`（[page-settings-top.json#L782-L790](../../evidence/ux3k/final-bdf3f447/pages/page-settings-top.json#L782-L790)），高级设置同样为 `(79,328,243,243)`（[page-advanced-top.json#L463-L472](../../evidence/ux3k/final-bdf3f447/pages/page-advanced-top.json#L463-L472)）；设置分类 4 的滚动快照为 `(79,695,243,243)`（[ux-settings-category-4.json#L887-L895](../../evidence/ux3k/final-bdf3f447/ux3k/ux-settings-category-4.json#L887-L895)）。这些矩形与左侧分类 Rail 有几何相交，降低静态布局的可解释性，但 `navigation 43/0`、`pages 80/0` 及实际配置动作均通过，当前没有“相交导致点击被吞”的因果证据。用户需决定是否保持默认悬浮球位置/大小，或改为边缘模式；本评审不把几何相交升级为产品故障。

## 用户决定项

1. **返回球默认位置**：保留可见悬浮球并接受它与设置 Rail 的几何相交，还是默认边缘驻留/调整大小；需要真实触摸观察决定，不由本报告改产品。
2. **技术术语解释**：`SGSR1`、`Temporal Smoothing`、`EQ`、`多仓`、`双模返回`是否补白话副标题；现有节点证明文案存在，但本轮没有人工可读性评分。
3. **播放器“更多”层级**：高级操作组宽于首屏并使用水平滚动；是否增加发现性/统一文案由产品决定，当前没有证据说明主控不可用。
4. **LocalFile/Push 完整任务**：是否补真实文件打开、权限拒绝恢复、设备发现/推送失败恢复专项；当前只验页面、节点与返回。
5. **直播真实数据**：本地 fixture 的空 EPG/单线路是否足够作为 UI 验收，还是必须加入非空 EPG、多线路和切台恢复样本。
6. **证据归档**：`searchdiag` 的最终 SHA identity/result、节点与截图已加入最终 `manifest.json`；这是证据管理闭环，不是产品缺陷。

## 未覆盖与限制

- 未人工看到截图像素；A/C、美感、颜色、字体、阴影、对比、海报观感、真实遮挡均 `N/A`。
- 未做真实车机 OEM 安装、物理显示/input/density、长稳性能、功耗、硬件 MEMC/DAC 音质。
- 未覆盖外部远端源全量可用性、真实 EPG、多线路、推送端到端、本地文件实际播放、备份恢复数据一致性。
- 未做响应式 viewport、键盘/无障碍焦点全流程或 reduced-motion 检查；BrowserPreview 不提供这些能力。
- 自动断言是归档结果，不是本评审重新操作；报告未读取凭据、私有偏好、原始远端正文或播放地址。

## 交付与验证

- 写入范围：仅本文件 `car/reports/ux_redesign/CAR-17.3-3K-VISUAL-QA-FINAL-BDF3F447.md`。
- Browser：已请求打开 [final-bdf3f447/index.html](../../evidence/ux3k/final-bdf3f447/index.html)；未获得可检查像素，因此视觉评分按规范保留 `N/A`。
- Device：未操作。
- Build：跳过；本任务为只读证据评审，不改产品、不构建 APK、不提交 Git。
- 最终判断：**最终 SHA 的功能/原生布局证据有条件通过；完整视觉美观验收不签署，等待能实际查看像素的评审或真实车机复核。**
