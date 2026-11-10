# r77：删除「音量自动对齐 / 动态电平平衡」

## 1. 结论

这个功能**技术上确实会改变声音**，但不该保留：

- `AutoLevel` 每约 1 秒按音频峰值计算 `makeup`，安静片段逐步抬升、响片段逐步回落；
- r76 又改成「用户手动调音量后也持续叠加」，导致固定 300%/500% 设定上再叠加一个随时间变化的系数；
- 用户实测「声音忽大忽小」，与算法的 attack/release 行为一致；
- 它无法真正把 App 内视频 PCM 与系统音乐输出对齐，因为音乐不经过这个 processor；
- 用户明确说明「不在我的规划内」并要求「没用不用修，直接删除」。

因此本轮不修补、不加开关，而是**彻底删除功能**。

## 2. 删除范围

- 删除 `player/.../exo/AutoLevel.java`：峰值测量、makeup、组合封顶、软限幅静态封装全部移除。
- 删除 `app/.../car/AutoLevelPreference.java` 与 `auto_level_enabled` 偏好。
- `AdvancedSettingsDialog` 删除「音量自动对齐」整行开关，高级设置恢复为：返回组件四选项、亮度手势、自动隐藏、诊断日志。
- `ExoPlayer` 不再向 `AudioGainProcessor` 下发自动对齐开关；`registerAudioGainProcessor()` 只同步固定用户增益。
- `CarEnhance` / `BaseController` / `AudioGainProcessor` 删除 `auto_level`、`makeup`、`target_peak`、`auto_samples` 等动态对齐字段；日志改标 `auto_level=removed`、`makeup=removed`。
- 删除 `AutoLevelHostTest` / `AutoLevelProcessorHostTest`，新增 `FixedGainHostTest`：连续 8 个约 1 秒观察窗，固定 300% 不漂移，0% 静音，500% 仍放大，共 26 checks。

## 3. 保留行为

- 用户手动音量：0–500%，5% 一档，不改系统音量。
- PCM 链路只应用一个固定 `userGain`；软限幅仍保留在 `AudioGainProcessor` 内，避免 500% 硬削顶。
- 返回组件、封面缓存、更新通道、诊断日志等非音量功能全部不动。
- 日志仍保留 `user_gain / combined / peak / peak_dbfs / measured_ratio`，用于判断音轨电平，但不再据此自动改增益。

## 4. 验证

- `git diff --check`：PASS。
- Gradle `:app:assemblePythonRelease`：BUILD SUCCESSFUL。
- host 全量 PASS：`FixedGain 26`、`AudioVolumeRouting 4958`、`VolumeBoostMath 25`、`VolumeLimit 18`、`AudioGainProcessor`、`EdgeGeometry 10` 等。
- MuMu r77 实机：
  - 锁横屏 2880×1620，配置 `https://clun.top/box.json`；
  - 高级设置截图确认无「音量自动对齐」行；
  - 起播日志 `auto_level=removed`、`max_gain=5.0`、`makeup=removed`，`set_gain 1.0 -> 5.0` 后固定保持 5.0；
  - 无 FATAL。
- 版本号：vc49 / `3.1.0-car.7-r77`。

## 5. 已知边界

- 非 PCM 初始化阶段仍会短暂出现 `app_gain request=100%`，这是播放器尚未报告 PCM 能力时的初始化值；用户偏好由 `reapplyAppVolume()` 在 PREPARED/PLAYING 恢复。该行为在 r77 前已存在，本轮没有扩大修改范围。
- 车机零跑 B01 听感 A/B 未做；删除动态增益后，音量完全由用户手动档位决定。
