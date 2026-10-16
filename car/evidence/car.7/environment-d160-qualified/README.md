# car.7 Display Environment Qualification

This folder records the environment correction prompted by the user's portrait-screen observation. It does not certify final car.7 application behaviour.

## Finding

The earlier run had a logical 2880x1620 app/PNG but orientation0 and physical display composition `Rect(0,984 - 1620,1895)`. A landscape override had been scaled onto portrait backing. Earlier car.7 geometry results from that state are exploratory only and must not be used as final car-head-unit acceptance.

## Corrected State

- MuMu6.8.7.0 instance1; custom2880x1620, physical density160.
- No manual `wm size` or density override.
- Rotation90degrees, locked; fixed-to-user rotation enabled; automatic rotation disabled.
- Launcher and application display composition: `[0,0,2880,1620]`.
- Input logical and physical viewports: `[0,0,2880,1620]`; deviceSize2880x1620.
- Native PNGs for launcher and application:2880x1620.
- Application hash and PID are parsed from the actual installed package in `qualification.json`.
- Host and guest remain muted; accessibility remains disabled.

## Restart Limitation

The emulator was restarted and both display and input viewports remained full landscape. However, MuMu reset the Android rotation controls to `free/default` and enabled automatic rotation. The read-only gate rejected this state. Explicit relocking was then applied and the gate passed.

Every MuMu startup therefore requires setup plus qualification. Persistent rotation locking across restart is NOT claimed. The production test gate is `car/tests/verify_car_display.sh`; nonzero exit must stop UI acceptance.

## Evidence

- `before-display.*`: incorrect composition before correction.
- `after-restart-before-relock.txt`: actual restarted state, captured without applying setup again.
- `gate-rejected-automatic-rotation.txt`: expected rejection of the unlocked restarted state.
- `qualified-display.txt`, `qualified-*.png`: final explicitly locked and qualified state.
- `gate-qualified.txt`: actual `CAR_DISPLAY_PASS` output.
- `qualification.json`: structured values and remaining limits, generated only after assertions against the real logs and PNGs.
- `SHA256SUMS.txt`: hashes for all archived files.

Screenshots were captured and their dimensions verified programmatically. Visual browser inspection was unavailable for this session and is not claimed. Real head-unit OEM window handling, hardware performance, and final car.7 functional retests remain separate acceptance work.
