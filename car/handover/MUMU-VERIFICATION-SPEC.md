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

## 4. 推送代码 ≠ 发布更新；GitHub Release 是唯一发布通道

> 2026-11-12 起：官方 `dl.z736.vip` / `tvbox-car` / `TVBox` 通道**已废弃**。
> 服务器 121.40.69.97 的 22 / 443 / 4450 全部不可达，本机与 GitHub Actions 境外 runner
> 均实测不通；不要再执行 `publish_release.py`、`dl-publish.sh`、SMB 上传或 `latest.json` 校验。
> r81 起 `AppUpdate.OFFICIAL_CHANNEL_ENABLED=false`，App 直接查 GitHub Release 多通道。

规范：

1. 用户确认实现/报告后，默认流程是 **提交 → 合入 main → 推进 versionCode/versionName → 构建签名 → 发布 → 校验**，不能停在「已推送」。
2. 每次发布写 **2–5 行短说明**（约 100–250 字）：第一行 `rNN 更新说明`，后续用 `- ` 要点写「改了什么 / 用户要注意什么」；不写内部实现细节，不写空话。
3. 用 `gh release create v<version> --repo 736368217/tvboxos-car-2880 --target <完整 40 位 SHA> --title ... --notes-file <UTF-8 文件> <APK>` 发布；没有 `--notes-file` 不许发版。
4. `--target` 必须是完整 40 位 SHA，短 SHA 会报 `HTTP 422`。
5. 签名统一用 `car/ci/debug.keystore`（证书 `2FEB63C5…FA9C`）；云端打包用 `.github/workflows/build-release.yml` 的 `workflow_dispatch`，签名后有证书硬校验。
6. 发布后校验：`gh release view` 的 tag / target / asset digest 与本地 APK 一致，notes 非空且为 2–5 行。
7. **没有 notes 不许发版；没有 Release 校验不许回复用户「已发布」。**

## 5. 发布自检清单

- [ ] `main` 已推送，`versionCode/versionName` 已推进到新 rNN
- [ ] APK 用 `car/ci/debug.keystore` 签名，`apksigner verify` 证书为 `2FEB63C5…FA9C`
- [ ] GitHub Release 已创建，notes 2–5 行且非空
- [ ] Release `--target` 是完整 40 位 SHA，asset digest 与本地 APK SHA-256 一致
- [ ] 没有向 `dl.z736.vip` / `tvbox-car` / `TVBox` 发布任何东西（旧通道已废弃）
- [ ] MuMu 租约已释放，未碰 `do_not_touch` 实例
- [ ] 报告中「未验证」项写明原因，不编造结论
