<#
# Paron Guard — End-to-End Smoke Test ("does the repo actually run?")
#
# Two modes:
#
#   Offline mode (default) — needs ZERO infra. Proves the repo is runnable:
#       1. all 5 Java services COMPILE (.mvnw.cmd validate)
#       2. the fast Java unit tests PASS (skips the 4 pre-existing
#          Supabase-context tests that require env vars)
#       3. the fraud drill passes 8/8 (loads the local model artifact)
#       4. the evaluation harness runs and produces a report
#     Deterministic PASS/FAIL, no Docker, no Supabase, no network.
#
#   --live                 — boots the real stack against Docker/Kafka/Redis +
#                            Supabase env (set-env.ps1) and exercises the HTTP
#                            happy path + live fraud drill. Exits non-zero if
#                            any step fails. Requires the infra to be UP.
#
# Usage:
#   powershell -File scripts/e2e-smoke-test.ps1            # offline
#   powershell -File scripts/e2e-smoke-test.ps1 --live     # full stack
#
# Exit codes:
#   0 = PASS    1 = FAIL
#
# Offline mode also accepts --skip-build to reuse already-built jars when you
# only want to re-verify the risk layer quickly.
#>
param(
    [switch]$live,
    [switch]$skipBuild,
    [switch]$verbose
)

$ErrorActionPreference = "Stop"
$root = (Get-Location).Path
$failures = 0
$checks = 0

function Check($name, $ok) {
    $script:checks++
    $mark = if ($ok) { "[PASS]" } else { "[FAIL]" }
    Write-Output ("{0} {1}" -f $mark, $name)
    if (-not $ok) { $script:failures++ }
}

function Have($cmd) { $null -ne (Get-Command $cmd -ErrorAction SilentlyContinue) }

Write-Output ""
Write-Output "======== Paron Guard E2E Smoke Test ========"
Write-Output ("Repo root: {0}" -f $root)
Write-Output ("Mode:      {0}" -f $(if ($live) { "LIVE (full stack)" } else { "OFFLINE (zero infra)" }))
Write-Output ""

# ── JAVA for the Maven services ────────────────────────────────────────────
$mvnw = Join-Path $root "mvnw.cmd"
if (-not (Test-Path $mvnw)) { Write-Output "mvnw.cmd not found at $mvnw"; exit 1 }

