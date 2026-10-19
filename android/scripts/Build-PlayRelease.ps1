param(
    [Parameter(Mandatory = $true)][string]$SigningProperties,
    [Parameter(Mandatory = $true)][string]$AndroidSdk,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Gradle = (Join-Path $PSScriptRoot '..\gradlew.bat'),
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$signingFile = (Resolve-Path -LiteralPath $SigningProperties).Path
$sdkPath = (Resolve-Path -LiteralPath $AndroidSdk).Path
$javaPath = (Resolve-Path -LiteralPath $JavaHome).Path
$gradlePath = (Resolve-Path -LiteralPath $Gradle).Path
$projectPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$previousSdkRoot = $env:ANDROID_SDK_ROOT
try {
    $env:JAVA_HOME = $javaPath
    $env:ANDROID_HOME = $sdkPath
    $env:ANDROID_SDK_ROOT = $sdkPath
    $gradleArguments = @(
        '--no-daemon', '--console=plain', '-p', $projectPath,
        "-PopenNoodoeSigningProperties=$signingFile",
        'testReleaseUnitTest', 'lintRelease', 'assembleRelease', 'bundleRelease'
    )
    if ($Offline) { $gradleArguments += '--offline' }
    & $gradlePath @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Release build failed (exit $LASTEXITCODE)." }
    Write-Output "Play upload: $projectPath\app\build\outputs\bundle\release\app-release.aab"
    Write-Output "Signed APK: $projectPath\app\build\outputs\apk\release\app-release.apk"
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
    $env:ANDROID_SDK_ROOT = $previousSdkRoot
}