[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$LegacyDump,

    [Parameter(Mandatory = $true)]
    [string]$LegacyUploadsPath,

    [string]$EnvFile = "./.env",
    [string]$BackupDirectory = "./cutover-backups",

    [Parameter(Mandatory = $true)]
    [switch]$LegacyBackendStopped
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $LegacyBackendStopped) {
    throw "Stop the legacy backend first, then pass -LegacyBackendStopped."
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envPath = (Resolve-Path -LiteralPath $EnvFile).Path
$dumpPath = (Resolve-Path -LiteralPath $LegacyDump).Path
$uploadsPath = (Resolve-Path -LiteralPath $LegacyUploadsPath).Path

if (-not (Test-Path -LiteralPath $dumpPath -PathType Leaf)) {
    throw "LegacyDump must be a readable SQL file."
}
if (-not (Test-Path -LiteralPath $uploadsPath -PathType Container)) {
    throw "LegacyUploadsPath must be a readable directory."
}
$envText = Get-Content -Raw -Encoding utf8 -LiteralPath $envPath
if ($envText -match "(?m)=CHANGE_ME") {
    throw "The selected .env file still contains CHANGE_ME secrets."
}

function Get-RequiredEnvValue {
    param([string]$Name)
    $match = [regex]::Match($envText, "(?m)^$([regex]::Escape($Name))=(.*)$")
    if (-not $match.Success -or [string]::IsNullOrWhiteSpace($match.Groups[1].Value)) {
        throw "$Name must be present and non-empty in the selected .env file."
    }
    return $match.Groups[1].Value.Trim()
}

function Assert-JwtKeyPair {
    $privateValue = Get-RequiredEnvValue "JWT_PRIVATE_KEY"
    $publicValue = Get-RequiredEnvValue "JWT_PUBLIC_KEY"
    try {
        $privateBytes = [Convert]::FromBase64String($privateValue)
        $publicBytes = [Convert]::FromBase64String($publicValue)
    }
    catch {
        throw "JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be valid single-line Base64 values."
    }

    $keyCheckPath = Join-Path ([IO.Path]::GetTempPath()) ("sic-jwt-check-" + [guid]::NewGuid())
    New-Item -ItemType Directory -Path $keyCheckPath | Out-Null
    try {
        $privatePath = Join-Path $keyCheckPath "private.der"
        $derivedPublicPath = Join-Path $keyCheckPath "derived-public.der"
        [IO.File]::WriteAllBytes($privatePath, $privateBytes)
        & openssl pkey -inform DER -in $privatePath -pubout -outform DER -out $derivedPublicPath 2>$null
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $derivedPublicPath -PathType Leaf)) {
            throw "JWT_PRIVATE_KEY is not a readable PKCS#8 RSA private key."
        }
        $derivedPublic = [IO.File]::ReadAllBytes($derivedPublicPath)
        if ([Convert]::ToBase64String($derivedPublic) -ne [Convert]::ToBase64String($publicBytes)) {
            throw "JWT_PUBLIC_KEY does not match JWT_PRIVATE_KEY."
        }
    }
    finally {
        Remove-Item -LiteralPath $keyCheckPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Assert-JwtKeyPair
$unsafeDumpDirective = Select-String -Path $dumpPath -Pattern '^\s*(CREATE\s+DATABASE|USE\s+`?[^;]+)' -CaseSensitive:$false
if ($unsafeDumpDirective) {
    throw "The dump must not contain CREATE DATABASE or USE directives; it is imported only into sic_legacy."
}
if (-not (Select-String -Quiet -Path $dumpPath -Pattern '^\s*(CREATE TABLE|INSERT INTO)' -CaseSensitive:$false)) {
    throw "The dump does not appear to contain schema/data statements."
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose --env-file $envPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Wait-ServiceHealthy {
    param([string]$Service, [int]$TimeoutSeconds = 600)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $containerId = (& docker compose --env-file $envPath ps -q $Service).Trim()
        if ($LASTEXITCODE -eq 0 -and $containerId) {
            $health = (& docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $containerId).Trim()
            if ($LASTEXITCODE -eq 0 -and $health -eq 'healthy') {
                Write-Host "$Service is healthy."
                return
            }
        }
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for $Service to become healthy."
}

$backupRoot = if ([IO.Path]::IsPathRooted($BackupDirectory)) {
    [IO.Path]::GetFullPath($BackupDirectory)
} else {
    [IO.Path]::GetFullPath((Join-Path $projectRoot $BackupDirectory))
}
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupPath = Join-Path $backupRoot $timestamp
New-Item -ItemType Directory -Force -Path $backupPath | Out-Null
$dumpBackup = Join-Path $backupPath "sic-legacy.sql"
$uploadsBackup = Join-Path $backupPath "legacy-uploads.zip"
Copy-Item -LiteralPath $dumpPath -Destination $dumpBackup
Compress-Archive -LiteralPath $uploadsPath -DestinationPath $uploadsBackup -CompressionLevel Optimal
$dumpHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $dumpBackup).Hash.ToLowerInvariant()
$uploadsHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $uploadsBackup).Hash.ToLowerInvariant()
@(
    "createdAt=$((Get-Date).ToString('o'))"
    "sourceDump=$dumpPath"
    "sourceUploads=$uploadsPath"
    "dumpBackup=$dumpBackup"
    "dumpSha256=$dumpHash"
    "uploadsBackup=$uploadsBackup"
    "uploadsArchiveSha256=$uploadsHash"
) | Set-Content -Encoding utf8 -LiteralPath (Join-Path $backupPath "backup-manifest.txt")
Write-Host "Immutable cutover inputs copied to $backupPath"

