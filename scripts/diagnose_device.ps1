# Read-only diagnostics: no launches, settings changes, or device file writes.
$ErrorActionPreference = 'Stop'
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { throw 'adb not found in PATH.' }
$ErrorActionPreference = 'Continue'
$listing = @(& adb devices 2>&1 | ForEach-Object { "$_" })
$listExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($listExit -ne 0) { throw 'adb devices failed; raw output withheld to protect identifiers.' }
$devices = @($listing | Where-Object { $_ -match '^\S+\s+(device|unauthorized|offline|recovery|sideload|bootloader)\s*$' })
foreach ($line in $devices) { Write-Output ('Device [REDACTED]: ' + ($line -split '\s+')[1]) }
if ($devices.Count -ne 1 -or $devices[0] -notmatch '^\S+\s+device\s*$') {
    throw 'Expected exactly one authorized device; no device diagnostics executed.'
}
$serial = ($devices[0] -split '\s+')[0]
$script:failures = 0
function Read-Adb {
    param([string[]]$AdbArgs, [string]$Filter = '')
    Write-Output ('COMMAND: adb shell ' + ($AdbArgs -join ' '))
    $ErrorActionPreference = 'Continue'
    $output = @(& adb -s $serial shell @AdbArgs 2>&1 | ForEach-Object { "$_" })
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $failed = $code -ne 0 -or ($output -match '(?i)unknown command|not found|exception|permission denial|error:').Count -gt 0
    if ($failed) { $script:failures++; Write-Output "FAILED (exit $code); continuing with remaining read-only commands." }
    foreach ($line in $output) {
        if ($Filter -and -not $failed -and $line -notmatch $Filter) { continue }
        Write-Output ($line.Replace($serial, '[REDACTED]'))
    }
}
foreach ($property in @('ro.product.model','ro.product.manufacturer','ro.build.version.release','ro.build.version.sdk','ro.build.fingerprint')) {
    Read-Adb -AdbArgs @('getprop', $property)
}
Read-Adb -AdbArgs @('wm', 'size')
Read-Adb -AdbArgs @('wm', 'density')
Read-Adb -AdbArgs @('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
Read-Adb -AdbArgs @('cmd','package','query-activities','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
Read-Adb -AdbArgs @('pm','list','packages') -Filter 'onyx|boox|reader|kobo'
foreach ($mime in @('application/epub+zip','application/pdf')) {
    # Synthetic URI: handler discovery only; does not open or read any book.
    Read-Adb -AdbArgs @('cmd','package','query-activities','--brief','-a','android.intent.action.VIEW','-c','android.intent.category.DEFAULT','-d','content://org.kobostyle.probe/document/probe','-t',$mime)
}
Write-Output "DONE: $script:failures failed command(s). No device settings or files changed."
Write-Output 'Handler discovery is not proof of content URI compatibility or app-level package visibility.'
