# 上游跟进：TVBoxOS 合并与自动化巡检

## 上游是什么

- 远端：`upstream` = `https://github.com/q215613905/TVBoxOS.git`，分支 `main`
- 本仓库：`origin` = `736368217/tvboxos-car-2880`，分支 `main`
- 版本号规则 `<上游白盒基准>-car.7-r<迭代号>`，上游基准本身不带 r 号

## 本次合并（r86）

| 项 | 值 |
|---|---|
| 上次基线 | `ab11d289`（三倍速位置微调） |
| 本次上游 | `89ec1b75`（Merge branch 'dev'） |
| 新增提交 | 30 个 |
| 上游改动文件 | 28 个 |
| 真冲突文件 | 4 个 |

上游带来的主要变化：ExoPlayer 卡缓冲自动恢复与 HLS 重同步、JarLoader/pyLoader 加载修复、
快速选集弹窗优化、豆瓣「猜你喜欢」、搜索结果精确匹配移除、站点预加载去除、局域网文件传输、
搜索历史动态列宽、详情快搜修复。

### 4 个冲突的处理

| 文件 | 冲突原因 | 处理 |
|---|---|---|
| `player/ExoPlayer.java` | 上游加了整套缓冲恢复；我们把 `setDataSource` 改成 `synchronized`、给 `buildRenderersFactory` 加了 `owner` 参数 | 保留我们的 `synchronized` 与 `owner` 签名，完整并入上游缓冲恢复（handler 接线、超时探测、解码器日志、`seekTo`/`reset`/`release` 钩子） |
| `crawler/ProtectedInitJar.java` | 两边都处理「jar 没有 DexNative」 | 语义相同（都返回 true），保留我们带说明的分支 |
| `python/.../pyLoader.java` | 上游删掉 `isPythonSupported()` 守卫并改用 `LOG`；我们有 `pendingConfig` 重放与 `recentPyKey` 缓存 | 采用上游 `LOG` 统一与上游的守卫移除，保留 `synchronized`、`pendingConfig` 重放 |
| `ui/fragment/UserFragment.java` | 上游加「文件传输」入口；我们加「长按设置」入口 | 两段都保留 |

## 能不能自动跟进

**检测可以全自动，合并不能全自动。** 分开看：

### 已自动化：每日巡检 + 合并预演 + Issue 提醒

- 脚本 `car/ci/upstream-check.py`
  - 拉取 `upstream/main`，统计落后提交
  - 用 `git merge-tree --write-tree` **在临时对象库里预演合并**，不碰工作区、不改 index
  - 输出：落后提交数、能否自动合并、冲突文件清单、触及热点文件清单
  - 退出码：`0` 已最新 / `10` 可自动合并 / `11` 有冲突 / `2` 环境异常
  - `--json` 机器可读，`--base` 指定基线（回溯/自检），`--no-fetch` 离线
- 工作流 `.github/workflows/upstream-watch.yml`
  - 每天 03:17 UTC 自动跑一次，也可手动触发
  - 有新提交时创建或追加 Issue「上游 TVBoxOS 有新提交，待合并」，正文含提交列表、冲突文件、热点文件、处理命令
  - **只读**：不自动合并、不自动改代码、不自动发版

本地手动跑：

```powershell
python car\ci\upstream-check.py            # 人类可读
python car\ci\upstream-check.py --json     # 机器可读
```

### 为什么不能全自动合并

本仓库对上游文件有大量**语义性**自有改动，机器无法判断该留谁：

- `ExoPlayer`：我们加了音轨峰值锁定与 PCM 补偿，上游在加缓冲恢复，两边都动同一个类
- `PlayFragment`：我们的切屏暂停语义（`pausedByLifecycle`）、`setData` 的进度匹配放宽
- `DetailActivity`：我们的 `playKey` 统一（历史与播放器共用 sourceKey）
- `pyLoader`：我们的 `pendingConfig` 重放解决了「配置早于 loader 就绪」
- `HomeActivity`：我们的存活详情页复用，上游直接删了站点预加载

用 `-X theirs` 或自动合并会**静默丢功能**，且编译和 host 测试都可能照常通过。
所以策略是：自动检测 + 自动预演 + 自动提醒，人工按语义解冲突，再跑完整回归。

## 合并后的回归清单

每次合并上游后必须跑：

```powershell
python "$env:PI_SCRATCH_DIR\run_all_host_tests.py"     # 期望 ALL_HOST_TESTS_PASS
```

再在车机模拟器（2880×1620 / d160 / r=1）上验：

1. 播放：能起播、PCM 有输出（`CarAudioGain: queue ...`）
2. 切屏保活：`playback_lifecycle_pause` → 切回 `playback_restore_reuse_live_page` + `playback_lifecycle_resume`，位置连续
3. 历史：站点名与封面正常，点进去从上次进度续播（不是 0）
4. 返回：全屏→详情→首页；主动返回后不复活
5. 应用内更新：能检查到新版本、下载进度推进、复制链接可达
6. 音量补偿：设置页档位生效，>100% 实际作用于 PCM

## 本次回归结果（r86，vc58）

| 验收点 | 证据 |
|---|---|
| 订阅与详情 | 详情页正常显示「来源：河豚[py]」、线路、1–14 选集 |
| 播放 | `CarAudioGain: queue n=4001 ... makeup_locked=true` |
| 切屏保活 | `playback_pause_saved position=615866` + `playback_lifecycle_pause audio_only=false was_playing=true` |
| 切回复用 | `playback_restore_reuse_live_page id=594981` + `playback_lifecycle_resume position=615881` |
| 历史 | 显示「河豚[py]」「可可追剧[py]」与真实封面，无「搜」 |
| 进度保留 | 从历史进入后播放器 `12:49 / 1:53:55` |
| Host 测试 | `ALL_HOST_TESTS_PASS` |

证据目录 `car/reports/r86-upstream-merge/`：详情页、历史页、续播位置截图，`runtime.log`，`upstream-state.json`。

## 边界

- 本次只验证模拟车机与当前网络；真车 OEM 后台策略、真车听感未验证。
- 上游 `oldTv`/`dev`/`file_transfer` 分支未合，只跟 `main`。
- 上游删除的 `isPythonSupported()` 守卫不再恢复；Android 16+ 32 位进程的 Python 限制依赖
  Pyramid/Chaquopy 自身行为，本次未在 Android 16 上实测。
