param([string]$Apk = '.\app\build\outputs\apk\debug\app-debug.apk')
$ErrorActionPreference = 'Stop'
if (-not (Test-Path $Apk)) { throw "Debug APK missing: $Apk. Build first with .\gradlew.bat assembleDebug" }
$devices = @(& adb devices | Select-String '^[^\s]+\s+device$')
if ($devices.Count -ne 1) { throw 'Exactly one authorized ADB device required.' }
& adb install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'adb install failed' }
Write-Host 'Installed. Launch manually from BOOX Apps. This script does NOT change default HOME.'
