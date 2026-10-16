#!/system/bin/sh
# Run through the MuMu plugin after the host pool lease check and env.sh wrapper.
# args: mode tag expected_apk_sha256 shared_output_directory [api_url] [home_site]
set -eu
mode=$1
tag=$2
expected=$3
out=$4
api=${5:-}
home_site=${6:-py_juzong}
pkg=com.github.tvbox.osc.jun
case "$tag" in *[!a-zA-Z0-9_.-]*|'') echo INVALID_TAG; exit 3;; esac
/system/bin/sh "$out/verify_car_display.sh" || exit 42
base=$(pm path "$pkg" | head -1 | cut -d: -f2-)
actual=$(sha256sum "$base" | cut -d' ' -f1)
[ "$actual" = "$expected" ] || { echo APK_HASH_MISMATCH; exit 43; }
echo installed_sha256=$actual
settings put system volume_music 0
settings put system sound_effects_enabled 0
cmd media_session volume --stream 3 --set 0 >/dev/null 2>&1 || true
logcat -c
if test -n "$api"; then
  am instrument --no-hidden-api-checks -w -e tag "$tag" -e mode "$mode" -e api "$api" -e homeSite "$home_site" com.github.tvbox.osc.car7.test/com.github.tvbox.osc.car7.test.Car7Acceptance > "$out/car7-native-$tag.txt" 2>&1
else
  am instrument --no-hidden-api-checks -w -e tag "$tag" -e mode "$mode" com.github.tvbox.osc.car7.test/com.github.tvbox.osc.car7.test.Car7Acceptance > "$out/car7-native-$tag.txt" 2>&1
fi
mkdir -p "$out/car7-native-$tag"
cp -r "/data/data/$pkg/files/car7-native-evidence/$tag/." "$out/car7-native-$tag/"
# Only target test/result and renderer diagnostics; never remote bodies/headers.
logcat -d -s Car7Native:I CarVideo:I GlEnhance:I CarAudio:I JarLoader:I JSLoader:I AndroidRuntime:E '*:S' > "$out/car7-native-$tag-filtered.log"
dumpsys activity exit-info "$pkg" > "$out/car7-native-$tag-exit.txt"
cat "$out/car7-native-$tag.txt"
result="$out/car7-native-$tag/result.json"
test -s "$result" || { echo NO_RESULT_JSON; exit 44; }
grep -Eq '"failed"[[:space:]]*:[[:space:]]*0[[:space:]]*,' "$result" || { echo NATIVE_ASSERTION_FAILED; exit 45; }
echo NATIVE_ASSERTIONS_PASS
