param(
    [ValidateSet('ghnAdminShops','ghnConfigureSeller','ghnConfigureParcel')][string]$Action = 'ghnAdminShops',
    [string]$DataFile,
    [switch]$UseEmulators,
    [ValidateSet('Cloud','Emulator')][string]$Target = 'Cloud',
    [Management.Automation.PSCredential]$AdminCredential,
    [switch]$CheckConfig
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$appConfig = Get-Content (Join-Path $repoRoot 'app/google-services.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$projectId = $appConfig.project_info.project_id
if ($projectId -ne 'fashion2hand') { throw 'Unexpected Firebase project; review the app configuration.' }
$gradleSource = Get-Content (Join-Path $repoRoot 'app/build.gradle.kts') -Raw -Encoding UTF8
if ($gradleSource -notmatch '(?m)^\s*applicationId\s*=\s*"([^"\r\n]+)"') { throw 'Cannot determine the Android applicationId.' }
$androidPackage = $Matches[1]
$androidClients = @($appConfig.client | Where-Object { $_.client_info.android_client_info.package_name -ceq $androidPackage })
if ($androidClients.Count -ne 1) { throw 'Expected exactly one Firebase Android client matching applicationId.' }
$publicApiKey = @($androidClients[0].api_key | Where-Object { -not [string]::IsNullOrWhiteSpace($_.current_key) })[0].current_key
if ([string]::IsNullOrWhiteSpace($publicApiKey)) { throw 'The matching Android client has no Firebase API key.' }
$firebaseAliases = Get-Content (Join-Path $repoRoot '.firebaserc') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($firebaseAliases.projects.default -cne $projectId) { throw 'Firebase CLI default project does not match the Android configuration.' }
if ($UseEmulators) {
    if ($PSBoundParameters.ContainsKey('Target') -and $Target -ne 'Emulator') { throw 'Conflicting Target and UseEmulators options.' }
    $Target = 'Emulator'
}
$authEndpoint = if ($Target -eq 'Emulator') { "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$publicApiKey" } else { "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$publicApiKey" }
$callableBase = if ($Target -eq 'Emulator') { "http://127.0.0.1:5001/$projectId/us-central1" } else { "https://us-central1-$projectId.cloudfunctions.net" }
$configSummary = [pscustomobject]@{Project=$projectId;AndroidPackage=$androidPackage;Target=$Target;AuthHost=([uri]$authEndpoint).Authority;FunctionsBase=$callableBase;MatchingApiKeyFound=$true}
if ($CheckConfig) { $configSummary; return }
Write-Host "Project: $projectId | Android package: $androidPackage | Target: $Target"
$requestData = if ($DataFile) { Get-Content -LiteralPath $DataFile -Raw -Encoding UTF8 | ConvertFrom-Json } else { @{} }
# Keep this script ASCII-compatible for Windows PowerShell 5.1 (UTF-8 without BOM).
if ($AdminCredential) { $adminLogin = $AdminCredential }
else {
    $adminEmail = Read-Host 'Fashion2hand Firebase Auth Admin email'
    $adminPassword = Read-Host 'Firebase Auth Admin password (not GHN Token)' -AsSecureString
    $adminLogin = New-Object Management.Automation.PSCredential($adminEmail.Trim(),$adminPassword)
}
if (-not $adminLogin) { throw 'Admin sign-in cancelled.' }
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($adminLogin.Password)
$requestStage = 'Firebase Auth sign-in'
try {
    # Never trim, interpolate into a JSON template or manually escape the password.
    $signInObject = @{ email = $adminLogin.UserName.Trim(); password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer); returnSecureToken = $true }
    $signInBody = $signInObject | ConvertTo-Json -Depth 5 -Compress
    $signInBytes = [Text.Encoding]::UTF8.GetBytes($signInBody)
    $signInObject.password = $null; $signInObject = $null; $signInBody = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer); $passwordPointer = [IntPtr]::Zero
    $adminSession = Invoke-RestMethod -Uri $authEndpoint -Method Post -ContentType 'application/json; charset=utf-8' -Body $signInBytes
    [Array]::Clear($signInBytes,0,$signInBytes.Length); $signInBytes = $null
    $requestStage = 'Firebase Auth token validation'
    if ([string]::IsNullOrWhiteSpace($adminSession.idToken)) { throw 'Missing Firebase ID token.' }
    $jwtParts = $adminSession.idToken.Split('.')
    if ($jwtParts.Count -ne 3) { throw 'Invalid Firebase ID token format.' }
    $jwtPayload = $jwtParts[1].Replace('-','+').Replace('_','/')
    $jwtPayload = $jwtPayload.PadRight($jwtPayload.Length + ((4 - $jwtPayload.Length % 4) % 4),'=')
    $sessionClaims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($jwtPayload)) | ConvertFrom-Json
    # This only catches project mixups. The Function still verifies the token and server-side Admin permissions.
    if ($sessionClaims.aud -cne $projectId -or $sessionClaims.iss -cne "https://securetoken.google.com/$projectId") { throw 'Firebase ID token project mismatch.' }
    Write-Host "Firebase sign-in succeeded; token project matches. Admin claim in fresh token: $($sessionClaims.admin -eq $true)"
    $requestStage = "Functions/$Action"
    $callBody = @{data = $requestData} | ConvertTo-Json -Depth 20 -Compress
    $result = Invoke-RestMethod -Uri "$callableBase/$Action" -Method Post -ContentType 'application/json; charset=utf-8' -Headers @{Authorization="Bearer $($adminSession.idToken)"} -Body ([Text.Encoding]::UTF8.GetBytes($callBody))
    $result.result | ConvertTo-Json -Depth 20
} catch {
    $failureRecord = $_
    $httpStatus = 0
    if ($failureRecord.Exception.Response) { $httpStatus = [int]$failureRecord.Exception.Response.StatusCode }
    $errorPayload = $null
    $safeCode = 'UNKNOWN'
    if ($failureRecord.Exception.Message -eq 'Firebase ID token project mismatch.') { $safeCode = 'PROJECT_MISMATCH' }
    if ($failureRecord.Exception.Message -in @('Missing Firebase ID token.','Invalid Firebase ID token format.')) { $safeCode = 'INVALID_AUTH_RESPONSE' }
    $rawError = $failureRecord.ErrorDetails.Message
    if (-not $rawError -and $failureRecord.Exception.Response) {
        try {
            if ($failureRecord.Exception.Response -is [System.Net.HttpWebResponse]) {
                $errorReader = New-Object IO.StreamReader($failureRecord.Exception.Response.GetResponseStream())
                try { $rawError = $errorReader.ReadToEnd() } finally { $errorReader.Dispose() }
            } elseif ($failureRecord.Exception.Response.Content) {
                $rawError = $failureRecord.Exception.Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            }
        } catch { $rawError = $null }
    }
    if ($rawError) { try { $errorPayload = $rawError | ConvertFrom-Json } catch { $errorPayload = $null } }
    $authCodes = @('INVALID_LOGIN_CREDENTIALS','EMAIL_NOT_FOUND','INVALID_PASSWORD','USER_DISABLED','OPERATION_NOT_ALLOWED','TOO_MANY_ATTEMPTS_TRY_LATER','MISSING_PASSWORD','INVALID_EMAIL','INVALID_API_KEY','API_KEY_INVALID','MFA_REQUIRED')
    $functionCodes = @('UNAUTHENTICATED','PERMISSION_DENIED','NOT_FOUND','FAILED_PRECONDITION','UNAVAILABLE','INTERNAL','INVALID_ARGUMENT','RESOURCE_EXHAUSTED','DEADLINE_EXCEEDED')
    if ($errorPayload.error.status -in $functionCodes) { $safeCode = $errorPayload.error.status }
    if ($requestStage -eq 'Firebase Auth sign-in' -and $errorPayload.error.message -match '^([A-Z0-9_]+)(?:\s*:.*)?$') {
        if ($Matches[1] -in $authCodes) { $safeCode = $Matches[1] }
    }
    $hint = 'Check network access and the Firebase project configuration.'
    if ($requestStage -eq 'Firebase Auth sign-in') {
        $hint = 'Use the Fashion2hand Firebase Auth Admin EMAIL and PASSWORD, not your GHN account, Google/Firebase Console login or GHN Token.'
        if ($safeCode -eq 'OPERATION_NOT_ALLOWED') { $hint = 'Email/password sign-in is not enabled for this Firebase Auth account/provider.' }
        if ($safeCode -eq 'USER_DISABLED') { $hint = 'This Firebase Auth user is disabled.' }
        if ($safeCode -eq 'TOO_MANY_ATTEMPTS_TRY_LATER') { $hint = 'Firebase Auth temporarily limited sign-in attempts. Wait before retrying.' }
        if ($httpStatus -eq 0 -and $safeCode -eq 'UNKNOWN') { $hint = 'No HTTP response from Firebase Auth. Check network access, TLS and proxy settings before retrying.' }
    } elseif ($requestStage -eq 'Firebase Auth token validation') {
        $hint = 'Firebase sign-in returned a token with an invalid format or a different project. Check the matching Android client/API key; no Function was called.'
    } elseif ($httpStatus -eq 404 -or $safeCode -eq 'NOT_FOUND') {
        $hint = 'Deploy the GHN Functions to project fashion2hand, region us-central1 before using this command.'
    } elseif ($safeCode -eq 'PERMISSION_DENIED') {
        $hint = 'This Firebase Auth user needs an active server-side admin=true claim AND users/{uid}: role=ADMIN, accountStatus=ACTIVE. No permissions were changed.'
    } elseif ($safeCode -eq 'UNAUTHENTICATED') {
        $hint = 'The deployed Function rejected the Firebase Auth session. Sign in again with the correct project account.'
    } elseif ($safeCode -eq 'FAILED_PRECONDITION') {
        $hint = 'Check GHN staging Token/Shop ID and deployed environment configuration. Do not send the Token in chat.'
        # Match only known setting names. Never print a raw provider response or exception.
        foreach ($setting in @('GHN_TOKEN_ENV','GHN_SHOP_ID','GHN_ENV','GHN_TOKEN')) {
            if ($errorPayload.error.message -like "*$setting*") { $hint = "Check the deployed $setting setting. See GHN_INTEGRATION.md."; break }
        }
    } elseif ($safeCode -eq 'UNAVAILABLE') {
        $hint = 'GHN did not accept/complete the request. Check staging credentials, Shop ID and GHN availability; consult sanitized server logs.'
    } elseif ($httpStatus -ge 500) {
        $hint = 'The Function returned a server error. Check its sanitized logs and runtime Secret binding.'
    }
    # Reconstruct diagnostics from a strict schema, never echo raw message/details.
    $diagnostic = ''
    if ($requestStage -like 'Functions/*') {
        $safeDetails = [ordered]@{}
        $reason = $errorPayload.error.details.reason
        $reasonMessages = @{
            GHN_ENV_INVALID='GHN_ENV must explicitly select staging or production.'
            GHN_TOKEN_ENV_MISMATCH='GHN_TOKEN_ENV does not match GHN_ENV; this label does not validate the actual Token.'
            GHN_TOKEN_MISSING='GHN_TOKEN is empty or unavailable in this Function revision.'
            GHN_AUTH_REJECTED='GHN rejected authentication. Verify the Token for the selected environment and its access restrictions.'
            GHN_REQUEST_REJECTED='GHN rejected the request; raw provider text was suppressed.'
        }
        if ($reason -is [string] -and $reasonMessages.ContainsKey($reason)) {
            $safeDetails.reason = $reason
            $hint = $reasonMessages[$reason]
        } elseif ($safeCode -eq 'FAILED_PRECONDITION' -and $errorPayload.error.message -like '*GHN*Token, Shop ID*') {
            $hint = 'GHN rejected the request. On the currently deployed provider code, FAILED_PRECONDITION here means GHN HTTP 401/403. Shop ID is not required for ghnAdminShops.'
            $safeDetails.reason = 'GHN_AUTH_REJECTED_LEGACY'
        }
        if ($errorPayload.error.details.environment -cin @('staging','production')) { $safeDetails.environment = $errorPayload.error.details.environment }
        if ($errorPayload.error.details.providerHttpStatus -is [int] -and $errorPayload.error.details.providerHttpStatus -ge 100 -and $errorPayload.error.details.providerHttpStatus -le 599) { $safeDetails.providerHttpStatus = $errorPayload.error.details.providerHttpStatus }
        if ($errorPayload.error.details.operation -ceq 'SHOPS_LIST') { $safeDetails.operation = 'SHOPS_LIST' }
        if ($safeDetails.Count) { $diagnostic = ' | Details: ' + ($safeDetails | ConvertTo-Json -Compress) }
    }
    $rawError = $null; $errorPayload = $null
    throw "Stage: $requestStage | HTTP: $httpStatus | Code: $safeCode$diagnostic. Message: $hint"
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    if ($signInBytes) { [Array]::Clear($signInBytes,0,$signInBytes.Length) }
    $signInBody = $null; $signInObject = $null; $signInBytes = $null
    $jwtParts = $null; $jwtPayload = $null; $sessionClaims = $null
    $adminSession = $null; $adminLogin = $null; $adminPassword = $null
}
