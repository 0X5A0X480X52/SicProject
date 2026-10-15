#requires -Version 5.1
[CmdletBinding()]
param(
    [ValidateSet('Fresh', 'History')][string]$Mode,
    [ValidatePattern('^[a-z0-9][a-z0-9_-]*$')][string]$ProjectName = 'sic-cloud',
    [string]$EnvFile,
    [PSCredential]$AdminCredential,
    [string]$ResetConfirmation,
    [switch]$SkipBuild,
    [ValidateRange(30, 7200)][int]$StartupTimeoutSeconds = 600,
    [string[]]$ComposeFile,
    [string]$ReplicaConfig,
    [string[]]$Replicas,
    [string]$GatewayBaseUrl,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$pythonScript = Join-Path $PSScriptRoot 'start_backend.py'
if (Get-Command py -CommandType Application -ErrorAction SilentlyContinue) {
    $pyLauncher = (Get-Command py -CommandType Application | Select-Object -First 1).Source
    & $pyLauncher -3.12 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)" 2>$null
    if ($LASTEXITCODE -eq 0) { $python = $pyLauncher; $prefix = @('-3.12') }
}
if (-not $python -and (Get-Command python -CommandType Application -ErrorAction SilentlyContinue)) {
    $python = (Get-Command python -CommandType Application | Select-Object -First 1).Source; $prefix = @()
}
if (-not $python) { throw 'Python 3.11+ is required. Install Python and retry.' }
$arguments = @($pythonScript, '--project-name', $ProjectName, '--startup-timeout', [string]$StartupTimeoutSeconds)
if ($Mode) { $arguments += @('--mode', $Mode.ToLowerInvariant()) }
if ($EnvFile) { $arguments += @('--env-file', (Resolve-Path -LiteralPath $EnvFile).Path) }
if ($ResetConfirmation) { $arguments += @('--reset-confirmation', $ResetConfirmation) }
if ($SkipBuild) { $arguments += '--skip-build' }
foreach ($file in $ComposeFile) { $arguments += @('--compose-file', (Resolve-Path -LiteralPath $file).Path) }
if ($ReplicaConfig) { $arguments += @('--replica-config', (Resolve-Path -LiteralPath $ReplicaConfig).Path) }
foreach ($replica in $Replicas) { $arguments += @('--replicas', $replica) }
if ($GatewayBaseUrl) { $arguments += @('--gateway-base-url', $GatewayBaseUrl) }
if ($DryRun) { $arguments += '--dry-run' }
$previousUser = $env:SIC_STARTUP_ADMIN_USERNAME; $previousPassword = $env:SIC_STARTUP_ADMIN_PASSWORD
$passwordPointer = [IntPtr]::Zero
try {
    if ($AdminCredential) {
        $env:SIC_STARTUP_ADMIN_USERNAME = $AdminCredential.UserName
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminCredential.Password)
        $env:SIC_STARTUP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    & $python @prefix @arguments
    $pythonExitCode = $LASTEXITCODE
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    $env:SIC_STARTUP_ADMIN_USERNAME = $previousUser; $env:SIC_STARTUP_ADMIN_PASSWORD = $previousPassword
}
exit $pythonExitCode
