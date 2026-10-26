# Invoke from the repository root after entering both Secrets privately.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $repoRoot
try {
    $config = Get-Content 'app/google-services.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($config.project_info.project_id -cne 'fashion2hand') { throw 'Unexpected Firebase project.' }
    $targets = @(
        'createVnpayPayment','getVnpayPaymentStatus','reconcileVnpayPayment','vnpayIpn','vnpayReturn',
        'getTransactionCapabilities','createPaidOrder','createCartOrders','cancelOrder','rejectOrder',
        'refreshOrder','simulatePaymentResult','updatePaymentFromProvider','updateOrderStatus',
        'expireUnpaidAndUnconfirmedOrders','createShipment','adminDashboard'
    ) | ForEach-Object { 'functions:' + $_ }
    & firebase.cmd deploy --project fashion2hand --only ($targets -join ',')
    if ($LASTEXITCODE -ne 0) { throw 'Deployment did not complete; do not advertise endpoint readiness.' }
    Write-Host 'Deploy completed. Verify returned IPN and Return URLs in Firebase CLI output.'
} finally { Pop-Location }
