# TVBox Car 3K Final Report

## Delivery

- Branch: `feature/car-ux-redesign-3k-fps`
- Final APK SHA-256: `1C0AD382B2A1C9244BEE1ACE49849A2FC734D94E9CDA1D9412C4E16316C652D5`
- Device: MuMu vm1, Android 15, `2880x1620 / 160 dpi`
- Visual report: [TVBox-Car3K-FINAL-VISUAL-REPORT.html](TVBox-Car3K-FINAL-VISUAL-REPORT.html)
- Evidence: `car/evidence/ux3k/final-1c0ad382/`

## Results

- Final APK installed SHA matched exactly.
- Java Release compile/assembly, v1/v2/v3 signatures and zipalign passed.
- 15-page matrix is fresh, parseable, 2880x1620 and crash-free for the target process.
- Home brand overlap is fixed.
- Search hotword layout is seven columns with no bottom clipping in the current page matrix.
- Remaining geometry findings are three scroll-tail fragments in settings pages; no primary action is below the 90px reference.
- xiaosa real content/playback evidence remains available under the same-layout runtime evidence and is clearly labeled in the HTML report.

## Non-Passes / Blockers

- clun runtime previously returned HTTP 404 despite a host HTTP 200 probe.
- Python HTTP test collection is blocked by missing `requests`.
- Live/Detail empty states are not content passes without data.
- OEM B01 physical validation, full remote compatibility and long stability remain outside this run.

## Commit And Delivery Policy

The repository changes from this large task are consolidated into one final task commit and pushed to the feature branch. APKs, keys, credentials and raw remote bodies remain outside Git.
