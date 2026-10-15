# TEST-REPORT — TVBoxOS 1.0.0-car.6

验收日期：2026-10-15。最终交付 `TVBox-1.0.0-car.6-python-signed.apk`，**52,635,477 字节**，SHA-256：

```text
02C7CF892C49BA5D286A325C6FC46B69B861070582FB552A2868B75352808D08
```

## 测试对象与环境

MuMu 6.8.4.0 / Android 15，横屏 2880×1620、density 240，经 ARM64 翻译运行。独占实例 2，两批各 acquire/release 一次，结束保留 MuMu 主程序；不操作其他会话实例。整批主机与 guest 静音，`volume_music=0`、`sound_effects_enabled=0`；`accessibility_enabled=0`。

测试分两阶段，避免把旧包证据冒充最终包的全量复测：

| 阶段 | APK SHA-256 | 覆盖 |
|---|---|---|
| 完整功能矩阵基线 | `E3B83211B34E4261AD72731DFC1F5D1D1DA4CCFC792162072ACB5380331426F8` | 新预设、四开关、1080p/4K、普通渲染回退、持久化、Python/QuickJS/JAR；PID3079，显式重启后23666 |
| 最终生命周期修复包 | `02C7CF892C49BA5D286A325C6FC46B69B861070582FB552A2868B75352808D08` | EXO/IJK真实前后台恢复、暂停恢复、全屏、退出重进、producer释放、Python/JAR恢复；PID2408 |

两包之间仅调整 `VideoView` 恢复/释放分支及 `GlEnhanceRenderView` producer 生命周期。音画参数表、生产 GLSL、时域策略和独立设置代码不变；没有在修复包上重复整个四组合矩阵。机器摘要：`evidence/car.6/baseline-device-acceptance.json` 与 `device-acceptance.json`。

## 构建与静态检查

| 项目 | 结果 |
|---|---|
| 最终 Python release 构建 | exit 0，107.23s |
| APK 身份 | `com.github.tvbox.osc.jun` / `TVBox` / code7 / name1.0.0-car.6 |
| native ABI | `armeabi-v7a`、`arm64-v8a`；均有 Python／Chaquopy／QuickJS 库 |
| 签名与对齐 | v1/v2/v3 true，zipalign 4通过，沿用既有测试证书 |
| 覆盖安装 | 最终包安装并启动通过 |
| Java 8 host tests | 11画面／9音效精确新旧参数、稳定ID、不可变表、迁移、默认开关与损坏值回退通过 |
| 时域/旁路策略 | 首帧、seek、暂停、切镜头差异抑制、权重衰减、4K/小窗口SR旁路、真实FBO字节测试通过 |

签名工具仍输出上游 META-INF 条目未被 v1 单独保护的提示；v2/v3 完整 APK 签名验证通过。构建有上游可选库缺类提示，最终 exit0。原始摘要在 `final-build-result.json`、`final-apk-signature.txt`。

## 生产 GLSL 像素验证

独立 `VideoGpuProbe.java` 经 app_process/EGL pbuffer 运行，直接编译生产 `VideoShaders` 字符串，无产品测试 Activity。输入16×16，输出96×96，GPU报告 `Adreno (TM) 640 / OpenGL ES 3.2 v334 R`，是模拟器环境。

- OES 调色 shader 与 SGSR/时域 shader 编译、链接通过。
- 四开关组合的实际 readback 像素通过；低差异数值混合、高差异切断、静态帧恒等通过。
- SGSR 平涂区域差异0；边缘平均绝对差异 **1.041097**、最大差异 **16/255**，符合限幅。
- 11档生产调色参数逐通道与CPU参考比较，误差≤1个8bit量化值。
- GL清理检查无错误，输出完整 `CAR6_GPU_PASS`。

全部断言完成后，工具进程在 Shutdown 阶段发生 `Segmentation fault`／退出139。这是独立探针退出异常，不能写成整个进程 exit0，也不是 TVBox 应用崩溃。原始输出与逐组合PNG保存在 `gpu-result.txt`、`gpu-*.png`。

## 新增音效和画面预设

9档音效 UI 循环0→1→…→8→0逐项有新鲜节点。快速连点曾被上游防连点节流，随后按≥0.8秒间隔选择，并在播放前核对设置值。

| 音效 | 实际会话 | Bass / Virtualizer / Loudness mB | 证据 |
|---|---:|---|---|
| 6 动漫·标准 | 89 | 150 / 180 / 450 | 运行日志；AudioFlinger四效果启用 |
| 7 动漫·剧场 | 105 | 280 / 320 / 550 | 运行参数日志；未成功保存该会话AudioFlinger转储 |
| 8 动漫·对白 | 113 | 0 / 80 / 650 | 运行日志与AudioFlinger转储；BassBoost按0强度禁用 |

静音测试验证绑定和参数，不构成车内频响、削波或主观听感验收。标准的四效果为 Equalizer、Dynamic Bass Boost、Loudness Enhancer、Virtualizer。

