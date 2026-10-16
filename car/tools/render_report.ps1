param(
    [Parameter(Mandatory=$true)][string]$InputDocx,
    [Parameter(Mandatory=$true)][string]$OutputPdf,
    [Parameter(Mandatory=$true)][string]$ResultJson
)
$ErrorActionPreference = 'Stop'
$before = @(Get-Process -Name WINWORD,wps -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$word = $null
$document = $null
$owned = $false
try {
    $word = New-Object -ComObject Word.Application
    Start-Sleep -Milliseconds 1000
    $created = @(Get-Process -Name WINWORD,wps -ErrorAction SilentlyContinue | Where-Object { $before -notcontains $_.Id })
    if ($created.Count -ne 1) { throw 'No unique isolated Word process; existing applications remain untouched.' }
    $wordPid = $created[0].Id
    $owned = $true
    Set-Content -LiteralPath ($ResultJson + '.owned-pid') -Value $wordPid -Encoding ASCII
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $word.AutomationSecurity = 3
    $word.ScreenUpdating = $false
    if ($null -eq $word.Documents) { throw 'Word automation returned no Documents collection.' }
    $file = [object][System.IO.Path]::GetFullPath($InputDocx)
    $confirm = [object]$false
    $readOnly = [object]$false
    $recent = [object]$false
    $document = $word.Documents.Open([ref]$file, [ref]$confirm, [ref]$readOnly, [ref]$recent)
    $document.Fields.Update() | Out-Null
    foreach ($toc in $document.TablesOfContents) { $toc.Update() }
    $document.Repaginate()
    $document.Save()
    $pages = $document.ComputeStatistics(2)
    $pictures = $document.InlineShapes.Count
    $document.ExportAsFixedFormat([System.IO.Path]::GetFullPath($OutputPdf), 17)
    @{renderer='Microsoft Word';pages=$pages;pictures=$pictures;status='completed'} | ConvertTo-Json | Set-Content -LiteralPath $ResultJson -Encoding UTF8
} finally {
    try {
        if ($null -ne $document) {
            $saveNo = [object]0
            $document.Close([ref]$saveNo)
            [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($document)
        }
    } finally {
        if ($null -ne $word) {
            $saveNo = [object]0
            if ($owned) { $word.Quit([ref]$saveNo) }
            [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($word)
        }
        [GC]::Collect()
        [GC]::WaitForPendingFinalizers()
    }
}
