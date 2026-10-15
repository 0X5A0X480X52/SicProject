#requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][ValidateSet('balance', 'business', 'fault')][string]$Mode,
    [string]$ProjectName = 'sic-cloud',
    [string]$EnvFile,
    [string[]]$ComposeFile,
    [string]$ReplicaConfig,
    [string[]]$Replicas,
    [string]$GatewayBaseUrl,
    [string]$ReportPath,
    [ValidateRange(1, 300)][int]$RequestTimeoutSeconds = 20,
    [ValidateRange(66, 7200)][int]$RecoveryTimeoutSeconds = 180,
    [PSCredential]$AdminCredential
)
$ErrorActionPreference = 'Stop'
$python = (Get-Command python -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$arguments = @((Join-Path $PSScriptRoot 'verify_load_balancing.py'), '--mode', $Mode, '--project-name', $ProjectName)
$arguments += @('--request-timeout', [string]$RequestTimeoutSeconds, '--recovery-timeout', [string]$RecoveryTimeoutSeconds)
if ($EnvFile) { $arguments += @('--env-file', (Resolve-Path -LiteralPath $EnvFile).Path) }
foreach ($file in $ComposeFile) { $arguments += @('--compose-file', (Resolve-Path -LiteralPath $file).Path) }
if ($ReplicaConfig) { $arguments += @('--replica-config', (Resolve-Path -LiteralPath $ReplicaConfig).Path) }
foreach ($replica in $Replicas) { $arguments += @('--replicas', $replica) }
if ($GatewayBaseUrl) { $arguments += @('--gateway-base-url', $GatewayBaseUrl) }
if ($ReportPath) { $arguments += @('--report-path', $ReportPath) }
$previousUser = $env:SIC_E2E_ADMIN_USERNAME
$previousPassword = $env:SIC_E2E_ADMIN_PASSWORD
$pointer = [IntPtr]::Zero
try {
    if ($AdminCredential) {
        $env:SIC_E2E_ADMIN_USERNAME = $AdminCredential.UserName
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminCredential.Password)
        $env:SIC_E2E_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    & $python @arguments
    $result = $LASTEXITCODE
} finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $env:SIC_E2E_ADMIN_USERNAME = $previousUser
    $env:SIC_E2E_ADMIN_PASSWORD = $previousPassword
}
exit $result
