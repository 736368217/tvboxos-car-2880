#!/system/bin/sh
# Read-only display/input qualification. Run inside the system_server namespace.
set -eu
size=$(wm size)
density=$(wm density)
rotation=$(wm user-rotation)
fixed=$(wm fixed-to-user-rotation)
display=$(dumpsys display)
input=$(dumpsys input)
window=$(dumpsys window displays)
echo "$size"
echo "$density"
echo "$rotation"
echo "$fixed"
if echo "$size $density" | grep -q Override; then echo CAR_DISPLAY_FAIL_OVERRIDE; exit 1; fi
echo "$density" | grep -q 'Physical density: 160'
test "$rotation" = 'lock 1'
test "$fixed" = 'enabled'
test "$(settings get system accelerometer_rotation)" = '0'
test "$(settings get system user_rotation)" = '1'
echo "$display" | grep -F 'mCurrentOrientation=1'
echo "$display" | grep -F 'mCurrentDisplayRect=Rect(0, 0 - 2880, 1620)'
echo "$input" | grep 'Viewport INTERNAL: displayId=0' | grep -F 'logicalFrame=[0, 0, 2880, 1620], physicalFrame=[0, 0, 2880, 1620], deviceSize=[2880, 1620]'
echo "$window" | grep -F 'DisplayFrames w=2880 h=1620 r=1'
echo CAR_DISPLAY_PASS
