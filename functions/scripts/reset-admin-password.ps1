param([switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$cliCommand = Get-Command firebase.cmd -ErrorAction Stop
$cliRoot = Join-Path (Split-Path $cliCommand.Source -Parent) 'node_modules/firebase-tools'
if (-not (Test-Path (Join-Path $cliRoot 'lib/auth.js'))) { throw 'Cannot locate Firebase CLI installation.' }
$nodeCommand = Get-Command node -ErrorAction Stop
$start = New-Object Diagnostics.ProcessStartInfo
$start.FileName = $nodeCommand.Source
$start.Arguments = '"' + (Join-Path $PSScriptRoot 'reset-admin-password.cjs') + '"'
if ($CheckOnly) { $start.Arguments += ' --check' }
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardInput = $true
$start.EnvironmentVariables['FASHION2HAND_FIREBASE_CLI'] = $cliRoot
$first = $null; $second = $null; $body = $null; $pointer = [IntPtr]::Zero
try {
    if (-not $CheckOnly) {
        Write-Host 'Cloud project fashion2hand: change password ONLY for existing admin@fashion2hand.test.'
        $first = Read-Host 'New Firebase Admin password (minimum 6 characters)' -AsSecureString
        $second = Read-Host 'Confirm new password' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($first)
        $plainFirst = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $pointer = [IntPtr]::Zero
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($second)
        $plainSecond = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $pointer = [IntPtr]::Zero
        if ($plainFirst -cne $plainSecond) { throw 'Passwords do not match. Nothing changed.' }
        if ($plainFirst.Length -lt 6) { throw 'Password must contain at least 6 characters. Nothing changed.' }
        $body = @{password=$plainFirst} | ConvertTo-Json -Compress
        $plainFirst = $null; $plainSecond = $null
    }
    $child = New-Object Diagnostics.Process
    $child.StartInfo = $start
    [void]$child.Start()
    try {
        if (-not $CheckOnly) {
            $bytes = [Text.Encoding]::UTF8.GetBytes($body)
            try { $child.StandardInput.BaseStream.Write($bytes,0,$bytes.Length); $child.StandardInput.BaseStream.Flush() }
            finally { [Array]::Clear($bytes,0,$bytes.Length) }
        }
        $child.StandardInput.Close()
        $body = $null
        $child.WaitForExit()
        if ($child.ExitCode -ne 0) { throw 'Admin password operation failed. See the safe error code above.' }
    } finally { $child.Dispose() }
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $plainFirst = $null; $plainSecond = $null; $body = $null
    if ($first) { $first.Dispose() }; if ($second) { $second.Dispose() }
}