新画面8动漫·暗光、9动漫·日间、10动漫·清透，均由设置UI实际选择，播放日志的饱和度/对比度/亮度/RGB/SGSR锐度与规格表一致。11档精确像素由生产GLSL探针覆盖；旧8档car.5截图比较保留在历史证据，不把本轮移动片截图当同帧视觉对比。

## 开关组合、持久化与内存

| SR / 补帧 | 实际播放结果 |
|---|---|
| 开 / 开 | 双FBO、SR激活、非零blended，24fps移动片常见输出提交约47–58次/秒 |
| 开 / 关 | SR激活，源/输出约24fps，blended=0，无持续补帧 |
| 关 / 开 | SR关闭，输出约57次/秒，非零blended |
| 关 / 关（清透调色） | 单Pass，fbo=false、sr=false、interp=false，blended=0 |
| 关 / 关（原画） | 原生视图树为 `TextureRenderView`，普通播放通过 |
| 开 / 开（原画） | EXO/IJK均能走增强链，原画不强制禁用两功能 |

显式关闭/启动应用后，节点仍为动漫·对白、原画、SR关闭、补帧关闭。两个新开关初始默认均开启，首次安装后设置节点已记录。

1080p：双FBO **16,588,800B**，1057×594小窗 `srActive=false`，2880×1620全屏 `srActive=true`。4K：双FBO **66,355,200B**，全屏仍 `srActive=false`，正常出帧。4K采样窗口输出提交约44–56次/秒；这些是软件计数，含启动、解码与采集扰动，不是目标硬件性能基准。

## 生命周期复核与最终包复测

独立代码复核发现原 `VideoView.resume()` 把 GLSurfaceView 识别为 SurfaceView，调用 `setDisplay(holder)` 会绕过 OES producer；另发现重建/退出时 producer 过早释放。已修复并重建：

1. GL恢复分支重绑SurfaceTexture producer，保留主线程调用。
2. 重建时旧producer保留至新Surface绑定返回后再释放。
3. 引擎异步释放前解除输出绑定，producer释放与完整GL帧串行。

最终包 PID2408 的实际验证：

- EXO全屏、暂停/恢复、退后台→插件app launch回前台：日志出现 `resume uses SurfaceTexture producer`，恢复后源帧持续增长，source约24fps、output约57次/秒、blended非零。
- 切换IJK，原生日志 `MEDIA_INFO_VIDEO_RENDERING_START`，session33；再次退后台/回前台，出现相同producer恢复标记，源帧和混合输出继续增长。
- 全屏输出viewport2880×1620，退出播放先 `player surface detached (main thread)`，再 `producer surfaces released; live=0`；退出后重新进入低饱和片正常播放。
- 两次会话退出均释放至live0，PID不变；未出现应用FATAL EXCEPTION、wrong-thread、draw failed、setSurface失败或POSTPROCESS_FALLBACK。

未通过测试入口强制注入EGL context loss；全屏移动与前后台恢复已经实测，异常context丢失后的所有驱动路径仍不作全覆盖承诺。证据见 `lifecycle-runtime-excerpts.txt`、`repair-*-1440.jpg`。

## 脚本、订阅与返回按钮回归

Python3.8.13实际执行8项依赖及本地HTTP断言，四卡片分类→详情→本地合成片播放。QuickJS基线包分类、详情与播放通过；最终生命周期包重新执行Python与JAR，未重复QuickJS全流程。测试源未写入APK默认订阅。

两批结束都通过配置地址弹窗恢复：

```text
https://ghfast.top/https://raw.githubusercontent.com/736368217/tvbox/123/xiaosa/api.json?v=3
```

最终包记录 `echo-load Init.init success`、`load success key=main`。基线包原订阅主页推荐与热播剧集真实卡片可见；猜你喜欢返回“没找到数据”。没有逐站验证全部外部源，也未在car.6重复car.5的78源聚合搜索。

应用内返回用于全屏→详情、详情→主页、分类→主页、设置→主页。原生节点证据：闲置6秒后按钮0个，触摸后1个；首页根级连续点击仍留在应用，PID稳定，无障碍关闭。弹窗仍用自己的取消/确定按钮。

## 剩余边界

真实2027零跑B01／SA8295P、OEM全屏、方控与真实DPI、长时间功耗温升、稳定60fps呈现、单帧<2ms尚无硬件证据。ARM32完成打包核验，未独立运行；HDR/DoVi、端到端10bit精度、旧Android版本、直播/外部播放器未全量验收。时域混合不含运动矢量估计。

全局包名检查最终exit0、Errors0、Warnings18；TVBoxOS全部8个产物路径与occupied身份一致。提示为历史多版本/已占用产物等记录，详见 `evidence/car.6/package-registry-check.txt`。检查期间一度遇到JSON解析错误，当前文件经Python、PowerShell解析与完整脚本复查均通过。
