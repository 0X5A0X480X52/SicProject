#requires -Version 5.1
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)][ValidateSet('Status', 'Stop')][string]$Action,
    [string]$ProjectName = 'sic-cloud',
    [string]$EnvFile,
    [string[]]$ComposeFile,
    [string]$ReplicaConfig,
    [string[]]$Replicas,
    [string]$GatewayBaseUrl,
    [ValidateRange(1, 60)][int]$TimeoutSeconds = 30,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$python = (Get-Command python -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$arguments = @((Join-Path $PSScriptRoot ($Action.ToLowerInvariant() + '_backend.py')), '--project-name', $ProjectName, '--timeout', [string]$TimeoutSeconds)
if ($EnvFile) { $arguments += @('--env-file', (Resolve-Path -LiteralPath $EnvFile).Path) }
foreach ($file in $ComposeFile) { $arguments += @('--compose-file', (Resolve-Path -LiteralPath $file).Path) }
if ($ReplicaConfig) { $arguments += @('--replica-config', (Resolve-Path -LiteralPath $ReplicaConfig).Path) }
foreach ($replica in $Replicas) { $arguments += @('--replicas', $replica) }
if ($GatewayBaseUrl) { $arguments += @('--gateway-base-url', $GatewayBaseUrl) }
if ($DryRun -or ($Action -eq 'Stop' -and -not $PSCmdlet.ShouldProcess($ProjectName, 'Stop all owned replicas and preserve data volumes'))) { $arguments += '--dry-run' }
& $python @arguments
exit $LASTEXITCODE
