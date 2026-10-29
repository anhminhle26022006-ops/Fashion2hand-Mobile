$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$previousPath = $env:PATH
$previousPayments = $env:ENABLE_SIMULATED_PAYMENTS
$previousShipping = $env:ENABLE_SIMULATED_SHIPPING
$previousDiscoveryTimeout = $env:FUNCTIONS_DISCOVERY_TIMEOUT
$emulatorExitCode = 1
Push-Location $PSScriptRoot
try {
    $env:ENABLE_SIMULATED_PAYMENTS = 'true'
    $env:ENABLE_SIMULATED_SHIPPING = 'true'
    # Cold Node workers can exceed the CLI's 30-second startup deadline on Windows.
    $env:FUNCTIONS_DISCOVERY_TIMEOUT = '90'
    $studioJava = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJava 'bin\java.exe')) {
        $env:JAVA_HOME = $studioJava
        $env:PATH = "$studioJava\bin;$previousPath"
    }
    $socketDirectory = Join-Path $PSScriptRoot '.gradle\socket'
    New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
    $socketDirectory = $socketDirectory.Replace('\', '/')
    $env:JAVA_TOOL_OPTIONS = "$previousJavaOptions -Djdk.net.unixdomain.tmpdir=`"$socketDirectory`" -Djava.net.preferIPv4Stack=true"
    $firebaseCommand = Get-Command firebase.cmd -ErrorAction SilentlyContinue
    $firebasePath = if ($firebaseCommand) { $firebaseCommand.Source } else { Join-Path $env:APPDATA 'npm\firebase.cmd' }
    if (!(Test-Path $firebasePath)) { throw 'Chưa có Firebase CLI. Cài bằng npm install -g firebase-tools.' }
    $emulatorArgs = @('emulators:start', '--only', 'auth,firestore,functions,storage', '--project', 'fashion2hand', '--export-on-exit=emulator-data')
    if (Test-Path (Join-Path $PSScriptRoot 'emulator-data/firebase-export-metadata.json')) {
        $emulatorArgs += '--import=emulator-data'
    }
    & $firebasePath @emulatorArgs
    $emulatorExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    $env:PATH = $previousPath
    $env:ENABLE_SIMULATED_PAYMENTS = $previousPayments
    $env:ENABLE_SIMULATED_SHIPPING = $previousShipping
    $env:FUNCTIONS_DISCOVERY_TIMEOUT = $previousDiscoveryTimeout
    Pop-Location
}
exit $emulatorExitCode
