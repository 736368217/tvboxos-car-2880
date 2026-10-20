# TVBox Car 3K Renderer Fix

- Branch: `feature/car-ux-redesign-3k-fps`
- Package: `com.github.tvbox.osc.jun`
- Final APK: `C:\Users\WIN10\.pi-desktop\scratch\18e6476a-13fa-44cd-8985-8cc0b204fc07\TVBox-Car3K-renderer-fix-signed.apk`
- SHA-256: `77AA77E8307A0D7499FC708082A472D9A2F51EF76B4DFC88E763D3879B4CFC80` · 52,893,692 bytes

## User-Reported Defects

The previous APK persisted the picture preset, SGSR1 switch and temporal switch, and changed the renderer configuration object, but did not provide sufficient proof that the current GL output path was active on the target device. The playback badge also displayed a static interpolation target and was hidden with the top control group during immersive fullscreen.

## Fixes

- `VideoPostProcessPolicy` keeps temporal blending active over the source-frame interval instead of only during the first 35 percent of the interval.
- `superResolutionActive` no longer silently bypasses SGSR1 when the source already covers the viewport or is 4K. It now requires a valid source size and output viewport; allocation and shader failures remain explicit fallbacks.
- `GlEnhanceRenderView` records measured source FPS, output FPS, actual SR/interpolation/colour states, and FBO/shader/draw failure reasons. The renderer continues to use the stable OES producer and does not restart or seek the decoder.
- `MyVideoView` exposes renderer measurements and reports actual runtime state instead of only requested preferences.
- `VodController` updates both the top badge and a bottom playback-control badge with `片源 <n> fps · 输出 <n> fps`. The output value is measured from the GL renderer; it is not a hard-coded 60 fps claim.
- `player_vod_control_view.xml` adds the bottom playback FPS badge so it remains visible when the immersive fullscreen mode hides the top information group.

## Verification Completed

- Release Java compilation: passed for `:player:compileReleaseJavaWithJavac` and `:app:compileJavaReleaseJavaWithJavac`.
- Python release build: `:app:assemblePythonRelease` passed with the existing handover Python 3.8 interpreter.
- Host core checks: preset, post-process policy, runtime configuration, search-session and Back policy checks passed. The separate `python_http` check remains blocked by the host environment missing the `requests` module.
- APK identity: package `com.github.tvbox.osc.jun`, version `1.0.0-car.7`, versionCode `8`, arm64-v8a and armeabi-v7a.
- APK signing: v1, v2 and v3 verification passed.
- APK alignment: `zipalign -c -p 4` passed.
- `git diff --check`: passed.

## Device Verification Boundary

Fresh install, real playback pixels, measured runtime FPS, and GL log verification are pending because the global MuMu pool currently has both running-ready instances leased by other conversations and marked `do_not_touch`; the idle instances were not leaseable in this batch. No emulator instance was operated outside the lease protocol.

The final APK is therefore build- and static-check verified, but this report does not claim that real-device visual effect and measured output FPS have been re-established until the same SHA is installed under a new exclusive lease.
