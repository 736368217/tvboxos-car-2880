# Acceptance Status

This file is a durable status pointer. It does not replace fresh evidence or claim a pass by itself.

## Current: Fullscreen player control redesign

- APK SHA-256: `ff7a7081e6e3ea44463ccc4530e4f55b66c829638176810f7a599bb247f5f1ee` (53,670,931 bytes).
- Main row now contains only previous, rewind 15 s, play/pause, forward 15 s, next, episodes and More.
- Picture/audio/SR/interpolation/aspect, speed/source/subtitle/audio/video track, danmaku/background/screen and advanced tools moved into a grouped playback-settings sheet.
- The dock remains usable while More is open; More toggles the sheet and shows an active state.
- MuMu instance 0: `CAR_DISPLAY_PASS`, playback `101/0`; player-specific pages/ux3k checks passed. Remaining suite failures are live-fixture or persisted back-mode cases outside this change.
- Evidence: `car/qa/ux-audit-car7-current/evidence-ff7a7081/`.
- Report: `car/reports/ux_redesign/CAR-17.3-3K-PLAYER-CONTROL-REDESIGN.md`.

## Previous: TVBox 3K icon system

- APK SHA-256: `9998629679fb9b5e24fc34c77d218ad253805430becc5df13ec04b2d5c9ae46b` (53,662,234 bytes).
- 61 default + 61 active `ic_tvbox_*` VectorDrawables, 108dp adaptive icon, five mipmap densities.
- UI palette unified to `#06100E / #0A1813 / #0E211A / #6FF2C6 / #A78BFA`.
- Evidence: `car/qa/ux-audit-car7-current/evidence-99986296/`.
- Report: `car/reports/ux_redesign/CAR-17.3-3K-ICON-SYSTEM-IMPLEMENTATION.md`.

## Previous: r8 final baseline

- [x] Final r8 Java Release built and signed.
- [x] Final APK SHA bound: `1C0AD382B2A1C9244BEE1ACE49849A2FC734D94E9CDA1D9412C4E16316C652D5`.
- [x] Final r8 APK installed on vm1 and guest `base.apk` SHA matched.
- [x] Voice-search chain removed; source and APK resource scans are zero.
- [x] Home brand overlap fixed and measured bounds no longer intersect.
- [x] Page target sizes corrected; seven-column search hotword grid no longer clips in the r8 matrix.
- [x] Final r8 page matrix captured: 15 XML/PNG pairs, target crash excerpts empty.
- [x] Final r8 geometry measured: 3/15 scroll-tail observations, 0 partial bounds.
- [x] Host handover tests 11/11 passed; core product checks passed.
- [x] Python HTTP failure recorded separately: missing `requests` before collection.
- [x] HTML visual report generated at `car/reports/ux_redesign/TVBox-Car3K-FINAL-VISUAL-REPORT.html`.
- [x] Device cleanup completed: vm1 stopped and unleased, target app stopped/removed, display/rotation/audio restored and read back.
- [x] One summary commit created for the large task and pushed to `origin/feature/car-ux-redesign-3k-fps`.
