# Host-only fake adb tests. Never contacts an Android device.
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'diagnose_device.ps1'
$global:diagnosticTestMode = 'authorized'
$global:diagnosticTestCalls = [System.Collections.Generic.List[string]]::new()
function adb {
    $line = $args -join ' '
    $global:diagnosticTestCalls.Add($line)
    $global:LASTEXITCODE = 0
    if ($line -eq 'devices') {
        'List of devices attached'
        switch ($global:diagnosticTestMode) {
            'none' { return }
            'unauthorized' { 'SENSITIVE_SERIAL unauthorized'; return }
            'offline' { 'SENSITIVE_SERIAL offline'; return }
            'multiple' { 'SENSITIVE_SERIAL device'; 'OTHER_SERIAL device'; return }
            default { 'SENSITIVE_SERIAL device'; return }
        }
    }
    if ($line -notmatch '^-s SENSITIVE_SERIAL shell (getprop |wm (size|density)$|cmd package (resolve-activity|query-activities) |pm list packages$)') {
        throw "Unexpected device command in diagnostic: $line"
    }
    if ($global:diagnosticTestMode -eq 'unsupported' -and $line -match 'resolve-activity') {
        $global:LASTEXITCODE = 1; 'Unknown command: resolve-activity'; return
    }
    'mock read-only response SENSITIVE_SERIAL'
}
foreach ($scenario in @('none','unauthorized','offline','multiple')) {
    $global:diagnosticTestMode = $scenario; $global:diagnosticTestCalls.Clear(); $failed = $false
    try { $result = @(& $scriptPath) } catch { $failed = $true }
    if (-not $failed -or $global:diagnosticTestCalls.Count -ne 1) { throw "FAIL: $scenario must stop before shell access" }
    Write-Output "PASS: $scenario rejected without shell access"
}
foreach ($scenario in @('authorized','unsupported')) {
    $global:diagnosticTestMode = $scenario; $global:diagnosticTestCalls.Clear()
    $result = @(& $scriptPath)
    if (($result -join "`n") -match 'SENSITIVE_SERIAL') { throw 'FAIL: serial leaked' }
    if ($global:diagnosticTestCalls.Count -ne 13) { throw "FAIL: expected all 13 read-only commands, got $($global:diagnosticTestCalls.Count)" }
    if ($scenario -eq 'unsupported' -and -not ($result -match '1 failed command')) { throw 'FAIL: OEM error not recorded' }
    Write-Output "PASS: $scenario completes and redacts identifiers"
}
Remove-Item Function:\adb
