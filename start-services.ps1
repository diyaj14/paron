$root = "E:\payment"
Set-Location $root

. .\set-env.ps1
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.1"

$services = @(
    @{ Name = "api-gateway"; Port = 8080; Jar = "api-gateway\target\api-gateway-1.0.jar" },
    @{ Name = "token-service"; Port = 8081; Jar = "token-service\target\token-service-1.0.jar" },
    @{ Name = "ledger-service"; Port = 8082; Jar = "ledger-service\target\ledger-service-1.0.jar" },
    @{ Name = "sync-service"; Port = 8083; Jar = "sync-service\target\sync-service-1.0.jar" },
    @{ Name = "fraud-service"; Port = 8084; Jar = "fraud-service\target\fraud-service-1.0.jar" }
)

foreach ($svc in $services) {
    Start-Process powershell -ArgumentList @(
        '-NoExit',
        '-Command',
        "Set-Location '$root'; . .\\set-env.ps1; `$env:JAVA_HOME='C:\\Program Files\\Java\\jdk-26.0.1'; java -jar '$($svc.Jar)' --server.port=$($svc.Port)"
    )
}

Write-Host "Started service launchers."
