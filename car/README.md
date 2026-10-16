# TVBoxOS 车机版 1.0.0-car.7-r2 候选

基于 TVBoxOS 的 Python ARM32＋ARM64 通用版，保留 Java/JAR、QuickJS、car.1–car.6 功能以及9档音效、11档画面、独立SR/补帧。包名 `com.github.tvbox.osc.jun`，应用名 `TVBox`，code 8。

car.7 修复了真实 PY 站点的 HTTP/Requests 兼容、脚本下载缓存和加载调度，并改进车机主页、两行标题、应用内返回球/边缘手势、弹窗与当前视频设置。它仍是Python通用版；Android页面和播放器继续使用Java。

## 下载与状态

当前 r2：`TVBox-1.0.0-car.7-r2-python-signed.apk`，52,676,888B，实际 versionName 仍为 `1.0.0-car.7`，SHA-256：

```text
B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1
```

r2 已修复 IJK 硬解全屏绿块、QuickJS 异步互等、搜索取消/队列推进和详情应用内返回的焦点分支。最终同 SHA：播放101/0、像素8/8、逐页49/0、返回UI58/0、QuickJS14/0、搜索压力12/0。真实源的空数据/阳性搜索未命中/搜索超时仍照实记录，不声称全部外站通过。

用户报告在交接包`tvbox-os-car/report-car.7/`：27页PDF、含22张标注截图的Word及单文件HTML。入口见[r2验收](TEST-REPORT-car.7-r2.md)和[r2交接](HANDOVER-car.7-r2.md)，新clone先读仓库根START-HERE.md。Git交接锚点为`handoff-car.7-r2-20261017`，ZIP的SOURCE-STATE.json给出完整commit；包源码逐Git blob核对。旧D2A草稿/`v1.0.0-car.7`不移动；上一正式版仍为[car.6](https://github.com/736368217/tvboxos-car-2880/releases/tag/v1.0.0-car.6)。APK/下载的Spider JAR/私钥不新增进源码仓库。

## 使用

安装和订阅操作见 [INSTALL.md](INSTALL.md)。设置 → **画面与音效高级功能** 提供9音效、11画面、SGSR1、时域补帧和返回交互。点播控制栏有画面、超分、补帧、音效入口，设置应用到当前内部播放器；无活动会话时保存在下次播放使用。

画面ID0–10、音效ID0–8保持。SR和补帧独立，默认开启；原画＋两项关闭时EXO使用普通Texture，IJK保留恒等OES输出以避免解码器重配，关闭调色/FBO/持续补帧。补帧为相邻帧时域混合，无光流/运动预测；SGSR在源宽≥2560或无放大需求时旁路。完整参数和范围见 [AUDIO-VIDEO-ENHANCE.md](AUDIO-VIDEO-ENHANCE.md)。

返回球、边缘手势和双模全部位于应用Activity内部，默认开启，无需系统悬浮窗或无障碍。支持拖动吸边、标准/大号、闲置4/6/8秒或常显、水滴/胶囊；分类回主页，全屏先退全屏，主页根点击只提示。尺寸见 [BACK-CAR-SCALE.md](BACK-CAR-SCALE.md)。

## 证据

[TEST-REPORT-car.7-r2.md](TEST-REPORT-car.7-r2.md) 与 `evidence/car.7/r2-B2EEDC14/` 是当前验收依据。苹果真实PY经EXO播放1280×720，位置162→2680ms。枫叶PY的138B响应含403 Forbidden特征；剧踪已知片名未命中；xiaosa爱动漫分类48/详情1、搜索到30秒截止。这些失败没有伪装成通过。

[BUILD-INFO-car.7-r2.md](BUILD-INFO-car.7-r2.md) 和 [HANDOVER-car.7-r2.md](HANDOVER-car.7-r2.md) 记录当前构建及继续工作入口。[旧car.7验收](TEST-REPORT-car.7.md) 对应D2A及更早候选，不能用它的7/8像素结果代替r2的8/8，也不能反过来改写旧结果。模拟器不证明OEM车机全屏、SA8295P性能、稳定60fps、HDR/DoVi或全部外站可用。
