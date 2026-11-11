param(
    [string]$EnvFile = (Join-Path $PSScriptRoot "..\.env")
)

$ErrorActionPreference = "Stop"
$sandboxBaseUrl = "https://api-m.sandbox.paypal.com"
Add-Type -AssemblyName System.Net.Http

function Read-DotEnvValues([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.Length -ge 2) {
                $first = $value.Substring(0, 1)
                $last = $value.Substring($value.Length - 1, 1)
                if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
            }
            $values[$name] = $value
        }
    }
    return $values
}

function Get-ConfiguredValue([string[]]$Names, [hashtable]$DotEnv, [string]$EnvFilePath) {
    foreach ($name in $Names) {
        $processValue = [Environment]::GetEnvironmentVariable($name, "Process")
        if (-not [string]::IsNullOrWhiteSpace($processValue)) {
            return [pscustomobject]@{ Value = $processValue.Trim(); Name = $name; Source = "process environment" }
        }
        if ($DotEnv.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace($DotEnv[$name])) {
            return [pscustomobject]@{ Value = ([string]$DotEnv[$name]).Trim(); Name = $name; Source = $EnvFilePath }
        }
    }
    return $null
}

$dotEnv = Read-DotEnvValues $EnvFile
$clientIdConfig = Get-ConfiguredValue -Names @("PAYPAL_CLIENT_ID", "CLIENT_ID") -DotEnv $dotEnv -EnvFilePath $EnvFile
$clientSecretConfig = Get-ConfiguredValue -Names @("PAYPAL_CLIENT_SECRET", "CLIENT_SECRET") -DotEnv $dotEnv -EnvFilePath $EnvFile
$baseUrlConfig = Get-ConfiguredValue -Names @("PAYPAL_BASE_URL") -DotEnv $dotEnv -EnvFilePath $EnvFile
$clientId = $clientIdConfig.Value
$clientSecret = $clientSecretConfig.Value
$configuredBaseUrl = $baseUrlConfig.Value

if ([string]::IsNullOrWhiteSpace($clientId) -or [string]::IsNullOrWhiteSpace($clientSecret)) {
    Write-Output "ERROR: PayPal credentials are missing. Set PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET in the process environment or in $EnvFile."
    exit 2
}

if (-not [string]::IsNullOrWhiteSpace($configuredBaseUrl) -and $configuredBaseUrl.TrimEnd('/') -ne $sandboxBaseUrl) {
    Write-Output "ERROR: This diagnostic is Sandbox-only. Set PAYPAL_BASE_URL to $sandboxBaseUrl (or remove it to use the Sandbox default)."
    exit 2
}

Write-Output "Credential source: $($clientIdConfig.Source) ($($clientIdConfig.Name)) and $($clientSecretConfig.Source) ($($clientSecretConfig.Name))."

$httpClient = [System.Net.Http.HttpClient]::new()
try {
    $credentialBytes = [System.Text.Encoding]::UTF8.GetBytes("${clientId}:${clientSecret}")
    $basicCredentials = [Convert]::ToBase64String($credentialBytes)
    $httpClient.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Basic", $basicCredentials)

    $form = [System.Net.Http.StringContent]::new(
        "grant_type=client_credentials",
        [System.Text.Encoding]::ASCII,
        "application/x-www-form-urlencoded"
    )
    $response = $httpClient.PostAsync("$sandboxBaseUrl/v1/oauth2/token", $form).GetAwaiter().GetResult()
    $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()

    if ($response.IsSuccessStatusCode) {
        $tokenResponse = $responseText | ConvertFrom-Json
        if ([string]::IsNullOrWhiteSpace($tokenResponse.access_token)) {
            Write-Output "ERROR: PayPal responded successfully but did not return an access token. No token was displayed or saved."
            exit 1
        }
        Write-Output "PASS: PayPal Sandbox accepted the configured client credentials."
        Write-Output "The temporary OAuth token was kept in memory only and was not displayed or saved."
        exit 0
    }

    $errorResponse = $null
    try { $errorResponse = $responseText | ConvertFrom-Json } catch { }
    $errorCode = if ($errorResponse.error) { [string]$errorResponse.error } else { "HTTP $([int]$response.StatusCode)" }
    $description = if ($errorResponse.error_description) { [string]$errorResponse.error_description } else { "PayPal did not provide an error description." }

    Write-Output "FAIL: PayPal Sandbox rejected the credentials ($errorCode)."
    Write-Output $description
    Write-Output "Check that the Client ID and Secret are from the same Sandbox REST app, and that they were copied without extra characters."
    exit 1
}
catch {
    Write-Output "ERROR: Could not contact PayPal Sandbox: $($_.Exception.Message)"
    exit 3
}
finally {
    if ($null -ne $httpClient) { $httpClient.Dispose() }
}
