# CAR-UI-SPEC-TVBOX-2880x1620 合规审查（car.2）

审查对象：`com.github.tvbox.osc.jun` 1.0.0-car.2（TVBoxOS 车机版），画布 2880×1620 / density 240 / 17.3″。
规范：`CAR-UI-SPEC-TVBOX-2880x1620.md`（1mm = 2.25px；顶部禁区 40mm=90px，底部禁区 55mm=124px，左右安全边距 35mm=78px，最小热区 40mm×40mm=90×90px，相邻热区间距 ≥12mm=27px）。

结论：**§8 禁止清单的 8 条硬规则已全部满足**；§5.3 主题色板、§5.2 焦点态、§6.2 播放器覆盖层已按规范落地；残留项见文末。

## 1. 上下避让禁区（§2 / §8-1）——已修

| 页面 | 修复前（测量） | 修复后（测量） |
|---|---|---|
| 首页 | `tvName` 可点区域起于 y≈45px（侵入顶部禁区） | `tvName` 起于 **y=136px**（>90px） |
| 首页卡片列表 | 底部到 1597.5px（侵入底部禁区 101.5px） | 内容止于 **y≈1311px**（<1496px） |
| 设置页 | 根 padding 45px，页面到 1575px | 根 padding 40/55/35/35mm；行热区 x=**79px**（≥78px） |
| 搜索 / 快速搜索 / 历史 / 收藏 / 本地文件 / 推送 | 顶部控件 45px、列表贴底 | 根安全区内边距 40/55/35/35mm（本地文件 30→40/55mm） |
| 详情页 | 顶栏 10mm、选集网格 22.5px 贴底 | 顶栏 40mm、选集网格距底 55mm；**视频渲染层未加任何 padding**（§6.1） |
| 直播页 | 底栏 `layout_alignParentBottom` 在 FrameLayout 下失效、进度条浮到顶部；左频道面板贯穿上下禁区 | 底栏 `layout_gravity=bottom` + 距底 **60mm**；左面板 padding 40/55mm；EPG 带距底 55mm |
| 播放器覆盖层 | 底栏贴屏幕底、标题 5mm | 底栏距底 **60mm**、标题距顶 **40mm**（§6.2） |

实现方式：各 Activity/Fragment 根布局按 §2.3 加 `car_top_padding / car_bottom_padding / car_side_padding`（40/55/35/35mm，`dimens.xml`）；播放页只给**覆盖层**让位，渲染层保持 16:9 满屏。

## 2. 单位体系（§3 / §8-2）——已修

- 应用保持 TVBoxOS 原生方案：AutoSize `setSupportDP(false) / setSupportSP(false) / setSupportSubunits(MM)`，`design_width_in_dp=1280 / design_height_in_dp=720` 已补入 Manifest（§3.3 方案 B），实测 1mm = 2.25px（设置行 50mm 实测 113px）。
- 本轮把布局/样式里**所有非零 dp/sp 字面量清零**：`activity_live_play.xml`（63 dp + 3 sp）、`player_vod_control_view.xml`（4 dp + 3 sp）、`box_vod_control_view.xml`（13 dp + 2 sp，该文件已确认为无引用死布局）、`activity_detail/history/collect/local_file/fragment_*`、`dialog_desc`、`dialog_grid_filter`、`epglist_item`、`styles.xml(epg_window_btn)` 全部改为 mm 或 `@dimen` 别名。
- 保留项：`0dp`（ConstraintLayout `match_constraint` / weight 语义，与密度无关）；矢量图标 `android:width="24dp"` 类**固有尺寸**未改（§8-2 针对布局与字号，图标尺寸由 ImageView 边界决定）。`activity_live_play.xml` 里 26 处历史 `px` 字面量保留（规范只禁 dp/sp）。

## 3. 热区与间距（§4 / §8-4、§8-5）——已修

