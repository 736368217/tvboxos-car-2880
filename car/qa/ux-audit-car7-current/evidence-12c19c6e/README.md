# Final UI audit evidence

- APK SHA-256: `12C19C6EB2E9ACDEB5B1074F3B76A35A94508D8C64AEDFBE26FC0CFA7A55FD17`
- Package: `com.github.tvbox.osc.jun`
- Version: `1.0.0-car.7` / code `8`
- Display gate: `CAR_DISPLAY_PASS`, 2880x1620, density 160, rotation lock 1
- `pages/identity.json`: native pages final matrix, tag `pages-12c19c6e-final-r1`, `80 passed / 0 failed`
- `pages/geometry.json`: 20 JSON snapshots, 411 interactive nodes, 17 secondary under-90 entries, 31 preloaded/offscreen partial bounds; primary action targets remain at or above the reference.
- `navigation/identity.json`: native navigation/dialog matrix, tag `navigation-12c19c6e-r1`, `43 passed / 0 failed`
- PNG and node JSON files were copied from the MuMu shared directory after guest completion. They are bound to the identity files above; no APK, keystore, raw remote body, or private preference is included.

The final pages run used host-addressed local fixtures (`10.0.2.2`), a harness retry for transient accessibility-window sampling, a live URL argument, and the product's application-internal Back entry for immersive playback. The final result covers detail playback, player controls, progress, live playback, live favorites and page navigation.
