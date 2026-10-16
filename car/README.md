# TVBoxOS 车机版 1.0.0-car.7 候选

基于 TVBoxOS 的 Python ARM32＋ARM64 通用版，保留 Java/JAR、QuickJS、car.1–car.6 功能以及9档音效、11档画面、独立SR/补帧。包名 `com.github.tvbox.osc.jun`，应用名 `TVBox`，code 8。

car.7 修复了真实 PY 站点的 HTTP/Requests 兼容、脚本下载缓存和加载调度，并改进车机主页、两行标题、应用内返回球/边缘手势、弹窗与当前视频设置。它仍是Python通用版；Android页面和播放器继续使用Java。

## 下载与状态

候选：`TVBox-1.0.0-car.7-python-signed.apk`，52,668,696B，SHA-256：

```text
D2A49B5DA2B0371CF3C84CCDAC6C5753EF649721639434FB21955F4054A30803
```

本候选Python兼容修复与默认EXO远程播放已验证，源码和附件以私有草稿交付。IJK硬解在MuMu全屏重建producer后仍有颜色异常，正式发布暂缓；IJK软解对照正常。上一正式版本仍为 [car.6](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.6)。APK/Spider JAR/私钥不入源码仓库。

## 使用

安装和订阅操作见 [INSTALL.md](INSTALL.md)。设置 → **画面与音效高级功能** 提供9音效、11画面、SGSR1、时域补帧和返回交互。点播控制栏有画面、超分、补帧、音效入口，设置应用到当前内部播放器；无活动会话时保存在下次播放使用。

画面ID0–10、音效ID0–8保持。SR和补帧独立，默认开启；原画＋两项关闭时EXO使用普通Texture，IJK保留恒等OES输出以避免解码器重配，关闭调色/FBO/持续补帧。补帧为相邻帧时域混合，无光流/运动预测；SGSR在源宽≥2560或无放大需求时旁路。完整参数和范围见 [AUDIO-VIDEO-ENHANCE.md](AUDIO-VIDEO-ENHANCE.md)。

返回球、边缘手势和双模全部位于应用Activity内部，默认开启，无需系统悬浮窗或无障碍。支持拖动吸边、标准/大号、闲置4/6/8秒或常显、水滴/胶囊；分类回主页，全屏先退全屏，主页根点击只提示。尺寸见 [BACK-CAR-SCALE.md](BACK-CAR-SCALE.md)。

## 证据

[TEST-REPORT-car.7.md](TEST-REPORT-car.7.md) 记录最终D2A与历史E395的不同证据。D2A：返回/UI58/0、播放生命周期95/0、像素7/8；真实苹果PY通过EXO播放1280×720，位置243→2761ms。枫叶成功加载但本轮内容为空，其余四个PY样本正常。没有将HTTP200、解释器存在或fixture通过当作全部站点可用。

[BUILD-INFO-car.7.md](BUILD-INFO-car.7.md)、[DESIGN-car.7.md](DESIGN-car.7.md)、`evidence/car.7/` 保存构建与验收范围。历史car.6证据继续保留。模拟器不证明OEM车机全屏、SA8295P性能、稳定60fps、HDR/DoVi或全部外站可用。
