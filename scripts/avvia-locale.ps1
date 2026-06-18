<#
Avvia in un colpo l'intero ambiente di sviluppo locale:
1. infrastruttura Docker (Postgres, Kafka, Kafka UI, MinIO)
2. backend (mvnw.cmd spring-boot:run, in una finestra dedicata)
3. frontend demo protocollo-frontend, se trovato (npm run dev, finestra dedicata)

Uso:
  ./scripts/avvia-locale.ps1
  ./scripts/avvia-locale.ps1 -FrontendPath "D:\altro\path\protocollo-frontend"
  ./scripts/avvia-locale.ps1 -SkipFrontend
#>
param(
    [string]$FrontendPath = (Join-Path $PSScriptRoot "..\..\protocollo-frontend"),
    [switch]$SkipFrontend
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

Write-Host "== 1/4 Avvio infrastruttura Docker (Postgres, Kafka, Kafka UI, MinIO) ==" -ForegroundColor Cyan
Push-Location $repoRoot
docker compose up -d
Pop-Location

Write-Host "== 2/4 Attendo che Postgres sia pronto ==" -ForegroundColor Cyan
$postgresReady = $false
for ($i = 1; $i -le 30; $i++) {
    $status = docker inspect --format='{{.State.Health.Status}}' protocollo-postgres 2>$null
    if ($status -eq "healthy") {
        $postgresReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if ($postgresReady) {
    Write-Host "Postgres pronto." -ForegroundColor Green
} else {
    Write-Warning "Postgres non risulta 'healthy' dopo 60s, procedo comunque."
}

Write-Host "== 3/4 Avvio backend (nuova finestra: mvnw.cmd spring-boot:run) ==" -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$repoRoot'; .\mvnw.cmd spring-boot:run"

Write-Host "Attendo che il backend risponda su http://localhost:8080/actuator/health ..."
$backendUp = $false
for ($i = 1; $i -le 60; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -TimeoutSec 2
        if ($resp.status -eq "UP") { $backendUp = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
}
if ($backendUp) {
    Write-Host "Backend pronto." -ForegroundColor Green
} else {
    Write-Warning "Backend non ancora pronto dopo 2 minuti: controlla la finestra del backend per eventuali errori."
}

if (-not $SkipFrontend) {
    if (Test-Path $FrontendPath) {
        Write-Host "== 4/4 Avvio frontend (nuova finestra: npm run dev) ==" -ForegroundColor Cyan
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$FrontendPath'; npm run dev"
    } else {
        Write-Warning "Frontend non trovato in '$FrontendPath': passo saltato (usa -FrontendPath per indicarne il percorso, o -SkipFrontend per ignorarlo)."
        $SkipFrontend = $true
    }
} else {
    Write-Host "Frontend saltato (-SkipFrontend)." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Fatto. URL utili:" -ForegroundColor Cyan
Write-Host "  Backend:    http://localhost:8080"
Write-Host "  Swagger UI: http://localhost:8080/swagger-ui.html"
Write-Host "  Kafka UI:   http://localhost:8081"
Write-Host "  MinIO:      http://localhost:9001 (minioadmin/minioadmin)"
if (-not $SkipFrontend) {
    Write-Host "  Frontend:   http://localhost:5173"
}
