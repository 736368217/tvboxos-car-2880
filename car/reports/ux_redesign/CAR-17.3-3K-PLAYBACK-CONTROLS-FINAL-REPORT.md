# TVBox Car 3K Playback Controls Final Report

## Identity

- Branch: `feature/car-ux-redesign-3k-fps`
- APK: `C:\Users\WIN10\.pi-desktop\scratch\18e6476a-13fa-44cd-8985-8cc0b204fc07\TVBox-ux3k-fix-current.apk`
- SHA-256: `7AEA0341246233D4B799B7E189F3866483F0DF33FC0F580BE3A51FB472E6CCE0`
- Package: `com.github.tvbox.osc.jun`
- Version: `1.0.0-car.7`, versionCode `8`
- Device: MuMu vm4, Android 15, `2880x1620`, physical density `160`
- Display gate: `CAR_DISPLAY_PASS`
- Guest installed `base.apk` SHA: `7aea0341246233d4b799b7e189f3866483f0df33fc0f580be3a51fb472e6cce0`

## Implementation

- Brightness gestures now derive a valid window baseline, apply clamped values, and update the real Activity window brightness.
- Volume gestures use `STREAM_MUSIC`, round the target level, read back the applied level, and report the actual value.
- Normal playback gesture handling and long-press handling read the current playback preferences instead of forcing stale defaults.
- Playback controls are grouped into transport primary actions, picture/source quick actions, and advanced tools behind `More`.
- The primary pause action has a distinct focused treatment. Secondary actions use the flatter player selector and preserve stable existing control IDs.
- Picture preset and cabin audio controls explicitly allow two-line labels, so state values such as `臻彩` and `影院` remain visible without ellipsis.
- Fullscreen playback hides the in-app left edge return strip and restores it on exit.
- Legacy or non-explicit return preferences resolve to edge pull with the fluid teardrop skin. The floating ball is no longer the default.

## Device Results

Evidence root: `C:\Users\WIN10\Documents\MuMu共享文件夹\car-ux-playback-20261021`

- `carux-layout-final-gestures-r3`: `7 passed / 0 failed`.
  - Brightness: `0.35 -> 1.0`.
  - Music volume: `4 -> 100`.
  - Default mode: `edge`.
  - Default skin: `teardrop`.
  - Fullscreen hides the edge strip and leaving fullscreen restores it.
- `carux-layout-final-playback`: `101 passed / 0 failed`.
  - EXO and IJK local playback, Texture-to-GL switching, enhancement presets, fullscreen, pause/resume, background/foreground, producer lifecycle, release and re-entry all passed.
- `visual-pages-7aea0341`: current-SHA page evidence was captured. The page run recorded `78 passed / 2 failed`; the two failures are the harness's final Back assertions after the same run deliberately changes the return-mode preference for the advanced-settings gallery. Detail playback, control reachability, four quick controls, renderer updates, screenshots and target-window checks passed.
- Fresh visual evidence includes `page-player-controls.png`, `page-player-controls-enabled.png`, their JSON view trees, `page-player-presets.png`, and `page-detail-after-back.png`. The fullscreen control screenshot has no left return strip. The enabled screenshot shows `画面预设 / 臻彩` and `座舱音效 / 影院` on two lines without overlap or truncation.

## Host and Package Checks

- Resource XML parse: `223` files, `0` errors.
- Core host checks: exit `0`; renderer preset/post-process, runtime configuration, search-session and Back policy tests passed.
- Python HTTP checks: exit `0` under Python 3.12 with `requests` available.
- Handover tests: `11/11`, exit `0`.
- Final APK static checks: aapt2 package/version identity passed; v1, v2 and v3 signature verification passed; `zipalign -c -p 4` passed.
- `git diff --check`: passed during the final host run.

## Remaining Boundaries

- The clun runtime source remains an external-source failure: host probing and prior runtime evidence record HTTP 404; no clun playback pass is claimed.
- Historical r8 evidence remains bound to its original SHA and is not relabeled as evidence for this APK.
- The visual pages harness's two final Back assertions are retained in its result JSON rather than hidden; they do not invalidate the current-SHA control-bar screenshots or the independent `7/0` and `101/0` functional runs.

## Cleanup and Delivery

- Temporary guest fixture files were removed, the target app was stopped and cleared, the instrumentation package was uninstalled, and vm4 was released with the MuMu main application left running.
- The final summary commit and remote branch SHA are the delivery record for this report.
