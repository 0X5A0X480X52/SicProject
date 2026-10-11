#requires -Version 5.1
[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [switch]$ContainersOnly,
    [ValidateRange(1, 60)][int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'
$cloudRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repositoryRoot = Split-Path -Parent $cloudRoot
$hadFailure = $false

function Test-PathWithin {
    param([string]$Candidate, [string]$Root)
    if ([string]::IsNullOrWhiteSpace($Candidate)) { return $false }
    try {
        $candidatePath = [IO.Path]::GetFullPath($Candidate).TrimEnd('\', '/')
        $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
        return $candidatePath.Equals($rootPath, [StringComparison]::OrdinalIgnoreCase) -or
            $candidatePath.StartsWith($rootPath + '\', [StringComparison]::OrdinalIgnoreCase)
    } catch { return $false }
}

# Inspect only Compose ownership labels, never container environment or secrets.
if (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue) {
    try {
        $runningIds = @(& docker ps --quiet)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot query Docker. Check Docker Desktop.' }
        $targets = @()
        foreach ($containerId in $runningIds) {
            $labelJson = & docker inspect --format '{{json .Config.Labels}}' $containerId
            if ($LASTEXITCODE -ne 0) { throw "Cannot inspect container $containerId" }
            $labels = $labelJson | ConvertFrom-Json
            $project = $labels.'com.docker.compose.project'
            if (-not $project) { continue }
            $owned = Test-PathWithin $labels.'com.docker.compose.project.working_dir' $cloudRoot
            if (-not $owned) {
                foreach ($configFile in ([string]$labels.'com.docker.compose.project.config_files' -split ',')) {
                    if (Test-PathWithin $configFile $cloudRoot) { $owned = $true; break }
                }
            }
            if ($owned) {
                Write-Host "Found container: project=$project service=$($labels.'com.docker.compose.service') id=$containerId"
                $targets += $containerId
            }
        }
        if ($targets.Count -eq 0) { Write-Host 'No running containers owned by this Cloud repository.' }
        elseif ($PSCmdlet.ShouldProcess(($targets -join ', '), 'Stop repository containers; preserve containers and volumes')) {
            & docker stop --time $TimeoutSeconds @targets
            if ($LASTEXITCODE -ne 0) { throw 'Some containers could not be stopped.' }
        }
    } catch {
        Write-Warning $_.Exception.Message
        $hadFailure = $true
    }
} else {
    Write-Warning 'Docker was not found; containers could not be checked.'
    $hadFailure = $true
}

if (-not $ContainersOnly) {
    try {
        $processRoots = @($cloudRoot, (Join-Path $repositoryRoot 'SicProjectIS_fronted'), (Join-Path $repositoryRoot 'SicProjectIS_backend'))
        $ownedProcesses = @()
        $processes = @(Get-CimInstance Win32_Process -Filter "Name='node.exe' OR Name='java.exe' OR Name='javaw.exe'")
        foreach ($process in $processes) {
            if ([string]::IsNullOrWhiteSpace($process.CommandLine)) { continue }
            $command = $process.CommandLine.Replace('/', '\')
            foreach ($processRoot in $processRoots) {
                # Require an absolute repository directory with a path boundary.
                if ($command.IndexOf($processRoot.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -ge 0) {
                    $ownedProcesses += $process
                    break
                }
            }
        }
        if ($ownedProcesses.Count -eq 0) { Write-Host 'No identifiable repository Node/Java processes.' }
        foreach ($process in $ownedProcesses) {
            $processIdentifier = [int]$process.ProcessId
            Write-Host "Found local process: $($process.Name) id=$processIdentifier"
            if ($PSCmdlet.ShouldProcess("$($process.Name) PID $processIdentifier", 'Stop repository process')) {
                # Recheck identity so a reused PID cannot stop an unrelated process.
                $current = Get-CimInstance Win32_Process -Filter "ProcessId=$processIdentifier"
                if ($current -and $current.CreationDate -eq $process.CreationDate -and $current.CommandLine -eq $process.CommandLine) {
                    Stop-Process -Id $processIdentifier -ErrorAction Stop
                }
            }
        }
    } catch {
        Write-Warning "Local processes could not all be checked/stopped: $($_.Exception.Message)"
        $hadFailure = $true
    }
}

Write-Host 'Finished. Docker Desktop, containers, volumes and database files are preserved.'
Write-Host 'Local processes without an absolute repository path cannot be identified; stop those in their launch terminal.'
if ($hadFailure) { exit 1 }
exit 0
