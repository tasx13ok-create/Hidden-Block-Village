# Verifies a reviewed transfer and creates a separate complete candidate.
# Does not install software, change execution policy, start Minecraft, or deploy.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Contract,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{64}$')]
    [string]$ContractSha256,

    [string]$Source = 'D:\Codex\2026-11-08\thi\work\actual-bisect-server-stage',

    [string]$Destination = 'D:\Codex\Vale-Export',

    [string]$Launcher
)

$ErrorActionPreference = 'Stop'
foreach ($requiredTool in @('prepare_connected_release.py', 'assemble_release.py', 'release_pack.py', 'package_bootstrap.py')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $requiredTool) -PathType Leaf)) {
        throw 'Keep Resume-Vale.ps1 and the four Python tools in the same tools folder.'
    }
}
$sourceItem = Get-Item -LiteralPath $Source
$contractItem = Get-Item -LiteralPath $Contract
if (-not $sourceItem.PSIsContainer -or $contractItem.PSIsContainer) {
    throw 'Source must be the complete server directory; Contract must be the reviewed JSON file.'
}
$pythonCommand = Get-Command 'py.exe' -CommandType Application -ErrorAction SilentlyContinue
$pythonPrefix = @('-3')
if ($null -eq $pythonCommand) {
    $pythonCommand = Get-Command 'python.exe' -CommandType Application -ErrorAction SilentlyContinue
    $pythonPrefix = @()
}
if ($null -eq $pythonCommand) {
    throw 'Python 3.8 or later is required. No software was downloaded or installed.'
}
$arguments = $pythonPrefix + @(
    (Join-Path $PSScriptRoot 'prepare_connected_release.py'),
    '--source', $sourceItem.FullName,
    '--contract', $contractItem.FullName,
    '--contract-sha256', $ContractSha256,
    '--export-root', $Destination
)
if ($Launcher) {
    $launcherItem = Get-Item -LiteralPath $Launcher
    if ($launcherItem.PSIsContainer) {
        throw 'Launcher must identify the exact reviewed base JAR.'
    }
    $arguments += @('--launcher', $launcherItem.FullName)
}
Write-Host 'Verifying the fixed source inventory and restoring reviewed artifacts.'
Write-Host 'The original server is read-only input. Keep it stopped throughout assembly.'
& $pythonCommand.Source @arguments
$prepareExit = $LASTEXITCODE
if ($prepareExit -ne 0) {
    [Console]::Error.WriteLine("Preparation stopped (exit $prepareExit). Keep incomplete output for inspection; do not upload it.")
    exit $prepareExit
}
Write-Host 'The complete candidate is assembled. ASSEMBLY.json records its bytes; runtime checks remain pending.'
