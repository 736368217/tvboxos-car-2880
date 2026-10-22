# 车机 UI 自动化自检（PowerShell）
#
# 目的：解决"测试环境偶尔把应用布局成竖屏 1620x2880，导致小棍点不到/看不到，
# 从而把环境问题误判成产品 bug"的问题。
#
# 做法：
#   1) 租一台 MuMu 实例（mumu-pool，独占 lease）
#   2) **几何门禁**：把 wm size/density 重置、旋转锁死，反复校验
#      dumpsys display = 2880x1620 且 dumpsys activity top 里应用配置是
#      w2880dp h1620dp（land）；不满足就重试，仍不满足则直接失败退出
#      —— 绝不带着错误几何继续跑用例
#   3) 逐项截图并用像素/节点断言
#   4) 结束时释放 lease
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File car/tests/device/run_car_ui_checks.ps1 `
#     -Apk <path-to-apk>
#
# 退出码：0 全部通过；1 断言失败；2 几何门禁失败；3 环境/依赖缺失

param(
    [Parameter(Mandatory = $true)][string]$Apk,
    [string]$Owner = ("car-ui-check-" + [guid]::NewGuid().ToString("N").Substring(0, 8)),
    [int]$MaxGeometryAttempts = 6
)

$ErrorActionPreference = "Continue"
$pool = Join-Path $env:USERPROFILE ".mumu-pool\mumu-pool.py"
$manager = "D:\Program Files\Netease\MuMu\nx_main\MuMuManager.exe"
$outDir = Join-Path $env:PI_SCRATCH_DIR ("car-ui-check-" + (Get-Date -Format "HHmmss"))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not (Test-Path $pool)) { Write-Host "FATAL: mumu-pool.py not found"; exit 3 }
if (-not (Test-Path $manager)) { Write-Host "FATAL: MuMuManager.exe not found"; exit 3 }
if (-not (Test-Path $Apk)) { Write-Host "FATAL: apk not found: $Apk"; exit 3 }

$script:index = -1
$script:failures = @()

function Log($msg) { Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $msg) }

function Pool([string[]]$poolArgs) {
    $out = & python $pool @poolArgs 2>&1 | Out-String
    return $out.Trim()
}

$script:adbExe = "D:\Program Files\Netease\MuMu\nx_device\15.0\shell\adb.exe"
if (-not (Test-Path $script:adbExe)) { $script:adbExe = "adb" }

function Adb([string[]]$adbArgs) {
    $argList = @("-s", $script:serial) + $adbArgs
    $text = (& $script:adbExe @argList 2>&1 | Out-String)
    return [string]$text
}

function Sh([string]$cmd) { return Adb @("shell", $cmd) }

function TopActivity() {
    $t = Sh "dumpsys activity activities | grep topResumedActivity"
    if ($t -match "activity\.(\w+)") { return $Matches[1] }
    return "unknown"
}

# 回到首页：最多按 4 次返回，直到 HomeActivity
function EnsureHome() {
    for ($i = 0; $i -lt 4; $i++) {
        if ((TopActivity) -eq "HomeActivity") { return $true }
        Sh "input keyevent 4" | Out-Null
        Start-Sleep -Milliseconds 700
    }
    return ((TopActivity) -eq "HomeActivity")
}

function TapText([string]$text) {
    $dump = Join-Path $outDir "ui.xml"
    if (Test-Path $dump) { Remove-Item $dump -Force }
    Sh "uiautomator dump /sdcard/ui.xml" | Out-Null
    Adb @("pull", "/sdcard/ui.xml", $dump) | Out-Null
    if (-not (Test-Path $dump)) { return $false }
    $xml = Get-Content $dump -Raw -Encoding UTF8
    $m = [regex]::Match($xml, 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $false }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    Sh ("input tap {0} {1}" -f [int]$x, [int]$y) | Out-Null
    return $true
}

# 打开设置页，并断言确实到了 SettingActivity
function OpenSettings() {
    if (-not (EnsureHome)) { return $false }
    if (-not (TapText "设置")) { return $false }
    Start-Sleep -Seconds 2
    return ((TopActivity) -eq "SettingActivity")
}

# 把返回组件调到确定的被测状态：进入高级弹窗，把模式切成「边缘拉出」、隐藏切成 4 秒
function NormalizeBackBarState() {
    if (-not (OpenSettings)) { return $false }
    if (-not (TapText "高级")) { return $false }
    Start-Sleep -Milliseconds 1200
    $dump = Join-Path $outDir "normalize.xml"
    Sh "uiautomator dump /sdcard/normalize.xml" | Out-Null
    Adb @("pull", "/sdcard/normalize.xml", $dump) | Out-Null
    if (-not (Test-Path $dump)) { return $false }
    $xml = Get-Content $dump -Raw -Encoding UTF8
    if ($xml -notmatch "返回模式") { return $false }
    for ($i = 0; $i -lt 3; $i++) {
        if ($xml -match "边缘拉出") { break }
        Sh "input tap 1400 578" | Out-Null   # 返回模式
        Start-Sleep -Milliseconds 700
        Sh "uiautomator dump /sdcard/normalize.xml" | Out-Null
        Adb @("pull", "/sdcard/normalize.xml", $dump) | Out-Null
        $xml = Get-Content $dump -Raw -Encoding UTF8
    }
    for ($i = 0; $i -lt 4; $i++) {
        if ($xml -match "4 秒") { break }
        Sh "input tap 1400 1202" | Out-Null  # 自动隐藏
        Start-Sleep -Milliseconds 700
        Sh "uiautomator dump /sdcard/normalize.xml" | Out-Null
        Adb @("pull", "/sdcard/normalize.xml", $dump) | Out-Null
        $xml = Get-Content $dump -Raw -Encoding UTF8
    }
    Sh "input keyevent 4" | Out-Null
    Start-Sleep -Milliseconds 800
    return (($xml -match "边缘拉出") -and ($xml -match "4 秒"))
}


function UiXml() {
    $dump = Join-Path $outDir "ui.xml"
    if (Test-Path $dump) { Remove-Item $dump -Force }
    Sh "uiautomator dump /sdcard/ui.xml" | Out-Null
    Adb @("pull", "/sdcard/ui.xml", $dump) | Out-Null
    if (-not (Test-Path $dump)) { return "" }
    return (Get-Content $dump -Raw -Encoding UTF8)
}

function TapResId([string]$id) {
    $xml = UiXml
    $m = [regex]::Match($xml, 'resource-id="[^"]*' + [regex]::Escape($id) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if (-not $m.Success) { return $false }
    $x = ([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2
    $y = ([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2
    Sh ("input tap {0} {1}" -f [int]$x, [int]$y) | Out-Null
    return $true
}

# 把实例自举成"有普通配置"的状态：写入订阅地址 -> 回到首页
function BootstrapConfig([string]$url) {
    # 干净实例首次启动会直接弹"配置地址"对话框；没有就退回设置页路径
    if (-not (TapResId "input")) {
        if (-not (OpenSettings)) { return $false }
        if (-not (TapText "配置地址")) { return $false }
        Start-Sleep -Milliseconds 1500
        if (-not (TapResId "input")) { return $false }
    }
    Start-Sleep -Milliseconds 500
    Sh "input keyevent 123" | Out-Null
    $del = ""
    for ($i = 0; $i -lt 90; $i++) { $del += "input keyevent 67; " }
    Sh $del | Out-Null
    Sh ("input text '" + $url + "'") | Out-Null
    Start-Sleep -Milliseconds 600
    if (-not (TapText "确定")) { return $false }
    Start-Sleep -Seconds 4
    # 首次配置会弹存储权限，按文本点允许
    TapText "允许" | Out-Null
    Start-Sleep -Seconds 3
    return ((TopActivity) -eq "HomeActivity")
}

function Screenshot([string]$name) {
    $remote = "/sdcard/$name.png"
    Sh "screencap -p $remote" | Out-Null
    $local = Join-Path $outDir "$name.png"
    Adb @("pull", $remote, $local) | Out-Null
    if (-not (Test-Path $local)) { throw "screenshot failed: $name" }
    return $local
}

function PngSize([string]$path) {
    $b = [IO.File]::ReadAllBytes($path)
    $w = [int]$b[16] * 16777216 + [int]$b[17] * 65536 + [int]$b[18] * 256 + [int]$b[19]
    $h = [int]$b[20] * 16777216 + [int]$b[21] * 65536 + [int]$b[22] * 256 + [int]$b[23]
    return @($w, $h)
}

# 把 PNG 转成 rgb24 裸数据，供像素断言使用
function ToRaw([string]$png) {
    $raw = [IO.Path]::ChangeExtension($png, ".raw")
    & $script:ffmpeg -y -loglevel error -i $png -f rawvideo -pix_fmt rgb24 $raw 2>$null
    if (-not (Test-Path $raw)) { throw "ffmpeg convert failed: $png" }
    return $raw
}

$script:ffmpeg = "C:\Users\WIN10\Tools\video-use-ffmpeg\ffmpeg-9.0-essentials_build\bin\ffmpeg.exe"
if (-not (Test-Path $script:ffmpeg)) { Write-Host "FATAL: ffmpeg not found"; exit 3 }

# ---------- 1. 租设备 ----------
Log "acquire lease (owner=$Owner)"
$acquire = Pool @("acquire", "--owner", $Owner, "--max-instances", "3", "--lease-minutes", "90", "--mute")
if ($acquire -notmatch '"index"\s*:\s*(\d+)') {
    Write-Host "FATAL: acquire failed: $acquire"
    exit 3
}
$script:index = [int]$Matches[1]
if ($acquire -notmatch '"adb"\s*:\s*"([^"]+)"') {
    Write-Host "FATAL: acquire returned no adb endpoint: $acquire"
    exit 3
}
$script:serial = $Matches[1]
Log "lease index=$script:index adb=$script:serial"
& $script:adbExe connect $script:serial | Out-Null
Start-Sleep -Milliseconds 500

try {
    # ---------- 2. 安装 ----------
    Log "push apk"
    Adb @("push", $Apk, "/data/local/tmp/car-ui-check.apk") | Out-Null
    Log "install"
    Sh "pm install -r /data/local/tmp/car-ui-check.apk" | Out-Null
    Log "clear app data (first-run state)"
    Sh "pm clear com.github.tvbox.osc.jun" | Out-Null

    # ---------- 3. 几何门禁 ----------
    $geometryOk = $false
    for ($attempt = 1; $attempt -le $MaxGeometryAttempts; $attempt++) {
        Log "geometry attempt $attempt/$MaxGeometryAttempts"
        Sh "wm size reset; wm density reset" | Out-Null
        Sh "wm user-rotation free" | Out-Null
        Start-Sleep -Milliseconds 800
        Sh "wm user-rotation lock 1; wm fixed-to-user-rotation enabled; settings put system accelerometer_rotation 0; settings put system user_rotation 1" | Out-Null
        Start-Sleep -Milliseconds 1500

        $display = Sh "dumpsys display | grep mCurrentDisplayRect"
        $size = Sh "wm size; wm density"
        $appCfg = Sh "dumpsys activity top | grep mCurrentConfig | tail -1"
        $overrideBad = ($size -match "Override size") -or ($size -match "Override density")
        $displayOk = $display -match "Rect\(0,\s*0\s*-\s*2880,\s*1620\)"
        $appOk = $appCfg -match "w2880dp" -and $appCfg -match "h1620dp" -and $appCfg -match "\bland\b"

        Log ("  display={0} overrideBad={1} appLandscape={2}" -f $displayOk, $overrideBad, $appOk)
        if ($attempt -eq 1) {
            $d1 = ($display -replace "\s+", " ")
            $a1 = ($appCfg -replace "\s+", " ")
            Log ("  RAW display: " + $d1.Substring(0, [Math]::Min(200, $d1.Length)))
            Log ("  RAW appCfg : " + $a1.Substring(0, [Math]::Min(200, $a1.Length)))
        }
        if ($displayOk -and $appOk -and -not $overrideBad) { $geometryOk = $true; break }

        # 几何不对：重启应用让 Activity 重新按当前方向布局
        Sh "am force-stop com.github.tvbox.osc.jun" | Out-Null
        Start-Sleep -Milliseconds 500
        Sh "monkey -p com.github.tvbox.osc.jun -c android.intent.category.LAUNCHER 1" | Out-Null
        Start-Sleep -Seconds 6
    }
    if (-not $geometryOk) {
        Write-Host "GEOMETRY_GATE_FAIL: 应用不是 2880x1620 横向布局，测试终止（避免测出假结果）"
        exit 2
    }
    Log "geometry gate passed"

    # 截图尺寸也必须与布局一致
    $shot = Screenshot "g0"
    $wh = PngSize $shot
    if ($wh[0] -ne 2880 -or $wh[1] -ne 1620) {
        Write-Host ("GEOMETRY_GATE_FAIL: 截图 {0}x{1} != 2880x1620" -f $wh[0], $wh[1])
        exit 2
    }
    Log "screenshot geometry ok (2880x1620)"

    # ---------- 3.5 自举配置（让干净实例变成可测状态） ----------
    Log "bootstrap config (clun.top)"
    if (BootstrapConfig "https://clun.top/box.json") {
        Log "  config bootstrapped, waiting for sites"
        Start-Sleep -Seconds 8
    } else {
        Log "  WARN: 配置自举失败，后续用例可能不可用"
    }

    # ---------- 4. 用例 1：返回杠可见 + 自动隐藏 + 触摸唤醒 ----------
    Log "normalize back-bar state (edge mode / hide 4s)"
    if (-not (NormalizeBackBarState)) { Log "  WARN: 前置状态归一化失败（该镜像首页可能没有设置入口），按当前配置继续" }
    EnsureHome | Out-Null
    Start-Sleep -Milliseconds 900
    Log "case1: pill visible -> hidden -> woken"
    $visible = Screenshot "pill_visible"
    Start-Sleep -Seconds 6
    $hidden = Screenshot "pill_hidden"
    Sh "input tap 20 800" | Out-Null
    Start-Sleep -Milliseconds 600
    $woken = Screenshot "pill_woken"
    ToRaw $visible | Out-Null
    ToRaw $hidden | Out-Null
    ToRaw $woken | Out-Null

    $script:checkScript = Join-Path $outDir "check_pill.py"
    @'
import sys, subprocess, json
w, h = 2880, 1620
def load(png):
    raw = png.replace('.png', '.raw')
    return open(raw, 'rb').read()
def pill_bbox(data):
    minx, miny, maxx, maxy, n = w, h, -1, -1, 0
    for y in range(h):
        base = y * w * 3
        for x in range(0, 100):
            i = base + x * 3
            r, g, b = data[i], data[i+1], data[i+2]
            if r > 85 and g > 85 and b > 85:
                n += 1
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
    return n, (minx, miny, maxx, maxy)
def tall_run(data):
    best = 0; cur = 0
    for y in range(h):
        base = y * w * 3
        hit = False
        for x in range(0, 100):
            i = base + x * 3
            if data[i] > 85 and data[i+1] > 85 and data[i+2] > 85:
                hit = True; break
        cur = cur + 1 if hit else 0
        best = max(best, cur)
    return best
a = load(sys.argv[1]); b = load(sys.argv[2]); c = load(sys.argv[3])
res = {
  'visible_pixels': pill_bbox(a)[0],
  'visible_run': tall_run(a),
  'hidden_pixels': tall_run(b),
  'woken_run': tall_run(c),
}
print(json.dumps(res))
ok = res['visible_run'] >= 150 and res['hidden_pixels'] <= 3 and res['woken_run'] >= 150
sys.exit(0 if ok else 1)
'@ | Set-Content -Path $script:checkScript -Encoding UTF8

    $pillResult = & python $script:checkScript $visible $hidden $woken 2>&1 | Out-String
    Log ("  " + $pillResult.Trim())
    if ($LASTEXITCODE -ne 0) { $script:failures += "pill hide/wake" }

    # ---------- 5. 用例 2：设置页「高级」入口 + 弹窗 ----------
    Log "case2: advanced settings entry"
    if (-not (OpenSettings)) {
        $script:failures += "cannot open settings"
        Log "  FAIL: 打不开设置页"
    }
    Sh "uiautomator dump /sdcard/adv.xml" | Out-Null
    $advLocal = Join-Path $outDir "adv.xml"
    Adb @("pull", "/sdcard/adv.xml", $advLocal) | Out-Null
    $adv = if (Test-Path $advLocal) { Get-Content $advLocal -Raw -Encoding UTF8 } else { "" }
    if ($adv -notmatch "高级") { $script:failures += "advanced entry missing" ; Log "  FAIL: 高级 button not found" }
    else { Log "  OK: 高级 button found" }

    # ---------- 6. 结果 ----------
    if ($script:failures.Count -gt 0) {
        Write-Host ("CHECKS_FAILED: " + ($script:failures -join ", "))
        exit 1
    }
    Write-Host "ALL_CHECKS_PASSED"
    exit 0
}
finally {
    Log "release lease"
    Pool @("release", "--owner", $Owner, "--index", "$script:index") | Out-Null
}


