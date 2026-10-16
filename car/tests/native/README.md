# Native acceptance (car.7)

The separate instrumentation APK runs inside the installed production target. It contains no production debug Activity and is removed after the test batch. It reads the final build's R8 mapping; do not use a mapping from another APK.

## Build

Set `JAVA_HOME` to JDK17 and configure the Android SDK (build-tools34.0.0, android33). From the repository root:

```powershell
python -B car/tests/native/build_native.py --out "$env:PI_SCRATCH_DIR\car7-native-final"
```

The target and test APK must share the existing Android developer test certificate. Generated APK/JAR/classes/dex stay in scratch. Before any MuMu operation check the current conversation's global pool lease. Install and invoke only through the `hawchou.mumu-player` plugin, using the namespace/classpath wrapper from `mumu-android-shell-namespace`. Never use another conversation's instance.

Copy `run_guest.sh` and `car/tests/verify_car_display.sh` into the current batch's shared folder with UTF8/LF, then run under that wrapper:

```text
/system/bin/sh <share>/env.sh <share>/run_guest.sh playback <unique-tag> <actual-final-apk-sha256> <share>
```

For long probes capture output to a guest/shared file and poll it; avoid streaming all output through the manager shell. Only run one instrumentation process at a time. The script validates display/input2880x1620/d160 and actual installed APK SHA, mutes, saves per-run identity/result files, and treats nonzero assertions as failure. A successful plugin call or instrumentation shell exit alone is not acceptance.

## Modes

- `python`: Android HTTP transport contract, actual clun configuration and selected PY import/init/home/category/detail/search/playerContent; direct resolved URL remains in process memory and one real EXO playback is measured. Store only counts, elapsed time, scheme/parse flags, dimensions and positions. Do not run downloaded scripts on Windows or archive full URLs/headers/bodies.
- `subscription`: selected xiaosa JAR/QuickJS stages, bounded calls. External empty results/timeouts remain explicit failures, not interpreter regressions.
- `playback`: local200-second24fps motion fixture in EXO then IJK hard decode; current9/11settings, late pause callbacks, Texture/GL handoff without decoder restart, rapid settings, fullscreen/background/reentry, release during pending handoff and producer cleanup. Actual engine identity/session/source frames/positions are checked.
- other mode: native home/Back/dialog behavior and screenshots. Pass optional config URL and home site through the guest runner; this changes temporary prefs, so restore the original backup afterwards.

Run actual pixel validation too:

```powershell
python -B car/tests/verify_car7_pixels.py <evidence-directory> --sha256 <final-sha> --lifecycle --output <pixel-result.json>
```

The color bars have known green/yellow/blue/magenta/cyan stripes. Frame counters alone cannot detect the old IJK green output; the E395 baseline deliberately fails two IJK images. A final pass must bind image and identity to the new APK, and old baseline failures must remain marked historical.

Environment cleanup: stop the target through the plugin, restore original private prefs without printing them, verify byte equality, uninstall only the test package, check target startup/PID/crash, and release the one leased instance. Keep the MuMu main process and other instances intact.