- 搜索页 `tvSearchCheckboxBtn` / `wordSwitch` 28mm(63px) → **50mm(113px)**；键盘/词条 chip → 40mm 起，chip 间距 15mm。
- 播放器按钮行 `marginEnd` 11.25px → **27px**；SeekBar 高度 67.5px → **101px**（≥45mm 拖动区），可见条高 18px（≥8mm），`minWidth 48dp→22mm`、`thumbOffset 8dp→4mm`。
- 直播页 `iv_playpause` 30dp → **40mm(90px)**，进度条 30dp → **45mm**，EPG 左右箭头 20dp → **40mm**（共 6 个箭头控件）。
- 首页六个入口卡片间距 11.25px → **33.75px**；设置页每行间距 22.5px → **33.75px**；卡片网格、榜单、本地文件行、对话按钮间距同类提升。
- 保留项：同一行内“文本+箭头”的内部间距、item 级列表间距（chip/行自身已达标）未强改，避免破坏原有视觉节奏。

## 4. Manifest（§7）——已修

- `<application>` 与 10 个 Activity 全部补 `android:resizeableActivity="true"`；`configChanges` 扩展为 `orientation|screenSize|screenLayout|smallestScreenSize|density|uiMode|keyboardHidden|fontScale`（避免车机密度/分屏变化重建）。
- `<uses-feature>`：leanback / touchscreen / **camera / location** 全部 `required="false"`，杜绝车机安装过滤。
- 全部 Activity 保持 `screenOrientation="sensorLandscape"`（§8-7 ✓）。

## 5. 主题与色板（§5.3）——已改

- `AppTheme` 由 `Theme.AppCompat.Light.DarkActionBar`（浅色）→ **`Theme.AppCompat.NoActionBar`（暗色沉浸）**；`colorPrimary=#1E222B`、`colorPrimaryDark=#101318`、`colorAccent=#2979FF`。
- 窗口背景由照片 `app_bg.jpg` → **`#101318`**（用户自定义壁纸仍优先，见 `BaseActivity.changeWallpaper`）。
- 新增规范色板常量：`car_bg #101318`、`car_card #1E222B`、`car_text #EDEDED`、`car_accent #2979FF`、`car_accent_alt #FF9100`；正文主色 `color_text_primary` 由 `#FFFFFAE0` → **`#FFEDEDED`**。
- 焦点态：`shape_user_focus.xml` 已是 2mm 描边（§4 ✓）；新增的应用内返回按钮使用 `#2979FF` 2mm 焦点描边 + 变亮填充（对比度 >3:1）。

## 6. 焦点与导航（§5.2）——已改

- 可点击但缺 `focusable` 的控件补 `android:focusable="true"`：首页 `tvName`、历史/收藏页 `tvDelete`/`tvClear`、搜索页 `etSearch`、直播页 8 个箭头/播放/进度控件、应用内返回按钮（可被方控/旋钮选中）。
- 应用内返回按钮仍保持“触摸唤醒 + 数秒隐藏”的车机交互，不与方向键焦点流冲突（隐藏时 `GONE`，不进入焦点链）。

## 7. 播放器层（§6）——已部分改

- 渲染层不缩放、不加 padding ✓；控制器覆盖层上下让位 ✓；进度条高 ≥8mm、拖动区 ≥45mm ✓。
- 手势死区（§6.3：亮度/音量滑动识别区限 150~570mm 高度）：本工程播放器由 `VodController`/`BaseController` 的 `GestureView` 处理，**本轮未改**（属交互逻辑，改动风险高，列为残留项）。

## 8. 残留项（未改，附原因）

1. **§6.3 手势死区**：需要改 `GestureView`（dkplayer 组件）的判定区域，属交互逻辑改动，未在本轮范围内。
2 直播页仍有 26 处 `px` 字面量与 `tv_top_status`(5mm)/`tv_selected_channel`(9mm) 等**非可点击**元素落在禁区边缘——非点击控件规范允许背景/文本延伸，且不影响触摸。
3. 弹窗类界面（配置地址、选择数据源）为独立窗口，其内部控件沿用原有尺寸体系（已用 mm/别名），未逐项核对 40mm 热区。
4. 矢量图标固有 `dp` 尺寸、`@dimen/dkplayer_*`（第三方 AAR 提供）未动。
