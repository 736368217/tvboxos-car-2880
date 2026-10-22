# TVBox Car 3K 全局视觉系统验收

## 结论

已完成从“深空黑曜组件堆叠”到“更安静、更有主次的车机桌面”的全局样式重构。核心变化不是换颜色，而是统一信息层级、焦点语义和返回入口：

- 中性深绿底层 + 薄荷焦点/成功 + 紫色主操作 + 琥珀提醒 + 玫红错误 + 蓝色信息。
- 首页导航改为连续轨道；播放器就绪状态改为轻量状态文本。
- 设置页分区标题改为无卡片文本，设置行改为连续透明行，避免卡片套卡片。
- 详情、直播、搜索明确区分主操作与次级操作。
- 应用内返回球缩小为约 100px 视觉直径；顶部页面返回仍保留。
- 全屏播放器保留唯一常驻返回入口，隐藏控制层时仍可见。

## 身份与回归

- APK SHA-256：`1b0eb5e2dfac4af3dd1c18c6aee76ffeab388f84bdc0f14f135ba4cff580dd61`
- 大小：`53,675,084 bytes`
- 设备：独占 MuMu 实例 1，`CAR_DISPLAY_PASS`，`2880x1620`，density `160`
- `pages`：`80/0`
- `ux3k`：`98/0`
- `playback`：`101/0`
- `gestures`：`7/0`
- `states`：`5/0`（状态壳组件截图，不把它当作网络事件录像）

完整结果：`car/qa/ux-audit-car7-current/evidence-1b0eb5e2/`

## 实际截图

- 首页：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/home.png`
- 分类：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/category.png`
- 搜索：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/search.png`
- 搜索结果：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/search-results.png`
- 详情/选集：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/detail.png`
- 直播：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/live.png`
- 频道列表：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/live-channels.png`
- 设置：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/settings.png`
- 高级功能：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/advanced.png`
- 收藏空状态：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/collect-empty.png`
- 历史：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/history.png`
- 本地文件：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/local-file.png`
- 多端推送：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/push.png`
- 聚合搜索：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/fast-search.png`
- 状态壳：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/state-empty.png`、`state-error.png`、`state-offline.png`、`state-loading.png`
- 播放器：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/player.png`
- 播放设置：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/player-more.png`
- 高级设置：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/player-advanced.png`
- 暂停态：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/player-paused.png`
- 隐藏控件：`../../qa/ux-audit-car7-current/evidence-1b0eb5e2/screenshots/player-hidden.png`

可交互浏览的 HTML 报告：`car/reports/ux_redesign/CAR-17.3-3K-GLOBAL-STYLE-REPORT.html`

## 边界

截图背景彩条是本地验收测试视频，不是产品 UI。远程源、真实影片极亮/极暗场景与长时间驾驶稳定性属于独立验收范围；本次没有修改包名、版号、签名或第三方源。