# ---------------------------------------------------------------------------
# PART 1 — every service compiles (both modes)
# ---------------------------------------------------------------------------
Write-Output "--------------------------------------------------------------------------"
Write-Output "1. Compile all Java services (api-gateway, token, ledger, sync, fraud)"
Write-Output "--------------------------------------------------------------------------"
if ($skipBuild) {
    Check "Build skipped (--skip-build)" $true
} else {
    Push-Location $root
    $build = cmd /c "`"$mvnw`" -q -pl api-gateway,token-service,ledger-service,sync-service,fraud-service -am compile 2>&1"
    $code = $LASTEXITCODE
    Pop-Location
    Check "Maven compile of 5 services (exit $code)" ($code -eq 0)
}

# ---------------------------------------------------------------------------
# PART 2 — fast Java unit tests (both modes; excludes the 4 env-context tests)
# ---------------------------------------------------------------------------
Write-Output ""
Write-Output "--------------------------------------------------------------------------"
Write-Output "2. Run fast Java unit tests (excludes pre-existing Supabase-context tests)"
Write-Output "--------------------------------------------------------------------------"
$servicePoms = @(
    @{ Name="token-service";  Test="TokenServiceTest" },
    @{ Name="ledger-service"; Test="MerchantServiceTest,ReservationServiceTest" },
    @{ Name="sync-service";   Test="DisputeAdjudicatorTest,SignatureVerifierTest,SyncServiceTest,SettlementProcessorTest" },
    @{ Name="fraud-service";  Test="FraudScoringServiceTest,FraudControllerTest,DecisionPolicyTest,TokenReuseRuleTest,VelocityRuleTest,AmountAnomalyRuleTest,TemporalImpossibilityRuleTest,TimePatternRuleTest,FraudAlertServiceTest,RiskFeatureBuilderGoldenTest" }
)
foreach ($svc in $servicePoms) {
    $pom = Join-Path $root "$($svc.Name)\pom.xml"
    if (-not (Test-Path $pom)) { Check "$($svc.Name) unit tests (no pom)" $false; continue }
    Push-Location $root
    $out = cmd /c "`"$mvnw`" -q -f `"$($svc.Name)\pom.xml`" test `"-Dtest=$($svc.Test)`" 2>&1"
    $code = $LASTEXITCODE
    Pop-Location
    $ranLines = @($out | Select-String "Tests run:" | ForEach-Object { $_.ToString() })
    $ran = "none"
    if ($ranLines.Count -gt 0) { $ran = ($ranLines -join " | ") }
    $pass = $code -eq 0
    $label = "$($svc.Name) unit tests"
    if (-not $pass) {
        $label = "$($svc.Name) unit tests -> $($ran)"
    }
    Check $label $pass
}

# ---------------------------------------------------------------------------
# PART 3 — the AI risk layer (both modes; no infra needed)
# ---------------------------------------------------------------------------
Write-Output ""
Write-Output "--------------------------------------------------------------------------"
Write-Output "3. AI risk layer: fraud drill + evaluation harness"
Write-Output "--------------------------------------------------------------------------"
$mlDir = Join-Path $root "ml"
Push-Location $mlDir
$drillJson = Join-Path $mlDir "artifacts\fraud_drill_report.json"
$drill = python fraud_drill.py 2>&1
$drillCode = $LASTEXITCODE
$drillPassed = $drillCode -eq 0
if ($drillPassed -and (Test-Path $drillJson)) {
    $drillReport = Get-Content $drillJson | ConvertFrom-Json
    $drillPassed = $drillReport.passed -eq $drillReport.scenarios_total
}
Check ("Fraud drill (8 scenarios)" + $(if ($drillPassed) { " -> PASS" } else { "" })) $drillPassed

$eval = python evaluate.py --model ./artifacts/model.joblib --holdout ./data/holdout/features.csv --threshold 0.37 --out-dir ./artifacts 2>&1
$evalCode = $LASTEXITCODE
$evalPassed = $evalCode -eq 0
# A successful run produces the evidence JSON with the production-decision block
if ($evalPassed -and (Test-Path (Join-Path $mlDir "artifacts\evaluation-evidence.json"))) {
    $ev = Get-Content (Join-Path $mlDir "artifacts\evaluation-evidence.json") | ConvertFrom-Json
    $evalPassed = $evalPassed -and $null -ne $ev.production_decision
}
Check ("Evaluation harness produces report + production-decision block") $evalPassed
Pop-Location

# ---------------------------------------------------------------------------
# PART 4 — optional LIVE stack exercise
# ---------------------------------------------------------------------------
if ($live) {
    Write-Output ""
    Write-Output "--------------------------------------------------------------------------"
    Write-Output "4. LIVE stack: boot services + HTTP happy path (needs Docker + Supabase env)"
    Write-Output "--------------------------------------------------------------------------"
    $dockerOk = $true
    try { docker info *> $null } catch { $dockerOk = $false }
    Check "Docker running" $dockerOk
    if ($dockerOk) {
        $up = docker compose ps --services 2>&1
        Check "Kafka + Redis services defined" ($up | Select-String "kafka|redis" -CaseSensitive:$false | Measure-Object).Count -ge 2
    }

    $envScript = Join-Path $root "set-env.ps1"
    $haveEnv = Test-Path $envScript
    Check "set-env.ps1 present (Supabase creds)" $haveEnv
    if ($haveEnv) { try { . "$envScript" } catch { Check "cold load set-env.ps1" $false } }

    # juice the ports once, then try to reach each
    $proc = @()
    $services = @(
        @{ Name="api-gateway"; Port=8080; Jar="api-gateway\target\api-gateway-1.0.jar" },
        @{ Name="token-service"; Port=8081; Jar="token-service\target\token-service-1.0.jar" },
        @{ Name="ledger-service"; Port=8082; Jar="ledger-service\target\ledger-service-1.0.jar" },
        @{ Name="sync-service"; Port=8083; Jar="sync-service\target\sync-service-1.0.jar" },
        @{ Name="fraud-service"; Port=8084; Jar="fraud-service\target\fraud-service-1.0.jar" }
    )
    foreach ($svc in $services) {
        $jar = Join-Path $root $svc.Jar
        if (-not (Test-Path $jar)) { Check "$($svc.Name) jar present" $false; continue }
        $p = Start-Process powershell -ArgumentList @(
            '-NoExit', '-Command',
            "Set-Location '$root'; . .\set-env.ps1; `$env:JAVA_HOME=$($env:JAVA_HOME); java -jar '$jar' --server.port=$($svc.Port)"
        )
        $proc += $p
    }
    # give Spring a chance to come up
    Start-Sleep -Seconds 25
    foreach ($svc in $services) {
        $uri = "http://localhost:$($svc.Port)/api/v1/$($svc.Name.Replace('-',''))/ping"
        # reach the service's own actuator health instead (hostname differs per svc)
        $ok = $false
        try {
            $healthUri = "http://localhost:$($svc.Port)/actuator/health"
            $r = Invoke-WebRequest -Uri $healthUri -TimeoutSec 5 -UseBasicParsing
            $ok = $r.StatusCode -eq 200
        } catch {}
        Check "$($svc.Name) health $([int]$svc.Port)" $ok
    }

    # model service (python) — boot only if it has deps
    if (Have "uvicorn") {
        $m = Start-Process python -ArgumentList @("-m","uvicorn","app.main:app","--port","8600")
        $proc += $m
        Start-Sleep -Seconds 8
        $ok = $false
        try { $r = Invoke-WebRequest -Uri "http://localhost:8600/healthz" -TimeoutSec 5 -UseBasicParsing; $ok = $r.StatusCode -eq 200 } catch {}
        Check "risk-model-service health :8600" $ok
    } else {
        Check "risk-model-service health :8600 (uvicorn not installed)" $false
    }

    # happy-path spot check through the gateway (create account + issue token)
    $happy = $false
    try {
        $create = Invoke-WebRequest -Uri "http://localhost:8082/api/v1/ledger/accounts/create-test-account" -Method POST -ContentType "application/json" -Body '{"userId":"smoke-user","initialBalance":1000.0}' -UseBasicParsing
        $happy = $create.StatusCode -eq 201
    } catch {}
    Check "Create test account (ledger)" $happy

    # live fraud drill against running fraud-service
    Push-Location $mlDir
    $liveDrill = python fraud_drill.py --endpoint http://localhost:8084 2>&1
    $liveDrillCode = $LASTEXITCODE
    Pop-Location
    Check "Live fraud drill (HTTP :8084)" ($liveDrillCode -eq 0)

    # cleanup background processes
    foreach ($p in $proc) { try { $p.Kill() } catch {} }
}

# ---------------------------------------------------------------------------
# SUMMARY
# ---------------------------------------------------------------------------
Write-Output ""
Write-Output "=========================================================================="
Write-Output ("TOTAL: {0} passed, {1} failed (of {2})" -f ($checks - $failures), $failures, $checks)
if ($failures -eq 0) {
    Write-Output "RESULT: PASS"
    exit 0
} else {
    Write-Output "RESULT: FAIL"
    exit 1
}