# Paron demo helper — trigger suspicious transactions so the admin review
# queue has something to show. Hits fraud-service directly (that endpoint is
# intentionally NOT routed through the gateway).
#
#   .\scripts\demo-fraud-trigger.ps1            # default: localhost:8084
#   .\scripts\demo-fraud-trigger.ps1 -Scalar $score
#
# The alert you create shows up in
#   pwa\admin\index.html  -> API base = http://localhost:8080  -> Refresh queue
param(
    [string]$FraudBase = "http://localhost:8084"
)

$Headers = @{ "Content-Type" = "application/json" }

# A token string shared between calls. Real deployments use a JWT; for the
# demo a stable string is enough to exercise the rules. Time is "now" so the
# temporal rules stay quiet.
$Token = "demo.token.$([int](Get-Random -Minimum 100000 -Maximum 999999))"
$Now = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
$Expiry = (Get-Date).AddHours(6).ToString("yyyy-MM-ddTHH:mm:ss")
$UserId = "user_demo_$([int](Get-Random -Minimum 1000 -Maximum 9999))"

function Post-Check($Label, $Body) {
    Write-Host ""
    Write-Host "--- $Label ---"
    try {
        $response = Invoke-RestMethod -Uri "$FraudBase/api/v1/fraud/check" -Method Post -Headers $Headers -Body ($Body | ConvertTo-Json)
        Write-Host ("decision: {0}  reason: {1}  score: {2}" -f $response.decision, $response.reason, $response.score)
    } catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            Write-Host "HTTP $status  (is fraud-service up on $FraudBase ?)"
        } else {
            Write-Host "ERROR: $($_.Exception.Message)  (is fraud-service up on $FraudBase ?)"
        }
    }
}

Write-Host "Demo user: $UserId   token: $Token"
Write-Host "Fraud endpoint: $FraudBase/api/v1/fraud/check"

# 1) Normal payment -> APPROVE (also seeds the token against device_1)
Post-Check "1) NORMAL payment (device_1)" @{
    userId              = $UserId
    deviceTransactionId = "txn-ok-$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    offlineToken        = $Token
    deviceId            = "device_1"
    amount              = 150.00
    merchantId          = "merchant_abc"
    transactedAt        = $Now
    tokenExpiryTime     = $Expiry
}

# 2) SAME token, DIFFERENT device -> TOKEN_REUSE rule fires -> REJECT
Post-Check "2) REPLAYED token on device_2 (should REJECT)" @{
    userId              = $UserId
    deviceTransactionId = "txn-reuse-$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    offlineToken        = $Token
    deviceId            = "device_2"
    amount              = 50.00
    merchantId          = "merchant_abc"
    transactedAt        = $Now
    tokenExpiryTime     = $Expiry
}

# 3) Brand-new user + brand-new device, no history -> HOLD (low-signal guard)
Post-Check "3) NEW device, zero history (should HOLD)" @{
    userId              = "user_never_seen_$([int](Get-Random -Minimum 1000 -Maximum 9999))"
    deviceTransactionId = "txn-new-$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    offlineToken        = "demo.token.$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    deviceId            = "new_device_x9"
    amount              = 50.00
    merchantId          = "merchant_abc"
    transactedAt        = $Now
    tokenExpiryTime     = $Expiry
}

# 4) Clean first-time payment for ANOTHER user -> APPROVE (control case)
Post-Check "4) NORMAL payment, fresh user (control, APPROVE)" @{
    userId              = "user_control_$([int](Get-Random -Minimum 1000 -Maximum 9999))"
    deviceTransactionId = "txn-ctl-$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    offlineToken        = "demo.token.$([int](Get-Random -Minimum 100000 -Maximum 999999))"
    deviceId            = "control_device"
    amount              = 200.00
    merchantId          = "merchant_abc"
    transactedAt        = $Now
    tokenExpiryTime     = $Expiry
}

Write-Host ""
Write-Host "Open pwa\admin\index.html, set API base to http://localhost:8080, click Refresh queue."
Write-Host "You should see: REJECT (replay) + HOLD (new device) in the queue."