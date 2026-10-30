param([switch]$RunUnitTests, [switch]$RunDeviceTests, [switch]$UseFirebaseEmulators, [string]$TestClass, [switch]$BuildDeviceTests)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$previousJavaHome = $env:JAVA_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$buildExitCode = 1

Push-Location $repoRoot
try {
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJava 'bin\java.exe')) {
        $env:JAVA_HOME = $studioJava
    }

    # Windows Java sockets can fail when TEMP uses a short user-profile path.
    $socketDirectory = Join-Path $repoRoot '.gradle\socket'
    New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
    $socketDirectory = $socketDirectory.Replace('\', '/')
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djdk.net.unixdomain.tmpdir=`"$socketDirectory`" -Djava.net.preferIPv4Stack=true"

    $buildTasks = @(':app:assembleDebug')
    if ($RunUnitTests) { $buildTasks += ':app:testDebugUnitTest' }
    if ($RunDeviceTests) { $buildTasks += ':app:connectedDebugAndroidTest' }
    elseif ($BuildDeviceTests) { $buildTasks += ':app:assembleDebugAndroidTest' }
    $firebaseMode = if ($UseFirebaseEmulators -or $RunDeviceTests) { 'true' } else { 'false' }
    $testArguments = @()
    if ($TestClass) { $testArguments += "-Pandroid.testInstrumentationRunnerArguments.class=$TestClass" }
    & .\gradlew.bat @buildTasks @testArguments "-PuseFirebaseEmulators=$firebaseMode" --no-daemon --no-configuration-cache
    $buildExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    Pop-Location
}

exit $buildExitCode
