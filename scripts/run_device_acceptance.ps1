# Run only after the user authorizes physical acceptance and grants the synthetic SAF folder.
# Never auto-enables the main app: that would conceal OEM freezing regressions.
[CmdletBinding(DefaultParameterSetName = 'Core')]
param(
    [Parameter(ParameterSetName = 'Core')][ValidateSet(23, 103, 1003)][int]$ExpectedBooks = 23,
    [Parameter(ParameterSetName = 'Rotation', Mandatory)][switch]$RotationOnly,
    [Parameter(ParameterSetName = 'Extended', Mandatory)][switch]$Extended,
    [Parameter(ParameterSetName = 'Card', Mandatory)][ValidateSet('mounted','missing')][string]$CardState,
    [Parameter(ParameterSetName = 'Revoke', Mandatory)][switch]$RevokeSyntheticDocument,
    [Parameter(ParameterSetName = 'Keys', Mandatory)][switch]$ManualKeys,
    [Parameter(ParameterSetName = 'PageKeys', Mandatory)][switch]$PageKeys,
    [Parameter(ParameterSetName = 'ReaderUi', Mandatory)][switch]$ReaderUi
)
$ErrorActionPreference = 'Stop'
$devices = @(& adb devices | Select-String '^\S+\s+device$')
if ($devices.Count -ne 1) { throw 'Exactly one authorized device is required.' }
$mainState = @(& adb shell dumpsys package org.kobostyle.launcher)
if ($LASTEXITCODE -ne 0 -or ($mainState -match 'enabled=[234]').Count -gt 0) {
    throw 'Main app is disabled or unavailable. Resolve through BOOX per-app UI before testing.'
}
# OEM may freeze the instrumentation package, which has no ordinary launcher UI.
& adb shell pm enable --user 0 org.kobostyle.launcher.test
if ($LASTEXITCODE -ne 0) { throw 'Could not enable instrumentation package.' }
$arguments = @('shell','am','instrument','-w','-r')
if ($RotationOnly) {
    $arguments += @('-e','class','org.kobostyle.launcher.DeviceAcceptanceTest#activityRotatesWithoutChangingSystemSettings')
} elseif ($Extended) {
    $arguments += @('-e','class','org.kobostyle.launcher.ExtendedAcceptanceTest')
} elseif ($CardState) {
    $arguments += @('-e','class','org.kobostyle.launcher.StorageAcceptanceTest#cardAvailability','-e','cardState',$CardState)
} elseif ($RevokeSyntheticDocument) {
    $arguments += @('-e','class','org.kobostyle.launcher.StorageAcceptanceTest#singleDocumentImportAndRevoke','-e','allowSyntheticGrantRelease','yes')
} elseif ($ManualKeys) {
    $arguments += @('-e','class','org.kobostyle.launcher.HardwareKeyAcceptanceTest','-e','manualKeys','yes')
} elseif ($ReaderUi) {
    $arguments += @('-e','class','org.kobostyle.launcher.ReaderUiAcceptanceTest')
} elseif ($PageKeys) {
    $arguments += @('-e','class','org.kobostyle.launcher.PageKeySettingsTest')
} else {
    $arguments += @('-e','class','org.kobostyle.launcher.DeviceAcceptanceTest','-e','expectedBooks',"$ExpectedBooks")
}
$arguments += 'org.kobostyle.launcher.test/androidx.test.runner.AndroidJUnitRunner'
$output = [System.Collections.Generic.List[string]]::new()
& adb @arguments 2>&1 | ForEach-Object {
    $line = "$_"
    $output.Add($line)
    Write-Output $line
}
$adbExit = $LASTEXITCODE
# am instrument can exit 0 even when a test fails or the process crashes.
if ($adbExit -ne 0 -or -not ($output -match '^OK \([0-9]+ tests?\)') -or ($output -match 'FAILURES!!!|Process crashed').Count -gt 0) {
    throw 'Device acceptance did not pass; inspect instrumentation output.'
}
