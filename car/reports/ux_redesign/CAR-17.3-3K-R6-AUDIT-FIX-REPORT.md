# TVBox 车机 3K · R6 实机审计修复报告

- 报告日期：2026-10-24
- 最终 APK：`TVBox-1.0.0-car.7-r6f.apk`
- SHA-256：`1e7a1cebf553ab82c04208f55a13323e3e32db1515293185f331af34c6b022ae`
- 大小：`53,691,917` bytes
- 包名 / 版本：`com.github.tvbox.osc.jun` / `1.0.0-car.7`（versionCode 8）
- 设备：MuMu 实例 1，`2880x1620`，density `160`，`CAR_DISPLAY_PASS`

完整 HTML（含 18 张修复前后截图）：`car/reports/ux_redesign/CAR-17.3-3K-R6-AUDIT-FIX-REPORT.html`

## 回归

| 套件 | 结果 |
|---|---|
| `pages` | 81 / 0 |
| `ux3k` | 98 / 0 |
| `playback` | 101 / 0 |
| `gestures` | 7 / 0 |
| `states` | 5 / 0 |

五份 identity 的 `sourceApkSha256` 均为 `1e7a1ceb…6b022ae`。

## 九条修复

1. **R6-01（P1）分类失败不再静默**：点击即移动胶囊选中态；请求失败走“暂时没有内容 + 重新加载 + 返回上一页”；分类列表未就绪时记住跳转意图，数据到达后自动进入分类页。
2. **R6-02（P1）详情页先有壳**：返回与标题移出 LoadSir 替换区，加载/错误/空态都保留返回入口。
3. **R6-03（P2）播放器选项一致**：仅当播放器列表实际包含 IJK 时显示“IJK 解码方式 / IJK 缓存”。
4. **R6-04（P2）搜索空态收口**：无历史时隐藏“最近搜索”空块，只留面板中央的单一空态。
5. **R6-05（P2）搜索结果层级**：副信息提亮到 26px，站点名降为 20px 的“来源 · …”；列表底部增加 120px 渐隐。
6. **R6-06（P2）导航安全区**：容器 x=80、图标 x=96、文字 x=144，品牌标 x=96，状态条上移至 y=1428；顶栏时钟与搜索页“清空”各右缩 20px。
7. **R6-07（P2）收藏空态**：隐藏“删除选中 / 清空收藏”，只保留“去搜索影片”。
8. **R6-08（P2）推送对齐**：去掉右栏 paddingTop，左右从 y=220 开始；二维码卡片改 `wrap_content`。
9. **R6-09（P2）详情预览**：提示改为底部居中小胶囊，播放器未出画面时隐藏，避开右下“广告”角标。

## 证据

- 截图与测试结果：`car/qa/ux-audit-car7-current/evidence-1e7a1ceb/`
- 关键截图：
  - `page-category.png`、`page-detail.png`、`page-detail-after-back.png`
  - `page-search.png`、`ux-search-results.png`
  - `page-collect.png`、`page-push.png`
  - `ux-home.png`、`ux-settings-category-2.png`
  - `state-empty/error/loading/offline.png`

## 边界

- 截图中的彩条、灰块、二维码是本地 fixture 测试视频/图片，不是产品 UI。
- `state-*` 由实际 Android 状态组件生成，不是网络事件录像。
- 冷启动后立即点“分类专题”时，`pages` 出现过一次同 SHA 的单点时序抖动；预热后重跑 81/0，最终取该次。产品侧已增加“未就绪时记住跳转意图”。
- 未改包名、版号、签名、订阅源、第三方脚本、返回链与全屏播放单一返回入口。
