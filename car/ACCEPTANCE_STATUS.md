# Acceptance Status

This file is a durable status pointer. It does not replace fresh evidence or claim a pass by itself.

## Current: External audit verification + global visual system

- APK SHA-256: `f93416d784de3f463485421fc5f35c75f0e2fbc049592cf59dc90d6a1e54dba3` (53,679,236 bytes).
- External 30-item audit: 8 shared issues fixed (zero-config feedback, dialog dim, history empty state, search-history hint, per-site JS spider locks, IME pan, explicit detail retry/back, search-source title/count), 11 already covered, 11 not applicable/environment-only.
- Additional polish: settings title follows the selected section; player option icons group with their labels; video-preset sheet no longer overlaps hint/cancel; edge-back guide uses the mint/neutral palette.
- MuMu instance 1: `CAR_DISPLAY_PASS`; pages 80/0, ux3k 98/0, playback 101/0, gestures 7/0, state shells 5/0. All results bind the same APK SHA.
- Evidence: `car/qa/ux-audit-car7-current/evidence-f93416d7/`.
- Report: `car/reports/ux_redesign/CAR-17.3-3K-EXTERNAL-AUDIT-VERIFICATION.md`.

## Previous: Global visual system + fullscreen player
- APK SHA-256: `1b0eb5e2dfac4af3dd1c18c6aee76ffeab388f84bdc0f14f135ba4cff580dd61` (53,675,084 bytes).

## Previous: Fullscreen player control redesign
- APK SHA-256: `d6d0820ccd13a05337465f8247b262feab4da9fd95c785e82b199790956c7d27` (53,670,931 bytes).
- Main row now contains only previous, rewind 15 s, play/pause, forward 15 s, next, episodes and More.
- Picture/audio/SR/interpolation/aspect, speed/source/subtitle/audio/video track, danmaku/background/playback-info and advanced tools moved into a grouped playback-settings sheet.
- The dock remains usable while More is open; More uses a dark-green/mint selected state so purple stays the primary playback action. “Playback info” off hides the optional time/network and resolution/frame-rate layer while keeping title, back and lock.
- MuMu instance 1: `CAR_DISPLAY_PASS`; `ux3k` 103/0, `playback` 101/0, `pages` 80/0, `gestures` 7/0. All results bind the same APK SHA.
- Evidence: `car/qa/ux-audit-car7-current/evidence-d6d0820c/`.
- Report: `car/reports/ux_redesign/CAR-17.3-3K-PLAYER-CONTROL-REDESIGN.md`.
- Visual QA: `car/reports/ux_redesign/CAR-17.3-3K-PLAYER-VISUAL-QA-FINAL-d6d0820c.md` (no P0/P1; five states 4/5).

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
