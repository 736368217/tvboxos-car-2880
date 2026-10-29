param(
    [Parameter(Mandatory=$true)][string]$Path,
    [int]$X = 1440,
    [int]$Step = 20
)
# 沿一列逐段输出颜色，用来判断弹窗/控件的纵向边界（弹窗底板 vs 页面背景）。
[Console]::OutputEncoding=[Text.Encoding]::UTF8
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap($Path)
"size {0}x{1} column x={2}" -f $bmp.Width, $bmp.Height, $X
$prev = $null
$runStart = 0
for ($y = 0; $y -lt $bmp.Height; $y += $Step) {
    $c = $bmp.GetPixel($X, $y)
    $key = "{0:X2}{1:X2}{2:X2}" -f $c.R, $c.G, $c.B
    if ($key -ne $prev) {
        if ($prev -ne $null) { "  y={0,4}..{1,4}  #{2}" -f $runStart, ($y - $Step), $prev }
        $prev = $key
        $runStart = $y
    }
}
"  y={0,4}..{1,4}  #{2}" -f $runStart, ($bmp.Height - 1), $prev
$bmp.Dispose()
