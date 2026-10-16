#!/system/bin/sh
# Read-only MuMu qualification gate; run inside the system-server mount namespace.
set -u
fail() { echo "CAR_DISPLAY_FAIL: $*"; exit 42; }
size=$(wm size) || fail 'wm size unavailable'
density=$(wm density) || fail 'wm density unavailable'
rotation=$(wm user-rotation) || fail 'rotation unavailable'
fixed=$(wm fixed-to-user-rotation) || fail 'fixed rotation unavailable'
auto=$(settings get system accelerometer_rotation)
user=$(settings get system user_rotation)
display=$(dumpsys display | grep -E 'mCurrentOrientation=|mCurrentDisplayRect=')
viewport=$(dumpsys input | grep 'Viewport INTERNAL: displayId=0' | head -1)
frames=$(dumpsys window displays | grep 'DisplayFrames' | head -1)
printf '%s\n' "$size" "$density" "rotation=$rotation fixed=$fixed auto=$auto user=$user" "$display" "$viewport" "$frames"
echo "$size" | grep -q 'Override size:' && fail 'manual size override is not accepted'
echo "$density" | grep -q 'Override density:' && fail 'manual density override is not accepted'
echo "$density" | grep -q 'Physical density: 160' || fail 'physical density must be160'
test "$rotation" = 'lock 1' || fail 'landscape rotation is not locked'
test "$fixed" = 'enabled' || fail 'fixed-to-user rotation is not enabled'
test "$auto" = 0 || fail 'auto rotation must be disabled'
test "$user" = 1 || fail 'user rotation must be90degrees'
echo "$display" | grep -q 'mCurrentOrientation=1' || fail 'display orientation is not landscape'
echo "$display" | grep -Eq 'mCurrentDisplayRect=Rect\(0, ?0 - 2880, ?1620\)' || fail 'display is scaled or offset'
echo "$viewport" | grep -q 'orientation=1' || fail 'input orientation mismatch'
echo "$viewport" | grep -Fq 'logicalFrame=[0, 0, 2880, 1620]' || fail 'logical input viewport mismatch'
echo "$viewport" | grep -Fq 'physicalFrame=[0, 0, 2880, 1620]' || fail 'physical input viewport mismatch'
echo "$viewport" | grep -Fq 'deviceSize=[2880, 1620]' || fail 'input device size mismatch'
echo "$frames" | grep -q 'w=2880 h=1620 r=1' || fail 'window display frame mismatch'
echo CAR_DISPLAY_PASS
