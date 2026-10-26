# Run with Windows PowerShell 5.1 or PowerShell 7. All credentials/HTTP are fixtures.
$ErrorActionPreference = 'Stop'
$targetPath = Join-Path $PSScriptRoot '../scripts/ghn-admin.ps1'
$configPath = Join-Path $PSScriptRoot '../../app/google-services.json'
$app = Get-Content $configPath -Raw -Encoding UTF8 | ConvertFrom-Json
$expectedKey = $app.client[0].api_key[0].current_key
$global:ghnScriptRequests = New-Object 'System.Collections.Generic.List[object]'
$global:ghnScriptCase = 'success'
$global:ghnScriptReadCount = 0
function New-FixtureJwt {
    param([string]$Project = 'fashion2hand')
    $payload = @{aud=$Project;iss="https://securetoken.google.com/$Project";admin=$true} | ConvertTo-Json -Compress
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload)).TrimEnd('=').Replace('+','-').Replace('/','_')
    return "eyJhbGciOiJub25lIn0.$encoded.fixture-not-real"
}
function Read-Host {
    param($Prompt,[switch]$AsSecureString)
    $global:ghnScriptReadCount++
    if($AsSecureString){return ConvertTo-SecureString $global:ghnScriptPassword -AsPlainText -Force}
    return 'fixture@example.test'
}
function Invoke-RestMethod {
    param($Uri,$Method,$ContentType,$Body,$Headers)
    if($Body -isnot [byte[]]){throw 'Expected UTF-8 request bytes'}
    $parsed = [Text.Encoding]::UTF8.GetString($Body) | ConvertFrom-Json
    $global:ghnScriptRequests.Add([pscustomobject]@{Uri=$Uri;Data=$parsed;ContentType=$ContentType;Headers=$Headers})
    $signIn = $Uri -like '*accounts:signInWithPassword*'
    if($signIn -and $global:ghnScriptCase -eq 'wrong-project'){return @{idToken=(New-FixtureJwt 'different-project')}}
    if($signIn -and $global:ghnScriptCase -ne 'auth-error'){return @{idToken=(New-FixtureJwt)}}
    if($global:ghnScriptCase -eq 'success'){return @{result=@{ok=$true}}}
    $errorBody = switch($global:ghnScriptCase){
        'auth-error' {@{error=@{message='INVALID_LOGIN_CREDENTIALS';private='NEVER-ECHO-RAW-RESPONSE'}}}
        'permission-error' {@{error=@{status='PERMISSION_DENIED';message='NEVER-ECHO-RAW-RESPONSE'}}}
        'ghn-auth-error' {@{error=@{status='FAILED_PRECONDITION';message='NEVER-ECHO-RAW-RESPONSE';details=@{reason='GHN_AUTH_REJECTED';environment='staging';providerHttpStatus=401;operation='SHOPS_LIST';Token='NEVER-ECHO-RAW-RESPONSE';Authorization='fixture-not-real'}}}}
        'unsafe-details' {@{error=@{status='FAILED_PRECONDITION';message='NEVER-ECHO-RAW-RESPONSE';details=@{reason='NEVER-ECHO-RAW-RESPONSE';environment='NEVER-ECHO-RAW-RESPONSE';providerHttpStatus='NEVER-ECHO-RAW-RESPONSE';operation='NEVER-ECHO-RAW-RESPONSE'}}}}
        'legacy-ghn-error' {@{error=@{status='FAILED_PRECONDITION';message='GHN fixture Token, Shop ID'}}}
        'missing-ghn-address' {@{error=@{status='FAILED_PRECONDITION';message=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('xJDhu4thIGNo4buJIGNoxrBhIGPDsyBtw6MgR0hOLiBDaOG7jW4gxJHhu4thIGNo4buJIGdpYW8gaMOgbmcgR0hOIHRyb25nIOG7qW5nIGThu6VuZy4='))}}}
    }
    $exception = New-Object Exception('NEVER-ECHO-RAW-RESPONSE')
    $record = New-Object Management.Automation.ErrorRecord($exception,'Fixture',[Management.Automation.ErrorCategory]::InvalidOperation,$null)
    $record.ErrorDetails = New-Object Management.Automation.ErrorDetails(($errorBody | ConvertTo-Json -Depth 5 -Compress))
    throw $record
}
function Assert-True {
    param([bool]$Condition,[string]$Description)
    if(-not $Condition){throw "FAIL: $Description"}
}
$global:ghnScriptPassword = 'fixture-password'
$before = $global:ghnScriptReadCount
$config = & $targetPath -CheckConfig
Assert-True ($config.Project -ceq 'fashion2hand') 'project selection'
Assert-True ($config.AndroidPackage -ceq 'vn.edu.ueh.thudinh.fashion2hand') 'Android client selection'
Assert-True ($config.Target -eq 'Cloud' -and $config.AuthHost -eq 'identitytoolkit.googleapis.com') 'default cloud mode'
Assert-True ($global:ghnScriptReadCount -eq $before -and $global:ghnScriptRequests.Count -eq 0) 'CheckConfig never asks for credentials or contacts servers'
$passwordFixtures = @(
    '  literal $dollar $(not-executed) `backtick "quotes" \backslash % & ; {} []  ',
    ('Unicode-' + [char]0x0111 + [char]0x1EB7 + [char]0x0063 + [char]0x4E2D),
    ('long-' + ('x' * 600) + '-end')
)
foreach($passwordFixture in $passwordFixtures){
    $global:ghnScriptPassword = $passwordFixture
    $global:ghnScriptRequests.Clear()
    & $targetPath -Action ghnAdminShops | Out-Null
    Assert-True ($global:ghnScriptRequests.Count -eq 2) 'exactly sign-in and callable requests'
    $login = $global:ghnScriptRequests[0]
    Assert-True ($login.Data.password -ceq $passwordFixture) 'password preserved exactly through SecureString, BSTR and UTF-8 JSON'
    Assert-True ($login.Data.returnSecureToken -is [bool] -and $login.Data.returnSecureToken) 'returnSecureToken is a JSON boolean'
    Assert-True (([uri]$login.Uri).Host -eq 'identitytoolkit.googleapis.com') 'cloud sign-in endpoint'
    Assert-True ($login.Uri.EndsWith('key='+$expectedKey)) 'API key matches Android client; key never printed'
    Assert-True ($global:ghnScriptRequests[1].Uri -eq 'https://us-central1-fashion2hand.cloudfunctions.net/ghnAdminShops') 'cloud callable project and region'
    Assert-True ($login.ContentType -eq 'application/json; charset=utf-8') 'UTF-8 content type'
}
foreach($targetOption in @('Target','UseEmulators')){
    $global:ghnScriptRequests.Clear()
    if($targetOption -eq 'Target'){& $targetPath -Target Emulator | Out-Null}else{& $targetPath -UseEmulators | Out-Null}
    Assert-True (([uri]$global:ghnScriptRequests[0].Uri).Authority -eq '127.0.0.1:9099') 'emulator Auth endpoint'
    Assert-True ($global:ghnScriptRequests[1].Uri -eq 'http://127.0.0.1:5001/fashion2hand/us-central1/ghnAdminShops') 'emulator Functions endpoint'
}
$global:ghnScriptRequests.Clear()
$caught = ''
try{& $targetPath -Target Cloud -UseEmulators | Out-Null}catch{$caught=$_.Exception.Message}
Assert-True ($caught -like '*Conflicting*' -and $global:ghnScriptRequests.Count -eq 0) 'conflicting environment flags fail before sign-in'
foreach($case in @('auth-error','permission-error','wrong-project','ghn-auth-error','unsafe-details','legacy-ghn-error','missing-ghn-address')){
    $global:ghnScriptCase = $case
    $global:ghnScriptRequests.Clear()
    $caught = ''
    try{& $targetPath | Out-Null}catch{$caught=$_.Exception.Message}
    if($case -eq 'auth-error'){Assert-True ($caught -like '*Firebase Auth sign-in*INVALID_LOGIN_CREDENTIALS*') 'auth error classification'}
    if($case -eq 'permission-error'){Assert-True ($caught -like '*Functions/ghnAdminShops*PERMISSION_DENIED*') 'Admin error classification'}
    if($case -eq 'wrong-project'){Assert-True ($caught -like '*token validation*PROJECT_MISMATCH*' -and $global:ghnScriptRequests.Count -eq 1) 'wrong token project blocked before callable'}
    if($case -eq 'ghn-auth-error'){Assert-True ($caught -like '*GHN_AUTH_REJECTED*401*SHOPS_LIST*') 'safe GHN reason and details displayed'}
    if($case -eq 'legacy-ghn-error'){Assert-True ($caught -like '*GHN_AUTH_REJECTED_LEGACY*') 'deployed legacy error diagnosed without backend deploy'}
    if($case -eq 'missing-ghn-address'){Assert-True ($caught -like '*GHN_ADDRESS_CODES_MISSING*select GHN province/district/ward*') 'missing GHN codes diagnosed without backend deploy'}
    Assert-True (-not $caught.Contains('NEVER-ECHO-RAW-RESPONSE') -and -not $caught.Contains($global:ghnScriptPassword) -and -not $caught.Contains('fixture-not-real')) 'errors do not expose password, token or raw response'
}
Write-Output "PASS: PowerShell $($PSVersionTable.PSVersion): project/key, Cloud/Emulator, SecureString exact password roundtrip, JWT project and safe errors. Mock HTTP only; no real users changed."
