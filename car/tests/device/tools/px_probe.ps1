param(
    [Parameter(Mandatory=$true)][string]$Path,
    [int]$MaxX = 120,
    [int]$StepY = 4,
    [int]$MinCount = 30,
    [string]$Point = ''
)
# 像素取证工具：给「某个控件到底画没画出来」一个可复核的数字，而不是靠肉眼看截图。
#
#   -Point "x,y;x,y"  取样指定点，输出 #RRGGBB
#   （不带 -Point）     从 x=0 起逐列统计「有彩色」的像素数（通道极差 > 25），
#                       用于定位返回杠这类贴边、窄、深色背景上的控件：
#                       命中列会打印 avg=#RRGGBB，可直接与控件配色比对。
#
# 提醒：MuMu 的 2880x1620 实例，`wm size` 报的是 1620x2880（竖屏），
# 但 screencap 出来的图是 2880x1620 —— 本工具按图片坐标取样，与 input tap 一致。
[Console]::OutputEncoding=[Text.Encoding]::UTF8
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap($Path)
Write-Output ("size {0}x{1}" -f $bmp.Width, $bmp.Height)
if ($Point) {
    foreach ($p in ($Point -split ';')) {
        $xy = $p -split ','
        $c = $bmp.GetPixel([int]$xy[0], [int]$xy[1])
        Write-Output ("point {0},{1} = #{2:X2}{3:X2}{4:X2}" -f [int]$xy[0], [int]$xy[1], $c.R, $c.G, $c.B)
    }
    $bmp.Dispose(); exit 0
}
$maxX = [Math]::Min($MaxX, $bmp.Width)
for ($x = 0; $x -lt $maxX; $x++) {
    $count = 0; $firstY = -1; $sumR = 0; $sumG = 0; $sumB = 0
    for ($y = 0; $y -lt $bmp.Height; $y += $StepY) {
        $c = $bmp.GetPixel($x, $y)
        $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
        $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
        if (($mx - $mn) -gt 25) {
            if ($firstY -lt 0) { $firstY = $y }
            $count++; $sumR += $c.R; $sumG += $c.G; $sumB += $c.B
        }
    }
    if ($count -ge $MinCount) {
        $r = [int]($sumR / $count); $g = [int]($sumG / $count); $b = [int]($sumB / $count)
        Write-Output ("col x={0} count={1} firstY={2} avg=#{3:X2}{4:X2}{5:X2}" -f $x, $count, $firstY, $r, $g, $b)
    }
}
$bmp.Dispose()
