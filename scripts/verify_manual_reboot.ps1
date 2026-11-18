# Observe a separately performed normal Android restart. Never reboot, change HOME, or enable packages here.
param([switch]$RecordBaseline)
$ErrorActionPreference = 'Stop'
$devices = @(& adb devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) { throw 'Exactly one authorized device is required.' }
function Read-Adb([string[]]$Arguments) {
    $value = @(& adb shell @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Read-only reboot observation failed.' }
    return ($value -join "`n").Trim()
}
$boot = Read-Adb @('cat','/proc/sys/kernel/random/boot_id')
if ($boot -notmatch '^[0-9a-f-]{36}$') { throw 'OEM boot identifier unavailable; do not substitute a write command.' }
$homeResult = Read-Adb @('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME')
$homeComponent = @($homeResult -split "`n" | Where-Object { $_ -match '^\S+/\S+$' })
if ($homeComponent.Count -ne 1) { throw 'Could not determine baseline HOME.' }
$stateFile = Join-Path $PSScriptRoot '..\.tools\acceptance\reboot-baseline.json'
if ($RecordBaseline) {
    @{ boot = $boot; home = $homeComponent[0] } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    Write-Output 'Baseline recorded locally. Ready to verify a subsequent normal Android restart.'
    exit 0
}
$before = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
if ($before.boot -eq $boot) { throw 'Boot identifier unchanged: a restart has not been observed.' }
if ((Read-Adb @('getprop','sys.boot_completed')) -ne '1') { throw 'Boot has not completed.' }
if ($before.home -ne $homeComponent[0]) { throw 'HOME differs from baseline; no setting will be changed by this script.' }
$package = Read-Adb @('dumpsys','package','org.kobostyle.launcher')
if ($package -notmatch 'installed=true' -or $package -match 'enabled=[234]') { throw 'KoboStyle is missing or disabled after restart; no auto-enable attempted.' }
$started = Read-Adb @('am','start','-W','-n','org.kobostyle.launcher/.ui.MainActivity')
if ($started -notmatch 'Status: ok') { throw 'App launch did not report success.' }
Write-Output 'REBOOT changed=PASS completed=PASS homeUnchanged=PASS appLaunch=PASS'
Write-Output 'Still verify the visible library and SAF access; this is not a P5 HOME rollback demonstration.'
