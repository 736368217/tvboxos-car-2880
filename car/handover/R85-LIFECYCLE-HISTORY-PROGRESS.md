# r85：切屏保活、历史封面与播放进度

用户实测反馈四条：

1. 切到别的页面后，播放声音还在继续。
2. 切回应用会有明显延迟，然后视频「突然又开播」，而不是原样保留。
3. 历史记录原本正常，突然变成找不到片源、来源显示「搜」。
4. 之前看过的片点进去丢失播放进度，从头开始。

## 根因

### ① 切屏后声音还在放

`PlayFragment.onPause()` 里写着：

```java
if (mVideoView != null && !exitingPreview && !hasAudioOnlyPlayback()) {
    mVideoView.pause();
}
```

只要当前片源被判定成「只有音轨」（`hasAudioOnlyPlayback()` 为真），这一行就整段跳过。
页面切走时既不暂停也不保存，播放器继续出声。`onHiddenChanged(true)` 也只管 `pause()`
不管「这次暂停是谁造成的」，回来时无条件 `resume()`。

### ② 切回来延迟后突然开播

两处叠加：

- `PlayFragment.onResume()` 无条件 `mVideoView.resume()`。用户自己按过暂停，切回来也会被续上；
  而 `pause()` 后立刻 `resume()` 会让播放器重走一次起播，表现就是「先静一下，然后突然出声」。
- `HomeActivity.restorePlaybackIfNeeded()` 只判断 `PlaybackSession.needsRestore()`，不判断详情页
  是否还活着。页面明明还在，也会 `startActivity` 重开一次详情页，重走详情加载 + 起播。

### ③ 历史显示「搜」、封面消失

`HistoryAdapter` 在 `ApiConfig.get().getSource(item.sourceKey)` 返回 null 时直接写死 `"搜"`。
而历史记录的 key 与真实站点对不上，来自 `DetailActivity.jumpToPlay()`：

```java
insertVod(firstsourceKey, vodInfo);      // 历史写 firstsourceKey
bundle.putString("sourceKey", sourceKey); // 播放器拿 sourceKey
```

站点 fallback 会把 `sourceKey` 改成 `mVideo.sourceKey`（例如从 `py_pufferfish` 切到 `py_kkys`），
两个 key 从此不一致。历史里存的是旧 key，当前订阅里找不到 → 来源显示「搜」；
点击历史还会因为 `getSource()==null` 跳到搜索页，看起来就是「找不到片源」。

### ④ 进度丢失、从头开始

播放进度缓存 key 是：

```java
progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex + vs.name;
```

`sourceKey` 一变，key 就变，旧进度查不到。而 `PlayFragment.setData()` 恢复快照时还要求
`sourceKey` 完全相等：

```java
TextUtils.equals(sourceKey, PlaybackSnapshot.sourceKey(context))
        && TextUtils.equals(mVodInfo.id, PlaybackSnapshot.vodId(context))
```

于是同一部片只要来源 key 变过，就必然从头开始。

## 修复

- `PlayFragment.onPause()`：不再按「是否纯音频」分流，页面切走一律 `pause()`；
  用 `pausedByLifecycle` 记住「这次暂停是生命周期造成的」。
- `PlayFragment.onResume()`：只有 `pausedByLifecycle` 为真才 `resume()`，用户手动暂停不会被自动续上。
- `PlayFragment.onHiddenChanged()`：隐藏时同样记录 `pausedByLifecycle`，只有因隐藏而暂停的才续播。
- `HomeActivity.restorePlaybackIfNeeded()`：详情页仍存活时只 `CLEAR_TOP|SINGLE_TOP` 回到该页面，
  不再重建播放；只有页面真的没了才走 `claimRestore()` 重建。
- `DetailActivity.jumpToPlay()`：历史与播放器统一用同一个 `playKey`，消除 key 漂移。
- `HistoryAdapter`：站点查不到时不再写「搜」，退回记录里存的 `sourceName`。
- `VodInfo` 新增 `sourceName`，`DetailActivity.insertVod()` 写入站点显示名，老记录也能显示来源。
- `PlayFragment.setData()`：恢复快照的匹配从「sourceKey 完全相等」放宽为「同一 `vodId`」，
  `sourceKey` 只作为优先命中的依据。

## 验证

### 主机测试

`car/tests/PlaybackRegressionHostTest.java`（15 项）读取真实源码，断言：

- `onPause` 不得因 `!hasAudioOnlyPlayback()` 跳过暂停；
- `onResume` 必须在 `if (pausedByLifecycle)` 内续播；
- `jumpToPlay()` 不得再用 `insertVod(firstsourceKey, ...)`；
- 历史与播放器必须共用 `playKey`；
- `HistoryAdapter` 不得出现 `"搜"`；
- `setData` 必须以 `vodId` 匹配快照。

完整 host suite：`ALL_HOST_TESTS_PASS`。

### 设备实测（MuMu vm6，2880×1620 / d160 / r=1，r85 vc57）

| 验收点 | 证据 |
|---|---|
| 切后台真的暂停 | `playback_lifecycle_pause audio_only=false was_playing=true` + `playback_pause_saved position=236293 full=true` |
| 切回不重建、只续播 | `playback_restore_reuse_live_page id=594981` → `playback_lifecycle_resume position=236321` |
| 历史不再显示「搜」 | 历史页显示「河豚[py]」「可可追剧[py]」，封面为真实剧照 |
| 进度保留 | 从历史点进去后播放器显示 `07:24 / 1:53:55`，不是 0 |

证据目录 `car/reports/r85-lifecycle/`：历史页截图、首页截图、续播位置截图、`lifecycle.log`。

## 边界

- 本次只证明上述实测网络与这台模拟车机上的行为，不外推真车 OEM 后台策略。
- 历史页出现两条同名记录（河豚 / 可可追剧）是既有行为：同一部片在不同站点各存一条。
  本次修的是「来源显示成搜」和「key 漂移」，不做跨站点去重。
- 首页卡片在本镜像上用 `adb input tap` 点不开（`TvRecyclerView` 触摸走焦点路径），
  与本次改动无关；验收改用遥控器方向键 + 确认键完成，播放现场由搜索/历史进入。
