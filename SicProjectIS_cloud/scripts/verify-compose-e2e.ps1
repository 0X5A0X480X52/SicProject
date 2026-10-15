#requires -Version 5.1
[CmdletBinding(DefaultParameterSetName = 'Credential')]
param(
    [string]$ProjectName = 'sic-cloud', [string]$EnvFile,
    [string]$GatewayBaseUrl = 'http://127.0.0.1:8080',
    [string]$ConfigServerBaseUrl = 'http://127.0.0.1:8888',
    [string]$DiscoveryBaseUrl = 'http://127.0.0.1:8761',
    [string]$ExpectedJwtIssuer = 'sic-iam', [string]$ExpectedJwtAudience = 'sic-api',
    [Parameter(Mandatory = $true, ParameterSetName = 'Plain')][string]$AdminUsername,
    [Parameter(Mandatory = $true, ParameterSetName = 'Plain')][string]$AdminPassword,
    [Parameter(Mandatory = $true, ParameterSetName = 'Credential')][PSCredential]$AdminCredential,
    [int]$StartTimeoutSeconds = 300, [int]$RequestTimeoutSeconds = 20, [string]$ReportPath
)
$ErrorActionPreference = 'Stop'; $pythonScript = Join-Path $PSScriptRoot 'verify_compose_e2e.py'
if (Get-Command py -CommandType Application -ErrorAction SilentlyContinue) {
    $pyLauncher = (Get-Command py -CommandType Application).Source
    & $pyLauncher -3.12 -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 11) else 1)" 2>$null
    if ($LASTEXITCODE -eq 0) { $python = $pyLauncher; $prefix = @('-3.12') }
}
if (-not $python -and (Get-Command python -CommandType Application -ErrorAction SilentlyContinue)) {
    $python = (Get-Command python -CommandType Application).Source; $prefix = @()
}
if (-not $python) { throw 'Python 3.11+ is required. Install Python and retry.' }
if ($AdminCredential) { $AdminUsername = $AdminCredential.UserName }
$arguments = @($pythonScript, '--project-name', $ProjectName, '--admin-username', $AdminUsername,
    '--gateway-base-url', $GatewayBaseUrl, '--config-server-base-url', $ConfigServerBaseUrl,
    '--discovery-base-url', $DiscoveryBaseUrl, '--expected-jwt-issuer', $ExpectedJwtIssuer,
    '--expected-jwt-audience', $ExpectedJwtAudience, '--start-timeout', [string]$StartTimeoutSeconds,
    '--request-timeout', [string]$RequestTimeoutSeconds)
if ($EnvFile) { $arguments += @('--env-file', (Resolve-Path -LiteralPath $EnvFile).Path) }
if ($ReportPath) { $arguments += @('--report-path', $ReportPath) }
$previousPassword = $env:SIC_STARTUP_ADMIN_PASSWORD; $passwordPointer = [IntPtr]::Zero
try {
    if ($AdminCredential) {
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($AdminCredential.Password)
        $env:SIC_STARTUP_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    } else { $env:SIC_STARTUP_ADMIN_PASSWORD = $AdminPassword }
    & $python @prefix @arguments
    $pythonExitCode = $LASTEXITCODE
} finally {
    if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
    $env:SIC_STARTUP_ADMIN_PASSWORD = $previousPassword
}
exit $pythonExitCode
