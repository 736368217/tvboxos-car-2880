# TVBox Car Workspace

Read `START-HERE.md` first, then `car/HANDOVER-car.7-r2.md`, `car/handover/PROJECT-MAP.md`, and `car/handover/LESSONS.md`.

## Baseline And Scope

- The accepted APK is car.7-r2, SHA-256 `B2EEDC14021696467C3E21412208BEDA9CA3FC78F173FFFFF56F970BD77AA8C1`. `car/handover/baseline.json` freezes the candidate identity and eleven critical production fingerprints.
- Use the immutable handoff ref named in that JSON; the delivery ZIP records the full source commit in `SOURCE-STATE.json`. The older `v1.0.0-car.7` tag is historical, not this handoff.
- Local app gates passed. Remote Maple data, Juzong positive-title search, and Aidongman search still have recorded failures. Do not state all sites passed, and do not rebuild working UI just because those external checks failed.
- Do not publish a formal release or move existing tags automatically.

## Device And Identity Rules

- Keep Application ID `com.github.tvbox.osc.jun` and label `TVBox`. Consult the original APK workspace's shared registry before changing identity. A bundled registry copy is read-only reference, not another allocation authority.
- Use only the global `$env:USERPROFILE\.mumu-pool\mumu-pool.py`, one lease per batch, with a new conversation owner. Check before each device operation; never touch another lease. Use hawchou.mumu-player tools, not hand-rolled adb/MuMu calls. Mute all runs and preserve the main MuMu app.
- Require `verify_car_display.sh` to pass actual 2880x1620/d160/input geometry/no overrides before acceptance. Verify the installed APK SHA, native assertions, and eight actual screenshot pixel samples. No emulator is needed for documentation or host checks.
- Keep Back controls inside Activity DecorView. No system overlay, accessibility service, or global key permission.

## Preserve Verified Contracts

- IJK uses a stable Surface/SurfaceTexture across EGL detach/attach; do not return to replacing its producer on fullscreen changes.
- QuickJS callbacks run on the JS executor; waiting belongs on the caller thread. Network/timer resources have per-call cancellation and native handles are released on their owner executor.
- Search uses per-session/per-source cancellation and a bounded shared worker pool. Register FutureTask before submission, advance waiting work after rejection/timeout, and discard late results.
- A local fixture passing is not proof of remote service compatibility. Keep empty content, transport failure, tool failure, and pixel failure separate.

## Editing And Delivery

- Preserve unrelated user changes. In the original workspace `car/cartest.mp4` and `car/cartest-lowsat.mp4` are user-deleted and remain unstaged. A fresh Git clone may contain their historical blobs; new fixtures are generated in scratch, not based on these files.
- Use explicit task path lists for staging; never broad-stage private material or unrelated deletions. Existing upstream dependencies are not newly downloaded Spider JARs.
- New APKs, keys/keystores, private preferences, raw remote response bodies and logs must not enter Git. Do not read or print signing secrets. Do not silently remove historical upstream files while making this handoff.
- Keep non-interactive helpers hidden, capture real exit codes, clean up owned processes, and avoid overlapping builds or instrumentation. Default to doing bounded work directly; terminate separable agents before consuming a final build snapshot.
- Use `car/tools/run_host_checks.py`, `package_handover.py`, and `verify_handover.py` from the repository root. Generated outputs go outside this checkout, preferably `PI_SCRATCH_DIR`.
