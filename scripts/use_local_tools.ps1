# Dot-source: . .\scripts\use_local_tools.ps1
# Applies only to this PowerShell process; never changes machine/user environment.
$project = Split-Path $PSScriptRoot -Parent
$jdk = Get-ChildItem -LiteralPath (Join-Path $project '.tools/jdk') -Directory | Select-Object -First 1
if (-not $jdk) { throw 'Project-local JDK missing. Set JAVA_HOME to your JDK 17 instead.' }
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $project '.tools/android-sdk'
$env:GRADLE_USER_HOME = Join-Path $project '.tools/gradle-home'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
