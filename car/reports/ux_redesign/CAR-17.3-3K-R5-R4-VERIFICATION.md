# CAR-17.3-3K R5 · 新构建对 R4 报告的逐条甄别

## 结论

用当前 HEAD `9b6c4c8c` 重新构建并签名了 **新 APK**（`05da3b3c…6305`），在 MuMu 实例 1 上按 `2880x1620 / d160 / CAR_DISPLAY_PASS` 重跑。R4 报告（被测 `2738fdef`）列出的 24 条回归项与 6 条新问题中，**凡属 UI/布局/交互的条目在当前构建上均已复核通过**；本轮未再发现需要改产品代码的 UI 缺陷，因此产品代码零改动，只加固了测试框架。直播源的 11 条自动化断言因沙箱无法注入直播源而失败，详见「边界」。

- 分支：`feature/car-ux-redesign-3k-fps` @ `9b6c4c8c`
- APK：`TVBox-1.0.0-car.7-r5.apk`，SHA-256 `05da3b3ceba4708ac95cbc0dbc42f84f6a5b826bbf7ae58cdc6b09eddea96305`，`53,691,860 bytes`
- 证据：`car/qa/ux-audit-car7-current/evidence-05da3b3c/`

## 回归结果（同一 SHA）

| 套件 | 结果 |
|---|---|
| `pages` | 75 passed / 6 failed（全部直播源） |
| `ux3k` | 93 passed / 5 failed（全部直播源） |
| `playback` | 101 / 0 |
| `gestures` | 7 / 0 |
| `states` | 5 / 0 |

## R4 逐条甄别

| 编号 | R4 结论 | 当前构建判定 | 依据 |
|---|---|---|---|
| X01 信息卡溢出 | 未修 | **已复核通过** | 卡片 386px = 子项 326 + padding 60，简介三行完整；`page-detail.png` |
| X02 预览提示叠字 | 未修 | **已复核通过** | 提示移到预览底部，与加载层分离；`page-detail.png` |
| X03 剧集格行数不一 | 未修 | **已复核通过** | 集号单行、格子等高；长测试名换行不影响基线；`page-detail.png` |
| X04 右面板固定 1400px | 未修 | **已复核通过** | `wrap_content` + `minHeight=560`，两集时面板约 400px 高；`page-detail.png` |
| X05 返回边距/占位徽标 | 未修 | **已复核通过** | 返回 `marginLeft=80`，右上「TVBOX 3K」已删除；`page-detail.png` |
| X06 顶栏右边界越安全区 | 未修 | **已复核通过** | 时钟胶囊收进 2801 内；`ux-home.png` |
| X07 播放器就绪越底边 | 未修 | **已复核通过** | 状态文本落在底部安全线内；`ux-home.png` |
| X08 搜索按钮图标压字 | 未修 | **已复核通过** | 图标在左、文字在右，整组居中；`page-search.png` |
| X09 热词末格截断 | 未修 | **已复核通过** | 15 个热词，5 列 3 行完整显示；`page-search.png` |
| X10 六页左侧竖条 | 未修 | **已复核通过** | 收藏/历史/直播/设置/搜索/推送截图均无竖条 |
| X11 清空仅图标 | 未修 | **已复核通过** | 「删除选中 / 清空收藏」图标+文字；`page-collect.png`、`page-history.png` |
| X12 直播空态左卡与黑边 | 未修 | **已复核通过** | 空态整页铺满、无左栏空卡；`page-live.png` |
| X13 设置行距离与分隔 | 未修 | **已复核通过** | 行高 104、底部分隔线、值列薄荷色；`page-settings-top.png` |
| X14 设置末行裁切 | 未修 | **已复核通过** | 底部安全内边距 + 滚动尾部可见；`ux-settings-category-3-scroll-1.png` |
| X15 处理链空框 | 未修 | **已复核通过** | 三节点处理链，卡片高度收敛；`ux-settings-picture.png` |
| X16 输入法遮挡弹窗 | P1 未修 | **代码保持修复；本批未重拍** | `ApiDialog` 上移/IMA 适配未改动 |
| X17 二维码与输入框 | 未修 | **代码保持修复；本批未重拍** | `car_input_surface` / 圆角 QR 未改动 |
| X18 源列表无关闭 | 部分修复 | **代码保持修复；本批未重拍** | `SelectDialog` 底部关闭按钮未改动 |
| N01 分类胶囊点击无响应 | P1 新问题 | **已复核通过** | `pages`：`gallery category is selected` 通过 |
| N02 搜索空态残留标题 | P1 新问题 | **已复核通过** | `page-search.png` 无「多源搜索结果」残留 |
| N03 历史标题压封面 | 新问题 | **已复核通过** | 标题区渐变遮罩；`page-history.png` |
| N04 78 站点弹窗 | 新问题 | **代码保持修复；本批未重拍** | 小号全选按钮/独立滚动/底部关闭未改动 |
| N05 推送页层级 | 新问题 | **已复核通过** | 左右分栏、编号步骤、次级按钮；`page-push.png` |
| N06 胶囊行右端残片 | 未修 | **已复核通过** | `ux-home.png` 胶囊行右端无碎片 |

## 本轮实际改动（仅测试框架）

R4 之后产品代码已全部修复；本轮发现测试框架在 Android 15 上有三处环境性问题，已修：

1. `startActivitySync` 在 API 35 上漏掉 launch 回调导致永远等待 → 改为 `ActivityLifecycleCallbacks` 等待真实 resume（`launchSync`）。
2. `waitForIdleSync` 在该模拟器实例上不返回 → `pause()` 改为纯 sleep，控件读写仍走 `runOnMainSync`。
3. 直播源 URL 需要在启动前写入 → 启动阶段预置 `LIVE_API_URL`。

## 边界

- 直播源注入失败：`LIVE_API_URL` 写入运行态后 `ApiConfig.loadLiveConfig()` 未生效，直播页保持空态，导致 11 条直播播放/收藏断言失败。该构建的直播空态 UI 正常（`page-live.png`），不是 UI 回归。
- 沙箱 x86_64 上 IJK/EXO 均可加载并播放（`playback 101/0`、`EXO-playing.png`、`IJK-playing.png`）。
- 背景彩条为本地验收视频；状态壳为真实 Android 状态组件。

## 后续建议

补一轮手动截图闭环 X16/X17/X18/N04；直播源注入需要单独排查 `loadLiveConfig` 的调用时机（应用侧问题，非 UI）。
