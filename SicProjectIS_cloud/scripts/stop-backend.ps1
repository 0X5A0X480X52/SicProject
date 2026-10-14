#requires -Version 5.1
$ErrorActionPreference = 'Stop'
try {
    & (Join-Path $PSScriptRoot 'backend-lifecycle.ps1') -Action Stop @args
    exit $LASTEXITCODE
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 1
}
