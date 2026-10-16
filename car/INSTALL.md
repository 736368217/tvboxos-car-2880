# INSTALL — TVBoxOS car.7 Python 通用候选

## 安装与识别

使用 `TVBox-1.0.0-car.7-python-signed.apk`，核对 `SHA256SUMS-car.7.txt` 或 `BUILD-INFO-car.7.md`。APK code8/name1.0.0-car.7，标签TVBox，包名com.github.tvbox.osc.jun，包含ARM32＋ARM64的Python3.8.13、Chaquopy与QuickJS。沿用既有签名，可覆盖安装同签名car.2–car.6，保留配置和历史。

真实车机可用文件管理器安装，或：

```powershell
adb install -r .\TVBox-1.0.0-car.7-python-signed.apk
```

本候选以私有草稿交付，IJK硬解全屏重建producer时仍有颜色异常，正式发布暂缓。当前使用默认EXO；IJK软解对照正常。D2A的生命周期95项通过不能代替像素画质检查（八图7/8）。

## 配置订阅

设置 → **配置地址** → 在“点播”输入框完整替换旧地址 → 确定。直播输入框独立。可通过对话框提供的浏览器推送入口输入长URL。先核对文本，避免旧地址和新地址拼接。

本轮测试：

```text
https://clun.top/box.json
https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json
```

GitHub网络受限时，测试成功的转发入口为：

```text
https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/refs/heads/123/xiaosa/api.json
```

clun快照含44个PY；xiaosa快照是108个JAR/CSP＋1个JS、没有PY。Python flavor支持Python Spider，不替换Android页面、Java/JAR、QuickJS或播放器。版本判断以APK设置/versionCode及SHA为准，不能从站点名的py/js后缀判断APK flavor。

## 音画与返回

设置 → **画面与音效高级功能** 可选择9音效、11画面，以及独立SGSR1/时域补帧开关。音效按当前内部EXO/IJK会话更新；视频配置直接作用当前视频，已验证切换保持decoder identity/位置。没有播放时保存给下次播放。外部播放器与直播基类不属于完整音效链范围。

原画ID0、臻彩ID1及既有0–7保持；动漫画面追加8–10，动漫音效追加6–8。两开关默认开启，原画下也可单独启用；原画＋两项关时EXO回普通Texture，IJK保留恒等OES输出但不做调色/SR/补帧。HDR/DoVi色彩处理与真实车机性能边界见技术说明。

返回模式：悬浮球、边缘拉杆、双模；支持标准/大号、拖动吸边、水滴/胶囊、触感和4/6/8秒或常显。闲置半透明靠边，触摸唤醒。返回控件为应用内View，无Root/ADB/无障碍/悬浮窗权限要求。根主页不自行退出，全屏先退全屏，分类回主页，其他页面回上一级。弹窗按其取消/确定语义处理，外拖与多指不会提交。

## 已验收范围

MuMu6.8.4.0/Android15/横屏2880×1620/d160：最终D2A苹果PY远程EXO播放1280×720、位置243→2761ms，豆瓣/剧踪/苹果/瓜子分类正常，枫叶本轮内容为空；返回/UI58项、播放状态/生命周期95项通过，八图像素7/8。外站空响应和超时单列，详情/解析成功不等于每条线路都可播放。完整范围见 `TEST-REPORT-car.7.md`。

真实OEM全屏、SA8295P性能、长期温升功耗、稳定60fps、ARM32运行及HDR/DoVi未实测。测试不改变真实车机DPI。