$previousUploadsOverride = $env:LEGACY_UPLOADS_PATH
$previousBootstrapUser = $env:BOOTSTRAP_ADMIN_USERNAME
$env:LEGACY_UPLOADS_PATH = $uploadsPath
$env:BOOTSTRAP_ADMIN_USERNAME = ""

try {
    Push-Location $projectRoot
    try {
        Invoke-Compose up -d mysql rabbitmq redis minio discovery-server discovery-peer config-server
        Wait-ServiceHealthy mysql
        Wait-ServiceHealthy minio
        Wait-ServiceHealthy discovery-server
        Wait-ServiceHealthy discovery-peer
        Wait-ServiceHealthy config-server

        Invoke-Compose exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE IF NOT EXISTS sic_legacy CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"'
        $legacyTableCount = (& docker compose --env-file $envPath exec -T mysql sh -lc 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=''sic_legacy''"').Trim()
        if ($LASTEXITCODE -ne 0) { throw "Unable to inspect the sic_legacy staging schema." }
        if ([int]$legacyTableCount -gt 0) {
            throw "sic_legacy already contains tables. Use a clean Docker volume or remove only that staging schema after review."
        }
        Invoke-Compose cp $dumpPath mysql:/tmp/sic-legacy.sql
        Invoke-Compose exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" sic_legacy < /tmp/sic-legacy.sql'

        Invoke-Compose stop gateway-service notification-service material-service workflow-service project-service iam-service
        & python (Join-Path $PSScriptRoot 'verify_schema.py') --env-file $envPath --report-path (Join-Path $backupPath 'schema-before-import.json')
        if ($LASTEXITCODE -ne 0) { throw 'Destination schema verification failed; import was not started.' }

        Invoke-Compose --profile migration run --rm migration-tool prepare-files
        Invoke-Compose cp (Join-Path $projectRoot 'infra/migration/import-legacy.sql') mysql:/tmp/import-legacy.sql
        Invoke-Compose exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/import-legacy.sql'

        Invoke-Compose cp (Join-Path $projectRoot 'infra/mysql/seed/workflow-missing-assets.sql') mysql:/tmp/workflow-missing-assets.sql
        Invoke-Compose exec -T mysql sh -lc 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot sic_workflow < /tmp/workflow-missing-assets.sql'
        & python (Join-Path $PSScriptRoot 'verify_schema.py') --env-file $envPath --report-path (Join-Path $backupPath 'schema-after-import.json')
        if ($LASTEXITCODE -ne 0) { throw 'Destination schema verification failed after import; services remain stopped.' }

        Invoke-Compose cp (Join-Path $projectRoot 'infra/migration/verify-legacy-import.sql') mysql:/tmp/verify-legacy-import.sql
        $verificationReport = Join-Path $backupPath "database-verification.txt"
        & docker compose --env-file $envPath exec -T mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < /tmp/verify-legacy-import.sql' |
            Tee-Object -FilePath $verificationReport
        if ($LASTEXITCODE -ne 0) { throw "Database verification failed. See $verificationReport" }
        Invoke-Compose --profile migration run --rm migration-tool verify-files

        Invoke-Compose up -d --build iam-service project-service workflow-service material-service notification-service gateway-service
        Wait-ServiceHealthy gateway-service

        $healthBody = Join-Path $backupPath "gateway-health.json"
        $healthCode = (& curl.exe -sS -o $healthBody -w '%{http_code}' http://localhost:8080/actuator/health/readiness).Trim()
        if ($LASTEXITCODE -ne 0 -or $healthCode -ne '200') {
            throw "Gateway readiness smoke test failed with HTTP $healthCode."
        }
        $unauthorizedBody = Join-Path $backupPath "gateway-unauthorized.json"
        $unauthorizedCode = (& curl.exe -sS -o $unauthorizedBody -w '%{http_code}' http://localhost:8080/api/projects).Trim()
        if ($LASTEXITCODE -ne 0 -or $unauthorizedCode -ne '401') {
            throw "Gateway authorization smoke test failed with HTTP $unauthorizedCode."
        }

        Write-Host "Cutover completed. Gateway owns http://localhost:8080."
        Write-Host "Keep $backupPath, the old database, old uploads, and old image until acceptance is complete."
    }
    finally {
        Pop-Location
    }
}
finally {
    $env:LEGACY_UPLOADS_PATH = $previousUploadsOverride
    $env:BOOTSTRAP_ADMIN_USERNAME = $previousBootstrapUser
}
