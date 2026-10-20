# TVBox Car 3K Final Acceptance

## Identity

- Branch: `feature/car-ux-redesign-3k-fps`
- Final APK SHA-256: `1C0AD382B2A1C9244BEE1ACE49849A2FC734D94E9CDA1D9412C4E16316C652D5`
- Package: `com.github.tvbox.osc.jun`, version `1.0.0-car.7`, versionCode `8`
- Device: MuMu vm1, Android 15, 2880x1620, physical density 160
- Display gate: `CAR_DISPLAY_PASS`
- Visual report: [TVBox-Car3K-FINAL-VISUAL-REPORT.html](TVBox-Car3K-FINAL-VISUAL-REPORT.html)
- Evidence: `car/evidence/ux3k/final-1c0ad382/`

## Completed

- Voice-search chain removed from source and final resource table.
- Home brand overlap removed.
- Page and search interaction target sizing corrected.
- Search hotword grid changed to seven columns to prevent bottom clipping.
- Final APK installed SHA matched exactly.
- Page matrix: 15/15 XML and PNG captures, all PNGs 2880x1620, target crash excerpts empty.
- Host handover tests: 11/11.
- Core Java, XML, enhancement, runtime configuration, search-session and Back checks passed.

## Geometry

The final matrix reports 3/15 snapshots with small nodes. They are visible tail fragments of scrollable settings content, not primary page entry controls:

- `llDns` at the bottom of Playback settings.
- `uxNavHistoryRow` at the bottom of Touch settings.
- `switchAdvancedHaptic` at the bottom of Back settings.

No partial out-of-bounds nodes were found. Home, Search, Back, picture preset, live hint and search hotword targets meet the 90px reference after the current fix.

## Source Boundaries

- xiaosa: real Home data and positive content/playback were captured in the prior same-layout runtime and are labeled separately from exact-r8 matrix evidence.
- clun: host probe was HTTP 200, while the previous runtime check returned HTTP 404 twice. No clun playback pass is claimed.
- Python HTTP: blocked before collection because `requests` is unavailable.
- Historical evidence is never relabeled as r8 proof.

## Cleanup

The test package is stopped and removed after the final capture. Display/rotation/audio are restored and the vm1 lease is released before delivery. No formal release or tag movement is performed.
