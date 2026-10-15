#requires -Version 5.1
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Low')]
param(
    [ValidateNotNullOrEmpty()][string]$Url = 'http://localhost:5173',
    [ValidateRange(1, 32)][int]$Count = 7,
    [ValidateNotNullOrEmpty()][string]$EdgePath = 'msedge.exe'
)

$ErrorActionPreference = 'Stop'
$targetUri = $null
if (-not [Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$targetUri) -or
    $targetUri.Scheme -notin @('http', 'https')) {
    throw 'Url must be an absolute HTTP or HTTPS address.'
}

$edgeExecutable = $null
$edgeCommand = Get-Command $EdgePath -CommandType Application -ErrorAction SilentlyContinue
if ($edgeCommand) {
    $edgeExecutable = $edgeCommand.Source
} elseif (Test-Path -LiteralPath $EdgePath -PathType Leaf) {
    $edgeExecutable = (Resolve-Path -LiteralPath $EdgePath).Path
} elseif ($EdgePath -eq 'msedge.exe') {
    $candidates = foreach ($installationRoot in @(${env:ProgramFiles(x86)}, $env:ProgramFiles, $env:LOCALAPPDATA)) {
        if ($installationRoot) { Join-Path $installationRoot 'Microsoft\Edge\Application\msedge.exe' }
    }
    $edgeExecutable = $candidates | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
}
if (-not $edgeExecutable) {
    throw 'Microsoft Edge was not found. Supply its executable with -EdgePath.'
}

for ($index = 1; $index -le $Count; $index++) {
    $profileDirectory = Join-Path $env:TEMP ('edge-test-' + [guid]::NewGuid().ToString())
    $arguments = @(
        '--guest',
        "--user-data-dir=`"$profileDirectory`"",
        '--new-window',
        '--no-first-run',
        '--disable-sync',
        "`"$($targetUri.AbsoluteUri)`""
    )
    if ($PSCmdlet.ShouldProcess("Window $index; profile $profileDirectory", "Open Edge at $($targetUri.AbsoluteUri)")) {
        # Visible windows are intentional: this helper is for interactive multi-account testing.
        Start-Process -FilePath $edgeExecutable -ArgumentList $arguments | Out-Null
        Write-Host "Opened window $index/$Count; profile: $profileDirectory"
    }
}
