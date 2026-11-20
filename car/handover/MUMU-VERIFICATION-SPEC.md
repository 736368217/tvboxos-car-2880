# 零跑 B01 设备验收规范

本项目仅服务车主的零跑 B01。产品和构建边界以 [README](../../README.md) 为准，不使用上游多机型矩阵。

## 1. 验收对象

- 真车：SA8295P、Android API 32、`arm64-v8a`、2880×1620 横屏、density 160。
- 产物：`:app:assemblePythonRelease`。调试也只用同一 B01 的 `pythonDebug`。
- MuMu：仅做代理功能验证。API 35 的成功不能证明真车的 OEM 安装、后台、窗口和音频行为。
- 每轮记录源提交/工作树状态、APK SHA-256、包名、版本和签名；设备对 `pm path` 对应文件重新算 SHA。

## 2. 设备所有权与用户数据

先确认会话持有实例池独占租约。只操作自己的实例，结束释放，保留 MuMu 主程序。
优先用临时 Android 用户隔离测试；否则先备份应用偏好和数据库，结束恢复并比对，不执行 `pm clear` 清掉机主订阅、历史或收藏。

## 3. 显示门禁

通过 MuMu 插件及 system_server namespace/classpath wrapper 设置：

```sh
wm size reset
wm density reset
wm scaling auto
settings put system accelerometer_rotation 0
settings put system user_rotation 1
wm user-rotation lock 1
wm fixed-to-user-rotation enabled
```

然后运行仓库只读门禁 `car/tests/device/verify_car_display.sh`。必须同时满足：无手动 size/density override，d160，`lock 1`，实际显示与触摸映射完整覆盖 2880×1620，PNG 也是 2880×1620，并出现 `CAR_DISPLAY_PASS`。
模拟器重启后重新验门禁；不能沿用旧结论。真车分辨率不随意修改。

## 4. 内容与播放

验收时在隔离用户导入 `https://clun.top/box.json`；备用订阅见实际设置和既有版本记录。远端脚本只在 Android 执行。

“订阅加载”须有站点数量、非空分类/影片计数。“实际播放”另需视频尺寸、两次递增位置及对应截图，不用分类成功替代起播。
本地合成 H.264/AAC 用于复跑播放器、增益和生命周期；真实站源另报，不能把 fixture 通过扩大为所有站点通过。

日常覆盖：

1. 首页、设置、搜索、详情和常用触摸操作；缺少可用站源时记录具体阶段。
2. 小窗和全屏徽标、全屏→详情→首页返回、边缘返回。
3. HOME/失焦真暂停，重新取得焦点只恢复由系统导致的暂停；自家弹窗不停播，手动暂停不被恢复。
4. 进度和历史一致，显式退出不复活，冷启动恢复的保存/实际位置对得上。
5. >100% 应用音量由真实首批 PCM 输入输出比例证明；换集与重新建引擎分别验证。
6. R2 查询成功即返回；失败后才试 GitHub CDN。备用节点验证版本 JSON、实际 APK、Range 和签名，不以 HTTP 200 单独判通过。

独立原生套件在 `car/tests/device/r82/`，使用本轮 APK 的 R8 mapping 构建。r82 是测试包名称，不是要求安装旧产品版本。逐条检查断言及最终 result，不能只看 `am instrument` 的进程退出码。

## 5. 构建与交付门禁

只跑 B01 Release、相关主机测试和受影响设备场景。命令见 README 和 `car/ci/README.md`。
旧报告中的 API 19/23、32 位、Java-only 变体和 Play 上架要求不属于当前产品门禁。保留实际影响 B01 的告警和风险，不能降低相关断言阈值凑通过。

发布以 R2 为主、GitHub Release 为备用。仅在用户明确要求发布时运行发布脚本，要求 notes、摘要和远端回读。旧 `dl.z736.vip`、阿里云发布链永久退役。

结束前归档本轮证据，恢复显示、音量和用户，确认机主 APK 未改变、测试用户/包已移除，再归还租约。工作区原有未跟踪文件不作清理对象。
