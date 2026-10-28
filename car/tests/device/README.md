# 车机 UI 设备自检（geometry-gated）

这个目录解决一个具体的测试质量问题：**测试环境会把应用布局成竖屏，导致 UI 用例测出假结果。**

## 背景：为什么需要门禁

MuMu 实例每次被池子分配时，`wm user-rotation` 会被重置成 `free`。此时应用虽然声明了
`sensorLandscape`，实测仍会被布局成 **1620×2880（竖屏坐标）**，而 `screencap` 出的图是
2880×1620。后果：

- 返回杠（左侧竖向小棍）落到 y≈1400/1500 的位置，按横屏直觉点/拉必然落空
- 看起来像"返回杠失效/拉不动"，其实是**布局坐标系与屏幕坐标系不一致**

这类问题如果不拦，会把环境问题记成产品缺陷，测试结论完全不可信。

## 门禁做了什么

`run_car_ui_checks.ps1` 在跑任何用例之前，最多重试 6 次：

1. `wm size reset; wm density reset`（禁止 override，显示门禁本身也要求无 override）
2. `wm user-rotation free` → 再 `lock 1`，并固定 `accelerometer_rotation=0`、`user_rotation=1`
3. 校验三项全部成立才放行：
   - `dumpsys display` 的 `mCurrentDisplayRect` 为 `Rect(0, 0 - 2880, 1620)`
   - `dumpsys activity top` 里应用配置含 `w2880dp` / `h1620dp` / `land`
   - 实拍截图尺寸为 2880×1620
4. 任一项不满足就重启应用重试；6 次仍不满足 → **直接 `exit 2`**，绝不带着错误几何继续

## 用例

| 用例 | 断言 |
| :--- | :--- |
| case1 返回杠可见/隐藏/唤醒 | 可见时左边缘存在 ≥150px 高亮竖条；6 秒后消失（≤3px）；触摸后重新出现 |
| case2 设置入口 | 能从首页按文本定位「设置」→ 进入 SettingActivity → 页面含「高级」 |

前置：脚本会先尝试把返回组件归一化为「边缘拉出 + 4 秒隐藏」（进设置→高级→点模式/隐藏行）。
归一化失败只告警，不静默通过。

## 用法

```powershell
powershell -ExecutionPolicy Bypass -File car/tests/device/run_car_ui_checks.ps1 `
  -Apk <apk 路径>
```

- 自动通过 `mumu-pool.py` 租实例（owner 自动生成），结束一定 `release`
- 传输/截图用 MuMu 自带 adb（SDK adb 对 MuMu 会卡在 push）
- 输出：截图、`*.raw`、`adb.out/err`、`normalize.xml` 都在 `%PI_SCRATCH_DIR%\car-ui-check-*`

## 退出码

| 码 | 含义 |
| :--- | :--- |
| 0 | 全部通过 |
| 1 | 有用例失败（附具体项） |
| 2 | 几何门禁失败（环境不可信，测试已中止） |
| 3 | 环境/依赖缺失 |

## 已知限制

- **定制验收镜像**（首页是"本地合成素材"网格）没有「设置」入口，前置归一化会告警，
  此时 case1/case2 的结论不可用，需要换普通配置的实例。
- Android 15 镜像的 `uiautomator` 缺 `libnativeloader.so`，`uiautomator dump` 直接报
  `CANNOT LINK EXECUTABLE`；脚本已改为 OCR 定位，不再依赖它。

## 定位方式（2026-10-23 更新）

- 截图 → `tools/ocr.ps1`（Windows.Media.Ocr）→ 按文字坐标点击；
 - 需要**精确点击坐标**时用 `tools/ocr_words.ps1`：逐词输出 `x<TAB>y<TAB>w<TAB>h<TAB>中心x<TAB>中心y<TAB>文字`，中心点可直接喂 `input tap`；
 - 需要**证明控件真的画出来了**时用 `tools/px_probe.ps1`：`-Point "x,y;x,y"` 取样颜色；不带 `-Point` 则按列统计贴边彩色像素，命中列打印 `avg=#RRGGBB`（返回杠实测 `#1F698D`）；
 - 连续两帧截图若哈希相同 = 画面静止（视频没在播），别把它当成"控件没画"。
- 设置页用 `input keyevent 82`（MENU）打开，不再依赖首页「设置」按钮；
- 租到实例后先等 `sys.boot_completed=1`，adb 掉线自动重连一次。

要求 Windows 10+ 已安装中文 OCR 语言包；否则 `ocr.ps1` 返回 `NO_OCR_ENGINE`，脚本应按 exit 3 处理。
