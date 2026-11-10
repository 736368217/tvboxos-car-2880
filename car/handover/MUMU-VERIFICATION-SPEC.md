# MuMu 验收环境规范（强制，2026-11-09 补）

> 起因：r73 交付时写成「本机 MuMu 无可用点播源，未能起播」，但用户此前明确指定过订阅源，
> 且 `DELIVERY-ORIGINAL-APP.md` §12 已记录「干净实例 vm2 配 `https://clun.top/box.json`
> 后真实订阅加载、Python 源解析、详情页小窗、边缘拉动全部通过」。
> 把某台被清空配置的临时设备状态当成普遍事实，是流程缺失，不是记忆问题。**本文件即规范。**

## 1. 每台用于播放/详情/音量验收的 MuMu，开机后必须先做

```bash
adb -s <SERIAL> shell settings put system accelerometer_rotation 0
adb -s <SERIAL> shell settings put system user_rotation 1
adb -s <SERIAL> shell wm user-rotation lock 1
# 确认横屏：dumpsys display 必须是 Rect(0,0-2880,1620)，宿主日志 w=2880 h=1620
adb -s <SERIAL> shell dumpsys display | grep -i 'mCurrentDisplayRect\|DisplayDeviceInfo'
```

**不锁横屏会出现 1620x2880 竖屏坐标，导引小棍落到 y≈1500，按横屏直觉点/拉必然落空**
（§12.1 已实锤）。真车固定横屏，不会重现，但 MuMu 不锁就会误判成「功能失效」。

## 2. 订阅源（用户此前指定，必须写进设备，不得再说「无源」）

- 主源：`https://clun.top/box.json`（97 站 / 44 个 PY 站，已实测加载成功）
- 备用：`https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json`

配置路径：App 内「设置 → 配置地址 → 点播」填入主源并确认，等首页分类出现
「热门电影 / 热播剧集 / 热门动漫 / 热播综艺」后再开始验收。

判据（三项同时满足才算「有可用源」）：

1. 首页不再是「豆瓣热播」占位卡片，而是真实分类；
2. `PyLoader` 或 `JarLoader` 日志出现站点加载成功；
3. 点任一影片能进 `DetailActivity`，点预览/播放能出现 `CarAudioGain` 日志。

**任何一台设备如果确实无源**，必须在报告里写明「该实例的订阅配置为何为空、是哪一步被清空」，
并换一台按本节重配，**禁止直接写成「本机无可用点播源」这种全局结论**。

## 3. 验收脚本必须记录的开场状态

```
设备 index / adb / owner：
dumpsys display 分辨率与 rotation：
配置地址（点播 / 直播）：
首页分类列表：
是否成功进入 DetailActivity / PlayFragment：
```

## 4. 每次发布必须带更新说明（用户明确要求）

现状（r73 之前）：`dl-publish.sh` 生成的 `latest.json` 只有
`software / version / files / releasedAt`，App 更新弹窗显示「该版本没有填写更新说明」。

规范：

1. `publish_release.py` 增加第四个参数 `--notes <文件>`（UTF-8 纯文本，2–5 行、要点式）；
2. 服务端 `dl-publish.sh` 把说明写进 `latest.json` 的 `notes` 字段；
3. `AppUpdate` / `UpdateDialog` 读取并展示 `notes`，为空时再显示兜底文案；
4. GitHub Release 的 notes 与官方通道 `notes` 保持一致；
5. **没有 notes 不许发版**。说明写「改了什么 / 用户要注意什么」，不写内部实现细节。

## 5. 发布前自检清单

- [ ] 订阅源与横屏已按 §1/§2 配置（若本轮涉及播放/详情/音量验收）
- [ ] `latest.json` 含非空 `notes`
- [ ] GitHub Release notes 与之一致
- [ ] APK digest（本地 / 官方 / GitHub）三者一致
- [ ] MuMu 租约已释放，未碰 `do_not_touch` 实例
- [ ] 报告中「未验证」项写明原因，不编造结论
